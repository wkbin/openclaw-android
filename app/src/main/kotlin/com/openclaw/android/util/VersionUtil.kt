package com.openclaw.android.util

/**
 * Compares dotted version strings that may carry numeric or prerelease suffixes,
 * such as "2026.7.1" vs "2026.7.1-2" or "1.0.0-rc1".
 *
 * Numeric suffixes (e.g. "-2", "+build5") are parsed as an extra numeric
 * component, so "2026.7.1-3" > "2026.7.1-2" > "2026.7.1". Non-numeric segments
 * (e.g. "beta") are treated as 0 so they never crash the comparison; they sort
 * below any numeric suffix of the same base version.
 */
internal object VersionUtil {
    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun parse(version: String): List<Int> =
        version.trim()
            .split('.')
            .flatMap { segment -> segment.split('-', '+') }
            .map { part ->
                val trailingDigits = part.takeLastWhile { it.isDigit() }
                if (trailingDigits.isNotEmpty()) trailingDigits.toIntOrNull() ?: 0 else 0
            }
}
