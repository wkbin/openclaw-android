package com.openclaw.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openclaw.android.ui.AppRoot
import com.openclaw.android.util.AppForegroundTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var foregroundTracker: AppForegroundTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openChat = intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) ?: false
        foregroundTracker.isForeground = true
        setContent {
            AppRoot(openChat = openChat)
        }
    }

    override fun onResume() {
        super.onResume()
        foregroundTracker.isForeground = true
    }

    override fun onPause() {
        super.onPause()
        foregroundTracker.isForeground = false
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "com.openclaw.android.extra.OPEN_CHAT"
    }
}
