package de.passbutler.common.database

import de.passbutler.common.database.Differentiation.collectModifiedItems
import de.passbutler.common.database.Differentiation.collectNewItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class DifferentiationTest {

    /**
     * `collectNewItems()` tests
     */

    @Test
    fun `If no newItems list nor currentItems list are given, an empty list is returned`() {
        val currentItems = listOf<TestItem>()
        val newItems = listOf<TestItem>()

        val expectedItems = emptyList<TestItem>()
        assertEquals(expectedItems, collectNewItems(currentItems, newItems))
    }

    @Test
    fun `If the newItems list contains no item, an empty list is returned`() {
        val currentItems = listOf(
            createItem("item a"),
            createItem("item b")
        )

        val newItems = emptyList<TestItem>()

        val expectedItems = emptyList<TestItem>()
        assertEquals(expectedItems, collectNewItems(currentItems, newItems))
    }

    @Test
    fun `If the newItems list contains only the currentItems list, an empty list is returned`() {
        val currentItems = listOf(
            createItem("item a"),
            createItem("item b")
        )

        val newItems = currentItems.toList()

        val expectedItems = emptyList<TestItem>()
        assertEquals(expectedItems, collectNewItems(currentItems, newItems))
    }

    @Test
    fun `If the newItems list contains only 1 new item, a list containing only the new item is returned`() {
        val currentItems = listOf(
            createItem("item a"),
            createItem("item b")
        )

        val newItems = listOf(
            createItem("item c")
        )

        val expectedItems = newItems.toList()
        assertEquals(expectedItems, collectNewItems(currentItems, newItems))
    }

    @Test
    fun `If the newItems list contains the currentItems list and a new item, a list containing only the new item is returned`() {
        val currentItems = listOf(
            createItem("item a"),
            createItem("item b")
        )

        val newItems = listOf(
            createItem("item a"),
            createItem("item b"),
            createItem("item c")
        )

        val expectedItems = listOf(
            createItem("item c")
        )

        assertEquals(expectedItems, collectNewItems(currentItems, newItems))
    }

    /**
     * `collectModifiedItems()` tests
     */

    @Test
    fun `If the list size is different, an exception is thrown`() {
        val currentItems = listOf(
            createItem("item a"),
            createItem("item b")
        )

        val updatedItems = listOf(
            createItem("item a")
        )

        assertThrows(IllegalArgumentException::class.java) {
            collectModifiedItems(currentItems, updatedItems)
        }
    }

    @Test
    fun `If the list contains different items, an exception is thrown`() {
        val currentItems = listOf(
            createItem("item a"),
            createItem("item b")
        )

        val updatedItems = listOf(
            createItem("item a"),
            createItem("item c")
        )

        assertThrows(IllegalStateException::class.java) {
            collectModifiedItems(currentItems, updatedItems)
        }
    }

    @Test
    fun `If the updated list is the same as the currentItems list list, an empty list is returned`() {
        val currentItems = listOf(
            createItem("item a", modified = "2019-03-12T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T10:15:00Z")
        )

        val updatedItems = currentItems.toList()

        val expectedItems = emptyList<TestItem>()
        assertEquals(expectedItems, collectModifiedItems(currentItems, updatedItems))
    }

    @Test
    fun `If no item in the updated list has newer modified date, an empty list is returned`() {
        val currentItems = listOf(
            createItem("item a", modified = "2019-03-13T10:15:00Z"),
            createItem("item b", modified = "2019-03-13T10:15:00Z")
        )

        val updatedItems = listOf(
            createItem("item a", modified = "2019-03-12T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T10:15:00Z")
        )

        val expectedItems = emptyList<TestItem>()
        assertEquals(expectedItems, collectModifiedItems(currentItems, updatedItems))
    }

    @Test
    fun `If one item was changed, it is returned`() {
        val currentItems = listOf(
            createItem("item a", modified = "2019-03-12T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T10:15:00Z")
        )

        // Only item B was modified
        val updatedItems = listOf(
            createItem("item a", modified = "2019-03-12T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T15:15:00Z")
        )

        val expectedItems = listOf(
            createItem("item b", modified = "2019-03-12T15:15:00Z")
        )

        assertEquals(expectedItems, collectModifiedItems(currentItems, updatedItems))
    }

    @Test
    fun `If multiple items were changed, they are returned`() {
        val currentItems = listOf(
            createItem("item a", modified = "2019-03-12T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T10:15:00Z")
        )

        // Both item A and B were modified
        val updatedItems = listOf(
            createItem("item a", modified = "2019-03-13T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T15:15:00Z")
        )

        val expectedItems = listOf(
            createItem("item a", modified = "2019-03-13T10:15:00Z"),
            createItem("item b", modified = "2019-03-12T15:15:00Z")
        )

        assertEquals(expectedItems, collectModifiedItems(currentItems, updatedItems))
    }

    /**
     * `collectChanges()` tests
     */

    @Test
    fun `Collect changes for both sides`() {
        val localItems = listOf(
            createItem("item a", modified = "2020-01-04T18:00:00Z"), // synced
            createItem("item b", modified = "2020-01-04T18:00:00Z"), // synced - modified on remote side
            createItem("item c", modified = "2020-01-04T18:02:00Z"), // synced - modified on local side
            createItem("item l", modified = "2020-01-04T18:00:00Z") // created on local side
        )

        val remoteItems = listOf(
            createItem("item a", modified = "2020-01-04T18:00:00Z"), // synced
            createItem("item b", modified = "2020-01-04T18:01:00Z"), // synced - modified on remote side
            createItem("item c", modified = "2020-01-04T18:00:00Z"), // synced - modified on local side
            createItem("item r", modified = "2020-01-04T18:00:00Z") // created on remote side
        )

        val result = Differentiation.collectChanges(localItems, remoteItems)

        assertEquals(listOf(
            createItem("item r", modified = "2020-01-04T18:00:00Z")
        ), result.newItemsForLocal)

        assertEquals(listOf(
            createItem("item b", modified = "2020-01-04T18:01:00Z")
        ), result.modifiedItemsForLocal)

        assertEquals(listOf(
            createItem("item l", modified = "2020-01-04T18:00:00Z")
        ), result.newItemsForRemote)

        assertEquals(listOf(
            createItem("item c", modified = "2020-01-04T18:02:00Z")
        ), result.modifiedItemsForRemote)

        assertEquals(listOf(
            createItem("item l", modified = "2020-01-04T18:00:00Z"),
            createItem("item c", modified = "2020-01-04T18:02:00Z")
        ), result.remoteChangedItems)
    }
}

private fun createItem(identification: String, modified: String? = null): TestItem {
    val currentDate = Date.from(Instant.parse("2019-03-12T10:00:00Z"))

    return TestItem(
        identification,
        false,
        modified?.let { Date.from(Instant.parse(it)) } ?: currentDate,
        currentDate
    )
}

data class TestItem(
    val identification: String,
    override val deleted: Boolean,
    override val modified: Date,
    override val created: Date
) : Synchronizable {
    override val primaryField = identification
}