package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication

class PassButlerApplication : AbstractPassButlerApplication() {
    override fun createLoggerConfiguration(): Map<String, String> {
        return emptyMap()
    }
}
