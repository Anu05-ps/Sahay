package com.example.pathfinderindoor.indoor

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
fun IndoorNavigationScreen() {
    val context = LocalContext.current

    var transcript by remember { mutableStateOf("") }
    var parsedSteps by remember { mutableStateOf<List<Step>>(emptyList()) }
    var currentPrompt by remember { mutableStateOf("Tap a button below to begin.") }
    var isWalking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val pdr = remember { PdrEngine(context) }
    val executor = remember { StepExecutor(pdr) }

    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine?.language = Locale.US
        }
        engine
    }

    fun speak(text: String) {
        currentPrompt = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    val speechCapture = remember {
        SpeechCapture(context).apply {
            onListeningStarted = { statusMessage = "Listening..." }
            onResult = { text ->
                transcript = text
                statusMessage = "Heard: $text"
                parsedSteps = DirectionsParser.parse(text)
            }
            onError = { msg -> statusMessage = "Error: $msg" }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speechCapture.startListening()
        else statusMessage = "Microphone permission is required."
    }

    fun requestSpeechCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) speechCapture.startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun runRoute(route: List<Step>) {
        parsedSteps = route
        isWalking = true
        executor.onPrompt = { msg -> speak(msg) }
        executor.onRouteComplete = {
            isWalking = false
            speak("You have arrived.")
        }
        executor.start(route)
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
            speechCapture.destroy()
            pdr.stop()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Indoor Navigation Test", style = MaterialTheme.typography.headlineSmall)

        Button(
            onClick = {
                runRoute(
                    listOf(
                        Step(action = Step.Action.FORWARD, distanceMeters = 3f),
                        Step(action = Step.Action.TURN_LEFT),
                        Step(action = Step.Action.FORWARD, distanceMeters = 2f)
                    )
                )
            },
            enabled = !isWalking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Sensors (Hardcoded: 3m, left, 2m)")
        }

        Button(
            onClick = { requestSpeechCapture() },
            enabled = !isWalking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ask for Directions")
        }

        if (transcript.isNotEmpty()) Text("Heard: \"$transcript\"")
        if (statusMessage.isNotEmpty()) Text(statusMessage, style = MaterialTheme.typography.bodySmall)

        if (parsedSteps.isNotEmpty()) {
            Text("Steps (${parsedSteps.size}):", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(parsedSteps) { step -> Text("• $step") }
            }

            if (!isWalking) {
                Button(
                    onClick = { runRoute(parsedSteps) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start Walking This Route") }
            } else {
                Button(
                    onClick = {
                        executor.stop()
                        isWalking = false
                        statusMessage = "Walk stopped."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Stop") }

                if (parsedSteps.any { it.action == Step.Action.FORWARD && it.distanceMeters == null }) {
                    Button(
                        onClick = { executor.confirmLandmarkReached() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("I've Reached It") }
                }
            }
        }

        Text(currentPrompt, style = MaterialTheme.typography.bodyLarge)
    }
}