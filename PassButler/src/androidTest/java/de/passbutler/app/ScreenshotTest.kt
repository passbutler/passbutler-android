package de.passbutler.app

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.passbutler.app.robots.LockedScreenRobot
import de.passbutler.app.robots.introductionScreen
import de.passbutler.app.robots.itemDetailScreen
import de.passbutler.app.robots.localUserCreationScreen
import de.passbutler.app.robots.lockedScreen
import de.passbutler.app.robots.loginScreen
import de.passbutler.app.robots.overviewScreen
import de.passbutler.app.rules.SCREENSHOT_DIRECTORY
import de.passbutler.app.rules.ScreenshotRule
import de.passbutler.common.UserManager
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.time.Duration

/**
 * The tests need to run all together because some of the tests depend on previous test (e.g. for creating local user).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenshotTest {

    @get:Rule
    val ruleChain: RuleChain = RuleChain.emptyRuleChain()
        .around(ActivityScenarioRule(MainActivity::class.java))
        .around(ScreenshotRule())

    @Test
    fun screenshot_01_IntroductionScreen() {
        introductionScreen {
            matchScreenShown()
        }
    }

    @Test
    fun screenshot_02_LoginScreen() {
        introductionScreen {
            clickLoginButton()

            loginScreen {
                enterServerUrl()
                enterUsername()
                enterMasterPassword()

                // Wait to ensure the label of `TextInputLayout` is transitioned
                waitFor(Duration.ofMillis(250))

                // Do not click login button because we just want to capture screenshot of filled screen

                matchScreenShown()
            }
        }
    }

    @Test
    fun screenshot_03_OverviewScreenEmpty() {
        introductionScreen {
            clickCreateLocalUserButton()

            localUserCreationScreen {
                enterUsername()
                clickNextButton()

                enterMasterPassword()
                enterMasterPasswordConfirm()
                clickDoneButton()

                matchScreenShown()
            }

            overviewScreen {
                matchScreenShown()
            }
        }
    }

    @Test
    fun screenshot_04_LockedScreen() {
        lockedScreen {
            matchScreenShown()
        }
    }

    @Test
    fun screenshot_05_ItemDetail() {
        lockedScreen {
            unlockWithPassword()
        }

        overviewScreen {
            clickAddItemButton()

            itemDetailScreen {
                enterTitle()
                enterUsername()
                enterPassword()
                enterUrl()
                clickSaveButton()

                matchScreenShown()
            }
        }
    }

    @Test
    fun screenshot_06_OverviewScreenWithItem() {
        lockedScreen {
            unlockWithPassword()
        }

        overviewScreen {
            matchScreenShown()
        }
    }

    @Test
    fun screenshot_07_OverviewScreenWithOpenDrawer() {
        lockedScreen {
            unlockWithPassword()
        }

        overviewScreen {
            matchScreenShown()

            openDrawer()
            matchOpenDrawerDisplayed()
        }
    }

    private fun LockedScreenRobot.unlockWithPassword() {
        enterMasterPassword()
        clickUnlockButton()

        // Wait to ensure the locked screen is popped
        waitFor(Duration.ofMillis(2500))
    }

    companion object : ScreenshotTestSetup {
        @JvmStatic
        @BeforeClass
        fun setup() {
            clearDatabase()
            clearScreenshots()

            enterDemoUserInterfaceMode()
            disableAnimations()
        }

        @JvmStatic
        @AfterClass
        fun finish() {
            exitDemoUserInterfaceMode()
            enableAnimations()
        }
    }
}

private interface ScreenshotTestSetup {
    fun clearDatabase() = runBlocking {
        PassButlerApplication.userManager.logoutUser(UserManager.LogoutBehaviour.ClearDatabase)
    }

    fun clearScreenshots() {
        runShellCommand("run-as de.passbutler.app.debug rm -rf /data/data/de.passbutler.app.debug/$SCREENSHOT_DIRECTORY/")
    }

    fun enableAnimations() {
        runShellCommand("settings put global window_animation_scale 1")
        runShellCommand("settings put global transition_animation_scale 1")
        runShellCommand("settings put global animator_duration_scale 1")
    }

    fun disableAnimations() {
        runShellCommand("settings put global window_animation_scale 0")
        runShellCommand("settings put global transition_animation_scale 0")
        runShellCommand("settings put global animator_duration_scale 0")
    }

    fun enterDemoUserInterfaceMode() {
        runShellCommand("settings put global sysui_demo_allowed 1")
        runShellCommand("am broadcast -a com.android.systemui.demo -e command enter")
        runShellCommand("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
        runShellCommand("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000")
        runShellCommand("am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4")
        runShellCommand("am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype lte -e level 4")
    }

    fun exitDemoUserInterfaceMode() {
        runShellCommand("am broadcast -a com.android.systemui.demo -e command exit")
    }

    /**
     * Run command on device shell (equivalent on host computer is `adb shell "command"`)
     */
    private fun runShellCommand(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }
}
