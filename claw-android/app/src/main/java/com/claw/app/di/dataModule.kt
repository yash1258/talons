package com.claw.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.claw.app.data.local.TokenManager
import com.claw.app.data.remote.ClawApi
import com.claw.app.data.remote.GatewayClient
import com.claw.app.data.repository.AuthRepository
import com.claw.app.data.repository.InstanceRepository
import com.claw.app.data.repository.UsageRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "claw_prefs")

val dataModule = module {
    single { androidContext().dataStore }
    single { TokenManager(get()) }
    
    single { ClawApi(get()) }
    single { GatewayClient(get()) }
    
    single { AuthRepository(get(), get()) }
    single { InstanceRepository(get()) }
    single { UsageRepository(get()) }
}

val networkModule = module {
    single { provideOkHttpClient(get()) }
}

private fun provideOkHttpClient(tokenManager: TokenManager): okhttp3.OkHttpClient {
    return okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = tokenManager.getTokenSync()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .build()
}
