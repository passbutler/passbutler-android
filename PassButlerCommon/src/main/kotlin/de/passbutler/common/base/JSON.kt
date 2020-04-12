package de.passbutler.common.base

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tinylog.kotlin.Logger
import java.util.*

interface JSONSerializable {
    fun serialize(): JSONObject
}

abstract class JSONSerializableDeserializer<T : JSONSerializable> {

    /**
     * Deserialize a `JSONSerializable` by given `JSONObject` and throw an exception if it does not worked.
     */
    @Throws(JSONException::class)
    abstract fun deserialize(jsonObject: JSONObject): T

    /**
     * Deserialize a `JSONSerializable` by given JSON string and throw an exception if it does not worked.
     */
    @Throws(JSONException::class)
    fun deserialize(jsonString: String): T {
        val jsonObject = JSONObject(jsonString)
        return deserialize(jsonObject)
    }

    /**
     * Deserialize a `JSONSerializable` by given `JSONObject` and return null if it does not worked.
     */
    fun deserializeOrNull(jsonObject: JSONObject): T? {
        return try {
            deserialize(jsonObject)
        } catch (exception: JSONException) {
            Logger.debug("The optional JSONSerializable could not be deserialized using the following JSON: $jsonObject (${exception.message})")
            null
        }
    }

    /**
     * Deserialize a `JSONSerializable` by given JSON string and return null if it does not worked.
     */
    fun deserializeOrNull(jsonString: String): T? {
        return try {
            val jsonObject = JSONObject(jsonString)
            deserialize(jsonObject)
        } catch (exception: JSONException) {
            Logger.debug("The optional JSONSerializable could not be deserialized using the following JSON: $jsonString (${exception.message})")
            null
        }
    }
}

fun JSONArray.asJSONObjectSequence(): Sequence<JSONObject> {
    return (0 until length()).asSequence().mapNotNull { get(it) as? JSONObject }
}

fun List<JSONSerializable>.serialize(): JSONArray {
    val jsonSerializableList = this
    return JSONArray().apply {
        jsonSerializableList.forEach {
            put(it.serialize())
        }
    }
}

/**
 * The following `get*OrNull()` extension methods provide a consistent way to access optional values.
 */

fun JSONObject.getBooleanOrNull(name: String): Boolean? {
    return try {
        return optBoolean(name)
    } catch (exception: JSONException) {
        Logger.debug("The optional boolean value with key '$name' could not be deserialized using the following JSON: $this (${exception.message})")
        null
    }
}

fun JSONObject.getIntOrNull(name: String): Int? {
    return try {
        return getInt(name)
    } catch (exception: JSONException) {
        Logger.debug("The optional integer value with key '$name' could not be deserialized using the following JSON: $this (${exception.message})")
        null
    }
}

fun JSONObject.getLongOrNull(name: String): Long? {
    return try {
        return getLong(name)
    } catch (exception: JSONException) {
        Logger.debug("The optional long value with key '$name' could not be deserialized using the following JSON: $this (${exception.message})")
        null
    }
}

fun JSONObject.getStringOrNull(name: String): String? {
    return try {
        return getString(name)
    } catch (exception: JSONException) {
        Logger.debug("The optional string value with key '$name' could not be deserialized using the following JSON: $this (${exception.message})")
        null
    }
}

/**
 * The following `put*()` extension methods explicitly ensures the argument type (compared to multi signature `put()` method):
 */

fun JSONObject.putString(name: String, value: String?): JSONObject {
    return put(name, value)
}

fun JSONObject.putBoolean(name: String, value: Boolean?): JSONObject {
    return put(name, value)
}

fun JSONObject.putInt(name: String, value: Int?): JSONObject {
    return put(name, value)
}

fun JSONObject.putLong(name: String, value: Long?): JSONObject {
    return put(name, value)
}

fun JSONObject.putJSONObject(name: String, value: JSONObject?): JSONObject {
    return put(name, value)
}

/**
 * Extensions to serialize/deserialize a `ByteArray`.
 */

@Throws(JSONException::class)
fun JSONObject.getByteArray(name: String): ByteArray {
    val base64EncodedValue = getString(name)
    return try {
        base64EncodedValue.toByteArrayFromBase64String()
    } catch (exception: IllegalArgumentException) {
        throw JSONException("The value could not be Base64 decoded!")
    }
}

fun JSONObject.putByteArray(name: String, value: ByteArray): JSONObject {
    val base64EncodedValue = value.toBase64String()
    return putString(name, base64EncodedValue)
}

/**
 * Extensions to serialize/deserialize a `JSONSerializable`.
 */

@Throws(JSONException::class)
fun <T : JSONSerializable> JSONObject.getJSONSerializable(name: String, deserializer: JSONSerializableDeserializer<T>): T {
    val serialized = getJSONObject(name)
    return deserializer.deserialize(serialized)
}

fun <T : JSONSerializable> JSONObject.getJSONSerializableOrNull(name: String, deserializer: JSONSerializableDeserializer<T>): T? {
    return try {
        val serialized = getJSONObject(name)
        deserializer.deserializeOrNull(serialized)
    } catch (exception: JSONException) {
        Logger.debug("The optional JSONSerializable with key '$name' could not be deserialized using the following JSON: $this (${exception.message})")
        null
    }
}

fun <T : JSONSerializable> JSONObject.putJSONSerializable(name: String, value: T?): JSONObject {
    val serialized = value?.serialize()
    return putJSONObject(name, serialized)
}

/**
 * Extensions to serialize/deserialize `Date`.
 */

@Throws(JSONException::class)
fun JSONObject.getDate(name: String): Date {
    val serialized = getLong(name)
    return Date(serialized)
}

fun JSONObject.putDate(name: String, value: Date): JSONObject {
    return putLong(name, value.time)
}