package com.example.pathfinderindoor.indoor

class StepExecutor(
    private val pdr: PdrEngine,
    private val strideLengthMeters: Float = 0.7f
) {

    var onPrompt: ((message: String) -> Unit)? = null

    var onRouteComplete: (() -> Unit)? = null

    var onStepComplete: ((stepIndex: Int) -> Unit)? = null

    /*
     * Sends the current step count to the screen.
     */
    var onStepCountChanged: ((stepCount: Int) -> Unit)? = null

    private var route: List<Step> = emptyList()

    private var currentIndex = -1

    private var stepsAtSegmentStart = 0

    private var headingAtTurnStart = 0f

    private var targetHeadingDelta = 0f

    /*
     * Used so we don't announce every single step.
     */
    private var lastAnnouncedStep = 0


    fun start(route: List<Step>) {

        this.route = route

        currentIndex = -1

        stepsAtSegmentStart = pdr.totalSteps

        lastAnnouncedStep = 0

        /*
         * Receive step detector events.
         */
        pdr.onStep = { total ->

            handleStepDetected(total)
        }

        /*
         * Receive heading changes.
         */
        pdr.onHeadingChanged = { heading ->

            handleHeadingChanged(heading)
        }

        /*
         * Start sensors.
         */
        pdr.start()

        /*
         * Start first navigation instruction.
         */
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

        val step =
            route[currentIndex]

        when (step.action) {

            Step.Action.FORWARD -> {

                stepsAtSegmentStart =
                    pdr.totalSteps

                lastAnnouncedStep = 0

                val message = when {

                    step.distanceMeters != null -> {

                        "Walk forward about " +
                                "${step.distanceMeters.toInt()} meters."
                    }

                    step.landmark != null -> {

                        "Walk forward until you reach " +
                                "${step.landmark}."
                    }

                    else -> {

                        "Walk forward."
                    }
                }

                onPrompt?.invoke(message)
            }

            Step.Action.TURN_LEFT -> {

                beginTurn(
                    -90f,
                    "Turn left."
                )
            }

            Step.Action.TURN_RIGHT -> {

                beginTurn(
                    90f,
                    "Turn right."
                )
            }

            Step.Action.TURN_AROUND -> {

                beginTurn(
                    180f,
                    "Turn around, a full 180 degrees."
                )
            }
        }
    }


    private fun beginTurn(
        delta: Float,
        message: String
    ) {

        headingAtTurnStart =
            pdr.currentHeadingDegrees

        targetHeadingDelta =
            delta

        onPrompt?.invoke(message)
    }


    private fun handleStepDetected(
        totalSteps: Int
    ) {

        val step =
            route.getOrNull(currentIndex)
                ?: return

        /*
         * Only count steps during forward movement.
         */
        if (
            step.action !=
            Step.Action.FORWARD
        ) {
            return
        }

        val stepsTaken =
            totalSteps -
                    stepsAtSegmentStart

        /*
         * Tell the screen the current count.
         */
        onStepCountChanged?.invoke(
            stepsTaken
        )

        /*
         * Announce every 5 steps.
         *
         * Example:
         * 5 steps
         * 10 steps
         * 15 steps
         */
        if (
            stepsTaken > 0 &&
            stepsTaken % 5 == 0 &&
            stepsTaken != lastAnnouncedStep
        ) {

            lastAnnouncedStep =
                stepsTaken

            onPrompt?.invoke(
                "$stepsTaken steps."
            )
        }

        /*
         * If this is a distance-based instruction,
         * calculate the approximate distance walked.
         */
        val distance =
            step.distanceMeters
                ?: return

        val distanceWalked =
            stepsTaken *
                    strideLengthMeters

        /*
         * Destination for this segment reached.
         */
        if (
            distanceWalked >= distance
        ) {

            onStepComplete?.invoke(
                currentIndex
            )

            advanceToNextStep()
        }
    }


    private fun handleHeadingChanged(
        heading: Float
    ) {

        val step =
            route.getOrNull(currentIndex)
                ?: return

        /*
         * Heading matters only during turns.
         */
        if (
            step.action ==
            Step.Action.FORWARD
        ) {
            return
        }

        val turned =
            pdr.angularDifference(
                headingAtTurnStart,
                heading
            )

        val target =
            targetHeadingDelta

        val tolerance =
            8f

        val done =
            if (target > 0) {

                turned >=
                        target -
                        tolerance

            } else {

                turned <=
                        target +
                        tolerance
            }

        if (done) {

            onPrompt?.invoke(
                "Good. Continue straight ahead."
            )

            onStepComplete?.invoke(
                currentIndex
            )

            advanceToNextStep()
        }
    }


    /*
     * Used for landmark-based instructions.
     *
     * Example:
     * "Walk forward until you reach the stairs."
     *
     * The screen can call this after the user
     * confirms that they reached the landmark.
     */
    fun confirmLandmarkReached() {

        val step =
            route.getOrNull(currentIndex)
                ?: return

        if (
            step.action ==
            Step.Action.FORWARD &&
            step.distanceMeters == null
        ) {

            onStepComplete?.invoke(
                currentIndex
            )

            advanceToNextStep()
        }
    }
}
