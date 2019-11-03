package de.sicherheitskritisch.passbutler.base.viewmodels

interface SensibleDataViewModel<SensibleDataDecryptionResource> {
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    suspend fun decryptSensibleData(sensibleDataDecryptionResource: SensibleDataDecryptionResource)

    suspend fun clearSensibleData()
}