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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.myapplication67.ui.theme.MyApplication67Theme
import com.example.myapplication67.ui.theme.ThemeAccent
import com.example.myapplication67.ui.theme.ThemeMode
import androidx.core.view.WindowCompat
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
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
            var themeMode by remember(preferences) {
                mutableStateOf(
                    if (preferences.contains("theme_mode")) ThemeMode.fromKey(preferences.getString("theme_mode", null))
                    else if (preferences.contains("dark_theme")) {
                        if (preferences.getBoolean("dark_theme", false)) ThemeMode.DARK else ThemeMode.LIGHT
                    } else ThemeMode.SYSTEM,
                )
            }
            var themeAccent by remember(preferences) {
                mutableStateOf(ThemeAccent.fromKey(preferences.getString("theme_accent", null)))
            }
            val darkTheme = themeMode.isDark(systemDarkTheme)
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            MyApplication67Theme(mode = themeMode, accent = themeAccent) {
                AppRoot(
                    client = supabase,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        preferences.edit().putString("theme_mode", mode.key).remove("dark_theme").apply()
                    },
                    themeAccent = themeAccent,
                    onThemeAccentChange = { accent ->
                        themeAccent = accent
                        preferences.edit().putString("theme_accent", accent.key).apply()
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    client: SupabaseClient,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
) {
    val context = LocalContext.current
    val status by client.auth.sessionStatus.collectAsState()
    var showRegistration by rememberSaveable { mutableStateOf(false) }
    var previewMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLogin by rememberSaveable { mutableStateOf<String?>(null) }
    val repository = remember(client) { AuthRepository(client) }
    val profileRepository = remember(client) { RemoteProfileRepository(client) }
    val feedRepository = remember(client) { RemoteFeedRepository(client) }

    if (status is SessionStatus.Authenticated) {
        val session = (status as SessionStatus.Authenticated).session
        val user = session.user
        NawHostScreen(
            accountEmail = user?.email,
            loginHint = null,
            profileKey = user?.id ?: tokenSubject(session.accessToken) ?: "account",
            profileRepository = profileRepository,
            feedRepository = feedRepository,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            themeAccent = themeAccent,
            onThemeAccentChange = onThemeAccentChange,
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
        val previewKey = if (previewMode == "pending_email") pendingEmail ?: "pending" else "guest"
        val previewFeedRepository = remember(feedRepository, context, previewKey) {
            PreviewFeedRepository(feedRepository, LocalFeedRepository(context, previewKey))
        }
        NawHostScreen(
            previewTitle = if (previewMode == "guest") "Гостевой режим" else "Подтвердите почту",
            accountEmail = if (previewMode == "pending_email") pendingEmail else null,
            loginHint = if (previewMode == "pending_email") pendingLogin else "guest",
            profileKey = previewKey,
            profileRepository = null,
            feedRepository = previewFeedRepository,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            themeAccent = themeAccent,
            onThemeAccentChange = onThemeAccentChange,
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
