package com.claw.app.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onLoginSuccess: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()

    // Navigate to dashboard when wizard completes
    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Complete) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Step indicator (only visible after auth)
        if (currentStep > 0) {
            StepIndicator(currentStep = currentStep, totalSteps = 3)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Step content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f),
            label = "wizard_step"
        ) { step ->
            when (step) {
                0 -> AuthStep(viewModel, uiState)
                1 -> ModelStep(viewModel, uiState)
                2 -> TelegramStep(viewModel, uiState)
            }
        }
    }
}

// ============================================================
// Step Indicator
// ============================================================

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val stepLabels = listOf("Account", "AI Model", "Channel")

        for (i in 1..totalSteps) {
            val isActive = i <= currentStep
            val isCurrent = i == currentStep

            // Dot
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )

            if (i < totalSteps) {
                // Connector line
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            if (i < currentStep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }

    // Step label
    Text(
        text = when (currentStep) {
            1 -> "Step 1 of 3 — Account Setup"
            2 -> "Step 2 of 3 — AI Model"
            3 -> "Step 3 of 3 — Connect Channel"
            else -> ""
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ============================================================
// Step 1: Auth
// ============================================================

@Composable
private fun AuthStep(
    viewModel: OnboardingViewModel,
    uiState: OnboardingUiState
) {
    val focusManager = LocalFocusManager.current
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Logo
        Icon(
            imageVector = Icons.Default.Pets,
            contentDescription = "Claw",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Claw",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Your Personal AI Assistant",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = if (isLoginMode) "Welcome Back" else "Create Account",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Name field (register only)
        if (!isLoginMode) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (isLoginMode) viewModel.login(email, password)
                    else viewModel.register(email, password, name.ifBlank { null })
                }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Submit button
        Button(
            onClick = {
                if (isLoginMode) viewModel.login(email, password)
                else viewModel.register(email, password, name.ifBlank { null })
            },
            enabled = email.isNotBlank() && password.length >= 8 &&
                    uiState !is OnboardingUiState.Loading && uiState !is OnboardingUiState.CreatingInstance,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState is OnboardingUiState.Loading || uiState is OnboardingUiState.CreatingInstance) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (uiState is OnboardingUiState.CreatingInstance) "Setting up AI..."
                    else "Signing in..."
                )
            } else {
                Text(if (isLoginMode) "Sign In" else "Create Account")
            }
        }

        // Error display
        ErrorCard(uiState)

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(
                if (isLoginMode) "Don't have an account? Sign Up"
                else "Already have an account? Sign In"
            )
        }
    }
}

