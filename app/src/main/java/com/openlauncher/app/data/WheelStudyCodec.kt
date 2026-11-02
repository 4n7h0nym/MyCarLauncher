package com.openlauncher.app.data

/** Values are bytes in the OEM wire format; channels 5/6 are steering inputs. */
data class WheelAdc(val channel: Int, val value: Int, val high: Int, val mid: Int, val low: Int) {
    fun valid() = channel in 5..6 && listOf(value, high, mid, low).all { it in 0..255 } && listOf(high, mid, low).any { it < 253 }
}
data class WheelMapping(val sample: WheelAdc, val action: String, val repeat: Boolean = false, val longAction: String = "")

enum class WheelFunction(val action: String, val label: String) {
    VOLUME_UP("add_volume", "Volume +"), VOLUME_DOWN("del_volume", "Volume −"), MUTE("mute", "Sourdine"),
    NEXT("next", "Piste suivante"), PREVIOUS("prev", "Piste précédente"), PLAY_PAUSE("play_pause", "Lecture / Pause"),
    NEXT_HANGUP("next_hangup", "Piste suivante / Raccrocher"),
    PREVIOUS_ANSWER("prev_answer", "Piste précédente / Décrocher"),
    HOME("home", "Accueil"), ANDROID_AUTO("phone_link", "Android Auto"), NAV("navigation", "GPS / Navigation"),
    MODE("mode", "Mode"), ANSWER("answer", "Décrocher"), HANG_UP("hang_up", "Raccrocher"), VOICE("voice", "Assistant vocal"),
    CONTEXTUAL("launcher_app", "Accueil / Android Auto")
}

object WheelStudyCodec {
    /** Preserve the other press action when relearning the same physical button. */
    fun assign(existing:List<WheelMapping>,sample:WheelAdc,function:WheelFunction,longPress:Boolean):List<WheelMapping> {
        val matched=existing.firstOrNull { it.sample.let { old -> old.channel==sample.channel &&
            kotlin.math.abs(old.high-sample.high)<=4 && kotlin.math.abs(old.mid-sample.mid)<=4 && kotlin.math.abs(old.low-sample.low)<=4 } }
        val mapping=WheelMapping(sample,if(longPress) matched?.action.orEmpty() else function.action,
            if(longPress) matched?.repeat ?: false else function in listOf(WheelFunction.VOLUME_UP,WheelFunction.VOLUME_DOWN),
            if(longPress) function.action else matched?.longAction.orEmpty())
        return existing.filterNot { it===matched }.map {
            if(longPress && it.longAction==function.action) it.copy(longAction="")
            else if(!longPress && it.action==function.action) it.copy(action="",repeat=false) else it
        }.filter { it.action.isNotBlank() || it.longAction.isNotBlank() }+mapping
    }
    const val SECTION = "section_wheel_Study_Key"
    /** Port of the OEM smali setOriMiddle selection and midpoint boundaries, per channel. */
    fun entries(mappings: List<WheelMapping>): List<Pair<String, String>> = mappings.groupBy { it.sample.channel }.values.flatMap { channel ->
        val sorted = channel.sortedBy { it.sample.low + it.sample.mid + it.sample.high }
        sorted.mapIndexed { index, mapping ->
            val s = mapping.sample
            require(s.valid())
            val values = listOf(s.low, s.mid, s.high)
            val previous = sorted.getOrNull(index-1)?.sample?.let { listOf(it.low,it.mid,it.high) } ?: listOf(0,0,0)
            val next = sorted.getOrNull(index+1)?.sample?.let { listOf(it.low,it.mid,it.high) } ?: listOf(252,252,252)
            val lower = values.indices.map { (values[it]-previous[it]).coerceAtLeast(0) }
            val upper = values.indices.map { (next[it]-values[it]).coerceAtLeast(0) }
            val preferred = values.indices.firstOrNull { values[it] > 210 && lower[it] > 4 && upper[it] > 4 }
            val best = preferred ?: values.indices.maxWith(compareBy<Int> { minOf(lower[it],upper[it]) }.thenBy { values[it] }.thenBy { it })
            val start = if (index == 0) 0 else values[best] - (lower[best]-1)/2
            val end = if (index == sorted.lastIndex) minOf(values[best]+6,252) else values[best]+(upper[best]-1)/2
            val center = (start+end)/2; val radius = (end-start)/2
            val min = (center-radius).coerceIn(0,252); val max = (center+radius).coerceIn(0,252)
            require(min <= max && values[best] in min..max) { "Calibration ADC ambiguë" }
            val flags = (if (best == 0) 4 else if (best == 1) 2 else 0) + if (mapping.repeat) 1 else 0
            val key = (flags shl 24) or (s.channel shl 16) or (max shl 8) or min
            key.toString() to "${s.low},${s.mid},${s.high};${mapping.action}${if (mapping.longAction.isBlank()) "" else ",${mapping.longAction}"}"
        }
    }
    /** Preserve every unrelated section and comment; replace only the wheel section. */
    fun replaceSection(original: String, entries: List<Pair<String,String>>): String {
        val output = mutableListOf<String>(); var skip = false
        original.lines().forEach { line ->
            if (line.trim().startsWith("[") && line.trim().endsWith("]")) skip = line.trim() == "[$SECTION]"
            if (!skip) output += line
        }
        return output.joinToString("\n").trimEnd()+"\n\n[$SECTION]\n"+entries.joinToString("\n") { "${it.first}=${it.second}" }+"\n"
    }
    fun read(original: String): List<WheelMapping> {
        var inside = false
        return original.lines().mapNotNull { line ->
            val text = line.trim()
            if (text.startsWith("[")) { inside = text == "[$SECTION]"; null }
            else if (!inside || text.isBlank() || text.startsWith(";") || text.startsWith("#")) null
            else {
                val pair = text.split('=',limit=2); require(pair.size==2)
                val key = pair[0].trim().toInt(); val value = pair[1].split(';',limit=2)
                require(value.size==2) { "Ancien format de calibration non pris en charge" }
                val adc = value[0].split(',').map { it.toInt() }; require(adc.size==3)
                val actions = value[1].split(',',limit=2)
                WheelMapping(WheelAdc((key ushr 16) and 255,0,adc[2],adc[1],adc[0]),actions[0],key and (1 shl 24)!=0,actions.getOrElse(1){""}).also { require(it.sample.valid()) }
            }
        }
    }
}
