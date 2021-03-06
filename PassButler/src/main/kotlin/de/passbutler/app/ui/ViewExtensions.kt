package de.passbutler.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Filterable
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.SearchView
import androidx.viewbinding.ViewBinding
import de.passbutler.app.PassButlerApplication
import de.passbutler.common.ui.FADE_TRANSITION_DURATION

val animationDurationScale: Float
    get() {
        val applicationContext = PassButlerApplication.applicationContext
        return Settings.Global.getFloat(applicationContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
    }

var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

val ViewBinding.context: Context
    get() = root.context

/**
 * Returns resolved theme attribute value (e.g. `ColorInt`)
 */
fun Context.resolveThemeAttributeData(@AttrRes attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)

    return typedValue.data
}

/**
 * Returns resolved theme attribute id (e.g. `ColorRes`)
 */
fun Context.resolveThemeAttributeId(@AttrRes attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)

    return typedValue.resourceId
}

fun Context.copyToClipboard(value: String) {
    // The value seems barely used, so ignore it for now
    val description = ""

    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(description, value)
    clipboard.setPrimaryClip(clipData)
}

/**
 * Apply given resolved color value to drawable.
 */
fun Drawable.applyTint(color: Int) {
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
}

/**
 * Adds a `OnQueryTextListener` to a `SearchView` which passes the search queries to a `RecyclerView.Adapter` with implemented `Filterable`.
 */
fun SearchView.setupWithFilterableAdapter(filterableAdapter: Filterable) {
    setOnQueryTextListener(
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                // Not used
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                filterableAdapter.filter?.filter(newText)
                return false
            }
        }
    )
}

/**
 * Animates view with fade-in and fade-out.
 */
fun View.showFadeInOutAnimation(shouldShow: Boolean, visibilityHideMode: VisibilityHideMode = VisibilityHideMode.GONE) {
    val startAlpha = if (shouldShow) 0.0f else 1.0f
    val endAlpha = if (shouldShow) 1.0f else 0.0f

    val viewPropertyAnimator = animate()
    val animationDuration = (FADE_TRANSITION_DURATION.toMillis() * animationDurationScale).toLong()

    viewPropertyAnimator
        .setDuration(animationDuration)
        .alpha(endAlpha)
        .setListener(animatorListenerAdapter(
            onAnimationStart = {
                // If view should be shown, set values always on start to be sure also on animation cancel, the values are re-applied
                if (shouldShow) {
                    alpha = startAlpha
                    visibility = View.VISIBLE
                }
            },
            onAnimationEnd = {
                // Hide view according to desired hide mode if necessary
                if (!shouldShow) {
                    visibility = visibilityHideMode.viewConstant
                }

                // Remove this listener to avoid it is called if view is animated from other location (the `ViewPropertyAnimator` stays the same)
                viewPropertyAnimator.setListener(null)
            }
        ))
}

enum class VisibilityHideMode {
    INVISIBLE,
    GONE;

    val viewConstant: Int
        get() = when (this) {
            INVISIBLE -> View.INVISIBLE
            GONE -> View.GONE
        }
}

inline fun animatorListenerAdapter(crossinline onAnimationEnd: () -> Unit, crossinline onAnimationStart: () -> Unit): AnimatorListenerAdapter {
    return object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) {
            onAnimationStart.invoke()
        }

        override fun onAnimationEnd(animation: Animator) {
            onAnimationEnd.invoke()
        }
    }
}

/**
 * Text views
 */

fun TextView.setTextWithClickablePart(text: String, clickableTextPart: String, clickedBlock: () -> Unit) {
    val spannableString = SpannableString(text)
    val clickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) {
            clickedBlock.invoke()
        }
    }

    val clickableTextPartIndexStart = text.indexOf(clickableTextPart)
    val clickableTextPartEnd = clickableTextPartIndexStart + clickableTextPart.length
    spannableString.setSpan(clickableSpan, clickableTextPartIndexStart, clickableTextPartEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    this.text = spannableString
    movementMethod = LinkMovementMethod.getInstance()
    highlightColor = Color.TRANSPARENT
}

/**
 * Form views
 */

fun EditText.onActionNext(block: () -> Unit) {
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_NEXT) {
            block()
            true
        } else {
            false
        }
    }
}

fun EditText.onActionDone(block: () -> Unit) {
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            block()
            true
        } else {
            false
        }
    }
}
