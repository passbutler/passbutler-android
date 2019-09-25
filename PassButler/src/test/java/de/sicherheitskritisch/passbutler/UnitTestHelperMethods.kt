package de.sicherheitskritisch.passbutler

import org.json.JSONObject
import org.junit.jupiter.api.Assertions

internal fun String.hexToBytes(): ByteArray {
    require(this.length % 2 == 0) { "The given string must have an even length!" }

    return ByteArray(this.length / 2) {
        this.substring(it * 2, (it * 2) + 2).toInt(16).toByte()
    }
}

internal fun assertJSONObjectEquals(expected: JSONObject, actual: JSONObject) {
    Assertions.assertEquals(expected.toString(), actual.toString())
}
