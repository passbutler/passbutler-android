package de.sicherheitskritisch.passbutler.common

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.asJSONObjectSequence(): Sequence<JSONObject> {
    return (0 until length()).asSequence().mapNotNull { get(it) as? JSONObject }
}