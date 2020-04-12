package de.passbutler.common.database

import de.passbutler.common.base.Result
import java.util.*

object Differentiation {
    /**
     * Collects new items (determined by identifying field) from two list states.
     */
    fun <T : Synchronizable> collectNewItems(currentItems: List<T>, newItems: List<T>): List<T> {
        return newItems.filter { newItem ->
            // The element should not be contained in current items (identified via primary field)
            !currentItems.any { it.primaryField == newItem.primaryField }
        }
    }

    /**
     * Collects modified items (determined by modified date) from two list states.
     *
     * Note: The lists must have the same size to be able to compare them!
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun <T : Synchronizable> collectModifiedItems(currentItems: List<T>, updatedItems: List<T>): List<T> {
        require(currentItems.size == updatedItems.size) { "The current list and updated list must have the same size!" }

        val sortedCurrentItems = currentItems.sortedBy { it.primaryField }
        val sortedUpdatedItems = updatedItems.sortedBy { it.primaryField }

        return sortedCurrentItems.mapIndexedNotNull { index, currentUserItem ->
            val updatedItem = sortedUpdatedItems[index]

            check(currentUserItem.primaryField == updatedItem.primaryField) { "The current list and updated list must contain the same items!" }

            if (updatedItem.modified > currentUserItem.modified) {
                updatedItem
            } else {
                null
            }
        }
    }

    /**
     * Collects differentiation result from two list states.
     */
    fun <T : Synchronizable> collectChanges(localItems: List<T>, remoteItems: List<T>): Result<T> {
        val newItemsForLocal = collectNewItems(localItems, remoteItems)
        val newItemsForRemote = collectNewItems(remoteItems, localItems)

        // Merge current items and new items to be able to collect modifications
        val mergedLocalItems = localItems + newItemsForLocal
        val mergedRemoteItems = remoteItems + newItemsForRemote

        val modifiedItemsForLocal = collectModifiedItems(mergedLocalItems, mergedRemoteItems)
        val modifiedItemsForRemote = collectModifiedItems(mergedRemoteItems, mergedLocalItems)

        return Result(
            newItemsForLocal = newItemsForLocal,
            modifiedItemsForLocal = modifiedItemsForLocal,
            newItemsForRemote = newItemsForRemote,
            modifiedItemsForRemote = modifiedItemsForRemote
        )
    }

    data class Result<T : Synchronizable>(
        val newItemsForLocal: List<T>,
        val modifiedItemsForLocal: List<T>,
        val newItemsForRemote: List<T>,
        val modifiedItemsForRemote: List<T>
    ) {
        override fun toString(): String {
            return "Differentiation.Result(newItemsForLocal=${newItemsForLocal.compactRepresentation()}, modifiedItemsForLocal=${modifiedItemsForLocal.compactRepresentation()}, newItemsForRemote=${newItemsForRemote.compactRepresentation()}, modifiedItemsForRemote=${modifiedItemsForRemote.compactRepresentation()})"
        }
    }
}

/**
 * Contains the items to upload to remote side (new and modified items)
 */
val <T : Synchronizable> Differentiation.Result<T>.remoteChangedItems
    get() = newItemsForRemote + modifiedItemsForRemote

/**
 * Interface to mark models as synchronizable. The `primaryField` is needed to differentiate between the model items when comparing,
 * and the `modified` item is used to determine which item is newer when comparing the same items.
 */
interface Synchronizable {
    val primaryField: String
    val deleted: Boolean
    val modified: Date
    val created: Date
}

fun List<Synchronizable>.compactRepresentation(): List<String> {
    return map { "'${it.primaryField}' (${it.modified.time})" }
}

/**
 * Interface for classes that implement a synchronization functionality.
 */
interface SynchronizationTask {

    /**
     * Implements actual synchronization code. Code should be called in a `coroutineScope` block
     * to be sure a failed tasks cancel others but does not affect outer coroutine scope.
     */
    suspend fun synchronize(): Result<Differentiation.Result<*>>
}