package com.example.sahay

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale

class VoiceManager(
    context: Context
) {

    private var textToSpeech: TextToSpeech? = null

    init {

        textToSpeech = TextToSpeech(
            context
        ) { status ->

            if (status == TextToSpeech.SUCCESS) {

                textToSpeech?.language =
                    Locale.getDefault()
            }
        }
    }

    fun speak(
        message: String,
        onDone: (() -> Unit)? = null
    ) {

        val id =
            "SAHAY_${System.currentTimeMillis()}"

        textToSpeech?.setOnUtteranceProgressListener(

            object : UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {
                }

                override fun onDone(
                    utteranceId: String?
                ) {

                    if (utteranceId == id) {

                        Handler(
                            Looper.getMainLooper()
                        ).post {

                            onDone?.invoke()
                        }
                    }
                }

                override fun onError(
                    utteranceId: String?
                ) {

                    if (utteranceId == id) {

                        Handler(
                            Looper.getMainLooper()
                        ).post {

                            onDone?.invoke()
                        }
                    }
                }
            }
        )

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            id
        )
    }

    fun shutdown() {

        textToSpeech?.stop()

        textToSpeech?.shutdown()

        textToSpeech = null
    }
}