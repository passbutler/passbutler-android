package de.passbutler.app.robots

import de.passbutler.app.R
import de.passbutler.app.TestItem

class ItemDetailRobot : BaseTestRobot() {
    override fun matchScreenShown() {
        matchDisplayed(R.id.constraintLayout_itemdetail_root)
    }

    fun enterItemDetails(testItem: TestItem) {
        enterTitle(testItem.title)
        enterUsername(testItem.username)
        enterPassword(testItem.password)
        enterUrl(testItem.url)
    }

    private fun enterTitle(title: String) {
        enterText(R.id.textInputEditText_title, title)
    }

    private fun enterUsername(username: String) {
        enterText(R.id.textInputEditText_username, username)
    }

    private fun enterPassword(password: String) {
        enterText(R.id.textInputEditText_password, password)
    }

    private fun enterUrl(url: String) {
        enterText(R.id.textInputEditText_url, url)
    }

    fun clickSaveButton() {
        clickButton(R.id.item_detail_menu_item_save)
    }
}

// The extension receiver is for semantic scoping
@Suppress("unused")
fun OverviewScreenRobot.itemDetailScreen(block: ItemDetailRobot.() -> Unit): ItemDetailRobot {
    return ItemDetailRobot().apply(block)
}
