package de.sicherheitskritisch.passbutler.database

import de.sicherheitskritisch.passbutler.base.asJSONObjectSequence
import de.sicherheitskritisch.passbutler.database.models.User
import kotlinx.coroutines.Deferred
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
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

interface AuthWebservice {
    /*
    // TODO: Implement
    @GET("/token")
    fun getTokenAsync(): Deferred<Response<Token>>
    */
}

interface UserWebservice {
    @GET("/users")
    fun getUsersAsync(): Deferred<Response<List<User>>>

    // TODO: Remove
    @POST("/users")
    fun addUsersAsync(@Body newUsers: List<User>): Deferred<Response<Unit>>

    @PUT("/users")
    fun updateUsersAsync(@Body modifiedUsers: List<User>): Deferred<Response<Unit>>

    @GET("/user/{username}")
    fun getUser(@Path("username") username: String): Call<User>

    // TODO: Implement
    @PUT("/user/{username}")
    fun updateUserAsync(@Path("username") username: String, @Body modifiedUser: User): Deferred<Response<Unit>>
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

/**
 * Allows to have "no content" responses like `Deferred<Response<Unit>>`.
 */
class UnitConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        return if (type == Unit::class.java) {
            UnitConverter
        } else {
            null
        }
    }

    private object UnitConverter : Converter<ResponseBody, Unit> {
        override fun convert(value: ResponseBody) {
            value.close()
        }
    }
}