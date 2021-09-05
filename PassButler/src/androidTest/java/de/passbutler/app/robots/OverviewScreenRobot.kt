package de.passbutler.app.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.contrib.DrawerMatchers.isOpen
import androidx.test.espresso.matcher.ViewMatchers.withId
import de.passbutler.app.R

class OverviewScreenRobot : BaseTestRobot() {
    fun openDrawer() {
        onView(withId(R.id.drawerLayout_overview_root))
            .perform(open())
    }

    fun clickAddItemButton() {
        onView(withId(R.id.floatingActionButton_add_entry))
            .perform(click())
    }

    fun matchOpenDrawerDisplayed() {
        onView(withId(R.id.drawerLayout_overview_root))
            .check(matches(isOpen()))
    }

    override fun matchScreenShown() {
        matchDisplayed(R.id.drawerLayout_overview_root)
    }
}

fun overviewScreen(block: OverviewScreenRobot.() -> Unit): OverviewScreenRobot {
    return OverviewScreenRobot().apply(block)
}
