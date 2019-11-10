package de.sicherheitskritisch.passbutler.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.ItemAuthorization
import de.sicherheitskritisch.passbutler.database.models.ItemData
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@Database(entities = [User::class, Item::class, ItemAuthorization::class], version = 1, exportSchema = false)
@TypeConverters(GeneralDatabaseConverters::class, ModelConverters::class)
abstract class PassButlerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun itemAuthorizationDao(): ItemAuthorizationDao
}

class GeneralDatabaseConverters {
    @TypeConverter
    fun longToDate(long: Long?) = long?.let { Date(it) }

    @TypeConverter
    fun dateToLong(date: Date?) = date?.time
}

class LocalRepository(applicationContext: Context) {

    private val localDatabase by lazy {
        Room.databaseBuilder(applicationContext, PassButlerDatabase::class.java, "PassButlerDatabase").build()
    }

    suspend fun findAllUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            localDatabase.userDao().findAll()
        }
    }

    suspend fun findUser(username: String): User? {
        return withContext(Dispatchers.IO) {
            localDatabase.userDao().find(username)
        }
    }

    suspend fun insertUser(vararg user: User) {
        withContext(Dispatchers.IO) {
            localDatabase.userDao().insert(*user)
        }
    }

    suspend fun updateUser(vararg user: User) {
        withContext(Dispatchers.IO) {
            localDatabase.userDao().update(*user)
        }
    }

    fun findAllItems(): LiveData<List<Item>> {
        return localDatabase.itemDao().findAll()
    }

    suspend fun insertItem(vararg item: Item) {
        withContext(Dispatchers.IO) {
            localDatabase.itemDao().insert(*item)
        }
    }

    suspend fun updateItem(vararg item: Item) {
        withContext(Dispatchers.IO) {
            localDatabase.itemDao().update(*item)
        }
    }

    suspend fun deleteItem(vararg item: Item) {
        withContext(Dispatchers.IO) {
            localDatabase.itemDao().delete(*item)
        }
    }

    suspend fun findItemAuthorization(itemId: String): ItemAuthorization? {
        return withContext(Dispatchers.IO) {
            localDatabase.itemAuthorizationDao().find(itemId)
        }
    }

    suspend fun insertItemAuthorization(vararg itemAuthorization: ItemAuthorization) {
        withContext(Dispatchers.IO) {
            localDatabase.itemAuthorizationDao().insert(*itemAuthorization)
        }
    }

    suspend fun updateItemAuthorization(vararg itemAuthorization: ItemAuthorization) {
        withContext(Dispatchers.IO) {
            localDatabase.itemAuthorizationDao().update(*itemAuthorization)
        }
    }

    suspend fun deleteItemAuthorization(vararg itemAuthorization: ItemAuthorization) {
        withContext(Dispatchers.IO) {
            localDatabase.itemAuthorizationDao().delete(*itemAuthorization)
        }
    }

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            localDatabase.clearAllTables()
        }
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun findAll(): List<User>

    @Query("SELECT * FROM users WHERE username = :username")
    fun find(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg users: User)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY created")
    fun findAll(): LiveData<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg items: Item)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg items: Item)

    @Delete
    fun delete(vararg items: Item)
}

@Dao
interface ItemAuthorizationDao {
    @Query("SELECT * FROM item_authorizations WHERE itemId = :itemId")
    fun find(itemId: String): ItemAuthorization?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg itemAuthorizations: ItemAuthorization)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg itemAuthorizations: ItemAuthorization)

    @Delete
    fun delete(vararg itemAuthorizations: ItemAuthorization)
}

class ModelConverters {
    @TypeConverter
    fun cryptographicKeyToString(cryptographicKey: CryptographicKey?): String? {
        return cryptographicKey?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToCryptographicKey(serializedCryptographicKey: String?): CryptographicKey? {
        return serializedCryptographicKey?.let {
            CryptographicKey.Deserializer.deserializeOrNull(it)
        }
    }

    @TypeConverter
    fun keyDerivationInformationToString(keyDerivationInformation: KeyDerivationInformation?): String? {
        return keyDerivationInformation?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToKeyDerivationInformation(serializedKeyDerivationInformation: String?): KeyDerivationInformation? {
        return serializedKeyDerivationInformation?.let {
            KeyDerivationInformation.Deserializer.deserializeOrNull(it)
        }
    }

    @TypeConverter
    fun protectedValueToString(protectedValue: ProtectedValue<*>?): String? {
        return protectedValue?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToProtectedValueWithCryptographicKey(serializedProtectedValue: String?): ProtectedValue<CryptographicKey>? {
        return serializedProtectedValue?.let {
            ProtectedValue.Deserializer<CryptographicKey>().deserializeOrNull(it)
        }
    }

    @TypeConverter
    fun stringToProtectedValueWithUserSettings(serializedProtectedValue: String?): ProtectedValue<UserSettings>? {
        return serializedProtectedValue?.let {
            ProtectedValue.Deserializer<UserSettings>().deserializeOrNull(it)
        }
    }

    @TypeConverter
    fun stringToProtectedValueWithItemData(serializedProtectedValue: String?): ProtectedValue<ItemData>? {
        return serializedProtectedValue?.let {
            ProtectedValue.Deserializer<ItemData>().deserializeOrNull(it)
        }
    }
}