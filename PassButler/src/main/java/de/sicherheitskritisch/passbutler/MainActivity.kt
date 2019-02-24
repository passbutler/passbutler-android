package de.sicherheitskritisch.passbutler

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.Synchronization
import de.sicherheitskritisch.passbutler.models.ResponseUserConverterFactory
import de.sicherheitskritisch.passbutler.models.User
import de.sicherheitskritisch.passbutler.models.UserWebservice
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit


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



            val retrofit = Retrofit.Builder()
                .baseUrl("http://10.0.0.20:5000")
                .addConverterFactory(ResponseUserConverterFactory())
                .build()

            val userWebservice = retrofit.create(UserWebservice::class.java)


            userWebservice.getUser("a@sicherheitskritisch.de").enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    val r = response.body()
                    L.d("MainActivity", "onResponse(): r = $r")
                }

                override fun onFailure(call: Call<User>, t: Throwable) {
                    L.d("MainActivity", "onFailure(): r = $t")
                }
            })

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

