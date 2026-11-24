package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apk_forge_settings")

data class UserSettings(
    val githubUsername: String = "",
    val githubToken: String = "",
    val lastRepoName: String = "",
    val isPrivateRepo: Boolean = false,
    val notificationsEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val LAST_REPO_NAME = stringPreferencesKey("last_repo_name")
        val IS_PRIVATE_REPO = booleanPreferencesKey("is_private_repo")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            githubUsername = prefs[Keys.GITHUB_USERNAME].orEmpty(),
            githubToken = prefs[Keys.GITHUB_TOKEN].orEmpty(),
            lastRepoName = prefs[Keys.LAST_REPO_NAME].orEmpty(),
            isPrivateRepo = prefs[Keys.IS_PRIVATE_REPO] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun saveCredentials(username: String, token: String, repoName: String, isPrivate: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GITHUB_USERNAME] = username
            prefs[Keys.GITHUB_TOKEN] = token
            prefs[Keys.LAST_REPO_NAME] = repoName
            prefs[Keys.IS_PRIVATE_REPO] = isPrivate
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
