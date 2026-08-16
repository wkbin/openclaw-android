package com.openclaw.android.util

/**
 * Compares dotted version strings that may carry numeric or prerelease suffixes,
 * such as "2026.7.1" vs "2026.7.1-2" or "1.0.0-rc1".
 *
 * 后缀段（-/+ 分隔）按尾随数字取优先级：整段纯数字（如 "-2"）或末尾带数字
 * （如 "+build5"→5、"rc1"→1）都取该数字作为额外数值分量，因此
 * "2026.7.1-3" > "2026.7.1-2" > "2026.7.1"，"+build5" > "+build3"。
 * 无尾随数字的段（如 "beta"）取 0，故其排在同基数的任何数字后缀之下。
 * 注意：预发布名与纯数字 build 号可能取到相同分量而比较相等（如 "rc1" 与 "-1"），
 * 本项目打包的 OpenClaw 版本均使用纯数字后缀（"-N"/"+buildN"），不受此影响。
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
