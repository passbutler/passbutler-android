package de.sicherheitskritisch.passbutler.common

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Database
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Insert
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.RoomDatabase
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

    /*
    @Query(
        "SELECT * FROM items " +
                "INNER JOIN item_keys ON item_keys.itemId = item.id AND item_keys.userId = users.id" +
                "WHERE users.username LIKE :username"
    )
    fun findAccessableForUser(username: String): LiveData<Item>
    */

    @Insert
    fun insert(user: Item)

    @Update
    fun update(vararg users: Item)

    @Query("DELETE FROM items WHERE id = :id")
    fun delete(id: Int)
}

@Entity(tableName = "item_keys")
class ItemKey(
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

@Dao
interface ItemKeyDao {
    @Query("SELECT * FROM item_keys WHERE id = :id")
    fun findById(id: Int): LiveData<ItemKey>

    @Insert
    fun insert(user: ItemKey)

    @Update
    fun update(vararg itemKeys: ItemKey)

    @Query("DELETE FROM item_keys WHERE id = :id")
    fun delete(id: Int)
}

@Database(entities = [User::class, Item::class, ItemKey::class], version = 1, exportSchema = true)
abstract class PassDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun itemKeysDao(): ItemKeyDao
}

class PassRepository(application: Application) {

}