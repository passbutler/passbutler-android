package de.passbutler.app.ui

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import de.passbutler.app.R

fun BaseFragment.showEditTextDialog(title: String?, positiveClickListener: (EditText) -> Unit, negativeClickListener: () -> Unit): AlertDialog? {
    return context?.let { fragmentContext ->
        val builder = AlertDialog.Builder(fragmentContext)
        builder.setTitle(title)

        val editText = EditText(fragmentContext).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.dialogSpacingHorizontal)
                marginEnd = resources.getDimensionPixelSize(R.dimen.dialogSpacingHorizontal)
            }
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        val editTextContainer = FrameLayout(fragmentContext).also {
            it.addView(editText)
        }

        builder.setView(editTextContainer)

        builder.setPositiveButton(getString(R.string.general_okay)) { _, _ ->
            positiveClickListener(editText)
        }

        builder.setNegativeButton(getString(R.string.general_cancel)) { _, _ ->
            negativeClickListener()
        }

        builder.setOnDismissListener {
            negativeClickListener()
        }

        builder.create().also {
            // Enforce the keyboard to show up if any view requests focus
            it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            it.show()

            editText.requestFocus()
        }
    }
}
