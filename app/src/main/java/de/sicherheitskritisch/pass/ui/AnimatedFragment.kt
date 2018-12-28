package de.sicherheitskritisch.pass.ui

interface AnimatedFragment  {
    val transitionType: TransitionType

    enum class TransitionType {
        SLIDE_VERTICAL,
        SLIDE_HORIZONTAL
    }
}