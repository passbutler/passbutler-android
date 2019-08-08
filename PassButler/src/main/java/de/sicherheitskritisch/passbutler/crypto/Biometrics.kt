package de.sicherheitskritisch.passbutler.crypto

import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication

object Biometrics {

    val isHardwareCapable: Boolean
        get() {
            val applicationContext = AbstractPassButlerApplication.applicationContext

            return if (Build.VERSION.SDK_INT > 28) {
                val biometricManager = applicationContext.getSystemService(BiometricManager::class.java)
                biometricManager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                val fingerprintManagerCompat = FingerprintManagerCompat.from(applicationContext)
                fingerprintManagerCompat.hasEnrolledFingerprints() && fingerprintManagerCompat.isHardwareDetected
            }
        }
}