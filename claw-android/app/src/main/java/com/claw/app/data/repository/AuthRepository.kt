package com.claw.app.data.repository

import com.claw.app.data.local.TokenManager
import com.claw.app.data.remote.AuthResponse
import com.claw.app.data.remote.ClawApi
import com.claw.app.domain.model.User

class AuthRepository(
    private val api: ClawApi,
    private val tokenManager: TokenManager
) {
    suspend fun register(email: String, password: String, name: String? = null): Result<User> {
        return api.register(email, password, name).map { response ->
            tokenManager.saveToken(response.token)
            response.user
        }
    }
    
    suspend fun login(email: String, password: String): Result<User> {
        return api.login(email, password).map { response ->
            tokenManager.saveToken(response.token)
            response.user
        }
    }
    
    suspend fun logout() {
        tokenManager.clearToken()
    }
    
    suspend fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }
    
    fun getToken() = tokenManager.token
}
