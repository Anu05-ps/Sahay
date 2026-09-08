package com.example.pathfinderindoor.indoor

data class Step(
    val action: Action,
    val distanceMeters: Float? = null,   // e.g. "walk 5 meters" -> 5f
    val count: Int? = null,              // e.g. "second left" -> 2
    val landmark: String? = null         // e.g. "until you reach the door"
) {
    enum class Action {
        FORWARD,
        TURN_LEFT,
        TURN_RIGHT,
        TURN_AROUND
    }
}

data class Route(
    val destinationName: String,
    val steps: List<Step>,
    val strideLengthMeters: Float = 0.7f
)
