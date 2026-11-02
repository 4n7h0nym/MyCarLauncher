package com.openlauncher.app.data

import android.content.Context
import com.openlauncher.app.radio.ui.RadioPreset

/** Private preset slots: retain the previous screen's six initial FM/AM frequencies. */
object RadioPresetStore {
    private val fmDefaults = listOf(88500, 91100, 95700, 101100, 103500, 107900)
    private val amDefaults = listOf(540, 680, 820, 1040, 1260, 1420)
    fun read(context: Context, bank: String): List<RadioPreset> {
        val prefs = context.getSharedPreferences("radio_presets", Context.MODE_PRIVATE)
        return (if (bank == "AM") amDefaults else fmDefaults).mapIndexed { index, default ->
            val value = prefs.getInt("${bank}_$index", default).takeIf { it > 0 } ?: default
            RadioPreset("${bank}_$index", index + 1, value, fm = bank != "AM")
        }
    }
    fun save(context: Context, bank: String, index: Int, frequencyKhz: Int) {
        require(bank in listOf("FM1", "FM2", "AM") && index in 0..5 && frequencyKhz > 0)
        context.getSharedPreferences("radio_presets", Context.MODE_PRIVATE).edit()
            .putInt("${bank}_$index", frequencyKhz).apply()
    }
}
