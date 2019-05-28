package de.sicherheitskritisch.passbutler.database.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.Update
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.asJSONObjectSequence
import de.sicherheitskritisch.passbutler.base.putBoolean
import de.sicherheitskritisch.passbutler.base.putLong
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.getProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.getKeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.putKeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.putProtectedValue
import de.sicherheitskritisch.passbutler.database.Synchronizable
import kotlinx.coroutines.Deferred
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val username: String,
    val masterKeyDerivationInformation: KeyDerivationInformation?,
    var masterEncryptionKey: ProtectedValue<CryptographicKey>?,
    var settings: ProtectedValue<UserSettings>?,
    override var deleted: Boolean,
    override var modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = username

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_USERNAME, username)
            putKeyDerivationInformation(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, masterKeyDerivationInformation)
            putProtectedValue(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY, masterEncryptionKey)
            putProtectedValue(SERIALIZATION_KEY_SETTINGS, settings)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putLong(SERIALIZATION_KEY_MODIFIED, modified.time)
            putLong(SERIALIZATION_KEY_CREATED, created.time)
        }
    }

    companion object {
        fun deserialize(jsonObject: JSONObject): User? {
            return try {
                User(
                    username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                    masterKeyDerivationInformation = jsonObject.getKeyDerivationInformation(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION),
                    masterEncryptionKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY),
                    settings = jsonObject.getProtectedValue(SERIALIZATION_KEY_SETTINGS),
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
        private const val SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION = "masterKeyDerivationInformation"
        private const val SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY = "masterEncryptionKey"
        private const val SERIALIZATION_KEY_SETTINGS = "settings"
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
    fun insert(vararg users: User)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg users: User)

    @Delete
    fun delete(user: User)
}

class UserModelConverters {
    @TypeConverter
    fun keyDerivationInformationToString(keyDerivationInformation: KeyDerivationInformation?): String? {
        return keyDerivationInformation?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToKeyDerivationInformation(serializedKeyDerivationInformation: String?): KeyDerivationInformation? {
        return serializedKeyDerivationInformation?.let {
            KeyDerivationInformation.deserialize(JSONObject(it))
        }
    }

    @TypeConverter
    fun protectedValueToString(protectedValue: ProtectedValue<*>?): String? {
        return protectedValue?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToProtectedValueWithCryptographicKey(serializedProtectedValue: String?): ProtectedValue<CryptographicKey>? {
        return serializedProtectedValue?.let {
            ProtectedValue.deserialize(JSONObject(it))
        }
    }

    @TypeConverter
    fun stringToProtectedValueWithUserSettings(serializedProtectedValue: String?): ProtectedValue<UserSettings>? {
        return serializedProtectedValue?.let {
            ProtectedValue.deserialize(JSONObject(it))
        }
    }
}

interface UserWebservice {
    @GET("/users")
    fun getUsersAsync(): Deferred<Response<List<User>>>

    @POST("/users")
    fun addUsersAsync(@Body newUsers: List<User>): Deferred<Response<Unit>>

    @PUT("/users")
    fun updateUsersAsync(@Body modifiedUsers: List<User>): Deferred<Response<Unit>>

    @GET("/user/{username}")
    fun getUser(@Path("username") username: String): Call<User>
}

class UserConverterFactory : Converter.Factory() {
    override fun requestBodyConverter(type: Type, parameterAnnotations: Array<Annotation>, methodAnnotations: Array<Annotation>, retrofit: Retrofit): Converter<*, RequestBody>? {
        return when (type) {
            is ParameterizedType -> {
                when {
                    type.rawType == List::class.java && type.actualTypeArguments.firstOrNull() == User::class.java -> RequestUserListConverter()
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Converts a list of `User` objects to serialized JSON string.
     */
    private class RequestUserListConverter : Converter<List<User>, RequestBody> {
        override fun convert(userList: List<User>): RequestBody {
            return RequestBody.create(MediaType.get("application/json"), JSONArray().also { jsonArray ->
                userList.forEach {
                    jsonArray.put(it.serialize())
                }
            }.toString())
        }
    }

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

    /**
     * Converts a serialized JSON string response to a `User` object.
     */
    private class ResponseUserConverter : Converter<ResponseBody, User?> {
        override fun convert(responseBody: ResponseBody): User? {
            return User.deserialize(JSONObject(responseBody.string()))
        }
    }

    /**
     * Converts a serialized JSON string response to a list of `User` objects.
     */
    private class ResponseUserListConverter : Converter<ResponseBody, List<User>> {
        override fun convert(responseBody: ResponseBody): List<User> {
            return JSONArray(responseBody.string()).asJSONObjectSequence().mapNotNull { userJSONObject ->
                User.deserialize(userJSONObject)
            }.toList()
        }
    }
}
