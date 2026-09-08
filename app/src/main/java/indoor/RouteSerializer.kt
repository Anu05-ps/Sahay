package com.example.pathfinderindoor.indoor

import org.json.JSONArray
import org.json.JSONObject

fun Route.toJson(): String {
    val stepsArray = JSONArray()
    steps.forEach { step ->
        val obj = JSONObject()
        obj.put("action", step.action.name)
        step.distanceMeters?.let { obj.put("distanceMeters", it) }
        step.count?.let { obj.put("count", it) }
        step.landmark?.let { obj.put("landmark", it) }
        stepsArray.put(obj)
    }
    return JSONObject().apply {
        put("destinationName", destinationName)
        put("strideLengthMeters", strideLengthMeters)
        put("steps", stepsArray)
    }.toString()
}

fun routeFromJson(json: String): Route {
    val obj = JSONObject(json)
    val stepsArray = obj.getJSONArray("steps")
    val steps = (0 until stepsArray.length()).map { i ->
        val s = stepsArray.getJSONObject(i)
        Step(
            action = Step.Action.valueOf(s.getString("action")),
            distanceMeters = if (s.has("distanceMeters")) s.getDouble("distanceMeters").toFloat() else null,
            count = if (s.has("count")) s.getInt("count") else null,
            landmark = if (s.has("landmark")) s.getString("landmark") else null
        )
    }
    return Route(
        destinationName = obj.getString("destinationName"),
        steps = steps,
        strideLengthMeters = obj.getDouble("strideLengthMeters").toFloat()
    )
}

