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

        setContentView(
            R.layout.activity_main
        )

        voiceManager =
            VoiceManager(this)

        navigationManager =
            NavigationManager(this)

        destinationInput =
            findViewById(
                R.id.destinationInput
            )

        speakButton =
            findViewById(
                R.id.speakButton
            )

        startNavigationButton =
            findViewById(
                R.id.startNavigationButton
            )

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

    // ============================================================
    // ACTIVITY RESULT
    // ============================================================

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

        // --------------------------------------------------------
        // LOCATION SETTINGS RESULT
        // --------------------------------------------------------

        if (
            requestCode ==
            REQUEST_CHECK_SETTINGS
        ) {

            if (
                resultCode ==
                RESULT_OK
            ) {

                val destination =
                    pendingDestination

                if (
                    destination != null
                ) {

                    pendingDestination = null

                    voiceManager.speak(
                        "Location is enabled. " +
                                "Starting navigation to $destination."
                    ) {

                        startNavigationVoiceService()

                        navigationManager.openGoogleMaps(
                            destination
                        )
                    }
                }

            } else {

                pendingDestination = null

                voiceManager.speak(
                    "Location was not enabled. " +
                            "Navigation cannot start."
                )
            }

            return
        }

        // --------------------------------------------------------
        // SPEECH RESULT CHECK
        // --------------------------------------------------------

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

        if (
            results.isNullOrEmpty()
        ) {
            return
        }

        val spokenText =
            results[0].trim()

        // --------------------------------------------------------
        // DESTINATION RESULT
        // --------------------------------------------------------

        if (
            requestCode ==
            speechRequestCode
        ) {

            destinationInput.setText(
                spokenText
            )

            voiceManager.speak(
                "You said $spokenText. " +
                        "Say start to begin navigation."
            ) {

                startVoiceCommandRecognition()
            }

            return
        }

        // --------------------------------------------------------
        // VOICE COMMAND RESULT
        // --------------------------------------------------------

        if (
            requestCode ==
            voiceCommandRequestCode
        ) {

            handleVoiceCommand(
                spokenText.lowercase(
                    Locale.getDefault()
                )
            )
        }
    }

    // ============================================================
    // DESTINATION SPEECH
    // ============================================================

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

    // ============================================================
    // START COMMAND SPEECH
    // ============================================================

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
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            1500L
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            3000L
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

    // ============================================================
    // MICROPHONE PERMISSION
    // ============================================================

    private fun checkMicrophonePermissionForDestination() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
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

        if (
            requestCode ==
            microphonePermissionCode
        ) {

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

    // ============================================================
    // VOICE COMMAND HANDLER
    // ============================================================

    private fun handleVoiceCommand(
        command: String
    ) {

        when {

            // ----------------------------------------------------
            // START
            // ----------------------------------------------------

            command == "start" ||
                    command.contains(
                        "start navigation"
                    ) ||
                    command.contains(
                        "start navigating"
                    ) -> {

                startNavigation()
            }

            // ----------------------------------------------------
            // STOP
            // ----------------------------------------------------

            command == "stop" ||
                    command.contains(
                        "stop navigation"
                    ) ||
                    command.contains(
                        "stop navigating"
                    ) -> {

                stopNavigation()
            }

            // ----------------------------------------------------
            // CONTINUE
            // ----------------------------------------------------

            command == "continue" ||
                    command.contains(
                        "continue navigation"
                    ) ||
                    command.contains(
                        "continue navigating"
                    ) ||
                    command == "resume" ||
                    command.contains(
                        "resume navigation"
                    ) -> {

                continueNavigation()
            }

            // ----------------------------------------------------
            // RETURN TO APP
            // ----------------------------------------------------

            command.contains(
                "return to app"
            ) ||
                    command.contains(
                        "return to sahay"
                    ) ||
                    command.contains(
                        "return to sahāy"
                    ) ||
                    command.contains(
                        "open sahay"
                    ) ||
                    command.contains(
                        "open sahāy"
                    ) -> {

                returnToSahay()
            }

            // ----------------------------------------------------
            // INDOOR RECORDING
            // ----------------------------------------------------

            command.contains(
                "record directions"
            ) ||
                    command.contains(
                        "record direction"
                    ) -> {

                voiceManager.speak(
                    "Recording directions."
                )

                startIndoorRecording()
            }

            // ----------------------------------------------------
            // UNKNOWN COMMAND
            // ----------------------------------------------------

            else -> {

                voiceManager.speak(
                    "I did not understand. " +
                            "Please say start, stop, " +
                            "continue, or return to app."
                ) {

                    startVoiceCommandRecognition()
                }
            }
        }
    }

    // ============================================================
    // START NAVIGATION
    // ============================================================

    private fun startNavigation() {

        val destination =
            destinationInput.text
                .toString()
                .trim()

        if (
            destination.isEmpty()
        ) {

            voiceManager.speak(
                "Please say your destination first."
            ) {

                checkMicrophonePermissionForDestination()
            }

            return
        }

        pendingDestination =
            destination

        voiceManager.speak(
            "Checking location settings."
        )

        navigationManager.checkLocationSettings(

            // ----------------------------------------------------
            // LOCATION ALREADY ENABLED
            // ----------------------------------------------------

            onLocationReady = {

                pendingDestination = null

                voiceManager.speak(
                    "Starting navigation to $destination."
                ) {

                    startNavigationVoiceService()

                    navigationManager.openGoogleMaps(
                        destination
                    )
                }
            },

            // ----------------------------------------------------
            // LOCATION PROMPT
            // ----------------------------------------------------

            onLocationPromptShown = {

                voiceManager.speak(
                    "Location is required for outdoor navigation. " +
                            "Please enable Location."
                )
            },

            // ----------------------------------------------------
            // LOCATION ERROR
            // ----------------------------------------------------

            onLocationError = {

                pendingDestination = null

                voiceManager.speak(
                    "Unable to enable Location. " +
                            "Please check your phone settings."
                )
            }
        )
    }

    // ============================================================
    // START VOICE SERVICE
    // ============================================================

    private fun startNavigationVoiceService() {

        val serviceIntent =
            Intent(
                this,
                NavigationVoiceService::class.java
            )

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {

            ContextCompat.startForegroundService(
                this,
                serviceIntent
            )

        } else {

            startService(
                serviceIntent
            )
        }
    }

    // ============================================================
    // STOP NAVIGATION
    // ============================================================

    private fun stopNavigation() {

        pendingDestination = null

        /*
         * Stop Sahāy's microphone service.
         */
        val serviceIntent =
            Intent(
                this,
                NavigationVoiceService::class.java
            )

        stopService(
            serviceIntent
        )

        voiceManager.speak(
            "Navigation stopped."
        )
    }

    // ============================================================
    // CONTINUE NAVIGATION
    // ============================================================

    private fun continueNavigation() {

        /*
         * Ask the Accessibility Service
         * to find the Resume/Continue button
         * in Google Maps.
         */
        val success =
            SahayAccessibilityService
                .continueMapsNavigation()

        if (success) {

            voiceManager.speak(
                "Navigation continued."
            )

        } else {

            voiceManager.speak(
                "I could not find the continue navigation button."
            )
        }
    }

    // ============================================================
    // RETURN TO SAHĀY
    // ============================================================

    private fun returnToSahay() {

        /*
         * Stop voice recognition first.
         */
        val serviceIntent =
            Intent(
                this,
                NavigationVoiceService::class.java
            )

        stopService(
            serviceIntent
        )

        /*
         * Bring Sahāy to the foreground.
         */
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

    // ============================================================
    // INDOOR RECORDING
    // ============================================================

    private fun startIndoorRecording() {

        voiceManager.speak(
            "Indoor direction recording is ready."
        )
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        /*
         * Make absolutely sure that
         * the navigation voice service
         * is not left running.
         */
        val serviceIntent =
            Intent(
                this,
                NavigationVoiceService::class.java
            )

        stopService(
            serviceIntent
        )

        voiceManager.shutdown()

        super.onDestroy()
    }
}


