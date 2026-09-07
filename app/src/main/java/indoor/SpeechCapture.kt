package com.example.pathfinderindoor.indoor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechCapture(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    var onResult: ((transcript: String) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null
    var onListeningStarted: (() -> Unit)? = null

    fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke(
                "Speech recognition is not available on this phone."
            )
            return
        }

        recognizer?.destroy()

        recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStarted?.invoke()
            }

            override fun onBeginningOfSpeech() {
                // User started speaking
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Microphone level changed
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // User stopped speaking
            }

            override fun onError(error: Int) {

                val message = when (error) {

                    SpeechRecognizer.ERROR_AUDIO ->
                        "Microphone audio error."

                    SpeechRecognizer.ERROR_CLIENT ->
                        "Speech recognition client error."

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is required."

                    SpeechRecognizer.ERROR_NETWORK ->
                        "Network error. Check your internet connection."

                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network timeout. Check your internet connection."

                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "No speech recognized. Please speak clearly and try again."

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "Speech recognizer is busy. Try again."

                    SpeechRecognizer.ERROR_SERVER ->
                        "Speech recognition server error."

                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No speech detected. Tap the button and speak."

                    else ->
                        "Speech recognition error code: $error"
                }

                onError?.invoke(message)
            }

            override fun onResults(results: Bundle?) {

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val best = matches?.firstOrNull()

                if (!best.isNullOrBlank()) {
                    onResult?.invoke(best)
                } else {
                    onError?.invoke(
                        "No speech was recognized. Please try again."
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Final result is used for parsing.
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {}
        })

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            // Indian English
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-IN"
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "en-IN"
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            onError?.invoke(
                "Could not start speech recognition: ${e.message}"
            )
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
