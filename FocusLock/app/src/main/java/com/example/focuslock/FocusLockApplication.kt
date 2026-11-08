package com.example.focuslock

import android.app.Application
import com.example.focuslock.data.AppContainer

class FocusLockApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}