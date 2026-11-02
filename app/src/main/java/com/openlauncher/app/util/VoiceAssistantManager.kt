package com.openlauncher.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceAssistantManager(context: Context) {
    private val appContext = context.applicationContext
    private var tone: android.media.ToneGenerator? = null
    private var recognizer: SpeechRecognizer? = null

    fun isServiceAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    fun listen(
        languageTag: String = "fr-CA",
        onReady: () -> Unit = {},
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        stop()
        if (!isServiceAvailable()) {
            onFailure(if (languageTag.startsWith("en")) "Speech recognition is not available on this device." else "La reconnaissance vocale n’est pas disponible sur cet appareil.")
            return
        }
        val speechRecognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }
            .getOrElse {
                onFailure(if (languageTag.startsWith("en")) "Unable to open the speech service." else "Impossible d’ouvrir le service de reconnaissance vocale.")
                return
            }
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if(recognizer !== speechRecognizer) return
                runCatching {
                    tone=android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,35)
                    tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,120)
                }
                onReady()
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                if(recognizer !== speechRecognizer) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                if(recognizer !== speechRecognizer) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                stop()
                if (text.isNullOrBlank()) onFailure(if (languageTag.startsWith("en")) "I didn't understand the command." else "Je n’ai pas compris la commande.") else onFinal(text)
            }

            override fun onError(error: Int) {
                if(recognizer !== speechRecognizer) return
                stop()
                onFailure(
                    if (languageTag.startsWith("en")) when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't understand. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No command detected."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access is required."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech service is unavailable."
                        else -> "Speech recognition failed ($error)."
                    } else when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Je n’ai pas compris. Réessayez."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune commande détectée."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "L’accès au microphone est requis."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Le service vocal est indisponible."
                        else -> "La reconnaissance vocale a échoué ($error)."
                    }
                )
                stop()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra("android.speech.extra.PREFER_OFFLINE", true)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure {
                onFailure(if (languageTag.startsWith("en")) "Unable to start voice listening." else "Impossible de démarrer l’écoute vocale.")
            }
    }

    fun stop() {
        tone?.release(); tone=null
        val previous=recognizer
        recognizer=null
        previous?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    fun close() = stop()
}
