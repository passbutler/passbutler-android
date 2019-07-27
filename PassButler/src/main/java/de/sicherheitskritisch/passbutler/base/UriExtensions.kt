package de.sicherheitskritisch.passbutler.base

import android.net.Uri

val Uri.isHttpsScheme
    get() = scheme == "https"