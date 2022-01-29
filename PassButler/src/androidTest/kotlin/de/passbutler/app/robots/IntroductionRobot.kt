package de.passbutler.app.robots

import de.passbutler.app.R

class IntroductionRobot : BaseTestRobot() {
    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_introduction_root)
    }

    fun clickCreateLocalUserButton() {
        clickButton(R.id.button_create_user)
    }

    fun clickLoginButton() {
        clickButton(R.id.button_login)
    }
}

fun introductionScreen(block: IntroductionRobot.() -> Unit): IntroductionRobot {
    return IntroductionRobot().apply(block)
}
