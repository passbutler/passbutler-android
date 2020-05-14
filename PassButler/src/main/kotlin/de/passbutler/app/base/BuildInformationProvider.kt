package de.passbutler.app.base

import de.passbutler.app.BuildConfig
import de.passbutler.common.base.BuildInformationProviding
import de.passbutler.common.base.BuildType

object BuildInformationProvider : BuildInformationProviding {
    override val buildType: BuildType
        get() = when (BuildConfig.BUILD_TYPE) {
            "debug" -> BuildType.Debug
            "release" -> BuildType.Release
            else -> BuildType.Other
        }
}