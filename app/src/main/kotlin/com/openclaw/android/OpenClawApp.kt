package com.openclaw.android

import android.app.Application
import com.openclaw.android.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
