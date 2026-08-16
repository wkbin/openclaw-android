package com.openclaw.android.ui.linux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.service.ProotExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackageManagerViewModel @Inject constructor(
    private val executor: ProotExecutor,
) : ViewModel() {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun update() = execute("apt update")
    fun upgrade() = execute("apt-get -y upgrade")
    fun install(pkg: String) {
        if (pkg.isBlank()) return
        execute("apt-get -y install $pkg")
    }
    fun remove(pkg: String) {
        if (pkg.isBlank()) return
        execute("apt-get -y remove $pkg")
    }

    private fun execute(command: String) {
        if (_running.value) return
        _running.value = true
        append("$ $command\n")
        viewModelScope.launch {
            try {
                executor.run(
                    command = "command -v apt-get >/dev/null 2>&1 && $command || echo 'apt 不可用，请先执行 apt update'",
                    onOutput = { line -> append(line) },
                )
            } finally {
                _running.value = false
            }
        }
    }

    private fun append(text: String) {
        _lines.value = (_lines.value + text).takeLast(MAX_LINES)
    }

    companion object {
        private const val MAX_LINES = 1_000
    }
}