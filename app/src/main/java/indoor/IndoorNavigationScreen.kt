package com.example.pathfinderindoor.indoor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

private const val PREFS_NAME = "pathfinder_indoor_routes"

private enum class VoiceMode {
    DESTINATION,
    SAVED_ROUTE_CONFIRMATION,
    DIRECTIONS,
    SAVE_CONFIRMATION,
    IDLE
}

private fun listSavedRoutes(context: Context): List<String> {
    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    return (prefs.getString("route_names", "") ?: "")
        .split("|")
        .filter { it.isNotBlank() }
}

private fun saveRoute(
    context: Context,
    route: Route
) {
    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val names = listSavedRoutes(context).toMutableList()

    if (!names.contains(route.destinationName)) {
        names.add(route.destinationName)
    }

    prefs.edit()
        .putString("route_names", names.joinToString("|"))
        .putString(
            "route_${route.destinationName}",
            route.toJson()
        )
        .apply()
}

private fun loadRoute(
    context: Context,
    destinationName: String
): Route? {

    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val json = prefs.getString(
        "route_$destinationName",
        null
    ) ?: return null

    return try {
        routeFromJson(json)
    } catch (e: Exception) {
        null
    }
}

private fun findSavedRoute(
    context: Context,
    destination: String
): Route? {

    val target = destination
        .lowercase()
        .trim()

    for (name in listSavedRoutes(context)) {

        val saved = name
            .lowercase()
            .trim()

        if (
            saved == target ||
            saved.contains(target) ||
            target.contains(saved)
        ) {
            return loadRoute(context, name)
        }
    }

    return null
}

private fun extractDestination(
    speech: String
): String {

    var text = speech
        .lowercase()
        .trim()
        .replace(Regex("\\s+"), " ")

    val patterns = listOf(
        Regex("^take me to\\s+(.+)$"),
        Regex("^guide me to\\s+(.+)$"),
        Regex("^i want to go to\\s+(.+)$"),
        Regex("^i need to go to\\s+(.+)$"),
        Regex("^i need to reach\\s+(.+)$"),
        Regex("^i want to reach\\s+(.+)$"),
        Regex("^i am going to\\s+(.+)$"),
        Regex("^i'm going to\\s+(.+)$"),
        Regex("^go to\\s+(.+)$"),
        Regex("^navigate to\\s+(.+)$"),
        Regex("^take me\\s+(.+)$"),
        Regex("^i want\\s+(.+)$")
    )

    for (pattern in patterns) {

        val match = pattern.find(text)

        if (match != null) {
            text = match.groupValues[1].trim()
            break
        }
    }

    return text
        .trim()
        .trimEnd('.', ',', '!', '?')
}

private fun isYes(text: String): Boolean {
    return Regex(
        "\\b(yes|yeah|yep|yup|sure|okay|ok|correct|right)\\b"
    ).containsMatchIn(text)
}

private fun isNo(text: String): Boolean {
    return Regex(
        "\\b(no|nope|wrong|not correct|that's wrong|that is wrong)\\b"
    ).containsMatchIn(text)
}

private fun wantsToSave(text: String): Boolean {
    return Regex(
        "\\b(save|save it|please save)\\b"
    ).containsMatchIn(text)
}

private fun doesNotWantToSave(text: String): Boolean {
    return Regex(
        "\\b(don't save|do not save|no|nope|don't|do not)\\b"
    ).containsMatchIn(text)
}

