package de.passbutler.app.robots

import de.passbutler.app.R
import de.passbutler.app.TestConstants

class ItemDetailRobot : BaseTestRobot() {
    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_itemdetail_root)
    }

    fun enterTitle() {
        enterText(R.id.textInputEditText_title, TestConstants.TEST_ITEM_TITLE)
    }

    fun enterUsername() {
        enterText(R.id.textInputEditText_username, TestConstants.TEST_ITEM_USERNAME)
    }

    fun enterPassword() {
        enterText(R.id.textInputEditText_password, TestConstants.TEST_ITEM_PASSWORD)
    }

    fun enterUrl() {
        enterText(R.id.textInputEditText_url, TestConstants.TEST_ITEM_URL)
    }

    fun clickSaveButton() {
        clickButton(R.id.item_detail_menu_item_save)
    }
}

fun itemDetailScreen(block: ItemDetailRobot.() -> Unit): ItemDetailRobot {
    return ItemDetailRobot().apply(block)
}
