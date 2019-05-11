package de.sicherheitskritisch.passbutler.ui

import android.app.Activity
import android.content.Context
import android.support.v4.app.Fragment
import android.view.View
import android.view.inputmethod.InputMethodManager

object Keyboard {
    fun hideKeyboard(context: Context?, view: View?) {
        val imm = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager

        view?.windowToken?.let { viewWindowToken ->
            val defaultFlag = InputMethodManager.RESULT_UNCHANGED_SHOWN
            imm?.hideSoftInputFromWindow(viewWindowToken, defaultFlag)
        }
    }

    fun hideKeyboard(context: Context?, fragment: Fragment) {
        Keyboard.hideKeyboard(context, fragment.view)
    }
}
