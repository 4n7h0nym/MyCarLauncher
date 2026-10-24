package com.openlauncher.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceAssistantManager(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun listen(
        languageTag: String = "fr-CA",
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onFailure(if (languageTag.startsWith("en")) "Speech recognition is not available on this device." else "La reconnaissance vocale n’est pas disponible sur cet appareil.")
            return
        }
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onFailure(if (languageTag.startsWith("en")) "I didn't understand the command." else "Je n’ai pas compris la commande.") else onFinal(text)
                stop()
            }

            override fun onError(error: Int) {
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
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure {
                onFailure(if (languageTag.startsWith("en")) "Unable to start voice listening." else "Impossible de démarrer l’écoute vocale.")
            }
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    fun close() = stop()
}
