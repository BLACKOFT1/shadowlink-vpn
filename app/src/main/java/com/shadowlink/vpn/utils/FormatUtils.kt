package com.shadowlink.vpn.utils

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
            else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
        }
    }

    fun formatMb(mb: Long): String {
        return when {
            mb < 1024 -> "$mb MB"
            else -> "%.1f GB".format(mb / 1024f)
        }
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
