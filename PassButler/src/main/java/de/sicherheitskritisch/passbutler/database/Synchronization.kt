package de.sicherheitskritisch.passbutler.database

import de.sicherheitskritisch.passbutler.base.Result
import java.util.*

object Differentiation {
    /**
     * Detects new items between two lists. To detect new items, the item primary key must be specified (e.g. `username` or `id`).
     */
    fun <T : Synchronizable> collectNewItems(currentItems: List<T>, newItems: List<T>): List<T> {
        return newItems.filter { newItem ->
            // The element should not be contained in current items (identified via primary field)
            !currentItems.any { it.primaryField == newItem.primaryField }
        }
    }

    /**
     * Collects a list of items of modified items (determined by modified date) based on current item and potentially updated item list.
     */
    @Throws(IllegalStateException::class)
    fun <T : Synchronizable> collectModifiedItems(currentItems: List<T>, updatedItems: List<T>): List<T> {
        require(currentItems.size == updatedItems.size) { "The current user list and updated user list size must be the same!" }

        val sortedCurrentItems = currentItems.sortedBy { it.primaryField }
        val sortedUpdatedItems = updatedItems.sortedBy { it.primaryField }

        return sortedCurrentItems.mapIndexedNotNull { index, currentUserItem ->
            val updatedItem = sortedUpdatedItems[index]

            check(currentUserItem.primaryField == updatedItem.primaryField) { "The current item list and updated item list must contain the same items!" }

            if (updatedItem.modified > currentUserItem.modified) {
                updatedItem
            } else {
                null
            }
        }
    }
}

/**
 * Interface to mark models as synchronizable. The `primaryField` is needed to differentiate between the model items when comparing,
 * and the `modified` item is used to determine which item is newer when comparing the same items.
 */
interface Synchronizable {
    val primaryField: String
    var deleted: Boolean
    var modified: Date
    val created: Date
}

/**
 * Interface for classes that implement a synchronization functionality.
 */
interface Synchronization {

    /**
     * Implements actual synchronization code. Code should be called in a `coroutineScope` block
     * to be sure a failed tasks cancel others but does not affect outer coroutine scope.
     */
    suspend fun synchronize(): Result<Unit>
}