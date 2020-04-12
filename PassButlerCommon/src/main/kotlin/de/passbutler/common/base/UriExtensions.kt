package de.passbutler.common.base

import java.net.URI

val URI.isHttpsScheme
    get() = scheme == "https"