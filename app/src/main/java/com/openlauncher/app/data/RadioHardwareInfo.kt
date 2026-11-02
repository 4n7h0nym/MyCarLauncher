package com.openlauncher.app.data

import android.os.Build
import java.io.File

object RadioHardwareInfo {
    /** Same RK3326 sysfs node and radio-character test as ProductRK3326/ProductBase. */
    fun read():String {
        val board=runCatching { File("/sys/devices/platform/jac_hw/jancar_board_id").bufferedReader().use { it.readLine() }?.trim()?.take(5) }
            .getOrNull()?.takeIf { it.length==5 && !it.startsWith("00_") && it.all { c -> c.isLetterOrDigit() || c=='_' } }
        val tuner=when {
            board==null -> "variante tuner non déterminée"
            board[4] in 'T'..'Z' -> "tuner externe IRadio"
            else -> "tuner interne FmService"
        }
        return "${Build.MODEL} · carte ${board ?: "non lisible"} · $tuner"
    }
}
