package com.openclaw.android.util

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileUtil {
    fun extractAsset(
        context: Context,
        assetPath: String,
        targetFile: File,
    ) {
        targetFile.parentFile?.mkdirs()
        // 解压前预检目标盘可用空间，避免磁盘满时读到一半抛裸 IOException
        val open = context.assets.open(assetPath)
        open.use { input ->
            val needed = input.available().toLong().coerceAtLeast(0L)
            if (availableBytes(targetFile.parentFile!!) < needed) {
                throw java.io.IOException("存储空间不足，无法解压 $assetPath")
            }
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun ensureExecutable(file: File) {
        runCatching {
            android.system.Os.chmod(file.absolutePath, 0b111_101_101)
        }.getOrElse {
            file.setExecutable(true, false)
        }
    }

    fun availableBytes(directory: File): Long {
        return try {
            directory.mkdirs()
            StatFs(directory.absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    fun atomicWriteText(
        target: File,
        text: String,
    ) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        file.delete()
    }
}
