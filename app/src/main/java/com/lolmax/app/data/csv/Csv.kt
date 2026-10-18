package com.lolmax.app.data.csv

import com.lolmax.app.data.local.LolMaxDatabase
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.WorkoutDraft
import com.lolmax.app.data.repository.WorkoutExerciseDraft
import com.lolmax.app.data.repository.WorkoutRepository
import com.lolmax.app.data.repository.WorkoutSetDraft
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvExporter(
    private val db: LolMaxDatabase,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun exportWorkoutsCsv(): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        sb.appendLine(
            "date,title,bodyweight_kg,exercise,set_number,weight_kg,reps,is_warmup," +
                "duration_minutes,start_minute,end_minute",
        )
        val workouts = db.workoutDao().getAllOnce()
        for (w in workouts) {
            val wes = db.workoutExerciseDao().getForWorkout(w.id)
            val allSets =
                if (wes.isEmpty()) emptyList() else db.setDao().getForExercises(wes.map { it.id })
            val duration =
                com.lolmax.app.data.repository.StatsRepository.durationMinutes(w)
            for (we in wes) {
                val exercise = exerciseRepository.getById(we.exerciseId) ?: continue
                val sets = allSets.filter { it.workoutExerciseId == we.id }
                for (s in sets) {
                    sb.append(csv(w.dateEpochDay.let { LocalDate.ofEpochDay(it).toString() }))
                    sb.append(','); sb.append(csv(w.title))
                    sb.append(','); sb.append(w.bodyweightKg?.toString() ?: "")
                    sb.append(','); sb.append(csv(exercise.name))
                    sb.append(','); sb.append((s.orderInExercise + 1).toString())
                    sb.append(','); sb.append(s.weightKg?.toString() ?: "")
                    sb.append(','); sb.append(s.reps.toString())
                    sb.append(','); sb.append(if (s.isWarmup) "1" else "0")
                    sb.append(','); sb.append(duration?.toString() ?: "")
                    sb.append(','); sb.append(w.startMinuteOfDay?.toString() ?: "")
                    sb.append(','); sb.append(w.endMinuteOfDay?.toString() ?: "")
                    sb.appendLine()
                }
            }
        }
        sb.toString()
    }

    /**
     * RFC-4180 quoting plus CSV formula-injection neutralization: cells starting with
     * = + - @ tab or CR are prefixed with an apostrophe so Excel/Sheets treat them as text.
     */
    private fun csv(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in formulaStarters) {
            v = "'$v"
        }
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${v.replace("\"", "\"\"")}\""
        } else v
    }

    private companion object {
        val formulaStarters = charArrayOf('=', '+', '-', '@', '\t', '\r')
    }
}

