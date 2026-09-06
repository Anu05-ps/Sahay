package com.example.pathfinderindoor.indoor

object DirectionsParser {

    private val ordinalWords = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4,
        "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8
    )

    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    fun parse(rawText: String): List<Step> {
        val text = rawText.lowercase().trim()
        val clauses = text
            .split(Regex("""\bthen\b|\band then\b|,|\.|\band\b"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val steps = clauses.mapNotNull { parseClause(it) }

        // Collapse consecutive identical steps (e.g. "turn around and go
        // back the way you came" splits into two clauses that both mean
        // the same turn-around instruction).
        return steps.filterIndexed { i, step -> i == 0 || step != steps[i - 1] }
    }

    private fun parseClause(clause: String): Step? {
        if (Regex("""turn around|go back|u-?turn""").containsMatchIn(clause)) {
            return Step(action = Step.Action.TURN_AROUND)
        }

        val turnMatch = Regex("""(\w+)?\s*(left|right)""").find(clause)
        if (turnMatch != null && Regex("""turn|take|go""").containsMatchIn(clause)) {
            val ordinalWord = turnMatch.groupValues[1]
            val direction = turnMatch.groupValues[2]
            val count = ordinalWords[ordinalWord]
            return Step(
                action = if (direction == "left") Step.Action.TURN_LEFT else Step.Action.TURN_RIGHT,
                count = count
            )
        }

        val numberAlternation = numberWords.keys.joinToString("|")
        val distancePattern = """(\d+|$numberAlternation)\s*(meter|metre|step|foot|feet)"""
        val distanceMatch = Regex(distancePattern).find(clause)
        if (distanceMatch != null && Regex("""straight|forward|ahead|walk|go""").containsMatchIn(clause)) {
            val rawNum = distanceMatch.groupValues[1]
            val num = rawNum.toIntOrNull() ?: numberWords[rawNum] ?: 0
            val unit = distanceMatch.groupValues[2]
            val meters = when {
                unit.startsWith("step") -> num * 0.7f
                unit.startsWith("foot") || unit.startsWith("feet") -> num * 0.3048f
                else -> num.toFloat()
            }
            return Step(action = Step.Action.FORWARD, distanceMeters = meters)
        }

        val landmarkMatch = Regex("""until you (?:reach|see|get to|find) (.+)""").find(clause)
        if (landmarkMatch != null) {
            return Step(action = Step.Action.FORWARD, landmark = landmarkMatch.groupValues[1].trim())
        }

        if (Regex("""straight|forward|ahead""").containsMatchIn(clause)) {
            return Step(action = Step.Action.FORWARD)
        }

        val doorMatch = Regex("""(\w+)?\s*door on the (left|right)""").find(clause)
        if (doorMatch != null) {
            val ordinalWord = doorMatch.groupValues[1]
            val count = ordinalWords[ordinalWord]
            val label = if (count != null) "the $ordinalWord door on ${doorMatch.groupValues[2]}"
            else "the door on ${doorMatch.groupValues[2]}"
            return Step(action = Step.Action.FORWARD, landmark = label, count = count)
        }

        return null
    }
}