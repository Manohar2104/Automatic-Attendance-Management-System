package com.smartattendance.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val IS_SCANNING = booleanPreferencesKey("is_scanning")
        val USER_ROLE = stringPreferencesKey("user_role")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val LAST_LOCATION = stringPreferencesKey("last_location")

        const val DEFAULT_SERVER_URL = "http://snoxmo-ip-152-57-4-61.tunnelmole.net"
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

    val lastSyncTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_SYNC_TIME] ?: 0L
    }

    val isScanning: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_SCANNING] ?: false
    }

    val userRole: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_ROLE] ?: "STUDENT"
    }

    val accessToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN] ?: ""
    }

    val lastLocation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_LOCATION] ?: ""
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

    suspend fun updateLastSyncTime() {
        context.dataStore.edit { it[LAST_SYNC_TIME] = System.currentTimeMillis() }
    }

    suspend fun setScanning(scanning: Boolean) {
        context.dataStore.edit { it[IS_SCANNING] = scanning }
    }

    suspend fun saveUserRole(role: String) {
        context.dataStore.edit { it[USER_ROLE] = role }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[ACCESS_TOKEN] = token }
    }

    suspend fun saveLastLocation(location: String) {
        context.dataStore.edit { it[LAST_LOCATION] = location }
    }
}
