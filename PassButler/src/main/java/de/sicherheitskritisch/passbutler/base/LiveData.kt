package de.sicherheitskritisch.passbutler.base

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * A `MutableLiveData<T>` that only excepts non-null values.
 */
class NonNullMutableLiveData<T : Any>(initialValue: T) : MutableLiveData<T>() {
    init {
        value = initialValue
    }

    override fun getValue(): T {
        // Because non-null type is enforced by Kotlin the double-bang is okay
        return super.getValue()!!
    }

    // It is not redundant because it ensures non-nullability
    @Suppress("RedundantOverride")
    override fun setValue(value: T) {
        super.setValue(value)
    }

    // It is not redundant because it ensures non-nullability
    @Suppress("RedundantOverride")
    override fun postValue(value: T) {
        super.postValue(value)
    }
}

/**
 * A non-null value enforcing `LiveData<T>` that retrieves its value via lambda.
 * On a known change of values used in the lambda, `notifyChange()` must be called!
 */
class NonNullValueGetterLiveData<T : Any>(private val valueGetter: () -> T) : LiveData<T>() {
    init {
        value = valueGetter()
    }

    override fun getValue(): T {
        // Because non-null type is enforced by Kotlin the double-bang is okay
        return super.getValue()!!
    }

    fun notifyChange() {
        val newValue = valueGetter()
        postValue(newValue)
    }
}

class TransformingMutableLiveData<SourceType, DestinationType>(
    private val source: MutableLiveData<SourceType>,
    private val toDestinationConverter: (SourceType?) -> DestinationType?,
    private val toSourceConverter: (DestinationType?) -> SourceType?
) : MutableLiveData<DestinationType>() {

    private val sourceObserver = Observer<SourceType> { sourceValue ->
        // Call super method to avoid infinite notification loop
        super.postValue(toDestinationConverter(sourceValue))
    }

    init {
        value = toDestinationConverter(source.value)
    }

    override fun onActive() {
        source.observeForever(sourceObserver)
    }

    override fun onInactive() {
        source.removeObserver(sourceObserver)
    }

    override fun setValue(value: DestinationType?) {
        super.setValue(value)
        source.value = toSourceConverter(value)
    }

    override fun postValue(value: DestinationType?) {
        super.postValue(value)
        source.postValue(toSourceConverter(value))
    }
}

/**
 * Extension to observe a `LiveData<T>` with more convenient lambda instead of `Observer<T>` instance.
 */
@MainThread
fun <T> LiveData<T>.observe(owner: LifecycleOwner, observer: (T) -> Unit) {
    observe(owner, Observer<T> { newValue ->
        observer(newValue)
    })
}

/**
 * Extension to observe a `LiveData<T>`. Once it got notified about value change, it deregister itself automatically.
 */
@MainThread
fun <T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: (T) -> Unit) {
    observe(owner, object : Observer<T> {
        override fun onChanged(newValue: T) {
            observer(newValue)
            removeObserver(this)
        }
    })
}