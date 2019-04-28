package de.sicherheitskritisch.passbutler.base

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface JSONSerializable {
    fun serialize(): JSONObject
}

fun JSONArray.asJSONObjectSequence(): Sequence<JSONObject> {
    return (0 until length()).asSequence().mapNotNull { get(it) as? JSONObject }
}

/**
 * Put a string value to `JSONObject` (ensures the type compared to multi signature `put()` method).
 */
@Throws(JSONException::class)
fun JSONObject.putString(name: String, value: String): JSONObject {
    return put(name, value)
}

/**
 * Put a boolean value to `JSONObject` (ensures the type compared to multi signature `put()` method).
 */
@Throws(JSONException::class)
fun JSONObject.putBoolean(name: String, value: Boolean): JSONObject {
    return put(name, value)
}

/**
 * Put a integer value to `JSONObject` (ensures the type compared to multi signature `put()` method).
 */
@Throws(JSONException::class)
fun JSONObject.putInt(name: String, value: Int): JSONObject {
    return put(name, value)
}

/**
 * Put a long value to `JSONObject` (ensures the type compared to multi signature `put()` method).
 */
@Throws(JSONException::class)
fun JSONObject.putLong(name: String, value: Long): JSONObject {
    return put(name, value)
}

/**
 * Put a `JSONObject` value to `JSONObject` (ensures the type compared to multi signature `put()` method).
 */
@Throws(JSONException::class)
fun JSONObject.putJSONObject(name: String, value: JSONObject): JSONObject {
    return put(name, value)
}