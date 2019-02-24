package de.sicherheitskritisch.passbutler.models

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import de.sicherheitskritisch.passbutler.common.L
import org.json.JSONException
import org.json.JSONObject
import java.util.*

// TODO: Store "is user the logged in user" elsewhere

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val username: String,
    var lockTimeout: Int,
    var isLoggedIn: Boolean = false,
    var deleted: Boolean,
    var lastModified: Date,
    val created: Date
) {
    companion object {
        fun deserialize(jsonObject: JSONObject): User? {
            return try {
                User(
                    username = jsonObject.getString("username"),
                    lockTimeout = jsonObject.getInt("lockTimeout"),
                    deleted = jsonObject.getInt("deleted") == 1,
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
    @Query("SELECT * FROM users")
    fun findAll(): List<User>

    @Query("SELECT * FROM users WHERE isLoggedIn = 1")
    fun findLoggedInUser(): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: User)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
}