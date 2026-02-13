package com.claw.app

import android.app.Application
import com.claw.app.di.appModule
import com.claw.app.di.dataModule
import com.claw.app.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ClawApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@ClawApplication)
            modules(appModule, dataModule, networkModule)
        }
    }
}