@Composable
fun IndoorNavigationScreen() {

    val context = LocalContext.current

    var destinationName by remember {
        mutableStateOf("")
    }

    var transcript by remember {
        mutableStateOf("")
    }

    var parsedSteps by remember {
        mutableStateOf<List<Step>>(emptyList())
    }

    var currentPrompt by remember {
        mutableStateOf("Starting voice navigation...")
    }

    var statusMessage by remember {
        mutableStateOf("")
    }

    var isWalking by remember {
        mutableStateOf(false)
    }

    var voiceMode by remember {
        mutableStateOf(VoiceMode.IDLE)
    }

    var savedRouteCandidate by remember {
        mutableStateOf<Route?>(null)
    }

    var ttsReady by remember {
        mutableStateOf(false)
    }

    val pdr = remember {
        PdrEngine(context)
    }

    val executor = remember {
        StepExecutor(pdr)
    }

    /*
     * Text-to-speech
     */
    val tts = remember {

        TextToSpeech(context) { status ->

            Handler(Looper.getMainLooper()).post {

                if (status == TextToSpeech.SUCCESS) {

                    ttsReady = true
                }
            }
        }
    }

    /*
     * Microphone permission
     */
    fun hasMicrophonePermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /*
     * Speech recognition
     */
    val speechCapture = remember {

        SpeechCapture(context).apply {

            onListeningStarted = {

                statusMessage = "Listening..."
            }

            onError = { message ->

                statusMessage = message
            }
        }
    }

    /*
     * Start listening.
     *
     * Handler guarantees this runs on Android's main thread.
     */
    fun listen() {

        Handler(Looper.getMainLooper()).post {

            if (!hasMicrophonePermission()) {

                statusMessage =
                    "Microphone permission is required."

                return@post
            }

            statusMessage = "Listening..."

            speechCapture.startListening()
        }
    }

    /*
     * Speak first.
     * When speech finishes, automatically start listening.
     */
    fun speak(
        text: String,
        afterSpeech: (() -> Unit)? = null
    ) {

        currentPrompt = text

        if (!ttsReady) {

            Handler(Looper.getMainLooper()).postDelayed({

                afterSpeech?.invoke()

            }, 500)

            return
        }

        val utteranceId =
            "navigation_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {
                }

                override fun onDone(
                    utteranceId: String?
                ) {

                    Handler(
                        Looper.getMainLooper()
                    ).post {

                        afterSpeech?.invoke()
                    }
                }

                override fun onError(
                    utteranceId: String?
                ) {

                    Handler(
                        Looper.getMainLooper()
                    ).post {

                        afterSpeech?.invoke()
                    }
                }
            }
        )

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    /*
     * Start navigation.
     */
    fun startRoute(route: Route) {

        parsedSteps = route.steps

        isWalking = true

        voiceMode = VoiceMode.IDLE

        executor.onPrompt = { message ->

            speak(message)
        }

        executor.onRouteComplete = {

            isWalking = false

            voiceMode =
                VoiceMode.SAVE_CONFIRMATION

            speak(
                "You have arrived at $destinationName. " +
                        "Would you like to save this route? " +
                        "Please say save or no."
            ) {

                listen()
            }
        }

        executor.start(route.steps)
    }

    /*
     * Process destination.
     */
    fun processDestination(
        speech: String
    ) {

        val destination =
            extractDestination(speech)

        if (destination.isBlank()) {

            voiceMode =
                VoiceMode.DESTINATION

            speak(
                "I could not identify your destination. " +
                        "Please tell me where you would like to go."
            ) {

                listen()
            }

            return
        }

        destinationName =
            destination

        val saved =
            findSavedRoute(
                context,
                destination
            )

        if (saved != null) {

            savedRouteCandidate =
                saved

            voiceMode =
                VoiceMode.SAVED_ROUTE_CONFIRMATION

            speak(
                "I found a saved route to " +
                        "${saved.destinationName}. " +
                        "Would you like to use this route? " +
                        "Please say yes or no."
            ) {

                listen()
            }

        } else {

            voiceMode =
                VoiceMode.DIRECTIONS

            speak(
                "I do not have a saved route to " +
                        "$destination. " +
                        "Please ask someone nearby for directions. " +
                        "When they are ready, I will listen."
            ) {

                listen()
            }
        }
    }

    /*
     * Process YES / NO / SAVE responses and spoken directions.
     */
    fun processCommand(
        speech: String
    ) {

        val text =
            speech.lowercase().trim()

        when (voiceMode) {

            VoiceMode.SAVED_ROUTE_CONFIRMATION -> {

                when {

                    isYes(text) -> {

                        val route =
                            savedRouteCandidate

                        voiceMode =
                            VoiceMode.IDLE

                        if (route != null) {

                            speak(
                                "Okay. Starting the saved route to " +
                                        route.destinationName + "."
                            ) {

                                startRoute(route)
                            }
                        }
                    }

                    isNo(text) -> {

                        voiceMode =
                            VoiceMode.DIRECTIONS

                        speak(
                            "Okay. Please ask someone nearby for directions. " +
                                    "I will listen."
                        ) {

                            listen()
                        }
                    }

                    else -> {

                        speak(
                            "I did not understand. " +
                                    "Please say yes or no."
                        ) {

                            listen()
                        }
                    }
                }
            }

            VoiceMode.SAVE_CONFIRMATION -> {

                when {

                    wantsToSave(text) -> {

                        saveRoute(
                            context,
                            Route(
                                destinationName,
                                parsedSteps
                            )
                        )

                        voiceMode =
                            VoiceMode.IDLE

                        speak(
                            "Route saved successfully."
                        )
                    }

                    doesNotWantToSave(text) -> {

                        voiceMode =
                            VoiceMode.IDLE

                        speak(
                            "Okay. The route was not saved."
                        )
                    }

                    else -> {

                        speak(
                            "Please say save if you want to save the route, " +
                                    "or say no if you do not."
                        ) {

                            listen()
                        }
                    }
                }
            }

            VoiceMode.DIRECTIONS -> {

                val steps =
                    DirectionsParser.parse(speech)

                if (steps.isEmpty()) {

                    speak(
                        "I could not understand those directions. " +
                                "Please say them again."
                    ) {

                        listen()
                    }

                } else {

                    parsedSteps =
                        steps

                    voiceMode =
                        VoiceMode.IDLE

                    speak(
                        "I understood the directions. " +
                                "Starting navigation now."
                    ) {

                        startRoute(
                            Route(
                                destinationName,
                                steps
                            )
                        )
                    }
                }
            }

            else -> {
            }
        }
    }

    /*
     * Connect speech results to current voice mode.
     */
    LaunchedEffect(voiceMode) {

        speechCapture.onResult = { text ->

            transcript = text

            statusMessage =
                "Heard: $text"

            when (voiceMode) {

                VoiceMode.DESTINATION -> {

                    processDestination(text)
                }

                VoiceMode.SAVED_ROUTE_CONFIRMATION -> {

                    processCommand(text)
                }

                VoiceMode.DIRECTIONS -> {

                    processCommand(text)
                }

                VoiceMode.SAVE_CONFIRMATION -> {

                    processCommand(text)
                }

                VoiceMode.IDLE -> {
                }
            }
        }
    }

    /*
     * Permission launcher.
     */
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                voiceMode =
                    VoiceMode.DESTINATION

                speak(
                    "Where would you like to go?"
                ) {

                    listen()
                }

            } else {

                statusMessage =
                    "Microphone permission is required."
            }
        }

    /*
     * Start the application.
     */
    LaunchedEffect(ttsReady) {

        if (ttsReady) {

            if (hasMicrophonePermission()) {

                voiceMode =
                    VoiceMode.DESTINATION

                speak(
                    "Where would you like to go?"
                ) {

                    listen()
                }

            } else {

                permissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }
    }

    /*
     * Cleanup.
     */
    DisposableEffect(Unit) {

        onDispose {

            executor.stop()

            speechCapture.destroy()

            pdr.stop()

            tts.stop()

            tts.shutdown()
        }
    }

    /*
     * Debug/testing UI.
     *
     * The navigation itself is voice-first.
     */
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Indoor Navigation",
            style =
                MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Voice-first navigation",
            style =
                MaterialTheme.typography.titleMedium
        )

        Text(
            text = currentPrompt,
            style =
                MaterialTheme.typography.bodyLarge
        )

        if (destinationName.isNotBlank()) {

            Text(
                text = "Destination: $destinationName"
            )
        }

        if (transcript.isNotBlank()) {

            Text(
                text = "Heard: \"$transcript\""
            )
        }

        if (statusMessage.isNotBlank()) {

            Text(
                text = statusMessage,
                style =
                    MaterialTheme.typography.bodySmall
            )
        }

        if (parsedSteps.isNotEmpty()) {

            Text(
                text = "Navigation steps:",
                style =
                    MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier =
                    Modifier.weight(1f)
            ) {

                items(parsedSteps) { step ->

                    Text(
                        text = "• $step"
                    )
                }
            }

        } else {

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )
        }

        /*
         * Emergency/testing stop button.
         * Normal navigation does not require it.
         */
        if (isWalking) {

            Button(
                onClick = {

                    executor.stop()

                    isWalking =
                        false

                    voiceMode =
                        VoiceMode.IDLE

                    speak(
                        "Navigation stopped."
                    )
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text("Stop Navigation")
            }
        }
    }
}
