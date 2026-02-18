package com.claw.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claw.app.data.remote.ClawApi
import com.claw.app.data.repository.AuthRepository
import com.claw.app.data.repository.InstanceRepository
import com.claw.app.domain.model.Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class BotInfo(
    val username: String,
    val displayName: String
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val instanceRepository: InstanceRepository,
    private val api: ClawApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _botInfo = MutableStateFlow<BotInfo?>(null)
    val botInfo: StateFlow<BotInfo?> = _botInfo.asStateFlow()

    // Holds the instance ID once created (used for config API calls in steps 2/3)
    private var instanceId: String? = null

    // Whether this is a returning user (login) — skip wizard
    private var isReturningUser = false

    // OkHttp client for direct Telegram API calls
    private val httpClient = OkHttpClient()

    // --- Step 1: Auth ---

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading

            authRepository.login(email, password)
                .onSuccess {
                    isReturningUser = true
                    ensureInstance()
                }
                .onFailure { error ->
                    _uiState.value = OnboardingUiState.Error(error.message ?: "Login failed")
                }
        }
    }

    fun register(email: String, password: String, name: String?) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading

            authRepository.register(email, password, name)
                .onSuccess {
                    isReturningUser = false
                    ensureInstance()
                }
                .onFailure { error ->
                    _uiState.value = OnboardingUiState.Error(error.message ?: "Registration failed")
                }
        }
    }

    /**
     * Ensures the user has an instance. After auth succeeds:
     * - Returning users → go straight to dashboard (InstanceCreated)
     * - New users → advance to step 2 (AI model selection)
     */
    private suspend fun ensureInstance() {
        _uiState.value = OnboardingUiState.CreatingInstance

        instanceRepository.getInstances()
            .onSuccess { instances ->
                val existing = instances.firstOrNull()
                if (existing != null) {
                    instanceId = existing.id
                    if (isReturningUser) {
                        // Returning user — skip wizard, go to dashboard
                        _uiState.value = OnboardingUiState.Complete(existing)
                    } else {
                        // New user has instance already (backend auto-creates), advance to wizard
                        _uiState.value = OnboardingUiState.StepReady
                        _currentStep.value = 1
                    }
                } else {
                    createInstance()
                }
            }
            .onFailure {
                createInstance()
            }
    }

    private suspend fun createInstance() {
        instanceRepository.createInstance()
            .onSuccess { instance ->
                instanceId = instance.id
                if (isReturningUser) {
                    _uiState.value = OnboardingUiState.Complete(instance)
                } else {
                    _uiState.value = OnboardingUiState.StepReady
                    _currentStep.value = 1
                }
            }
            .onFailure { error ->
                _uiState.value = OnboardingUiState.Error(error.message ?: "Failed to create instance")
            }
    }

    // --- Step 2: AI Model ---

    fun selectFreeTier() {
        val id = instanceId ?: return
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading

            val config = JSONObject().apply {
                put("model", "openrouter/auto")
                put("provider", "openrouter")
            }

            api.updateConfig(id, config)
                .onSuccess {
                    _uiState.value = OnboardingUiState.StepReady
                    _currentStep.value = 2
                }
                .onFailure {
                    // Free tier is default anyway, so advance even if API fails
                    _uiState.value = OnboardingUiState.StepReady
                    _currentStep.value = 2
                }
        }
    }

    fun selectBYOK(apiKey: String, provider: String, model: String) {
        val id = instanceId ?: return
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading

            val config = JSONObject().apply {
                put("model", model)
                put("provider", provider)
                put("apiKey", apiKey)
            }

            api.updateConfig(id, config)
                .onSuccess {
                    _uiState.value = OnboardingUiState.StepReady
                    _currentStep.value = 2
                }
                .onFailure { error ->
                    _uiState.value = OnboardingUiState.Error(error.message ?: "Failed to save API key")
                }
        }
    }

    // --- Step 3: Telegram ---

    fun connectTelegram(botToken: String) {
        val id = instanceId ?: return
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading

            val config = JSONObject().apply {
                put("channels", JSONObject().apply {
                    put("telegram", JSONObject().apply {
                        put("botToken", botToken)
                    })
                })
            }

            api.updateConfig(id, config)
                .onSuccess {
                    _uiState.value = OnboardingUiState.TelegramConnected
                }
                .onFailure { error ->
                    _uiState.value = OnboardingUiState.Error(error.message ?: "Failed to connect Telegram")
                }
        }
    }

    /**
     * Validates a Telegram bot token by calling the getMe API.
     * On success, populates botInfo with the bot's username and display name.
     */
    fun validateToken(botToken: String) {
        if (botToken.isBlank()) {
            _botInfo.value = null
            return
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.telegram.org/bot$botToken/getMe")
                        .get()
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string()

                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        if (json.getBoolean("ok")) {
                            val result = json.getJSONObject("result")
                            BotInfo(
                                username = result.optString("username", ""),
                                displayName = result.optString("first_name", "Bot")
                            )
                        } else null
                    } else null
                }
                _botInfo.value = result
            } catch (_: Exception) {
                _botInfo.value = null
            }
        }
    }

    // --- Navigation ---

    fun skipStep() {
        val step = _currentStep.value
        if (step < 2) {
            _currentStep.value = step + 1
            _uiState.value = OnboardingUiState.StepReady
        } else {
            finishWizard()
        }
    }

    fun finishWizard() {
        viewModelScope.launch {
            // Fetch latest instance to pass through
            instanceRepository.getInstances()
                .onSuccess { instances ->
                    val instance = instances.firstOrNull()
                    if (instance != null) {
                        _uiState.value = OnboardingUiState.Complete(instance)
                    } else {
                        _uiState.value = OnboardingUiState.Complete(null)
                    }
                }
                .onFailure {
                    _uiState.value = OnboardingUiState.Complete(null)
                }
        }
    }

    fun clearError() {
        _uiState.value = OnboardingUiState.StepReady
    }
}

sealed class OnboardingUiState {
    data object Idle : OnboardingUiState()
    data object Loading : OnboardingUiState()
    data object CreatingInstance : OnboardingUiState()
    data object StepReady : OnboardingUiState()
    data object TelegramConnected : OnboardingUiState()
    data class Complete(val instance: Instance?) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}
