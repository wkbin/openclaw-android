package com.openclaw.android.model

import java.time.Instant

enum class LogLevel {
    Debug,
    Info,
    Error,
}

data class LogEntry(
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
) {
    val isoTime: String
        get() = Instant.ofEpochMilli(timestampEpochMillis).toString()
}

