package de.passbutler.app.robots

import de.passbutler.app.R
import de.passbutler.app.TestConstants

class LocalUserCreationRobot : BaseTestRobot() {
    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_create_local_user_root)
    }

    fun enterUsername() {
        enterText(R.id.textInputEditText_username, TestConstants.TEST_USERNAME)
    }

    fun enterMasterPassword() {
        enterText(R.id.textInputEditText_master_password, TestConstants.TEST_MASTER_PASSWORD)
    }

    fun enterMasterPasswordConfirm() {
        enterText(R.id.textInputEditText_master_password_confirm, TestConstants.TEST_MASTER_PASSWORD)
    }

    fun clickNextButton() {
        clickButton(R.id.button_next)
    }

    fun clickDoneButton() {
        clickButton(R.id.button_done)
    }
}

fun IntroductionRobot.localUserCreationScreen(block: LocalUserCreationRobot.() -> Unit): LocalUserCreationRobot {
    return LocalUserCreationRobot().apply(block)
}
