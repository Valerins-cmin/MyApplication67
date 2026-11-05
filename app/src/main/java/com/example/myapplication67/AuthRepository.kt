package com.example.myapplication67

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject

class AuthRepository(private val client: SupabaseClient) {
    suspend fun register(login: String, email: String, password: String) {
        val normalizedLogin = login.trim().lowercase(Locale.ROOT)
        client.auth.signUpWith(Email) {
            this.email = email.trim().lowercase(Locale.ROOT)
            this.password = password
            data = buildJsonObject { put("login", normalizedLogin) }
        }
    }

    suspend fun signIn(identifier: String, password: String) {
        val normalizedIdentifier = identifier.trim().lowercase(Locale.ROOT)
        if ('@' in normalizedIdentifier) {
            client.auth.signInWith(Email) {
                email = normalizedIdentifier
                this.password = password
            }
        } else {
            val session = requestUsernameSession(normalizedIdentifier, password)
            client.auth.importSession(session)
        }
    }

    private suspend fun requestUsernameSession(login: String, password: String): UserSession =
        withContext(Dispatchers.IO) {
            val url = URL("${BuildConfig.SUPABASE_URL}/functions/v1/login-by-username")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            }

            try {
                val requestBody = JSONObject()
                    .put("login", login)
                    .put("password", password)
                    .toString()
                connection.outputStream.use { output ->
                    output.write(requestBody.toByteArray(Charsets.UTF_8))
                }

                when (connection.responseCode) {
                    200 -> {
                        val response = connection.inputStream.bufferedReader().use { reader ->
                            JSONObject(reader.readText())
                        }
                        UserSession(
                            accessToken = response.getString("access_token"),
                            refreshToken = response.getString("refresh_token"),
                            expiresIn = response.getLong("expires_in"),
                            tokenType = response.getString("token_type"),
                            user = null,
                        )
                    }
                    401 -> throw IllegalArgumentException("Неверный логин или пароль либо почта не подтверждена.")
                    else -> throw IllegalStateException("Не удалось выполнить вход. Попробуйте позже.")
                }
            } finally {
                connection.disconnect()
            }
        }
}
