package de.sicherheitskritisch.passbutler.ui

interface AnimatedFragment  {
    val transitionType: TransitionType

    enum class TransitionType {
        MODAL,
        SLIDE,
        FADE
    }
}