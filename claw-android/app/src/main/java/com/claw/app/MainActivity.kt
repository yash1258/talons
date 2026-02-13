package com.claw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.claw.app.ui.screens.dashboard.DashboardScreen
import com.claw.app.ui.screens.onboarding.OnboardingScreen
import com.claw.app.ui.screens.settings.SettingsScreen
import com.claw.app.ui.screens.channels.ChannelSetupScreen
import com.claw.app.ui.theme.ClawTheme
import com.claw.app.data.local.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val tokenManager: TokenManager by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val isLoggedIn = runBlocking { tokenManager.isLoggedIn() }
                    
                    NavHost(
                        navController = navController,
                        startDestination = if (isLoggedIn) "dashboard" else "onboarding"
                    ) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                onLogout = {
                                    navController.navigate("onboarding") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                },
                                onSettings = {
                                    navController.navigate("settings")
                                },
                                onChannels = { instanceId ->
                                    navController.navigate("channels/$instanceId")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("channels/{instanceId}") { backStackEntry ->
                            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: ""
                            ChannelSetupScreen(
                                instanceId = instanceId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
