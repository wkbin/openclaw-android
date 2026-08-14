package com.openclaw.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.SkillInfo
import com.openclaw.android.service.OpenClawChatClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val chatClient: OpenClawChatClient,
) : ViewModel() {
    private val _skills = MutableStateFlow<List<SkillInfo>>(emptyList())
    val skills: StateFlow<List<SkillInfo>> = _skills.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _skills.value = chatClient.skillsStatus()
            _loading.value = false
        }
    }

    fun setEnabled(skill: SkillInfo, enabled: Boolean) {
        viewModelScope.launch {
            val ok = chatClient.skillSetEnabled(skill.skillKey, enabled)
            if (ok) {
                _skills.value = _skills.value.map {
                    if (it.skillKey == skill.skillKey) it.copy(disabled = !enabled) else it
                }
            } else {
                _message.value = "更新失败：请确认网关已连接且当前设备有管理权限"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
