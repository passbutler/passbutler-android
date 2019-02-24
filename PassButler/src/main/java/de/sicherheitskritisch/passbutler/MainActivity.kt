package de.sicherheitskritisch.passbutler

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.Synchronization
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    var rootFragment: RootFragment? = null

    private val onBackPressedListener = mutableListOf<OnBackPressedListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        L.d("MainActivity", "onCreate(): savedInstanceState = $savedInstanceState")

        val rootFragmentTag = FragmentPresentingDelegate.getFragmentTag(RootFragment::class.java)
        rootFragment = supportFragmentManager.findFragmentByTag(rootFragmentTag) as? RootFragment

        // Add `RootFragment` only if still not there (e.g. on configuration changes the fragment will be restored automatically)
        if (rootFragment == null) {
            rootFragment = RootFragment.newInstance().also {
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.frameLayout_main_activity_content_container, it, rootFragmentTag)
                fragmentTransaction.commit()
            }
        }

        GlobalScope.launch {
            val localUserList = UserManager.userList()
            val remoteUserList = Synchronization.fetchRemoteUsers("https://sicherheitskritisch.de/users.json")

            val newLocalUserItemList = Synchronization.collectNewUserItems(localUserList, remoteUserList)
            val newRemoteUserItemList = Synchronization.collectNewUserItems(remoteUserList, localUserList)

            L.d("MainActivity", "onCreate(): newLocalUserItemList = $newLocalUserItemList, newRemoteUserItemList = $newRemoteUserItemList")
        }
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

    fun addOnBackPressedListener(listener: OnBackPressedListener) = onBackPressedListener.add(listener)
    fun removeOnBackPressedListener(listener: OnBackPressedListener) = onBackPressedListener.remove(listener)

    interface OnBackPressedListener {
        fun onHandleBackPress(): Boolean
    }
}

