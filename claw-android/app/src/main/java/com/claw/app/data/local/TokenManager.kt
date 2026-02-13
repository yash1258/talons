package com.claw.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.map

class TokenManager(private val dataStore: DataStore<Preferences>) {
    
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val GATEWAY_URL_KEY = stringPreferencesKey("gateway_url")
    }
    
    val token: Flow<String?> = dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }
    
    val gatewayUrl: Flow<String?> = dataStore.data.map { preferences ->
        preferences[GATEWAY_URL_KEY]
    }
    
    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }
    
    suspend fun saveGatewayUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[GATEWAY_URL_KEY] = url
        }
    }
    
    suspend fun clearToken() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
        }
    }
    
    fun getTokenSync(): String? {
        return runBlocking {
            dataStore.data.first()[TOKEN_KEY]
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return token.first() != null
    }
}
