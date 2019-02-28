package de.sicherheitskritisch.passbutler.common

import java.io.Reader

fun Reader.readTextFileContents(): String {
    return readLines().joinToString("\n")
}