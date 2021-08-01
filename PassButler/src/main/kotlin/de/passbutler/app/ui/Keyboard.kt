package de.passbutler.app.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import org.tinylog.kotlin.Logger

object Keyboard {
    fun hideKeyboard(context: Context?, view: View?) {
        val inputMethodManager = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
        val viewWindowToken = view?.windowToken

        if (inputMethodManager != null && viewWindowToken != null) {
            val defaultFlag = InputMethodManager.RESULT_UNCHANGED_SHOWN
            inputMethodManager.hideSoftInputFromWindow(viewWindowToken, defaultFlag)

            view.clearFocus()
        } else {
            Logger.warn("The input method manager or the window token of the view is null!")
        }
    }

    fun hideKeyboard(context: Context?, fragment: Fragment) {
        hideKeyboard(context, fragment.view)
    }
}
