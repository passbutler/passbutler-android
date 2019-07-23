package de.sicherheitskritisch.passbutler.database

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.base.asJSONObjectSequence
import de.sicherheitskritisch.passbutler.database.models.AuthToken
import de.sicherheitskritisch.passbutler.database.models.User
import kotlinx.coroutines.Deferred
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

typealias OkHttpResponse = okhttp3.Response

interface AuthWebservice {
    @GET("/token")
    fun getTokenAsync(): Deferred<Response<AuthToken>>

    private class ConverterFactory : Converter.Factory() {
        override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
            return when (type) {
                AuthToken::class.java -> AuthTokenResponseConverter()
                else -> null
            }
        }

        private class AuthTokenResponseConverter : Converter<ResponseBody, AuthToken?> {
            override fun convert(responseBody: ResponseBody): AuthToken? {
                return AuthToken.deserialize(JSONObject(responseBody.string()))
            }
        }
    }

    companion object {
        fun create(serverUrl: String, username: String, password: String): AuthWebservice {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(PasswordAuthenticationInterceptor(username, password))
                .build()

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(okHttpClient)
                .addConverterFactory(ConverterFactory())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .build()

            return retrofitBuilder.create(AuthWebservice::class.java)
        }
    }
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
    fun getUserDetailsAsync(@Path("username") username: String): Deferred<Response<User>>

    // TODO: Implement
    @PUT("/user/{username}")
    fun updateUserAsync(@Path("username") username: String, @Body modifiedUser: User): Deferred<Response<Unit>>

    private class ConverterFactory : Converter.Factory() {
        override fun requestBodyConverter(type: Type, parameterAnnotations: Array<Annotation>, methodAnnotations: Array<Annotation>, retrofit: Retrofit): Converter<*, RequestBody>? {
            return when (type) {
                is ParameterizedType -> {
                    when {
                        type.rawType == List::class.java && type.actualTypeArguments.firstOrNull() == User::class.java -> UserListRequestConverter()
                        else -> null
                    }
                }
                else -> null
            }
        }

        /**
         * Converts a list of `User` objects to serialized JSON string.
         */
        private class UserListRequestConverter : Converter<List<User>, RequestBody> {
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
                User::class.java -> UserResponseConverter()
                is ParameterizedType -> {
                    when {
                        type.rawType == List::class.java && type.actualTypeArguments.firstOrNull() == User::class.java -> UserListResponseConverter()
                        else -> null
                    }
                }
                else -> null
            }
        }

        /**
         * Converts a serialized JSON string response to a `User` object.
         */
        private class UserResponseConverter : Converter<ResponseBody, User?> {
            override fun convert(responseBody: ResponseBody): User? {
                return User.deserialize(JSONObject(responseBody.string()))
            }
        }

        /**
         * Converts a serialized JSON string response to a list of `User` objects.
         */
        private class UserListResponseConverter : Converter<ResponseBody, List<User>> {
            override fun convert(responseBody: ResponseBody): List<User> {
                return JSONArray(responseBody.string()).asJSONObjectSequence().mapNotNull { userJSONObject ->
                    User.deserialize(userJSONObject)
                }.toList()
            }
        }
    }

    companion object {
        fun create(serverUrl: String, authToken: String): UserWebservice {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(TokenAuthenticationInterceptor(authToken))
                .build()

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(okHttpClient)
                .addConverterFactory(UnitConverterFactory())
                .addConverterFactory(ConverterFactory())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .build()

            return retrofitBuilder.create(UserWebservice::class.java)
        }
    }
}

/**
 * Allows a Retrofit web service to authenticate with username/password based HTTP basic auth.
 */
private class PasswordAuthenticationInterceptor(username: String, password: String) : AuthenticationInterceptor() {
    override val authorizationHeaderValue: String = Credentials.basic(username, password)
}

/**
 * Allows a Retrofit web service to authenticate with token in HTTP "Authorization" header.
 */
private class TokenAuthenticationInterceptor(authToken: String) : AuthenticationInterceptor() {
    override val authorizationHeaderValue: String = "Bearer $authToken"
}

private abstract class AuthenticationInterceptor : Interceptor {
    protected abstract val authorizationHeaderValue: String

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val request = chain.request()
        val authenticatedRequest = request.newBuilder()
            .header("Authorization", authorizationHeaderValue).build()
        return chain.proceed(authenticatedRequest)
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