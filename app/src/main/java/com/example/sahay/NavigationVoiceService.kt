package com.example.sahay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class NavigationVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null

    private val handler =
        Handler(Looper.getMainLooper())

    private var isDestroyed = false
    private var listening = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            1001,
            createNotification()
        )

        startListening()
    }

    private fun startListening() {

        if (isDestroyed) return

        releaseRecognizer()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: android.os.Bundle?
                ) {
                    listening = true
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(
                    error: Int
                ) {

                    listening = false

                    releaseRecognizer()

                    /*
                     * Wait before trying again.
                     * This prevents a tight microphone loop.
                     */
                    if (!isDestroyed) {

                        handler.postDelayed(
                            {
                                startListening()
                            },
                            1200
                        )
                    }
                }

                override fun onResults(
                    results: android.os.Bundle?
                ) {

                    listening = false

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches
                            ?.firstOrNull()
                            ?.lowercase(
                                Locale.getDefault()
                            )
                            ?.trim()

                    /*
                     * Release microphone BEFORE
                     * performing the command.
                     */
                    releaseRecognizer()

                    if (command != null) {
                        handleCommand(command)
                    }

                    /*
                     * Listen again after a short delay.
                     */
                    if (!isDestroyed) {

                        handler.postDelayed(
                            {
                                startListening()
                            },
                            1000
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: android.os.Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: android.os.Bundle?
                ) {
                }
            }
        )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            5
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            false
        )

        try {

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {

            releaseRecognizer()

            if (!isDestroyed) {

                handler.postDelayed(
                    {
                        startListening()
                    },
                    1500
                )
            }
        }
    }

    private fun releaseRecognizer() {

        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null
        listening = false
    }

    private fun handleCommand(
        command: String
    ) {

        when {

            command.contains("stop navigation") ||
                    command.contains("stop navigating") ||
                    command == "stop" -> {

                SahayAccessibilityService
                    .stopMapsNavigation()
            }

            command.contains("continue navigation") ||
                    command.contains("continue navigating") ||
                    command.contains("resume navigation") ||
                    command.contains("resume navigating") ||
                    command == "continue" ||
                    command == "resume" -> {

                SahayAccessibilityService
                    .continueMapsNavigation()
            }

            command.contains("return to app") ||
                    command.contains("return to sahay") ||
                    command.contains("open sahay") ||
                    command.contains("open sahāy") -> {

                SahayAccessibilityService
                    .returnToSahay()
            }
        }
    }

    override fun onDestroy() {

        isDestroyed = true

        handler.removeCallbacksAndMessages(null)

        /*
         * ALWAYS release the microphone.
         */
        releaseRecognizer()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    "SAHAY_NAVIGATION",
                    "Sahāy Navigation",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(): Notification {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            Notification.Builder(
                this,
                "SAHAY_NAVIGATION"
            )
                .setContentTitle(
                    "Sahāy Navigation"
                )
                .setContentText(
                    "Voice navigation control is active"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_compass
                )
                .build()

        } else {

            Notification.Builder(this)
                .setContentTitle(
                    "Sahāy Navigation"
                )
                .setContentText(
                    "Voice navigation control is active"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_compass
                )
                .build()
        }
    }
}