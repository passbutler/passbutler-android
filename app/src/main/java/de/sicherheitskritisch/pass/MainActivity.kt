package de.sicherheitskritisch.pass

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate(): savedInstanceState = $savedInstanceState")

        val rootFragment = RootFragment.newInstance()
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frameLayout_main_activity_content_container, rootFragment, rootFragment.tag)
        fragmentTransaction.commit()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

