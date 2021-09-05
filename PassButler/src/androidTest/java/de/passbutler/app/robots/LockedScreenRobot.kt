package de.passbutler.app.robots

import de.passbutler.app.R
import de.passbutler.app.TestConstants

class LockedScreenRobot : BaseTestRobot() {
    fun enterMasterPassword() {
        enterText(R.id.textInputEditText_master_password, TestConstants.TEST_MASTER_PASSWORD)
    }

    fun clickUnlockButton() {
        clickButton(R.id.button_unlock_password)
    }

    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_locked_screen_root)
    }
}

fun lockedScreen(block: LockedScreenRobot.() -> Unit): LockedScreenRobot {
    return LockedScreenRobot().apply(block)
}
