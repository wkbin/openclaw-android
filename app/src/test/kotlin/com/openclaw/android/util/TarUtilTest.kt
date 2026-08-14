package com.openclaw.android.util

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class TarUtilTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeArchive(
        entries: Map<String, String> = emptyMap(),
        hardLinks: Map<String, String> = emptyMap(),
        symlinks: Map<String, String> = emptyMap(),
    ): File {
        val archive = tempFolder.newFile("sample.tar.gz")
        TarArchiveOutputStream(
            BufferedOutputStream(GzipCompressorOutputStream(FileOutputStream(archive))),
        ).use { tar ->
            entries.forEach { (name, content) ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            hardLinks.forEach { (name, target) ->
                val entry = TarArchiveEntry(name, TarConstants.LF_LINK)
                entry.linkName = target
                tar.putArchiveEntry(entry)
                tar.closeArchiveEntry()
            }
            symlinks.forEach { (name, target) ->
                val entry = TarArchiveEntry(name, TarConstants.LF_SYMLINK)
                entry.linkName = target
                tar.putArchiveEntry(entry)
                tar.closeArchiveEntry()
            }
        }
        return archive
    }

    private fun writePlainTar(
        entries: Map<String, String>,
    ): File {
        val archive = tempFolder.newFile("sample.tar")
        TarArchiveOutputStream(
            BufferedOutputStream(FileOutputStream(archive)),
        ).use { tar ->
            entries.forEach { (name, content) ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return archive
    }

    private fun assertSecurityException(block: () -> Unit) {
        try {
            block()
            fail("expected SecurityException")
        } catch (_: SecurityException) {
            // expected
        }
    }

    @Test
    fun `extracts regular archive`() {
        val archive = writeArchive(
            entries = mapOf(
                "openclaw.mjs" to "console.log('hi')",
                "lib/a.txt" to "hello",
            ),
        )
        val dest = tempFolder.newFolder("dest")
        TarUtil.extractTarGz(archive, dest)
        assertTrue(File(dest, "openclaw.mjs").readText() == "console.log('hi')")
        assertTrue(File(dest, "lib/a.txt").readText() == "hello")
    }

    @Test
    fun `rejects dot dot entries`() {
        val archive = writeArchive(entries = mapOf("../escape.txt" to "evil"))
        val dest = tempFolder.newFolder("dest")
        assertSecurityException { TarUtil.extractTarGz(archive, dest) }
        assertFalse(File(tempFolder.root, "escape.txt").exists())
    }

    @Test
    fun `rejects hard link targets escaping destination`() {
        val archive = writeArchive(
            entries = mapOf("victim.txt" to "data"),
            hardLinks = mapOf("link" to "../outside.txt"),
        )
        val dest = tempFolder.newFolder("dest")
        assertSecurityException { TarUtil.extractTarGz(archive, dest) }
    }

    @Test
    fun `rejects symlink targets escaping destination`() {
        val archive = writeArchive(
            entries = mapOf("victim.txt" to "data"),
            symlinks = mapOf("link" to "../outside.txt"),
        )
        val dest = tempFolder.newFolder("dest")
        assertSecurityException { TarUtil.extractTarGz(archive, dest) }
    }

    @Test
    fun `allows safe internal hard link`() {
        val archive = writeArchive(
            entries = mapOf("real.txt" to "content"),
            hardLinks = mapOf("alias.txt" to "real.txt"),
        )
        val dest = tempFolder.newFolder("dest")
        TarUtil.extractTarGz(archive, dest)
        assertTrue(File(dest, "alias.txt").exists())
    }

    @Test
    fun `rejects absolute style dot dot with backslashes`() {
        val archive = writeArchive(entries = mapOf("..\\escape.txt" to "evil"))
        val dest = tempFolder.newFolder("dest")
        assertSecurityException { TarUtil.extractTarGz(archive, dest) }
        assertFalse(File(tempFolder.root, "escape.txt").exists())
    }

    @Test
    fun `extractAuto handles gzip and plain tar`() {
        val gzArchive = writeArchive(entries = mapOf("a.txt" to "gzip"))
        val dest1 = tempFolder.newFolder("dest-gz")
        TarUtil.extractAuto(gzArchive, dest1)
        assertTrue(File(dest1, "a.txt").readText() == "gzip")

        val plainArchive = writePlainTar(entries = mapOf("b.txt" to "plain"))
        val dest2 = tempFolder.newFolder("dest-plain")
        TarUtil.extractAuto(plainArchive, dest2)
        assertTrue(File(dest2, "b.txt").readText() == "plain")
    }
}

