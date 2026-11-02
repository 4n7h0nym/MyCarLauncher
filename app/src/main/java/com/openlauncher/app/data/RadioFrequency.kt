package com.openlauncher.app.data

import kotlin.math.roundToInt

/** Legacy UI MHz/FM and kHz/AM conversion; never truncate float frequencies. */
object RadioFrequency {
    fun matchesBand(band: Int?, khz: Int): Boolean = when(band) {
        0 -> khz in 1..9999
        1 -> khz >= 10000
        else -> false
    }
    fun toKhz(band: String, frequency: Float): Int? {
        if (!frequency.isFinite() || frequency <= 0f) return null
        val khz = frequency.toDouble() * if (band.equals("AM", true)) 1.0 else 1000.0
        if (khz < 1 || khz > Int.MAX_VALUE) return null
        return khz.roundToInt()
    }
}
