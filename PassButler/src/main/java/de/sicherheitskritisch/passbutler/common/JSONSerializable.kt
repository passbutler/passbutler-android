package de.sicherheitskritisch.passbutler.common

import org.json.JSONObject

interface JSONSerializable {
    fun serialize(): JSONObject
}