class CsvImporter(
    private val db: LolMaxDatabase,
    private val exerciseRepository: ExerciseRepository,
) {
    data class ImportResult(
        val workoutsImported: Int,
        val setsImported: Int,
        val unknownExercises: List<String>,
        val skippedDates: Int = 0,
    )

    data class NativeRow(
        val date: LocalDate,
        val exerciseName: String,
        val weightKg: Double?,
        val reps: Int,
        val bodyweightKg: Double?,
        val isWarmup: Boolean,
    )

    /**
     * Auto-detects either the app's own export format or the native
     * Strength Level export:
     *   Date Lifted,Exercise,Weight (kg),Weight (lb),Reps,Bodyweight (kg),
     *   Bodyweight (lb),Percentile (%),Warmup,Time (seconds),Distance (meters)
     */
    suspend fun importWorkoutsCsv(text: String, repo: WorkoutRepository): ImportResult =
        withContext(Dispatchers.Default) {
            val records = splitRecords(text).filter { it.isNotBlank() }
            if (records.isEmpty()) return@withContext ImportResult(0, 0, emptyList())
            val header = parse(records.first()).map { it.lowercase().trim() }
            if (header.any { it == "date lifted" }) {
                importNativeFormat(records, header, repo)
            } else {
                importGenericFormat(records, header, repo)
            }
        }

    // ---- Strength Level native export ----

    private suspend fun importNativeFormat(
        records: List<String>,
        header: List<String>,
        repo: WorkoutRepository,
    ): ImportResult {
        val (rows, _) = parseNativeRows(records, header)
        if (rows.isEmpty()) return ImportResult(0, 0, emptyList())

        val byName = exerciseRepository.getAll()
            .associateBy { it.name.lowercase() }
            .toMutableMap()
        val createdCustom = linkedSetOf<String>()
        val existingSigs = existingSignaturesByDate()

        val byDate = rows.groupBy { it.date }
        var workoutsImported = 0
        var setsImported = 0
        var skippedDates = 0

        for ((date, dateRows) in byDate.toSortedMap()) {
            val epochDay = date.toEpochDay()
            // Content fingerprint: skip only if an identical set of rows already
            // exists on this date (a prior import, or the same session logged
            // manually). A different workout on the date never blocks the import.
            val sig = signatureOf(
                dateRows.map {
                    Triple(it.exerciseName.lowercase(), it.weightKg, it.reps) to it.isWarmup
                },
            )
            if (existingSigs[epochDay]?.contains(sig) == true) {
                skippedDates++
                continue
            }

            val byExercise = dateRows.groupBy { it.exerciseName }
            val drafts = mutableListOf<WorkoutExerciseDraft>()
            for ((exName, exRows) in byExercise) {
                val known = byName[exName.lowercase()]
                val exercise = known ?: exerciseRepository.createCustom(
                    name = exName,
                    bodyPart = guessBodyPart(exName),
                    category = guessCategory(exName),
                    isBodyweight = guessBodyweight(exName),
                ) ?: continue
                if (known == null) {
                    byName[exName.lowercase()] = exercise
                    if (exercise.isCustom) createdCustom += exercise.name
                }
                val sets = exRows.map {
                    WorkoutSetDraft(weightKg = it.weightKg, reps = it.reps, isWarmup = it.isWarmup)
                }
                drafts += WorkoutExerciseDraft(exercise.id, sets)
                setsImported += sets.size
            }
            if (drafts.isEmpty()) continue
            val bodyweight = dateRows.firstOrNull { it.bodyweightKg != null }?.bodyweightKg
            repo.saveDraft(
                WorkoutDraft(
                    title = "Workout",
                    dateEpochDay = epochDay,
                    bodyweightKg = bodyweight,
                    notes = IMPORT_MARKER,
                    exercises = drafts,
                )
            )
            workoutsImported++
        }
        return ImportResult(workoutsImported, setsImported, createdCustom.toList(), skippedDates)
    }

    /**
     * Builds a canonical content signature (per set: exercise, weight, reps, warmup)
     * for every existing workout, grouped by date, used to make imports idempotent.
     */
    private suspend fun existingSignaturesByDate(): Map<Long, Set<String>> {
        val result = mutableMapOf<Long, MutableSet<String>>()
        for (w in db.workoutDao().getAllOnce()) {
            val wes = db.workoutExerciseDao().getForWorkout(w.id)
            if (wes.isEmpty()) continue
            val allSets = db.setDao().getForExercises(wes.map { it.id })
            if (allSets.isEmpty()) continue
            val parts = mutableListOf<Pair<Triple<String, Double?, Int>, Boolean>>()
            for (we in wes) {
                val name = exerciseRepository.getById(we.exerciseId)?.name?.lowercase()
                    ?: we.exerciseId
                for (s in allSets.filter { it.workoutExerciseId == we.id }) {
                    parts += Triple(name, s.weightKg, s.reps) to s.isWarmup
                }
            }
            result.getOrPut(w.dateEpochDay) { mutableSetOf() } += signatureOf(parts)
        }
        return result
    }

    private fun signatureOf(
        parts: List<Pair<Triple<String, Double?, Int>, Boolean>>,
    ): String = parts
        .sortedWith(
            compareBy(
                { it.first.first },
                { it.first.second ?: -1.0 },
                { it.first.third },
                { it.second },
            ),
        )
        .joinToString(";") { (t, warmup) -> "${t.first}|${t.second}|${t.third}|$warmup" }

    private fun guessBodyPart(name: String): String {
        val n = name.lowercase()
        return when {
            listOf("curl", "bicep", "preacher").any { it in n } -> "BICEPS"
            listOf("tricep", "kickback", "pushdown", "dip").any { it in n } -> "TRICEPS"
            listOf("row", "pulldown", "pull up", "chin", "lat ", "shrug", "rear delt", "fly")
                .any { it in n } && "chest" !in n -> "BACK"
            listOf("press", "bench", "fly", "push up", "chest").any { it in n } &&
                "shoulder" !in n && "overhead" !in n && "push press" !in n -> "CHEST"
            listOf("shoulder", "lateral raise", "front raise", "overhead", "push press", "arnold")
                .any { it in n } -> "SHOULDERS"
            listOf("crunch", "jackknife", "rollout", "plank", "leg raise", "ab")
                .any { it in n } -> "CORE"
            listOf("wrist", "forearm", "farmers").any { it in n } -> "FOREARMS"
            listOf("squat", "lunge", "leg", "calf", "hamstring", "hip", "deadlift", "press")
                .any { it in n } -> "LEGS"
            else -> "WHOLE_BODY"
        }
    }

    private fun guessCategory(name: String): String {
        val n = name.lowercase()
        return when {
            "cable" in n -> "CABLE"
            "machine" in n || "smith" in n -> "MACHINE"
            "dumbbell" in n -> "DUMBBELL"
            "kettlebell" in n -> "KETTLEBELL"
            "barbell" in n -> "BARBELL"
            "push up" in n || "pull up" in n || "chin" in n || "dip" in n -> "BODYWEIGHT"
            else -> "BARBELL"
        }
    }

    private fun guessBodyweight(name: String): Boolean {
        val n = name.lowercase()
        return listOf("push up", "pull up", "chin up", "dip", "bodyweight", "plank", "crunch")
            .any { it in n }
    }

    // ---- generic (app's own export) format ----

    private suspend fun importGenericFormat(
        records: List<String>,
        header: List<String>,
        repo: WorkoutRepository,
    ): ImportResult {
        val col = { names: List<String> ->
            names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { i -> i >= 0 } } ?: -1
        }
        val dateCol = col(listOf("date", "workout_date"))
        val titleCol = col(listOf("title", "workout", "workout_title", "name"))
        val bwCol = col(listOf("bodyweight_kg", "bodyweight", "bw_kg"))
        val exCol = col(listOf("exercise", "exercise_name"))
        val weightCol = col(listOf("weight_kg", "weight", "kg"))
        val repsCol = col(listOf("reps", "repetition", "repetitions"))
        val warmupCol = col(listOf("is_warmup", "warmup"))
        val durationCol = col(listOf("duration_minutes", "duration"))
        val startCol = col(listOf("start_minute"))
        val endCol = col(listOf("end_minute"))
        if (dateCol < 0 || exCol < 0 || repsCol < 0) {
            throw IllegalArgumentException(
                "Unrecognized CSV. Expected a Strength Level export or app export."
            )
        }

        data class Row(
            val date: LocalDate,
            val title: String,
            val bw: Double?,
            val exercise: String,
            val weight: Double?,
            val reps: Int,
            val warmup: Boolean,
            val duration: Int?,
            val startMinute: Int?,
            val endMinute: Int?,
        )

        val rows = mutableListOf<Row>()
        val unknown = linkedSetOf<String>()
        for (record in records.drop(1)) {
            val parts = parse(record)
            fun cell(i: Int) = parts.getOrNull(i)?.trim().orEmpty()
            val date =
                try { LocalDate.parse(cell(dateCol)) } catch (_: Exception) { continue }
            val title = cell(titleCol).ifBlank { "Workout" }
            val bw = cell(bwCol).toDoubleOrNull()?.takeIf { it >= 0 }
            val exName = cell(exCol)
            val weight = cell(weightCol).toDoubleOrNull()?.takeIf { it >= 0 }
            val reps = cell(repsCol).toIntOrNull() ?: continue
            if (reps <= 0) continue
            val warmup = cell(warmupCol).let { it == "1" || it.equals("true", true) }
            val duration = cell(durationCol).toIntOrNull()
            val startMinute = cell(startCol).toIntOrNull()?.coerceIn(0, 1439)
            val endMinute = cell(endCol).toIntOrNull()?.coerceIn(0, 1439)
            rows += Row(date, title, bw, exName, weight, reps, warmup, duration, startMinute, endMinute)
        }

        val byName = exerciseRepository.getAll().associateBy { it.name.lowercase() }
        val grouped = rows.groupBy { it.date to it.title }
        val existingSigs = existingSignaturesByDate()
        var workoutsImported = 0
        var setsImported = 0
        var skippedDates = 0
        for ((key, group) in grouped) {
            val (date, title) = key
            // Content fingerprint on (date, title): export -> import stays idempotent
            // while user-authored workouts with different content are never skipped.
            val sig = signatureOf(
                group.map {
                    Triple(it.exercise.lowercase(), it.weight, it.reps) to it.warmup
                },
            )
            if (existingSigs[date.toEpochDay()]?.contains(sig) == true) {
                skippedDates++
                continue
            }
            val byExercise = group.groupBy { it.exercise }
            val drafts = mutableListOf<WorkoutExerciseDraft>()
            for ((exName, exRows) in byExercise) {
                val exercise = byName[exName.lowercase()]
                if (exercise == null) {
                    unknown += exName
                    continue
                }
                val sets = exRows.map {
                    WorkoutSetDraft(it.weight, it.reps, it.warmup)
                }
                drafts += WorkoutExerciseDraft(exercise.id, sets)
                setsImported += sets.size
            }
            if (drafts.isEmpty()) continue
            val first = group.first()
            val startMinute = first.startMinute ?: 17 * 60
            val endMinute = first.endMinute
                ?: first.duration?.let { (startMinute + it).coerceAtMost(1439) }
            repo.saveDraft(
                WorkoutDraft(
                    title = title,
                    dateEpochDay = date.toEpochDay(),
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    bodyweightKg = group.firstOrNull()?.bw,
                    notes = IMPORT_MARKER,
                    exercises = drafts,
                )
            )
            workoutsImported++
        }
        return ImportResult(workoutsImported, setsImported, unknown.toList(), skippedDates)
    }

    /** RFC-4180-ish CSV line parser supporting quoted fields. */
    fun parseCsvLine(line: String): List<String> = parse(line)

    companion object {
        /**
         * Marker stored in Workout.notes for rows created by this importer so that
         * re-imports can be detected without guessing from titles. User-authored
         * workouts never have this marker.
         */
        internal const val IMPORT_MARKER = "imported-from-csv"

        /**
         * Splits a CSV document into records on newlines that are NOT inside quoted
         * fields, so multi-line title/exercise cells round-trip intact.
         */
        internal fun splitRecords(text: String): List<String> {
            val records = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            for (c in text) {
                when {
                    c == '"' -> {
                        inQuotes = !inQuotes
                        sb.append(c)
                    }
                    !inQuotes && c == '\r' -> {
                        // drop CR of CRLF; lone CR also ends the record
                        if (sb.isNotEmpty()) {
                            records.add(sb.toString())
                            sb.clear()
                        }
                    }
                    !inQuotes && c == '\n' -> {
                        records.add(sb.toString())
                        sb.clear()
                    }
                    else -> sb.append(c)
                }
            }
            if (sb.isNotEmpty()) records.add(sb.toString())
            return records
        }

        internal fun parseNativeRows(
            records: List<String>,
            header: List<String>,
        ): Pair<List<NativeRow>, Set<String>> {
            val col = { names: List<String> ->
                names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { i -> i >= 0 } } ?: -1
            }
            val dateCol = col(listOf("date lifted", "date"))
            val exCol = col(listOf("exercise"))
            val kgCol = col(listOf("weight (kg)", "weight kg", "kg"))
            val lbCol = col(listOf("weight (lb)", "weight lb", "lb"))
            val repsCol = col(listOf("reps"))
            val bwCol = col(listOf("bodyweight (kg)", "bodyweight kg"))
            val bwLbCol = col(listOf("bodyweight (lb)", "bodyweight lb"))
            val warmupCol = col(listOf("warmup"))
            require(dateCol >= 0 && exCol >= 0 && repsCol >= 0) {
                "Not a Strength Level export: missing required columns"
            }

            val rows = mutableListOf<NativeRow>()
            val parseErrors = linkedSetOf<String>()
            for (record in records.drop(1)) {
                val parts = parse(record)
                fun cell(i: Int) = parts.getOrNull(i)?.trim().orEmpty()
                val date = try {
                    LocalDate.parse(cell(dateCol))
                } catch (_: Exception) {
                    parseErrors += "bad date: ${cell(dateCol).take(20)}"
                    continue
                }
                val exercise = cell(exCol)
                if (exercise.isBlank()) continue
                val repsInt = cell(repsCol).toIntOrNull()
                if (repsInt == null || repsInt <= 0) {
                    parseErrors += "bad reps in row for $exercise"
                    continue
                }
                val reps = repsInt
                val weightKg = cell(kgCol).toDoubleOrNull()
                    ?: cell(lbCol).toDoubleOrNull()?.times(0.45359237)
                val bodyweightKg = cell(bwCol).toDoubleOrNull()
                    ?: cell(bwLbCol).toDoubleOrNull()?.times(0.45359237)
                val isWarmup = cell(warmupCol).let { it == "1" || it.equals("true", true) }
                rows += NativeRow(
                    date = date,
                    exerciseName = exercise,
                    weightKg = weightKg?.takeIf { it >= 0 },
                    reps = reps,
                    bodyweightKg = bodyweightKg?.takeIf { it >= 0 },
                    isWarmup = isWarmup,
                )
            }
            return rows to parseErrors
        }

        fun parse(line: String): List<String> {
            val result = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> {
                        result.add(sb.toString()); sb.clear()
                    }
                    else -> sb.append(c)
                }
                i++
            }
            result.add(sb.toString())
            return result
        }
    }
}
