package de.sicherheitskritisch.passbutler.common

import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.android.asCoroutineDispatcher

/**
 * Workaround for slow main dispatcher coroutine initialization on Android, see:
 * https://github.com/Kotlin/kotlinx.coroutines/issues/878
 */
fun Looper.asHandler(async: Boolean): Handler {
    return if (!async) {
        Handler(this)
    } else if (Build.VERSION.SDK_INT >= 28) {
        Handler.createAsync(this)
    } else {
        try {
            val constructor = Handler::class.java.getDeclaredConstructor(Looper::class.java, Handler.Callback::class.java, Boolean::class.javaPrimitiveType)
            constructor.newInstance(this, null, true)
        } catch (ignored: NoSuchMethodException) {
            Handler(this)
        }
    }
}

fun createMainDispatcher() = Looper.getMainLooper().asHandler(async = true).asCoroutineDispatcher("Main")