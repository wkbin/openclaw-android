package com.openclaw.android.repository

import android.content.Context
import com.openclaw.android.model.LogEntry
import com.openclaw.android.model.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val buffer = ArrayDeque<LogEntry>()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    suspend fun append(
        level: LogLevel,
        source: String,
        message: String,
    ) = withContext(Dispatchers.IO) {
        val entry = LogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message,
        )

        mutex.withLock {
            buffer.addLast(entry)
            while (buffer.size > MAX_MEMORY_ENTRIES) {
                buffer.removeFirst()
            }
            _entries.value = buffer.toList()
            appendToFile(entry)
        }
    }

    suspend fun clearMemory() {
        mutex.withLock {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    suspend fun exportLogs(): File = withContext(Dispatchers.IO) {
        val source = logFileFor(LocalDate.now())
        if (!source.exists()) {
            source.parentFile?.mkdirs()
            source.writeText("")
        }
        val exported = File(context.cacheDir, "openclaw-logs-${System.currentTimeMillis()}.txt")
        source.copyTo(exported, overwrite = true)
        exported
    }

    private fun appendToFile(entry: LogEntry) {
        runCatching {
            val file = logFileFor(LocalDate.now())
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { output ->
                val line = "${entry.isoTime} [${entry.level.name.uppercase()}] " +
                    "[${entry.source}] ${entry.message}\n"
                output.write(line.toByteArray())
            }
            pruneOldLogs()
        }
    }

    private fun logFileFor(date: LocalDate): File {
        val name = "gateway-${date.format(LOG_DATE_FORMAT)}.log"
        return File(context.filesDir, "logs/$name")
    }

    private fun pruneOldLogs() {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val cutoff = LocalDate.now().minusDays(LOG_RETENTION_DAYS)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("gateway-") }
            ?.forEach { file ->
                if (LocalDate.parse(
                        file.name.removePrefix("gateway-").removeSuffix(".log"),
                        LOG_DATE_FORMAT,
                    ).isBefore(cutoff)
                ) {
                    file.delete()
                }
            }
    }

    private companion object {
        const val MAX_MEMORY_ENTRIES = 500
        const val LOG_RETENTION_DAYS = 7L
        val LOG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
