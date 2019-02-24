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
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.lang.reflect.Type
import java.util.*

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val username: String,
    var lockTimeout: Int,
    var deleted: Boolean,
    var modified: Date,
    val created: Date
) {
    companion object {
        fun deserialize(jsonObject: JSONObject): User? {
            return try {
                User(
                    username = jsonObject.getString("username"),
                    lockTimeout = jsonObject.getInt("lockTimeout"),
                    deleted = jsonObject.getInt("deleted") == 1,
                    modified = Date(jsonObject.getLong("modified")),
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

    @Query("SELECT * FROM users WHERE username = :username")
    fun findUser(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: User)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
}

interface UserWebservice {
    @GET("/user/{username}")
    fun getUser(@Path("username") username: String): Call<User>
}

class ResponseUserConverter : Converter<ResponseBody, User?> {
    override fun convert(responseBody: ResponseBody): User? {
        return User.deserialize(JSONObject(responseBody.string()))
    }
}

class ResponseUserConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *> {
        return ResponseUserConverter()
    }
}
