package de.passbutler.app

import de.passbutler.app.base.AbstractPassButlerApplication

class PassButlerApplication : AbstractPassButlerApplication() {
    override fun setupLogger() {
        // No logger for release build
    }

    override fun setupStrictMode() {
        // No strict mode for release build
    }
}
