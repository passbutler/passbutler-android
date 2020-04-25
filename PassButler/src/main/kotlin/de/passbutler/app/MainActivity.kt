package de.passbutler.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.passbutler.app.ui.FragmentPresenter
import org.tinylog.kotlin.Logger

class MainActivity : AppCompatActivity() {

    var rootFragment: RootFragment? = null

    private val onBackPressedListener = mutableListOf<OnBackPressedListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.debug("savedInstanceState = $savedInstanceState")

        val rootFragmentTag = FragmentPresenter.getFragmentTag(RootFragment::class.java)
        rootFragment = supportFragmentManager.findFragmentByTag(rootFragmentTag) as? RootFragment

        // Add `RootFragment` only if still not there (e.g. on configuration changes the fragment will be restored automatically)
        if (rootFragment == null) {
            rootFragment = RootFragment.newInstance().also {
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.frameLayout_main_activity_content_container, it, rootFragmentTag)
                fragmentTransaction.commit()
            }
        }
    }

    override fun onBackPressed() {
        // Work on local copy of list to avoid concurrent modification issues
        val onBackPressedListenerCopy = onBackPressedListener.toList()

        // Only the top fragment on the stack (the one which is shown to user) handles the backpress
        val onBackPressedListenerStack = onBackPressedListenerCopy.reversed()
        val topFragmentHandlesBackpress = onBackPressedListenerStack.firstOrNull()?.onHandleBackPress() ?: false

        if (!topFragmentHandlesBackpress) {
            super.onBackPressed()
        }
    }

    fun addOnBackPressedListener(listener: OnBackPressedListener) = onBackPressedListener.add(listener)
    fun removeOnBackPressedListener(listener: OnBackPressedListener) = onBackPressedListener.remove(listener)

    interface OnBackPressedListener {
        fun onHandleBackPress(): Boolean
    }
}

