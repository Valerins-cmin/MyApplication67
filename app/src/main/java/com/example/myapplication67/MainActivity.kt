package com.example.myapplication67

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.myapplication67.ui.theme.MyApplication67Theme
import com.example.myapplication67.ui.theme.ThemeAccent
import com.example.myapplication67.ui.theme.ThemeMode
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

class MainActivity : ComponentActivity() {
    private val demoApi by lazy { ViewModelProvider(this)[DemoApi::class.java] }
    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Postgrest)
            install(Storage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("demo_session", Context.MODE_PRIVATE).edit().clear().apply()
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
                    api = demoApi,
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
    api: DemoApi,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
) {
    val context = LocalContext.current
    var showRegistration by rememberSaveable { mutableStateOf(false) }
    var previewMode by rememberSaveable { mutableStateOf<String?>(null) }
    val account = api.currentAccount
    val repository = remember(api) { AuthRepository(api) }
    val profileRepository = remember(client, api) { RemoteProfileRepository(api) }
    val feedRepository = remember(client) { RemoteFeedRepository(client) }
    val avatarStorage = remember(client, api) { RemoteAvatarStorage(api) }

    if (account != null) {
        val accountFeedRepository = remember(client, api, account.id) { RemoteFeedRepository(client, api) }
        NawHostScreen(
            accountEmail = account.email,
            loginHint = account.login,
            profileKey = account.id,
            profileRepository = profileRepository,
            feedRepository = accountFeedRepository,
            remoteAvatarStorage = avatarStorage,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            themeAccent = themeAccent,
            onThemeAccentChange = onThemeAccentChange,
            onSignOut = {
                api.signOut()
                previewMode = null
                showRegistration = false
            },
        )
        return
    }

    if (previewMode == "guest" && BuildConfig.DEBUG) {
        val previewKey = "guest"
        val previewFeedRepository = remember(feedRepository, context, previewKey) {
            PreviewFeedRepository(feedRepository, LocalFeedRepository(context, previewKey))
        }
        NawHostScreen(
            previewTitle = "Гостевой режим",
            accountEmail = null,
            loginHint = "guest",
            profileKey = previewKey,
            profileRepository = null,
            feedRepository = previewFeedRepository,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            themeAccent = themeAccent,
            onThemeAccentChange = onThemeAccentChange,
            onExitPreview = {
                previewMode = null
                showRegistration = false
            },
        )
        return
    }

            if (showRegistration) {
                RegScreen(
                    repository = repository,
                    onLogClick = { showRegistration = false },
                    onGuestClick = { previewMode = "guest" },
                    onRegistered = { showRegistration = false },
                )
            } else {
                LogScreen(
                    repository = repository,
                    onRegClick = { showRegistration = true },
                    onGuestClick = { previewMode = "guest" },
                )
            }
}
