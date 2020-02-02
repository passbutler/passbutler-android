package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.toHexString
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import java.text.SimpleDateFormat
import java.util.*

@Throws(IllegalArgumentException::class)
internal fun String.hexToBytes(): ByteArray {
    require(this.length % 2 == 0) { "The given string must have an even length!" }

    return ByteArray(this.length / 2) {
        this.substring(it * 2, (it * 2) + 2).toInt(16).toByte()
    }
}

internal fun String.toDate(): Date {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(this)!!
}

internal fun assertJSONObjectEquals(expected: JSONObject, actual: JSONObject) {
    Assertions.assertEquals(expected.toString(), actual.toString())
}

internal fun assertArrayNotEquals(expected: ByteArray?, actual: ByteArray?) {
    val arrayIsEqual = try {
        Assertions.assertArrayEquals(expected, actual)
        true
    } catch (e: AssertionError) {
        false
    }

    if (arrayIsEqual) {
        Assertions.fail<ByteArray>("expected: not equal but was: <${actual.toHexString()}>")
    }
}