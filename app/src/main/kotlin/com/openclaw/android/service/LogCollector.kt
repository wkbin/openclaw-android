package com.openclaw.android.service

import com.openclaw.android.model.LogLevel
import com.openclaw.android.repository.LogRepository
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject

class LogCollector @Inject constructor(
    private val logRepository: LogRepository,
) {
    suspend fun collect(
        stream: InputStream,
        level: LogLevel,
        source: String,
    ) {
        BufferedReader(InputStreamReader(stream)).use { reader ->
            while (true) {
                val line = try {
                    reader.readLine() ?: break
                } catch (_: IOException) {
                    break
                }
                if (line.isNotEmpty()) {
                    logRepository.append(level, source, line)
                }
            }
        }
    }
}
