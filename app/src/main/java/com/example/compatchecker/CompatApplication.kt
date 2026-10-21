package com.example.compatchecker

import android.app.Application
import com.example.compatchecker.core.di.AppContainer

class CompatApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
