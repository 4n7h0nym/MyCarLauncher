package com.openlauncher.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null
    @Volatile private var languageTag: String = "fr-CA"

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        applyLanguage()
        ready = true
        pendingText?.let { speakNow(it) }
        pendingText = null
    }

    fun speak(text: String) {
        if (!ready) {
            pendingText = text
            return
        }
        speakNow(text)
    }

    fun setLanguage(tag: String) {
        languageTag = tag
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val requested = Locale.forLanguageTag(languageTag)
        val result = engine.setLanguage(requested)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = if (requested.language == "en") Locale.CANADA else Locale.CANADA_FRENCH
            engine.setLanguage(fallback)
        }
    }

    private fun speakNow(text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "open_launcher_${System.nanoTime()}")
    }

    fun close() {
        ready = false
        pendingText = null
        engine.stop()
        engine.shutdown()
    }
}
