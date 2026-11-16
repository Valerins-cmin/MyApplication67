package com.example.myapplication67

import java.util.Locale

class AuthRepository(private val api: DemoApi) {
    suspend fun register(login: String, email: String, password: String) =
        api.register(LoginRules.normalize(login), email.trim().lowercase(Locale.ROOT), password)

    suspend fun signIn(identifier: String, password: String) =
        api.signIn(LoginRules.normalize(identifier), password)
}

internal object LoginRules {
    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
    fun isValid(value: String): Boolean = value.codePointCount(0, value.length) in 3..30
}
