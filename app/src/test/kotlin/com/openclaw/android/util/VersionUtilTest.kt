package com.openclaw.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilTest {

    private fun assertLess(left: String, right: String) {
        assertTrue("expected $left < $right", VersionUtil.compare(left, right) < 0)
    }

    private fun assertGreater(left: String, right: String) {
        assertTrue("expected $left > $right", VersionUtil.compare(left, right) > 0)
    }

    private fun assertEqual(left: String, right: String) {
        assertEquals("expected $left == $right", 0, VersionUtil.compare(left, right))
    }

    @Test
    fun `identical versions are equal`() {
        assertEqual("2026.7.1-2", "2026.7.1-2")
        assertEqual("2026.7.1", "2026.7.1")
        assertEqual("bootstrap", "bootstrap")
    }

    @Test
    fun `numeric suffix is newer than same base version`() {
        assertGreater("2026.7.1-3", "2026.7.1-2")
        assertGreater("2026.7.1-2", "2026.7.1")
        assertLess("2026.7.1", "2026.7.1-2")
    }

    @Test
    fun `normal dotted versions compare numerically`() {
        assertGreater("2026.8.0", "2026.7.1")
        assertLess("2026.7.1", "2026.8.0")
        assertGreater("2027.1.0", "2026.12.31")
    }

    @Test
    fun `missing components default to zero`() {
        assertEqual("2026.7", "2026.7.0")
        assertGreater("2026.7.1-2", "2026.7.1")
        assertLess("2026.7", "2026.7.0-1")
    }

    @Test
    fun `bootstrap sorts below any real version`() {
        assertGreater("2026.7.1-2", "bootstrap")
        assertGreater("2026.7.1", "bootstrap")
        assertLess("bootstrap", "0.1.0")
    }

    @Test
    fun `plus build metadata is treated as numeric component`() {
        assertGreater("2026.7.1+build5", "2026.7.1+build3")
        assertGreater("2026.7.1+build3", "2026.7.1")
    }

    @Test
    fun `non-numeric prerelease marker never crashes and sorts below numeric suffix`() {
        assertLess("1.0.0-beta", "1.0.0-1")
        assertEqual("1.0.0-beta", "1.0.0")
    }

    @Test
    fun `prerelease name with trailing digit follows trailing-digit precedence`() {
        // rc1 -> 1，与 "+buildN"/"纯数字" 共用同一尾随数字优先级
        assertGreater("1.0.0-rc2", "1.0.0-rc1")
        assertLess("1.0.0-beta", "1.0.0-rc1")
        assertGreater("1.0.0+build5", "1.0.0+build3")
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEqual(" 2026.7.1-2 ", "2026.7.1-2")
    }
}
