package de.passbutler.app.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

object Keyboard {
    fun hideKeyboard(context: Context?, view: View?) {
        val imm = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager

        view?.windowToken?.let { viewWindowToken ->
            val defaultFlag = InputMethodManager.RESULT_UNCHANGED_SHOWN
            imm?.hideSoftInputFromWindow(viewWindowToken, defaultFlag)
        }
    }

    fun hideKeyboard(context: Context?, fragment: Fragment) {
        hideKeyboard(context, fragment.view)
    }
}
