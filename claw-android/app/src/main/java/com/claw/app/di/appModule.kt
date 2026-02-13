package com.claw.app.di

import com.claw.app.ui.screens.onboarding.OnboardingViewModel
import com.claw.app.ui.screens.dashboard.DashboardViewModel
import com.claw.app.ui.screens.settings.SettingsViewModel
import com.claw.app.ui.screens.channels.ChannelSetupViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { OnboardingViewModel(get(), get(), get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ChannelSetupViewModel(get(), get()) }
}
