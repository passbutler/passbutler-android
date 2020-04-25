package de.passbutler.app.base

import de.passbutler.app.BuildConfig

object BuildType {
    val isDebugBuild
        get() = BuildConfig.BUILD_TYPE == "debug"

    val isReleaseBuild
        get() = BuildConfig.BUILD_TYPE == "release"
}