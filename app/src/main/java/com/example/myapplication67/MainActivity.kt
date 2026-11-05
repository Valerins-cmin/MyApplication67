package com.example.myapplication67

import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.myapplication67.ui.theme.MyApplication67Theme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val preferences = remember(context) {
                context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            }
            val systemDarkTheme = isSystemInDarkTheme()
            var darkTheme by remember {
                mutableStateOf(preferences.getBoolean("dark_theme", systemDarkTheme))
            }

            MyApplication67Theme(darkTheme = darkTheme) {
                AppRoot(
                    client = supabase,
                    darkTheme = darkTheme,
                    onDarkThemeChange = { enabled ->
                        darkTheme = enabled
                        preferences.edit().putBoolean("dark_theme", enabled).apply()
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    client: SupabaseClient,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val status by client.auth.sessionStatus.collectAsState()
    var showRegistration by rememberSaveable { mutableStateOf(false) }
    var previewMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLogin by rememberSaveable { mutableStateOf<String?>(null) }
    val repository = remember(client) { AuthRepository(client) }
    val profileRepository = remember(client) { RemoteProfileRepository(client) }

    if (status is SessionStatus.Authenticated) {
        val session = (status as SessionStatus.Authenticated).session
        val user = session.user
        NawHostScreen(
            accountEmail = user?.email,
            loginHint = null,
            profileKey = user?.id ?: tokenSubject(session.accessToken) ?: "account",
            profileRepository = profileRepository,
            darkTheme = darkTheme,
            onDarkThemeChange = onDarkThemeChange,
            onSignOut = {
                client.auth.signOut()
                previewMode = null
                pendingEmail = null
                pendingLogin = null
                showRegistration = false
            },
        )
        return
    }

    if ((previewMode == "guest" && BuildConfig.DEBUG) || previewMode == "pending_email") {
        NawHostScreen(
            previewTitle = if (previewMode == "guest") "Гостевой режим" else "Подтвердите почту",
            accountEmail = if (previewMode == "pending_email") pendingEmail else null,
            loginHint = if (previewMode == "pending_email") pendingLogin else "guest",
            profileKey = if (previewMode == "pending_email") pendingEmail ?: "pending" else "guest",
            profileRepository = null,
            darkTheme = darkTheme,
            onDarkThemeChange = onDarkThemeChange,
            onExitPreview = {
                previewMode = null
                pendingEmail = null
                pendingLogin = null
                showRegistration = false
            },
        )
        return
    }

    when (status) {
        is SessionStatus.NotAuthenticated -> {
            if (showRegistration) {
                RegScreen(
                    repository = repository,
                    onLogClick = { showRegistration = false },
                    onGuestClick = { previewMode = "guest" },
                    onRegistered = { login, email ->
                        pendingLogin = login
                        pendingEmail = email
                        previewMode = "pending_email"
                    },
                )
            } else {
                LogScreen(
                    repository = repository,
                    onRegClick = { showRegistration = true },
                    onGuestClick = { previewMode = "guest" },
                )
            }
        }
        else -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun tokenSubject(accessToken: String): String? = runCatching {
    val payload = accessToken.split('.')[1]
    val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
    JSONObject(String(bytes, Charsets.UTF_8)).optString("sub").ifBlank { null }
}.getOrNull()
