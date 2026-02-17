package com.claw.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claw.app.data.local.TokenManager
import com.claw.app.data.remote.ConnectionState
import com.claw.app.data.remote.GatewayClient
import com.claw.app.data.repository.InstanceRepository
import com.claw.app.data.repository.UsageRepository
import com.claw.app.domain.model.Instance
import com.claw.app.domain.model.SubscriptionStatus
import com.claw.app.domain.model.UsageSummary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val instanceRepository: InstanceRepository,
    private val usageRepository: UsageRepository,
    private val gatewayClient: GatewayClient,
    private val tokenManager: TokenManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    // Expose GatewayClient's connection state directly
    val gatewayConnection: StateFlow<ConnectionState> = gatewayClient.connectionState
    
    init {
        loadData()
    }
    
    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            instanceRepository.getInstances()
                .onSuccess { instances ->
                    _uiState.update { it.copy(instances = instances) }
                }
            
            usageRepository.getUsage()
                .onSuccess { usage ->
                    _uiState.update { it.copy(usage = usage) }
                }
            
            usageRepository.getSubscriptionStatus()
                .onSuccess { status ->
                    _uiState.update { it.copy(subscriptionStatus = status) }
                }
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    fun connectToGateway(instance: Instance) {
        viewModelScope.launch {
            // Build the gateway URL from the backend server URL + instance port
            val serverUrl = tokenManager.gatewayUrl.first()
            val url = if (serverUrl.isNullOrBlank()) {
                // Fallback: use the backend's base URL with the instance's docker port
                "http://152.53.164.238:${instance.dockerPort}"
            } else {
                serverUrl
            }
            
            val token = tokenManager.token.first()
            gatewayClient.connect(url, token)
        }
    }
    
    fun disconnectFromGateway() {
        gatewayClient.disconnect()
    }
    
    fun refreshUsage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            usageRepository.getUsage()
                .onSuccess { usage ->
                    _uiState.update { it.copy(usage = usage, isRefreshing = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
        }
    }

    fun approvePairing(instanceId: String, code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            instanceRepository.approvePairing(instanceId, code)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, pairingSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = "Pairing failed: ${error.message}") }
                }
        }
    }

    fun dismissPairingSuccess() {
        _uiState.update { it.copy(pairingSuccess = false) }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val instances: List<Instance> = emptyList(),
    val usage: UsageSummary? = null,
    val subscriptionStatus: SubscriptionStatus? = null,
    val pairingSuccess: Boolean = false,
    val error: String? = null
)
