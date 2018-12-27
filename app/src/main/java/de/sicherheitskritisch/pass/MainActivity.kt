package de.sicherheitskritisch.pass

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

class MainActivity : AppCompatActivity() {

    private val onBackPressedListener = mutableListOf<OnBackPressedListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate(): savedInstanceState = $savedInstanceState")

        val rootFragment = RootFragment.newInstance()
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frameLayout_main_activity_content_container, rootFragment, rootFragment.tag)
        fragmentTransaction.commit()
    }

    override fun onBackPressed() {
        val onBackPressedListenerStack = onBackPressedListener.reversed()
        val noFragmentHandledBackpress = (onBackPressedListenerStack.firstOrNull { it.onHandleBackPress() } == null)

        if (noFragmentHandledBackpress) {
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

    companion object {
        private const val TAG = "MainActivity"
    }
}

