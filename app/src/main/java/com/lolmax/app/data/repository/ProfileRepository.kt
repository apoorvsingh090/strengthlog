package com.lolmax.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lolmax.app.data.local.dao.UserProfileDao
import com.lolmax.app.data.local.entities.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class ProfileRepository(
    private val dao: UserProfileDao,
    private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")

    fun observeProfile(): Flow<UserProfile> = dao.observeProfile().map { it ?: UserProfile() }

    fun observeThemeMode(): Flow<String> = context.dataStore.data.map { it[themeKey] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[themeKey] = mode }
        val profile = dao.getProfile() ?: UserProfile()
        dao.upsert(profile.copy(themeMode = mode))
    }

    suspend fun getProfile(): UserProfile = dao.getProfile() ?: UserProfile()

    suspend fun getBodyweightKg(): Double = getProfile().bodyweightKg

    suspend fun isMale(): Boolean = getProfile().gender == "MALE"

    suspend fun saveProfile(profile: UserProfile) {
        val currentTheme = observeThemeMode().first()
        dao.upsert(profile.copy(id = 1, themeMode = currentTheme))
    }
}
