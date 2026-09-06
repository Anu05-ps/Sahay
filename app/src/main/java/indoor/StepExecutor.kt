package com.example.pathfinderindoor.indoor

class StepExecutor(
    private val pdr: PdrEngine,
    private val strideLengthMeters: Float = 0.7f
) {
    var onPrompt: ((message: String) -> Unit)? = null
    var onRouteComplete: (() -> Unit)? = null
    var onStepComplete: ((stepIndex: Int) -> Unit)? = null

    private var route: List<Step> = emptyList()
    private var currentIndex = -1

    private var stepsAtSegmentStart = 0
    private var headingAtTurnStart = 0f
    private var targetHeadingDelta = 0f

    fun start(route: List<Step>) {
        this.route = route
        currentIndex = -1
        pdr.onStep = { total -> handleStepDetected(total) }
        pdr.onHeadingChanged = { heading -> handleHeadingChanged(heading) }
        pdr.start()
        advanceToNextStep()
    }

    fun stop() {
        pdr.stop()
    }

    private fun advanceToNextStep() {
        currentIndex += 1
        if (currentIndex >= route.size) {
            onRouteComplete?.invoke()
            stop()
            return
        }

        val step = route[currentIndex]
        when (step.action) {
            Step.Action.FORWARD -> {
                stepsAtSegmentStart = pdr.totalSteps
                val message = when {
                    step.distanceMeters != null ->
                        "Walk forward about ${step.distanceMeters.toInt()} meters."
                    step.landmark != null ->
                        "Walk forward until you reach ${step.landmark}."
                    else ->
                        "Walk forward."
                }
                onPrompt?.invoke(message)
            }
            Step.Action.TURN_LEFT -> beginTurn(-90f, "Turn left.")
            Step.Action.TURN_RIGHT -> beginTurn(90f, "Turn right.")
            Step.Action.TURN_AROUND -> beginTurn(180f, "Turn around, a full 180 degrees.")
        }
    }

    private fun beginTurn(delta: Float, message: String) {
        headingAtTurnStart = pdr.currentHeadingDegrees
        targetHeadingDelta = delta
        onPrompt?.invoke(message)
    }

    private fun handleStepDetected(totalSteps: Int) {
        val step = route.getOrNull(currentIndex) ?: return
        if (step.action != Step.Action.FORWARD) return
        val distance = step.distanceMeters ?: return

        val stepsTaken = totalSteps - stepsAtSegmentStart
        val distanceWalked = stepsTaken * strideLengthMeters

        if (distanceWalked >= distance) {
            onPrompt?.invoke("Stop. Segment complete.")
            onStepComplete?.invoke(currentIndex)
            advanceToNextStep()
        }
    }

    private fun handleHeadingChanged(heading: Float) {
        val step = route.getOrNull(currentIndex) ?: return
        if (step.action == Step.Action.FORWARD) return

        val turned = pdr.angularDifference(headingAtTurnStart, heading)
        val target = targetHeadingDelta
        val tolerance = 8f
        val done = if (target > 0) turned >= target - tolerance else turned <= target + tolerance

        if (done) {
            onPrompt?.invoke("Good, that's straight ahead now.")
            onStepComplete?.invoke(currentIndex)
            advanceToNextStep()
        }
    }

    fun confirmLandmarkReached() {
        val step = route.getOrNull(currentIndex) ?: return
        if (step.action == Step.Action.FORWARD && step.distanceMeters == null) {
            onStepComplete?.invoke(currentIndex)
            advanceToNextStep()
        }
    }
}