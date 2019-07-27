package de.sicherheitskritisch.passbutler.base

import de.sicherheitskritisch.passbutler.BuildConfig

object BuildType {
    val isDebugBuild
        get() = BuildConfig.BUILD_TYPE == "debug"

    val isReleaseBuild
        get() = BuildConfig.BUILD_TYPE == "release"
}