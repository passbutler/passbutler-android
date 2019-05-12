package de.sicherheitskritisch.passbutler.base

import de.sicherheitskritisch.passbutler.BuildConfig

object Build {
    val isDebugBuild
        get() = BuildConfig.BUILD_TYPE == "debug"
}