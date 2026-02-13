package com.claw.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claw.app.data.repository.AuthRepository
import com.claw.app.data.repository.InstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val instanceRepository: InstanceRepository
) : ViewModel() {
    
    private val _gatewayUrl = MutableStateFlow("")
    val gatewayUrl: StateFlow<String> = _gatewayUrl.asStateFlow()
    
    fun setGatewayUrl(url: String) {
        _gatewayUrl.value = url
    }
    
    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLogoutComplete()
        }
    }
}
