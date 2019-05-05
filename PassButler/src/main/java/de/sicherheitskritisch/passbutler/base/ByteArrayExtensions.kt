package de.sicherheitskritisch.passbutler.base

// TODO: Test this
fun ByteArray?.contentEquals(other: ByteArray?): Boolean {
    return when {
        this == null && other == null -> true
        this != null && other != null -> this.contentEquals(other)
        else -> false
    }
}

fun ByteArray?.contentNotEquals(other: ByteArray?): Boolean {
    return !contentEquals(other)
}

fun ByteArray?.toHexString(): String {
    return this?.joinToString("") { "%02X".format(it) } ?: ""
}

/**
 * Clears out a `ByteArray` for security reasons (for crypto keys etc.).
 */
fun ByteArray.clear() {
    this.forEachIndexed { index, _ ->
        this[index] = 0
    }
}

val ByteArray.bitSize: Int
    get() = size * 8

val Int.byteSize: Int
    get() = this / 8