package com.openclaw.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.ChatMessage
import com.openclaw.android.model.ChatAttachment
import com.openclaw.android.model.ChatSession
import com.openclaw.android.service.OpenClawChatClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatClient: OpenClawChatClient,
) : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> = chatClient.messages
    val sessions: StateFlow<List<ChatSession>> = chatClient.sessions
    val connected: StateFlow<Boolean> = chatClient.connected
    val status: StateFlow<String> = chatClient.status
    val isStreaming: StateFlow<Boolean> = chatClient.isStreaming

    fun start() {
        chatClient.start()
    }

    fun stop() {
        chatClient.stop()
    }

    fun send(
        text: String,
        attachment: ChatAttachment? = null,
    ) {
        viewModelScope.launch {
            chatClient.sendMessage(text, attachment)
        }
    }

    fun newSession() {
        chatClient.newSession()
    }

    fun selectSession(key: String) {
        chatClient.selectSession(key)
    }

    fun resetSession() {
        chatClient.resetCurrentSession()
    }

    fun stopGeneration() {
        chatClient.stopGeneration()
    }
}
