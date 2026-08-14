package com.openclaw.android.util

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files

private data class PendingLink(
    val output: File,
    val targetName: String,
    val symbolic: Boolean,
)

object TarUtil {
    fun extractTar(
        archive: File,
        destination: File,
    ) {
        destination.mkdirs()
        TarArchiveInputStream(
            BufferedInputStream(FileInputStream(archive)),
        ).use { tar ->
            extractEntries(tar, destination)
        }
    }

    fun extractTarGz(
        archive: File,
        destination: File,
    ) {
        destination.mkdirs()
        TarArchiveInputStream(
            GzipCompressorInputStream(
                BufferedInputStream(FileInputStream(archive)),
            ),
        ).use { tar ->
            extractEntries(tar, destination)
        }
    }

    private fun extractEntries(
        tar: TarArchiveInputStream,
        destination: File,
    ) {
        val pendingLinks = mutableListOf<PendingLink>()
        while (true) {
            val entry: TarArchiveEntry = tar.nextEntry ?: break
            val root = destination.canonicalFile
            var relativeName = entry.name.replace('\\', '/').trimStart('/')
            while (relativeName.startsWith("./")) {
                relativeName = relativeName.substring(2)
            }
            if (relativeName.isEmpty()) {
                destination.mkdirs()
                continue
            }
            if (relativeName.split('/').any { it == ".." }) {
                throw SecurityException("Archive entry escapes destination: ${entry.name}")
            }

            val output = File(destination, relativeName).canonicalFile
            if (output != root && !output.path.startsWith(root.path + File.separator)) {
                throw SecurityException("Archive entry escapes destination: ${entry.name}")
            }

            if (entry.isLink || entry.isSymbolicLink) {
                pendingLinks += PendingLink(
                    output = output,
                    targetName = entry.linkName,
                    symbolic = entry.isSymbolicLink,
                )
                continue
            }

            if (entry.isDirectory) {
                output.mkdirs()
                continue
            }

            output.parentFile?.mkdirs()
            FileOutputStream(output).use { out ->
                tar.copyTo(out)
            }

            if (entry.mode and 0b001_000_000 != 0) {
                output.setExecutable(true, true)
            }
        }

        for (pending in pendingLinks) {
            var targetName = pending.targetName.replace('\\', '/').trimStart('/')
            while (targetName.startsWith("./")) {
                targetName = targetName.substring(2)
            }
            val target = File(destination, targetName).canonicalFile
            pending.output.parentFile?.mkdirs()
            if (target.exists()) {
                runCatching {
                    Files.createLink(pending.output.toPath(), target.toPath())
                }.getOrElse {
                    target.copyTo(pending.output, overwrite = true)
                }
            } else {
                pending.output.writeText("")
            }
        }
    }
}
