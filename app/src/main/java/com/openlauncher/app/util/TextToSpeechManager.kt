package com.openlauncher.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val focusListener = android.media.AudioManager.OnAudioFocusChangeListener { }
    @Volatile private var activeUtterance: String? = null
    private val engine = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null
    @Volatile private var languageTag: String = "fr-CA"

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        engine.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id:String?) = Unit
            override fun onDone(id:String?) = releaseFocus(id)
            @Deprecated("Android callback")
            override fun onError(id:String?) = releaseFocus(id)
            override fun onStop(id:String?,interrupted:Boolean) = releaseFocus(id)
        })
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
        if(text.isNotBlank()) speakNow(text)
    }

    fun setLanguage(tag: String) {
        if (languageTag == tag) return
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

    private fun releaseFocus(id:String?) {
        if(id==activeUtterance) { activeUtterance=null; audio.abandonAudioFocus(focusListener) }
    }
    fun stop() { pendingText=null; val id=activeUtterance; activeUtterance=null; if(ready) engine.stop(); if(id!=null) audio.abandonAudioFocus(focusListener) }
    private fun speakNow(text: String) {
        engine.setAudioAttributes(android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val id="open_launcher_${System.nanoTime()}"
        activeUtterance=id
        audio.requestAudioFocus(focusListener,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        if(engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)==TextToSpeech.ERROR) releaseFocus(id)
    }

    fun close() {
        stop()
        ready = false
        pendingText = null
        engine.stop()
        engine.shutdown()
    }
}
