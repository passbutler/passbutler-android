package de.sicherheitskritisch.passbutler.database

import android.content.Context
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
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.database.models.ItemKey
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@Database(entities = [User::class, ItemKey::class], version = 1, exportSchema = false)
@TypeConverters(GeneralDatabaseConverters::class, ModelConverters::class)
abstract class PassButlerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
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
            localDatabase.userDao().findUser(username)
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

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            localDatabase.clearAllTables()
        }
    }

    suspend fun insertItemKey(vararg itemKey: ItemKey) {
        withContext(Dispatchers.IO) {
            localDatabase.itemDao().insert(*itemKey)
        }
    }

    suspend fun findAllItemKeys(): List<ItemKey> {
        return withContext(Dispatchers.IO) {
            localDatabase.itemDao().findAll()
        }
    }

    suspend fun findUserItemKeys(username: String): List<ItemKey> {
        return withContext(Dispatchers.IO) {
            localDatabase.itemDao().findUserItemKeys(username)
        }
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun findAll(): List<User>

    @Query("SELECT * FROM users WHERE username = :username")
    fun findUser(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg users: User)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
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
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg itemKey: ItemKey)

    @Query("SELECT * FROM itemkeys")
    fun findAll(): List<ItemKey>

    @Query("SELECT * FROM itemkeys WHERE username = :username")
    fun findUserItemKeys(username: String): List<ItemKey>
}