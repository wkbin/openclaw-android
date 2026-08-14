package com.openclaw.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.CronJob
import com.openclaw.android.service.OpenClawChatClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CronViewModel @Inject constructor(
    private val chatClient: OpenClawChatClient,
) : ViewModel() {
    private val _jobs = MutableStateFlow<List<CronJob>>(emptyList())
    val jobs: StateFlow<List<CronJob>> = _jobs.asStateFlow()

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
            _jobs.value = chatClient.cronList()
            _loading.value = false
        }
    }

    fun add(
        name: String,
        expr: String,
        prompt: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val ok = chatClient.cronAdd(name, expr, prompt, enabled)
            _message.value = if (ok) {
                "已创建调度任务"
            } else {
                "创建失败：请确认网关已连接且当前设备有管理权限"
            }
            if (ok) {
                _jobs.value = chatClient.cronList()
            }
        }
    }

    fun setEnabled(job: CronJob, enabled: Boolean) {
        viewModelScope.launch {
            val ok = chatClient.cronUpdateEnabled(job.id, enabled)
            if (ok) {
                _jobs.value = _jobs.value.map {
                    if (it.id == job.id) it.copy(enabled = enabled) else it
                }
            } else {
                _message.value = "更新失败：请确认网关已连接且当前设备有管理权限"
            }
        }
    }

    fun remove(job: CronJob) {
        viewModelScope.launch {
            val ok = chatClient.cronRemove(job.id)
            _message.value = if (ok) {
                "已删除调度任务"
            } else {
                "删除失败：请确认网关已连接且当前设备有管理权限"
            }
            if (ok) {
                _jobs.value = chatClient.cronList()
            }
        }
    }

    fun runNow(job: CronJob) {
        viewModelScope.launch {
            val ok = chatClient.cronRunNow(job.id)
            _message.value = if (ok) "已触发执行" else "触发失败：请确认网关已连接"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
