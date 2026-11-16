package com.example.myapplication67

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CurrentAccount(val id: String, val login: String, val email: String)

class DemoApi : ViewModel() {
    private var password: String? = null
    var currentAccount by mutableStateOf<CurrentAccount?>(null)
        private set

    suspend fun register(login: String, email: String, password: String) {
        val result = request("register", JSONObject()
            .put("login", login).put("email", email).put("password", password), authenticated = false)
        setSession(result, password)
    }

    suspend fun signIn(identifier: String, password: String) {
        val result = request("login", JSONObject()
            .put("identifier", identifier).put("password", password), authenticated = false)
        setSession(result, password)
    }

    suspend fun refreshIdentity() {
        val current = currentAccount ?: return
        val result = request("account")
        val next = current.copy(login = result.getString("login"), email = result.getString("email"))
        currentAccount = next
    }

    fun signOut() {
        clearSession()
    }

    suspend fun request(
        action: String,
        payload: JSONObject = JSONObject(),
        authenticated: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("${BuildConfig.SUPABASE_URL}/functions/v1/demo-api")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }
        try {
            val body = JSONObject(payload.toString()).put("action", action)
            if (authenticated) {
                body.put("user_id", currentAccount?.id?.toLongOrNull() ?: error("Войдите в аккаунт"))
                body.put("password", password ?: error("Войдите в аккаунт"))
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (connection.responseCode == 401 && authenticated) clearSession()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(response.optString("error", "Ошибка сервера"))
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    suspend fun uploadImage(action: String, bytes: ByteArray): JSONObject =
        request(action, JSONObject().put("image", Base64.encodeToString(bytes, Base64.NO_WRAP)))

    private fun setSession(result: JSONObject, password: String) {
        val next = CurrentAccount(
            id = result.getLong("id").toString(),
            login = result.getString("login"),
            email = result.getString("email"),
        )
        this.password = password
        currentAccount = next
    }

    private fun clearSession() {
        password = null
        currentAccount = null
    }
}
