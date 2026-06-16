package com.smartattendance.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val IS_REGISTERED = booleanPreferencesKey("is_registered")
        val SESSION_ID = stringPreferencesKey("session_id")

        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8100"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_ID] ?: ""
    }

    val isRegistered: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_REGISTERED] ?: false
    }

    val sessionId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SESSION_ID] ?: ""
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL] = url }
    }

    suspend fun saveDeviceId(id: String) {
        context.dataStore.edit { it[DEVICE_ID] = id }
    }

    suspend fun setRegistered(registered: Boolean) {
        context.dataStore.edit { it[IS_REGISTERED] = registered }
    }

    suspend fun saveSessionId(sessionId: String) {
        context.dataStore.edit { it[SESSION_ID] = sessionId }
    }
}
