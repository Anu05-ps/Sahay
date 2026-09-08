package com.example.sahay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CHECK_SETTINGS = 200
    }

    private lateinit var destinationInput: EditText
    private lateinit var speakButton: Button
    private lateinit var startNavigationButton: Button

    private lateinit var voiceManager: VoiceManager
    private lateinit var navigationManager: NavigationManager

    private val speechRequestCode = 100
    private val microphonePermissionCode = 101
    private val voiceCommandRequestCode = 102

    private var pendingDestination: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        voiceManager = VoiceManager(this)
        navigationManager = NavigationManager(this)

        destinationInput =
            findViewById(R.id.destinationInput)

        speakButton =
            findViewById(R.id.speakButton)

        startNavigationButton =
            findViewById(R.id.startNavigationButton)

        speakButton.contentDescription =
            "Speak destination"

        startNavigationButton.contentDescription =
            "Start navigation"

        speakButton.setOnClickListener {
            checkMicrophonePermissionForDestination()
        }

        startNavigationButton.setOnClickListener {
            startNavigation()
        }

        voiceManager.speak(
            "Welcome to Sahāy. Say your destination."
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == REQUEST_CHECK_SETTINGS) {

            if (resultCode == RESULT_OK) {

                val destination = pendingDestination

                if (destination != null) {

                    pendingDestination = null

                    voiceManager.speak(
                        "Location is enabled. " +
                                "Starting navigation to $destination."
                    ) {

                        // IMPORTANT:
                        // Do NOT start NavigationVoiceService.
                        // Sahāy's microphone stays OFF.

                        navigationManager.openGoogleMaps(
                            destination
                        )
                    }
                }

            } else {

                pendingDestination = null

                voiceManager.speak(
                    "Location was not enabled."
                )
            }

            return
        }

        if (
            resultCode != RESULT_OK ||
            data == null
        ) {
            return
        }

        val results =
            data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

        if (results.isNullOrEmpty()) {
            return
        }

        if (requestCode == speechRequestCode) {

            val destination =
                results[0].trim()

            destinationInput.setText(destination)

            voiceManager.speak(
                "You said $destination. " +
                        "Say start to begin navigation."
            ) {

                startVoiceCommandRecognition()
            }

            return
        }

        if (requestCode == voiceCommandRequestCode) {

            val commands =
                results.map {
                    it.lowercase(
                        Locale.getDefault()
                    ).trim()
                }

            handleVoiceCommand(commands)
        }
    }

    // ---------------------------------------------------------
    // DESTINATION MICROPHONE
    // ---------------------------------------------------------

    private fun startDestinationRecognition() {

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
            RecognizerIntent.EXTRA_PROMPT,
            "Say your destination"
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            5
        )

        try {

            startActivityForResult(
                intent,
                speechRequestCode
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Speech recognition is not available",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------------------------------------------------
    // START COMMAND
    // ---------------------------------------------------------

    private fun startVoiceCommandRecognition() {

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
            RecognizerIntent.EXTRA_PROMPT,
            "Say start"
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            5
        )

        try {

            startActivityForResult(
                intent,
                voiceCommandRequestCode
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Voice recognition is not available",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------------------------------------------------
    // MICROPHONE PERMISSION
    // ---------------------------------------------------------

    private fun checkMicrophonePermissionForDestination() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                microphonePermissionCode
            )

        } else {

            startDestinationRecognition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == microphonePermissionCode) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                voiceManager.speak(
                    "Microphone enabled. " +
                            "Please say your destination."
                ) {

                    startDestinationRecognition()
                }

            } else {

                voiceManager.speak(
                    "Microphone permission is required."
                )
            }
        }
    }

    // ---------------------------------------------------------
    // VOICE COMMAND
    // ---------------------------------------------------------

    private fun handleVoiceCommand(
        commands: List<String>
    ) {

        if (
            commands.any {
                it == "start" ||
                        it == "star" ||
                        it == "starts" ||
                        it.contains("start navigation") ||
                        it.contains("start navigating")
            }
        ) {

            startNavigation()
            return
        }

        /*
         * These commands are available only when
         * Sahāy is actually listening.
         *
         * During Google Maps navigation the
         * microphone is OFF.
         */

        if (
            commands.any {
                it == "stop" ||
                        it.contains("stop navigation") ||
                        it.contains("stop navigating")
            }
        ) {

            stopNavigation()
            return
        }

        if (
            commands.any {
                it == "continue" ||
                        it == "resume" ||
                        it.contains("continue navigation") ||
                        it.contains("continue navigating") ||
                        it.contains("resume navigation") ||
                        it.contains("resume navigating")
            }
        ) {

            continueNavigation()
            return
        }

        if (
            commands.any {
                it.contains("return to app") ||
                        it.contains("return to sahay") ||
                        it.contains("return to sahāy") ||
                        it.contains("open sahay") ||
                        it.contains("open sahāy")
            }
        ) {

            returnToSahay()
            return
        }

        voiceManager.speak(
            "I did not understand."
        )
    }

    // ---------------------------------------------------------
    // START NAVIGATION
    // ---------------------------------------------------------

    private fun startNavigation() {

        val destination =
            destinationInput.text
                .toString()
                .trim()

        if (destination.isEmpty()) {

            voiceManager.speak(
                "Please say your destination first."
            ) {

                checkMicrophonePermissionForDestination()
            }

            return
        }

        pendingDestination = destination

        voiceManager.speak(
            "Checking location settings."
        )

        navigationManager.checkLocationSettings(

            onLocationReady = {

                pendingDestination = null

                voiceManager.speak(
                    "Starting navigation to $destination."
                ) {

                    /*
                     * IMPORTANT:
                     *
                     * Do NOT start NavigationVoiceService.
                     *
                     * Sahāy microphone = OFF
                     * Google Maps navigation voice = ON
                     */

                    navigationManager.openGoogleMaps(
                        destination
                    )
                }
            },

            onLocationPromptShown = {

                voiceManager.speak(
                    "Location is required for outdoor navigation. " +
                            "Please enable Location."
                )
            },

            onLocationError = {

                pendingDestination = null

                voiceManager.speak(
                    "Unable to enable Location."
                )
            }
        )
    }

    // ---------------------------------------------------------
    // STOP NAVIGATION
    // ---------------------------------------------------------

    private fun stopNavigation() {

        pendingDestination = null

        // Ensure Sahāy microphone service is stopped.
        stopService(
            Intent(
                this,
                NavigationVoiceService::class.java
            )
        )

        SahayAccessibilityService
            .stopMapsNavigation()
    }

    // ---------------------------------------------------------
    // CONTINUE
    // ---------------------------------------------------------

    private fun continueNavigation() {

        SahayAccessibilityService
            .continueMapsNavigation()
    }

    // ---------------------------------------------------------
    // RETURN TO SAHĀY
    // ---------------------------------------------------------

    private fun returnToSahay() {

        stopService(
            Intent(
                this,
                NavigationVoiceService::class.java
            )
        )

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

        startActivity(intent)
    }

    // ---------------------------------------------------------
    // INDOOR RECORDING
    // ---------------------------------------------------------

    private fun startIndoorRecording() {

        voiceManager.speak(
            "Indoor direction recording is ready."
        )
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    override fun onDestroy() {

        stopService(
            Intent(
                this,
                NavigationVoiceService::class.java
            )
        )

        voiceManager.shutdown()

        super.onDestroy()
    }
}




