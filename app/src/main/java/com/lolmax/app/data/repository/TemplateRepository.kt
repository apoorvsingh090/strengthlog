package com.lolmax.app.data.repository

import androidx.room.withTransaction
import com.lolmax.app.data.local.LolMaxDatabase
import com.lolmax.app.data.local.dao.TemplateDao
import com.lolmax.app.data.local.dao.TemplateExerciseDao
import com.lolmax.app.data.local.dao.TemplateSetDao
import com.lolmax.app.data.local.entities.Template
import com.lolmax.app.data.local.entities.TemplateExercise
import com.lolmax.app.data.local.entities.TemplateSet
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class TemplateSetDraft(val weightKg: Double?, val reps: Int, val isWarmup: Boolean = false)
data class TemplateExerciseDraft(val exerciseId: String, val sets: List<TemplateSetDraft>)
data class TemplateDraft(
    val id: String? = null,
    val name: String,
    val exercises: List<TemplateExerciseDraft> = emptyList(),
)

data class TemplateExerciseWithSets(
    val exercise: TemplateExercise,
    val sets: List<TemplateSet>,
)

data class TemplateWithExercises(
    val template: Template,
    val exercises: List<TemplateExerciseWithSets>,
)

class TemplateRepository(
    private val db: LolMaxDatabase,
    private val templateDao: TemplateDao,
    private val templateExerciseDao: TemplateExerciseDao,
    private val templateSetDao: TemplateSetDao,
    @Suppress("unused") private val exerciseRepository: ExerciseRepository,
) {
    fun observeTemplates(): Flow<List<Template>> = templateDao.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTemplateDetail(id: String): Flow<TemplateWithExercises?> =
        combine(
            templateDao.observeById(id),
            templateExerciseDao.observeForTemplate(id),
        ) { template, exercises -> template to exercises }
            .flatMapLatest { (template, exercises) ->
                when {
                    template == null -> flowOf(null)
                    exercises.isEmpty() -> flowOf(TemplateWithExercises(template, emptyList()))
                    else -> combine(
                        exercises.map { te ->
                            templateSetDao.observeForExercises(listOf(te.id)).map { sets ->
                                TemplateExerciseWithSets(te, sets)
                            }
                        }
                    ) { list -> TemplateWithExercises(template, list.toList()) }
                }
            }

    suspend fun getTemplate(id: String): Template? = templateDao.getById(id)

    suspend fun getTemplateWithExercises(id: String): TemplateWithExercises? {
        val template = templateDao.getById(id) ?: return null
        val exercises = templateExerciseDao.getForTemplate(id)
        if (exercises.isEmpty()) return TemplateWithExercises(template, emptyList())
        val allSets = templateSetDao.getForExercises(exercises.map { it.id })
        val detail = exercises.map { te ->
            TemplateExerciseWithSets(te, allSets.filter { it.templateExerciseId == te.id })
        }
        return TemplateWithExercises(template, detail)
    }

    suspend fun saveDraft(draft: TemplateDraft): String {
        val id = draft.id ?: UUID.randomUUID().toString()
        val order = draft.id?.let { templateDao.getById(it)?.orderIndex }
            ?: templateDao.nextOrderIndex()
        val exercises = mutableListOf<TemplateExercise>()
        val setsByExercise = mutableMapOf<String, List<TemplateSet>>()
        draft.exercises.forEachIndexed { exIndex, exDraft ->
            val teId = "$id-ex-$exIndex"
            exercises += TemplateExercise(
                id = teId,
                templateId = id,
                exerciseId = exDraft.exerciseId,
                orderInTemplate = exIndex,
            )
            setsByExercise[teId] = exDraft.sets.mapIndexed { setIndex, s ->
                TemplateSet(
                    id = "$teId-set-$setIndex",
                    templateExerciseId = teId,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    isWarmup = s.isWarmup,
                    orderInExercise = setIndex,
                )
            }
        }
        db.withTransaction {
            templateDao.upsert(Template(id = id, name = draft.name, orderIndex = order))
            templateSetDao.deleteForTemplate(id)
            templateExerciseDao.deleteForTemplate(id)
            templateExerciseDao.upsertAll(exercises)
            templateSetDao.upsertAll(setsByExercise.values.flatten())
        }
        return id
    }

    suspend fun deleteTemplate(id: String) {
        db.withTransaction {
            templateDao.deleteById(id)
        }
    }
}
