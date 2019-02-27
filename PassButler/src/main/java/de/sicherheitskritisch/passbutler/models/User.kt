package de.sicherheitskritisch.passbutler.models

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import de.sicherheitskritisch.passbutler.common.JSONSerializable
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.asJSONObjectSequence
import kotlinx.coroutines.Deferred
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.lang.reflect.ParameterizedType
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
): JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            put(SERIALIZATION_KEY_USERNAME, username)
            put(SERIALIZATION_KEY_LOCK_TIMEOUT, lockTimeout)
            put(SERIALIZATION_KEY_DELETED, deleted)
            put(SERIALIZATION_KEY_MODIFIED, modified.time)
            put(SERIALIZATION_KEY_CREATED, created.time)
        }
    }

    companion object {
        fun deserialize(jsonObject: JSONObject): User? {
            return try {
                User(
                    username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                    lockTimeout = jsonObject.getInt(SERIALIZATION_KEY_LOCK_TIMEOUT),
                    deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                    modified = Date(jsonObject.getLong(SERIALIZATION_KEY_MODIFIED)),
                    created = Date(jsonObject.getLong(SERIALIZATION_KEY_CREATED))
                )
            } catch (e: JSONException) {
                L.w("User", "The user could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }

        private const val SERIALIZATION_KEY_USERNAME = "username"
        private const val SERIALIZATION_KEY_LOCK_TIMEOUT = "lockTimeout"
        private const val SERIALIZATION_KEY_DELETED = "deleted"
        private const val SERIALIZATION_KEY_MODIFIED = "modified"
        private const val SERIALIZATION_KEY_CREATED = "created"
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
    @GET("/user")
    fun getUsers(): Deferred<Response<List<User>>>

    @GET("/user/{username}")
    fun getUser(@Path("username") username: String): Call<User>
}

class ResponseUserConverter : Converter<ResponseBody, User?> {
    override fun convert(responseBody: ResponseBody): User? {
        return User.deserialize(JSONObject(responseBody.string()))
    }
}

class ResponseUserListConverter : Converter<ResponseBody, List<User>> {
    override fun convert(responseBody: ResponseBody): List<User> {
        return JSONArray(responseBody.string()).asJSONObjectSequence().mapNotNull { userJSONObject ->
            User.deserialize(userJSONObject)
        }.toList()
    }
}

class ResponseUserConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        return when (type) {
            User::class.java -> ResponseUserConverter()
            is ParameterizedType -> {
                when {
                    type.rawType == List::class.java && type.actualTypeArguments.firstOrNull() == User::class.java -> ResponseUserListConverter()
                    else -> null
                }
            }
            else -> null
        }
    }
}
