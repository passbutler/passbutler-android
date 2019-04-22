package de.sicherheitskritisch.passbutler.common

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
 * Put a string value to `JSONObject` (ensure the string type in compare to multi signature `put()` method)
 */
@Throws(JSONException::class)
fun JSONObject.putString(name: String, value: String): JSONObject {
    return put(name, value)
}

/**
 * Put a boolean value to `JSONObject` (ensure the boolean type in compare to multi signature `put()` method)
 */
@Throws(JSONException::class)
fun JSONObject.putBoolean(name: String, value: Boolean): JSONObject {
    return put(name, value)
}

/**
 * Put a integer value to `JSONObject` (ensure the integer type in compare to multi signature `put()` method)
 */
@Throws(JSONException::class)
fun JSONObject.putInt(name: String, value: Int): JSONObject {
    return put(name, value)
}

/**
 * Put a long value to `JSONObject` (ensure the long type in compare to multi signature `put()` method)
 */
@Throws(JSONException::class)
fun JSONObject.putLong(name: String, value: Long): JSONObject {
    return put(name, value)
}
