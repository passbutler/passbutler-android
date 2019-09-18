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

/**
 * A `MutableLiveData<T>` that transforms between the value of a given source `MutableLiveData<SourceType>` and its own `TargetType` value.
 * Only initially the value of the `source` is transformed to `TargetType` and set. If a new value is set, it will be transformed and set to `source`.
 */
class NonNullTransformingMutableLiveData<SourceType, TargetType : Any>(
    private val source: MutableLiveData<SourceType>,
    private val transformToTarget: (SourceType?) -> TargetType,
    private val transformToSource: (TargetType) -> SourceType?
) : MutableLiveData<TargetType>() {

    init {
        val transformedTargetValue = transformToTarget(source.value)

        // Call super method to avoid infinite update cycle
        super.setValue(transformedTargetValue)
    }

    override fun getValue(): TargetType {
        // Because non-null type is enforced by Kotlin the double-bang is okay
        return super.getValue()!!
    }

    override fun setValue(value: TargetType) {
        transformToSourceValue(value) { transformedSourceValue ->
            super.setValue(value)
            source.setValue(transformedSourceValue)
        }
    }

    override fun postValue(value: TargetType) {
        // Do not use `postValue()` because it schedules a `setValue()` call on main thread, which causes `source` value change again
        throw NotImplementedError("The method postValue() is not possible!")
    }

    private fun transformToSourceValue(value: TargetType, applyValueBlock: (transformedSourceValue: SourceType) -> Unit) {
        val transformedSourceValue = transformToSource(value)

        // Only apply a value which source value transformation resulted a value (do not set null to `source` because of an invalid target value)
        if (transformedSourceValue != null) {
            applyValueBlock(transformedSourceValue)
        } else {
            L.w("NonNullTransformingMutableLiveData", "transformToSourceValue(): The given value '$value' could not be transformed to a source value, thus it was skipped!")
        }
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