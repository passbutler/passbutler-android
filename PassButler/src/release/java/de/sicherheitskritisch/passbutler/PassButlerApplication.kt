package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication

class PassButlerApplication : AbstractPassButlerApplication() {
    override fun setupStrictMode() {
        // No strict mode for release build
    }

    override fun setupLogger() {
        // No logger for release build
    }
}
