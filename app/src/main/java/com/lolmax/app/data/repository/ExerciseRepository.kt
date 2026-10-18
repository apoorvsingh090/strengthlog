package com.lolmax.app.data.repository

import com.lolmax.app.data.local.dao.CustomExerciseDao
import com.lolmax.app.data.local.dao.ExerciseDao
import com.lolmax.app.data.local.dao.ExerciseWithFrequency
import com.lolmax.app.data.local.entities.Exercise
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val customExerciseDao: CustomExerciseDao,
    @Suppress("unused") private val standardsRepository: StandardsRepository,
) {
    fun observeAll(): Flow<List<Exercise>> = exerciseDao.observeAll()

    fun observeWithFrequency(): Flow<List<ExerciseWithFrequency>> =
        exerciseDao.observeWithFrequency()

    fun observeCustom(): Flow<List<Exercise>> = customExerciseDao.observeCustom()

    suspend fun getAll(): List<Exercise> = exerciseDao.getAll()

    suspend fun getById(id: String): Exercise? = exerciseDao.getById(id)

    suspend fun getByIds(ids: List<String>): List<Exercise> =
        if (ids.isEmpty()) emptyList() else exerciseDao.getByIds(ids)

    suspend fun findBySlug(slug: String): Exercise? = exerciseDao.getBySlug(slug)

    suspend fun createCustom(
        name: String,
        bodyPart: String,
        category: String,
        isBodyweight: Boolean,
    ): Exercise? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        // Return the existing row instead of creating a same-named duplicate.
        val existing = exerciseDao.getAll().find {
            it.name.trim().equals(trimmed, ignoreCase = true)
        }
        if (existing != null) return existing
        val exercise = Exercise(
            id = "custom-$slug-${UUID.randomUUID().toString().take(8)}",
            name = trimmed,
            slug = slug,
            bodyPart = bodyPart,
            category = category,
            isBodyweight = isBodyweight,
            isCustom = true,
        )
        customExerciseDao.upsert(exercise)
        return exercise
    }
}
