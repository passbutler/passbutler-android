package de.sicherheitskritisch.passbutler.common

import org.json.JSONObject

interface PersistenceDelegate {
    fun persist()
}

interface JSONSerializable {
    fun serialize(): JSONObject
}