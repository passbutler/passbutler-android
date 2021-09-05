package de.passbutler.app.robots

import de.passbutler.app.R
import de.passbutler.app.TestConstants

class LoginRobot : BaseTestRobot() {
    fun enterServerUrl() {
        enterText(R.id.textInputEditText_serverurl, TestConstants.TEST_SERVERURL)
    }

    fun enterUsername() {
        enterText(R.id.textInputEditText_username, TestConstants.TEST_USERNAME)
    }

    fun enterMasterPassword() {
        enterText(R.id.textInputEditText_master_password, TestConstants.TEST_MASTER_PASSWORD)
    }

    fun clickLoginButton() {
        clickButton(R.id.button_login)
    }

    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_login_root)
    }
}

fun IntroductionRobot.loginScreen(block: LoginRobot.() -> Unit): LoginRobot {
    return LoginRobot().apply(block)
}
