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
import java.util.*


@Entity(tableName = "users")
class User(
    @PrimaryKey
    val username: String,
    val lastModified: Date,
    val created: Date
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username")
    fun findByUsername(username: String): User

    @Insert(onConflict = REPLACE)
    fun insert(user: User)

    @Update
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
}

@Database(entities = [User::class], version = 2, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class PassDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

class DatabaseConverters {
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }

    @TypeConverter
    fun toTimestamp(date: Date?): Long? {
        return date?.time
    }
}