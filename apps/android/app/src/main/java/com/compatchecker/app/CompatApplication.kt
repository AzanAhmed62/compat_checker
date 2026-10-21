package com.compatchecker.app

import android.app.Application
import com.compatchecker.app.core.di.AppContainer
import com.compatchecker.app.core.di.AppContainerImpl

class CompatApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }
}
