package de.passbutler.common.base

interface Signal {
    fun emitted()
}

inline fun signal(crossinline emitted: (() -> Unit)) = object : Signal {
    override fun emitted() {
        emitted.invoke()
    }
}

class SignalEmitter {
    private val signalObservers = mutableListOf<Signal>()

    fun emit() {
        val signalObserversCopy = signalObservers.toList()
        signalObserversCopy.forEach { it.emitted() }
    }

    fun addSignal(signal: Signal) = signalObservers.add(signal)
    fun removeSignal(signal: Signal) = signalObservers.remove(signal)
}
