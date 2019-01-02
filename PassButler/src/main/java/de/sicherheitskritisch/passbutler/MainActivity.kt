package de.sicherheitskritisch.passbutler

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.sicherheitskritisch.passbutler.common.L

class MainActivity : AppCompatActivity() {

    private val onBackPressedListener = mutableListOf<OnBackPressedListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        L.d("onCreate(): savedInstanceState = $savedInstanceState")

        val rootFragment = RootFragment.newInstance()
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frameLayout_main_activity_content_container, rootFragment, rootFragment.tag)
        fragmentTransaction.commit()
    }

    override fun onBackPressed() {
        // Work on local copy of list
        val onBackPressedListenerStack = onBackPressedListener.reversed()

        // Only the top fragment on the stack (the one which is shown to user) handles the backpress
        val topFragmentHandlesBackpress = onBackPressedListenerStack.firstOrNull()?.onHandleBackPress() ?: false

        if (!topFragmentHandlesBackpress) {
            super.onBackPressed()
        }
    }

    fun addOnBackPressedListener(listener: OnBackPressedListener) {
        onBackPressedListener.add(listener)
    }

    fun removeOnBackPressedListener(listener: OnBackPressedListener) {
        onBackPressedListener.remove(listener)
    }

    interface OnBackPressedListener {
        fun onHandleBackPress(): Boolean
    }
}

