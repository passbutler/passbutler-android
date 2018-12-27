package de.sicherheitskritisch.pass.ui

import android.support.v4.app.Fragment

open class AnimatedFragment : Fragment() {
    open val transitionType: TransitionType = TransitionType.SLIDE_HORIZONTAL

    enum class TransitionType {
        SLIDE_VERTICAL,
        SLIDE_HORIZONTAL
    }
}