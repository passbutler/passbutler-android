package de.passbutler.common

import de.passbutler.common.base.toHexString
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import java.text.SimpleDateFormat
import java.util.*

@Throws(IllegalArgumentException::class)
fun String.hexToBytes(): ByteArray {
    require(this.length % 2 == 0) { "The given string must have an even length!" }

    return ByteArray(this.length / 2) {
        this.substring(it * 2, (it * 2) + 2).toInt(16).toByte()
    }
}

fun String.toDate(): Date {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(this)!!
}

fun assertJSONObjectEquals(expected: JSONObject, actual: JSONObject) {
    Assertions.assertEquals(expected.toString(), actual.toString())
}

fun assertArrayNotEquals(expected: ByteArray?, actual: ByteArray?) {
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