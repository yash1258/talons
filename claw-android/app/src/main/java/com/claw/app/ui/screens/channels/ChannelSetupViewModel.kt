package com.claw.app.ui.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claw.app.data.remote.ClawApi
import com.claw.app.data.repository.InstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChannelSetupViewModel(
    private val api: ClawApi,
    private val instanceRepository: InstanceRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ChannelSetupState>(ChannelSetupState.Idle)
    val uiState: StateFlow<ChannelSetupState> = _uiState.asStateFlow()
    
    private var instanceId: String? = null
    
    fun setInstanceId(id: String) {
        instanceId = id
    }
    
    fun connectTelegram(botToken: String) {
        val id = instanceId ?: run {
            _uiState.value = ChannelSetupState.Error("No instance selected")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = ChannelSetupState.Connecting
            
            val config = JSONObject().apply {
                put("channels", JSONObject().apply {
                    put("telegram", JSONObject().apply {
                        put("botToken", botToken)
                    })
                })
            }
            
            api.updateConfig(id, config)
                .onSuccess {
                    _uiState.value = ChannelSetupState.Connected("telegram")
                }
                .onFailure { error ->
                    _uiState.value = ChannelSetupState.Error(
                        error.message ?: "Failed to connect Telegram"
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = ChannelSetupState.Idle
    }
}

sealed class ChannelSetupState {
    data object Idle : ChannelSetupState()
    data object Connecting : ChannelSetupState()
    data class Connected(val channel: String) : ChannelSetupState()
    data class Error(val message: String) : ChannelSetupState()
}
