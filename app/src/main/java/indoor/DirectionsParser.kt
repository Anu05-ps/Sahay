package com.example.pathfinderindoor.indoor

object DirectionsParser {

    private val ordinalWords = mapOf(
        "first" to 1,
        "second" to 2,
        "third" to 3,
        "fourth" to 4,
        "fifth" to 5,
        "sixth" to 6,
        "seventh" to 7,
        "eighth" to 8,
        "ninth" to 9,
        "tenth" to 10
    )

    private val numberWords = mapOf(
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "thirteen" to 13,
        "fourteen" to 14,
        "fifteen" to 15,
        "twenty" to 20,
        "thirty" to 30,
        "forty" to 40,
        "fifty" to 50
    )

    fun parse(rawText: String): List<Step> {

        val text = rawText
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.isBlank()) return emptyList()

        val clauses = splitIntoClauses(text)

        val steps = clauses
            .mapNotNull { parseClause(it) }

        return collapseRepeatedSteps(steps)
    }

    private fun splitIntoClauses(text: String): List<String> {

        return text
            .replace(
                Regex(
                    "\\b(after that|and then|then|next|afterwards|following that)\\b"
                ),
                "|"
            )
            .split("|", ",", ";", ".")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseClause(clause: String): Step? {

        // ------------------------------------------------------------
        // TURN AROUND
        // ------------------------------------------------------------

        if (
            Regex(
                "\\b(turn around|turn back|u[- ]?turn|go back|reverse direction)\\b"
            ).containsMatchIn(clause)
        ) {
            return Step(
                action = Step.Action.TURN_AROUND
            )
        }

        // ------------------------------------------------------------
        // LEFT TURN
        // ------------------------------------------------------------

        val leftTurn = Regex(
            "\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)?\\s*(left)\\b"
        ).find(clause)

        if (leftTurn != null) {

            val ordinal = leftTurn.groupValues[1]
            val count = ordinalWords[ordinal]

            return Step(
                action = Step.Action.TURN_LEFT,
                count = count
            )
        }

        // ------------------------------------------------------------
        // RIGHT TURN
        // ------------------------------------------------------------

        val rightTurn = Regex(
            "\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)?\\s*(right)\\b"
        ).find(clause)

        if (rightTurn != null) {

            val ordinal = rightTurn.groupValues[1]
            val count = ordinalWords[ordinal]

            return Step(
                action = Step.Action.TURN_RIGHT,
                count = count
            )
        }

        // ------------------------------------------------------------
        // DISTANCE
        // ------------------------------------------------------------

        val numberAlternation =
            numberWords.keys.joinToString("|")

        val distanceRegex = Regex(
            "(\\d+|$numberAlternation)\\s*" +
                    "(meters?|metres?|m|steps?|feet|foot|ft)"
        )

        val distanceMatch = distanceRegex.find(clause)

        if (distanceMatch != null) {

            val rawNumber = distanceMatch.groupValues[1]

            val number =
                rawNumber.toIntOrNull()
                    ?: numberWords[rawNumber]
                    ?: 0

            val unit = distanceMatch.groupValues[2]

            val meters = when {

                unit.startsWith("step") ->
                    number * 0.7f

                unit.startsWith("foot") ||
                        unit.startsWith("feet") ||
                        unit == "ft" ->
                    number * 0.3048f

                else ->
                    number.toFloat()
            }

            if (
                Regex(
                    "\\b(walk|go|move|continue|proceed|keep|straight|forward|ahead)\\b"
                ).containsMatchIn(clause)
            ) {

                return Step(
                    action = Step.Action.FORWARD,
                    distanceMeters = meters
                )
            }
        }

        // ------------------------------------------------------------
        // LANDMARK / DESTINATION WHILE WALKING
        // ------------------------------------------------------------

        val landmarkPatterns = listOf(
            Regex("\\buntil you reach (.+)"),
            Regex("\\buntil you get to (.+)"),
            Regex("\\buntil you see (.+)"),
            Regex("\\bwhen you reach (.+)"),
            Regex("\\bwhen you see (.+)"),
            Regex("\\bat the (.+)"),
            Regex("\\bnear the (.+)")
        )

        for (pattern in landmarkPatterns) {

            val match = pattern.find(clause)

            if (match != null) {

                val landmark = match.groupValues[1]
                    .trim()
                    .trimEnd('.', ',')

                if (landmark.isNotBlank()) {

                    return Step(
                        action = Step.Action.FORWARD,
                        landmark = landmark
                    )
                }
            }
        }

        // ------------------------------------------------------------
        // DOOR
        // ------------------------------------------------------------

        val doorMatch = Regex(
            "\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)?\\s*" +
                    "door\\s+(on|to)\\s+the\\s+(left|right)\\b"
        ).find(clause)

        if (doorMatch != null) {

            val ordinal = doorMatch.groupValues[1]
            val direction = doorMatch.groupValues[3]

            val count = ordinalWords[ordinal]

            val label =
                if (count != null) {
                    "the $ordinal door on the $direction"
                } else {
                    "the door on the $direction"
                }

            return Step(
                action = Step.Action.FORWARD,
                landmark = label,
                count = count
            )
        }

        // ------------------------------------------------------------
        // STAIRS / ELEVATOR / CORRIDOR / LANDMARK
        // ------------------------------------------------------------

        val landmarkWords = listOf(
            "stairs",
            "staircase",
            "stairway",
            "elevator",
            "lift",
            "corridor",
            "hallway",
            "reception",
            "entrance",
            "exit",
            "door",
            "desk",
            "counter",
            "lobby"
        )

        if (
            landmarkWords.any { word ->
                Regex("\\b$word\\b").containsMatchIn(clause)
            }
        ) {

            return Step(
                action = Step.Action.FORWARD,
                landmark = clause
            )
        }

        // ------------------------------------------------------------
        // GENERIC FORWARD MOVEMENT
        // ------------------------------------------------------------

        if (
            Regex(
                "\\b(straight|forward|ahead|walk|go|continue|proceed|keep walking|move ahead)\\b"
            ).containsMatchIn(clause)
        ) {

            return Step(
                action = Step.Action.FORWARD
            )
        }

        return null
    }

    private fun collapseRepeatedSteps(
        steps: List<Step>
    ): List<Step> {

        if (steps.isEmpty()) return emptyList()

        val result = mutableListOf<Step>()

        for (step in steps) {

            if (result.isEmpty() || result.last() != step) {
                result.add(step)
            }
        }

        return result
    }
}
