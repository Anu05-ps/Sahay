package com.example.pathfinderindoor.indoor

import org.junit.Test

class DirectionsParserTest {

    private val samples = listOf(
        "go straight then take the second left",
        "walk forward 10 meters then turn right",
        "go straight until you reach the door then turn left",
        "take a right, then go straight, it's the third door on the left",
        "turn around and go back the way you came",
        "go forward about 5 steps then turn left then walk straight"
    )

    @Test
    fun printParsedSteps() {
        samples.forEach { text ->
            println("INPUT:  $text")
            val steps = DirectionsParser.parse(text)
            if (steps.isEmpty()) {
                println("  -> NO STEPS PARSED")
            } else {
                steps.forEachIndexed { i, s -> println("  [$i] $s") }
            }
            println()
        }
    }
}