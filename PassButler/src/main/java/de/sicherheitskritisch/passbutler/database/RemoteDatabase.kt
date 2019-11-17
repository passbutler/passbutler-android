package de.sicherheitskritisch.passbutler.database

import android.net.Uri
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.asJSONObjectSequence
import de.sicherheitskritisch.passbutler.base.isHttpsScheme
import de.sicherheitskritisch.passbutler.crypto.models.AuthToken
import de.sicherheitskritisch.passbutler.database.models.User
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

typealias OkHttpResponse = okhttp3.Response

interface AuthWebservice {
    @GET("/token")
    fun getTokenAsync(): Deferred<Response<AuthToken>>

    private class ConverterFactory : Converter.Factory() {
        override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
            return when (type) {
                AuthToken::class.java -> createAuthTokenResponse()
                else -> null
            }
        }

        @Throws(JSONException::class)
        private fun createAuthTokenResponse() = Converter<ResponseBody, AuthToken> {
            AuthToken.Deserializer.deserialize(it.string())
        }
    }

    companion object {
        fun create(serverUrl: Uri, username: String, password: String): AuthWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(PasswordAuthenticationInterceptor(username, password))
                .build()

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(serverUrl.toString())
                .client(okHttpClient)
                .addConverterFactory(ConverterFactory())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .build()

            return retrofitBuilder.create(AuthWebservice::class.java)
        }
    }
}

suspend fun AuthWebservice?.requestAuthToken(): Result<AuthToken> {
    val authWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val getTokenRequest = authWebservice?.getTokenAsync()
            val getTokenResponse = getTokenRequest?.await()
            getTokenResponse.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

interface UserWebservice {
    @GET("/users")
    fun getUsersAsync(): Deferred<Response<List<User>>>

    @GET("/user/{username}")
    fun getUserDetailsAsync(@Path("username") username: String): Deferred<Response<User>>

    @PUT("/user/{username}")
    fun setUserDetailsAsync(@Path("username") username: String, @Body user: User): Deferred<Response<Unit>>

    private class ConverterFactory : Converter.Factory() {
        override fun requestBodyConverter(type: Type, parameterAnnotations: Array<Annotation>, methodAnnotations: Array<Annotation>, retrofit: Retrofit): Converter<*, RequestBody>? {
            return when (type) {
                User::class.java -> createUserRequestConverter()
                else -> null
            }
        }

        private fun createUserRequestConverter() = Converter<User, RequestBody> {
            RequestBody.create(MediaType.get("application/json"), it.serialize().toString())
        }

        override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
            return when {
                (type == User::class.java) -> createUserResponseConverter()
                (type as? ParameterizedType)?.let { it.rawType == List::class.java && it.actualTypeArguments.firstOrNull() == User::class.java } == true -> createUserListResponseConverter()
                else -> null
            }
        }

        @Throws(JSONException::class)
        private fun createUserResponseConverter() = Converter<ResponseBody, User> {
            User.DefaultUserDeserializer.deserialize(it.string())
        }

        @Throws(JSONException::class)
        private fun createUserListResponseConverter() = Converter<ResponseBody, List<User>> {
            JSONArray(it.string()).asJSONObjectSequence().mapNotNull { userJsonObject ->
                User.PartialUserDeserializer.deserialize(userJsonObject)
            }.toList()
        }
    }

    companion object {
        fun create(serverUrl: Uri, authToken: String): UserWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(TokenAuthenticationInterceptor(authToken))
                .build()

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(serverUrl.toString())
                .client(okHttpClient)
                .addConverterFactory(UnitConverterFactory())
                .addConverterFactory(ConverterFactory())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .build()

            return retrofitBuilder.create(UserWebservice::class.java)
        }
    }
}

suspend fun UserWebservice?.requestPublicUserList(): Result<List<User>> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val getUsersListRequest = userWebservice?.getUsersAsync()
            val getUsersListResponse = getUsersListRequest?.await()
            getUsersListResponse.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.requestUser(username: String): Result<User> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val getUserDetailsRequest = userWebservice?.getUserDetailsAsync(username)
            val getUserDetailsResponse = getUserDetailsRequest?.await()
            getUserDetailsResponse.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.updateUser(user: User): Result<Unit> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val setUserDetailsRequest = userWebservice?.setUserDetailsAsync(user.username, user)
            val setUserDetailsResponse = setUserDetailsRequest?.await()
            setUserDetailsResponse.completeRequestWithoutResult()
        } catch (exception: Exception) {
            Failure(exception)
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
    private val unitConverter = Converter<ResponseBody, Unit> {
        it.close()
    }

    override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        return if (type == Unit::class) unitConverter else null
    }
}

private fun <T> Response<T>?.completeRequestWithResult(): Result<T> {
    val responseResult = this?.body()

    return when {
        this?.isSuccessful == true && responseResult != null -> Success(responseResult)
        this?.code() == HTTP_UNAUTHORIZED -> Failure(RequestUnauthorizedException("The request in unauthorized ${this.technicalErrorDescription}"))
        this?.code() == HTTP_FORBIDDEN -> Failure(RequestForbiddenException("The request in forbidden ${this.technicalErrorDescription}"))
        else -> Failure(RequestFailedException("The request result could not be get ${this.technicalErrorDescription}"))
    }
}

private fun <T> Response<T>?.completeRequestWithoutResult(): Result<Unit> {
    return when {
        this?.isSuccessful == true -> Success(Unit)
        this?.code() == HTTP_UNAUTHORIZED -> Failure(RequestUnauthorizedException("The request in unauthorized ${this.technicalErrorDescription}"))
        this?.code() == HTTP_FORBIDDEN -> Failure(RequestForbiddenException("The request in forbidden ${this.technicalErrorDescription}"))
        else -> Failure(RequestFailedException("The request result could not be get ${this.technicalErrorDescription}"))
    }
}

private val Response<*>?.technicalErrorDescription
    get() = "(HTTP status code ${this?.code()}): ${this?.errorBody()?.string()?.minimized()}"

private fun String.minimized(): String {
    return this
        .trim()
        .replace("\n", "")
        .replace(" ", "")
}

class RequestUnauthorizedException(message: String? = null) : Exception(message)
class RequestForbiddenException(message: String? = null) : Exception(message)
class RequestFailedException(message: String? = null) : Exception(message)
