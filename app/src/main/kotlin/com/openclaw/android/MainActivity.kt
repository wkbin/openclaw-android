package com.openclaw.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openclaw.android.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openChat = intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) ?: false
        setContent {
            AppRoot(openChat = openChat)
        }
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "com.openclaw.android.extra.OPEN_CHAT"
    }
}
