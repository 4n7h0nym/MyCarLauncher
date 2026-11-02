package com.openlauncher.app.util

import com.openlauncher.app.model.NavDestination

/** Picker state cannot be restored from a persisted page name alone. */
fun restoredDestination(enabled: Boolean, page: String): NavDestination =
    if (!enabled) NavDestination.HOME else
        runCatching { NavDestination.valueOf(page) }.getOrDefault(NavDestination.HOME)
            .takeUnless { it == NavDestination.APP_LIBRARY } ?: NavDestination.HOME

fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

/** /proc/stat counters: guest time is already included in user/nice. */
data class CpuTimes(val total: Long, val idle: Long) {
    fun usageSince(previous: CpuTimes?): Float? {
        if (previous == null) return null
        val elapsed = total - previous.total
        val idleDelta = idle - previous.idle
        if (elapsed <= 0 || idleDelta !in 0..elapsed) return null
        return (100f * (elapsed - idleDelta) / elapsed).coerceIn(0f, 100f)
    }

    companion object {
        fun parse(line: String?): CpuTimes? = runCatching {
            val fields = line?.trim()?.split(Regex("\\s+")) ?: return null
            if (fields.firstOrNull() != "cpu" || fields.size < 5) return null
            val counters = fields.drop(1).take(8).map { it.toLong().also { n -> require(n >= 0) } }
            CpuTimes(counters.sum(), counters[3] + counters.getOrElse(4) { 0L })
        }.getOrNull()
    }
}
