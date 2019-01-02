package de.sicherheitskritisch.passbutler.common

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Insert
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import java.util.*

@Entity(tableName = "users")
class User(
    @PrimaryKey
    val username: String,
    val encryptedKGK: String,
    val encryptedSettings: String,
    val salt: String,
    val lastModified: Date,
    val created: Date
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username")
    fun findByUsername(username: String): LiveData<User>

    @Insert
    fun insert(user: User)

    @Update
    fun update(vararg users: User)

    @Query("DELETE FROM users WHERE username = :username")
    fun delete(username: String)
}

// TODO: Add `owner_user_id`?
@Entity(tableName = "items")
class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val encryptedData: String,
    val salt: String,
    val lastModified: Date,
    val created: Date
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE id = :id")
    fun findById(id: Int): LiveData<Item>

    @Insert
    fun insert(user: Item)

    @Update
    fun update(vararg users: Item)

    @Query("DELETE FROM items WHERE id = :id")
    fun delete(id: Int)
}

@Entity(tableName = "item_keys")
class ItemKeys(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    @ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)
    val itemId: Int,
    @ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    val userId: Int,
    val encryptedItemKey: String,
    val salt: String,
    val lastModified: Date,
    val created: Date
)
