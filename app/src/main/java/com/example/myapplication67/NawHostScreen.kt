package com.example.myapplication67

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Feed
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.example.myapplication67.ui.theme.ThemeAccent
import com.example.myapplication67.ui.theme.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class MainTab(val title: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("Лента", Icons.AutoMirrored.Outlined.Feed),
    MainTab("Главная", Icons.Outlined.Home),
    MainTab("Калькулятор", Icons.Outlined.Calculate),
)

@Composable
fun NawHostScreen(
    previewTitle: String? = null,
    accountEmail: String? = null,
    loginHint: String?,
    profileKey: String,
    profileRepository: ProfileDataSource?,
    feedRepository: FeedDataSource? = null,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
    onExitPreview: (() -> Unit)? = null,
    onSignOut: (suspend () -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataSource = remember(profileRepository, context, profileKey, loginHint, accountEmail) {
        profileRepository ?: LocalProfileRepository(
            context = context,
            profileKey = profileKey,
            loginHint = loginHint ?: "guest",
            accountEmail = accountEmail,
        )
    }
    val feedSource = remember(feedRepository, context, profileKey) {
        feedRepository ?: LocalFeedRepository(context, profileKey)
    }
    val avatarFile = remember(context, profileKey) { AvatarStorage.fileFor(context, profileKey) }
    var avatarVersion by remember(profileKey) { mutableIntStateOf(0) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    AvatarStorage.save(context, profileKey, uri)
                    avatarVersion++
                } catch (_: Exception) {
                    Toast.makeText(context, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var accountPage by rememberSaveable { mutableStateOf("profile") }
    var homeLogin by remember(dataSource) { mutableStateOf(loginHint) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var feedCategory by rememberSaveable { mutableStateOf(FeedCategory.MAIN) }

    LaunchedEffect(dataSource, showAccount) {
        if (!showAccount) {
            runCatching { dataSource.load().info.login }
                .onSuccess { homeLogin = it }
        }
    }

    val accountBack: () -> Unit = {
        when (accountPage) {
            "profile" -> showAccount = false
            "feed_settings" -> accountPage = "settings"
            else -> accountPage = "profile"
        }
    }
    BackHandler(showAccount) { accountBack() }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (showAccount || selectedTab != 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showAccount) {
                    IconButton(onClick = accountBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        when (accountPage) {
                            "edit" -> "Изменить профиль"
                            "settings" -> "Настройки"
                            "feed_settings" -> "Настройки ленты"
                            "new_post" -> "Новый рецепт"
                            else -> "Профиль"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                } else {
                    Text(
                        if (selectedTab == 1) previewTitle ?: mainTabs[selectedTab].title else mainTabs[selectedTab].title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                accountPage = "profile"
                                showAccount = true
                            }
                            .semantics { contentDescription = "Открыть профиль" },
                        contentAlignment = Alignment.Center,
                    ) {
                        AvatarBadge(avatarFile = avatarFile, avatarVersion = avatarVersion, size = 42.dp)
                    }
                }
            }
            }
        },
        bottomBar = {
            if (!showAccount) {
                NavigationBar {
                    mainTabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val screenModifier = Modifier.fillMaxSize().padding(innerPadding)
        if (showAccount) {
            AccountScreen(
                modifier = screenModifier,
                dataSource = dataSource,
                feedDataSource = feedSource,
                profileKey = profileKey,
                page = accountPage,
                onPageChange = { accountPage = it },
                previewTitle = previewTitle,
                avatarFile = avatarFile,
                avatarVersion = avatarVersion,
                onPickPhoto = { photoPicker.launch("image/*") },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                themeAccent = themeAccent,
                onThemeAccentChange = onThemeAccentChange,
                onExitPreview = onExitPreview,
                onSignOut = onSignOut,
            )
        } else {
            when (selectedTab) {
                0 -> FeedScreen(
                    modifier = screenModifier,
                    selectedCategory = feedCategory,
                    onCategorySelected = { feedCategory = it },
                    dataSource = feedSource,
                    currentUserId = profileKey,
                    currentLogin = homeLogin ?: loginHint.orEmpty(),
                )
                1 -> HomeScreen(
                    modifier = screenModifier,
                    login = homeLogin ?: accountEmail?.substringBefore('@') ?: "Пользователь",
                    profileKey = profileKey,
                    avatarFile = avatarFile,
                    avatarVersion = avatarVersion,
                    onProfileClick = {
                        accountPage = "profile"
                        showAccount = true
                    },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    dataSource = feedSource,
                )
                2 -> CalculatorScreen(
                    modifier = screenModifier,
                    dataSource = feedSource,
                    profileKey = profileKey,
                )
            }
        }
    }
}
