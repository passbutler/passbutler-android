package de.passbutler.app.robots

import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.util.TreeIterables
import org.hamcrest.Matcher
import java.time.Duration

abstract class BaseTestRobot {
    abstract fun matchScreenShown()

    fun clickButton(@IdRes resourceId: Int) {
        onView(withId(resourceId))
            .perform(click())
    }

    fun enterText(@IdRes resourceId: Int, text: String) {
        // Replace text instead typing for more speed, no keyboard closing and avoid partially unmasked password fields
        onView(withId(resourceId))
            .perform(replaceText(text))
    }

    fun waitFor(duration: Duration) {
        val waitViewAction = object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }

            override fun getDescription(): String {
                return "Wait for ${duration.toMillis()} milliseconds."
            }

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadForAtLeast(duration.toMillis())
            }
        }

        onView(isRoot())
            .perform(waitViewAction)
    }

    fun matchDisplayed(@IdRes resourceId: Int) {
        waitForView(withId(resourceId))
            .check(matches(isDisplayed()))
    }

    private fun waitForView(
        viewMatcher: Matcher<View>,
        waitDuration: Duration = Duration.ofSeconds(5),
        waitDurationPerTry: Duration = Duration.ofMillis(100)
    ): ViewInteraction {
        val maximumTries = waitDuration.toMillis() / waitDurationPerTry.toMillis()
        var tries = 0L

        for (i in 0..maximumTries)
            try {
                tries++

                onView(isRoot())
                    .perform(searchFor(viewMatcher))

                return onView(viewMatcher)
            } catch (exception: Exception) {
                if (tries == maximumTries) {
                    throw exception
                }

                Thread.sleep(waitDurationPerTry.toMillis())
            }

        throw Exception("Error finding a view matching $viewMatcher")
    }

    private fun searchFor(matcher: Matcher<View>): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }

            override fun getDescription(): String {
                return "Searching for view $matcher in the root view"
            }

            override fun perform(uiController: UiController, view: View) {
                val childViews: Iterable<View> = TreeIterables.breadthFirstViewTraversal(view)
                var tries = 0

                childViews.forEach {
                    tries++

                    if (matcher.matches(it)) {
                        return
                    }
                }

                throw NoMatchingViewException.Builder()
                    .withRootView(view)
                    .withViewMatcher(matcher)
                    .build()
            }
        }
    }
}
