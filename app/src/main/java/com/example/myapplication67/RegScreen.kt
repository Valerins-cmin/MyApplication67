package com.example.myapplication67

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RegScreen(
    repository: AuthRepository,
    onLogClick: () -> Unit,
    onGuestClick: () -> Unit,
    onRegistered: () -> Unit,
) {
    var login by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var repeatPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = login,
            onValueChange = { login = it; errorMessage = null },
            label = { Text("Логин") },
            supportingText = { Text("От 3 до 30 символов; буквы и знаки любые") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Почта") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Пароль") },
            supportingText = { Text("От 6 до 128 символов и хотя бы одна латинская буква") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = repeatPassword,
            onValueChange = { repeatPassword = it; errorMessage = null },
            label = { Text("Повторите пароль") },
            singleLine = true,
            visualTransformation = if (repeatPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { repeatPasswordVisible = !repeatPasswordVisible }) {
                    Icon(if (repeatPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (repeatPasswordVisible) "Скрыть повтор пароля" else "Показать повтор пароля")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val normalizedLogin = LoginRules.normalize(login)
                val normalizedEmail = email.trim().lowercase(Locale.ROOT)
                errorMessage = when {
                    !LoginRules.isValid(normalizedLogin) -> "Логин должен содержать от 3 до 30 символов."
                    !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> "Введите корректную почту."
                    password.length < 6 -> "Пароль должен содержать не менее 6 символов."
                    password.length > 128 -> "Пароль должен содержать не более 128 символов."
                    password.none { it in 'a'..'z' || it in 'A'..'Z' } ->
                        "Добавьте в пароль хотя бы одну латинскую букву."
                    password != repeatPassword -> "Пароли не совпадают."
                    else -> null
                }
                if (errorMessage == null) {
                    busy = true
                    scope.launch {
                        try {
                            repository.register(normalizedLogin, normalizedEmail, password)
                            onRegistered()
                        } catch (error: Exception) {
                            errorMessage = "Ошибка регистрации: ${error.message ?: "проверьте подключение"}"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Подождите…" else "Зарегистрироваться")
        }
        TextButton(onClick = onLogClick, enabled = !busy) {
            Text(
                "Уже есть аккаунт? Войти",
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
    if (BuildConfig.DEBUG) {
        TextButton(
            onClick = onGuestClick,
            enabled = !busy,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 4.dp),
        ) {
            Text("DEBUG · Гость", fontSize = 12.sp)
        }
    }
    }
}