// ============================================================
// Step 2: AI Model Selection
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelStep(
    viewModel: OnboardingViewModel,
    uiState: OnboardingUiState
) {
    var selectedTier by remember { mutableStateOf("free") } // "free" or "byok"
    var apiKey by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("openrouter") }
    var selectedModel by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Choose Your AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Select how your AI assistant is powered",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Free Tier Card
        TierCard(
            title = "Free Tier",
            subtitle = "No API key required",
            description = "Uses OpenRouter's free model pool. Great for trying things out.",
            icon = Icons.Default.Bolt,
            iconTint = Color(0xFFD4A017),
            isSelected = selectedTier == "free",
            onClick = { selectedTier = "free" }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // BYOK Card
        TierCard(
            title = "Bring Your Own Key",
            subtitle = "Use your own API key",
            description = "Full control over model and provider. Supports OpenRouter, Anthropic, and OpenAI.",
            icon = Icons.Default.Key,
            iconTint = Color(0xFFFFC107),
            isSelected = selectedTier == "byok",
            onClick = { selectedTier = "byok" }
        )

        // BYOK fields
        AnimatedVisibility(visible = selectedTier == "byok") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider selector
                Text(
                    "Provider",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val providers = listOf("openrouter" to "OpenRouter", "anthropic" to "Anthropic", "openai" to "OpenAI")
                    providers.forEachIndexed { index, (key, label) ->
                        SegmentedButton(
                            selected = selectedProvider == key,
                            onClick = { selectedProvider = key },
                            shape = SegmentedButtonDefaults.itemShape(index, providers.size)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Model field
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("Model (optional)") },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                "anthropic" -> "claude-sonnet-4-20250514"
                                "openai" -> "gpt-4o"
                                else -> "google/gemini-2.5-flash"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // API Key field
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Continue button
        Button(
            onClick = {
                if (selectedTier == "free") {
                    viewModel.selectFreeTier()
                } else {
                    viewModel.selectBYOK(
                        apiKey = apiKey,
                        provider = selectedProvider,
                        model = selectedModel.ifBlank {
                            when (selectedProvider) {
                                "anthropic" -> "claude-sonnet-4-20250514"
                                "openai" -> "gpt-4o"
                                else -> "google/gemini-2.5-flash"
                            }
                        }
                    )
                }
            },
            enabled = (selectedTier == "free" || apiKey.isNotBlank()) && uiState !is OnboardingUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState is OnboardingUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Continue")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }

        // Error display
        ErrorCard(uiState)

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { viewModel.skipStep() }) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun TierCard(
    title: String,
    subtitle: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Radio + Icon
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(20.dp)
            )

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// Step 3: Telegram Channel
// ============================================================

@Composable
private fun TelegramStep(
    viewModel: OnboardingViewModel,
    uiState: OnboardingUiState
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var botToken by remember { mutableStateOf("") }
    val botInfo by viewModel.botInfo.collectAsState()

    // Auto-validate when token changes (debounced by user typing)
    LaunchedEffect(botToken) {
        if (botToken.contains(":")) {
            viewModel.validateToken(botToken)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color(0xFF0088CC)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Connect Telegram",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Chat with your AI via Telegram",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "How to get a bot token:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                InstructionStep("1", "Open BotFather in Telegram")
                InstructionStep("2", "Send /newbot and follow the prompts")
                InstructionStep("3", "Come back and paste the token")

                FilledTonalButton(
                    onClick = {
                        // Deep link opens BotFather directly in Telegram app
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=BotFather"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback to web if Telegram app not installed
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open BotFather in Telegram")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Token input with paste button
        OutlinedTextField(
            value = botToken,
            onValueChange = { botToken = it },
            label = { Text("Bot Token") },
            placeholder = { Text("123456789:ABCdefGHIjklMNO...") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            trailingIcon = {
                if (botToken.isBlank()) {
                    IconButton(onClick = {
                        clipboardManager.getText()?.text?.let { pasted ->
                            // Only auto-fill if it looks like a bot token (contains ":")
                            if (pasted.contains(":")) {
                                botToken = pasted.trim()
                            }
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                } else if (botInfo != null) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Valid",
                        tint = Color(0xFF4CAF50)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (botToken.isNotBlank()) viewModel.connectTelegram(botToken)
                }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is OnboardingUiState.Loading && uiState !is OnboardingUiState.TelegramConnected
        )

        // Bot info validation card
        AnimatedVisibility(visible = botInfo != null && uiState !is OnboardingUiState.TelegramConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Found: @${botInfo?.username} (${botInfo?.displayName})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connect button
        Button(
            onClick = { viewModel.connectTelegram(botToken) },
            enabled = botToken.isNotBlank() &&
                    uiState !is OnboardingUiState.Loading &&
                    uiState !is OnboardingUiState.TelegramConnected,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState is OnboardingUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting...")
            } else if (uiState is OnboardingUiState.TelegramConnected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connected!")
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Telegram")
            }
        }

        // Error display
        ErrorCard(uiState)

        // Success → finish
        AnimatedVisibility(visible = uiState is OnboardingUiState.TelegramConnected) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Celebration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Your AI is live on Telegram!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Send a message to your bot to start chatting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = { viewModel.finishWizard() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Go to Dashboard")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }

        if (uiState !is OnboardingUiState.TelegramConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.skipStep() }) {
                Text("Skip for now")
            }
        }
    }
}

// ============================================================
// Shared Components
// ============================================================

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ErrorCard(uiState: OnboardingUiState) {
    AnimatedVisibility(visible = uiState is OnboardingUiState.Error) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    (uiState as? OnboardingUiState.Error)?.message ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
