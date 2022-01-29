package de.passbutler.app.robots

import de.passbutler.app.R
import de.passbutler.app.TestConstants

class LoginRobot : BaseTestRobot() {
    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_login_root)
    }

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
}

// The extension receiver is for semantic scoping
@Suppress("unused")
fun IntroductionRobot.loginScreen(block: LoginRobot.() -> Unit): LoginRobot {
    return LoginRobot().apply(block)
}
