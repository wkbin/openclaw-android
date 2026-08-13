package com.openclaw.android.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val FILE_NAME = "crash/last-crash.txt"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(context, thread, throwable)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }

    fun readLastCrash(context: Context): String? {
        val file = crashFile(context)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun write(context: Context, content: String) {
        writeText(context, content)
    }

    fun clear(context: Context) {
        crashFile(context).delete()
    }

    private fun writeCrash(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ) {
        val content = buildString {
            appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("thread=${thread.name}")
            appendLine(Log.getStackTraceString(throwable))
        }
        writeText(context, content)
    }

    private fun writeText(context: Context, content: String) {
        runCatching {
            val file = crashFile(context)
            file.parentFile?.mkdirs()
            FileOutputStream(file, false).use { output ->
                output.write(content.toByteArray())
            }
        }
    }

    private fun crashFile(context: Context): File = File(context.filesDir, FILE_NAME)
}
