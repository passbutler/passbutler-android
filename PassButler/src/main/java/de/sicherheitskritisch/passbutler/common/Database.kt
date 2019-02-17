package de.sicherheitskritisch.passbutler.common

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Database
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import android.arch.persistence.room.Update
import org.json.JSONException
import org.json.JSONObject
import java.util.*

// TODO: Store "is user the logged in user" elsewhere

@Entity(tableName = "users")
class User(
    @PrimaryKey
    val username: String,
    var lockTimeout: Int,
    var isLoggedIn: Boolean = false,
    var lastModified: Date,
    val created: Date
) {
    companion object {
        fun deserialize(jsonObject: JSONObject): User? {
            return try {
                User(
                    username = jsonObject.getString("username"),
                    lockTimeout = jsonObject.getInt("lockTimeout"),
                    lastModified = Date(jsonObject.getLong("lastModified")),
                    created = Date(jsonObject.getLong("created"))
                )
            } catch (e: JSONException) {
                L.w("User", "The user could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isLoggedIn = 1")
    fun findLoggedInUser(): User?

    @Insert(onConflict = REPLACE)
    fun insert(user: User)

    @Update(onConflict = REPLACE)
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
}

@Database(entities = [User::class], version = 1, exportSchema = false)
@TypeConverters(DatabaseConverters::class)
abstract class PassDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

class DatabaseConverters {
    @TypeConverter
    fun longToDate(long: Long?) = long?.let { Date(it) }

    @TypeConverter
    fun dateToLong(date: Date?) = date?.time

    @TypeConverter
    fun booleanToInt(boolean: Boolean?) = boolean?.let { if (it) 1 else 0 }

    @TypeConverter
    fun intToBoolean(int: Int?) = int?.let { it == 1 }
}