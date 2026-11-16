package com.example.myapplication67

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import com.example.myapplication67.ui.theme.ThemeAccent
import com.example.myapplication67.ui.theme.ThemeMode

@Composable
fun AvatarBadge(
    avatarFile: File,
    avatarVersion: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(avatarFile.absolutePath, avatarVersion) {
        if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.absolutePath) else null
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Фото профиля",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = "Фото профиля",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}

@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    dataSource: ProfileDataSource,
    feedDataSource: FeedDataSource,
    profileKey: String,
    page: String,
    onPageChange: (String) -> Unit,
    previewTitle: String?,
    avatarFile: File,
    avatarVersion: Int,
    onPickPhoto: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
    onExitPreview: (() -> Unit)?,
    onSignOut: (suspend () -> Unit)?,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("profile_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var snapshot by remember(dataSource) { mutableStateOf<ProfileSnapshot?>(null) }
    var ownFeedPosts by remember(feedDataSource, profileKey) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var feedCategories by remember(feedDataSource) { mutableStateOf<List<RecipeCategory>>(emptyList()) }
    var feedLoading by remember(feedDataSource, profileKey) { mutableStateOf(true) }
    var feedError by remember(feedDataSource, profileKey) { mutableStateOf(false) }
    var feedReloadKey by remember(feedDataSource) { mutableStateOf(0) }
    var loading by remember(dataSource) { mutableStateOf(true) }
    var reloadKey by remember(dataSource) { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showArchive by rememberSaveable(profileKey) { mutableStateOf(false) }
    var notificationsEnabled by remember(profileKey) {
        mutableStateOf(preferences.getBoolean("$profileKey.notifications", true))
    }
    var newestFirst by remember(profileKey) {
        mutableStateOf(preferences.getBoolean("$profileKey.newest_first", true))
    }
    var aboutDraft by rememberSaveable(profileKey) { mutableStateOf("") }
    var loginDraft by rememberSaveable(profileKey) { mutableStateOf("") }
    var phoneDraft by rememberSaveable(profileKey) { mutableStateOf("") }
    var birthdayDraft by rememberSaveable(profileKey) { mutableStateOf("") }
    var titleDraft by rememberSaveable(profileKey) { mutableStateOf("") }
    var bodyDraft by rememberSaveable(profileKey) { mutableStateOf("") }

    LaunchedEffect(dataSource, reloadKey) {
        loading = true
        try {
            snapshot = dataSource.load()
            errorMessage = null
        } catch (_: Exception) {
            errorMessage = "Не удалось загрузить профиль. Проверьте подключение."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(feedDataSource, profileKey, feedReloadKey) {
        feedLoading = true
        runCatching { feedDataSource.loadOwnPosts(profileKey) }
            .onSuccess { ownFeedPosts = it; feedError = false }
            .onFailure { feedError = true }
        runCatching { feedDataSource.loadCategories() }
            .onSuccess { feedCategories = it }
        feedLoading = false
    }

    fun change(action: suspend () -> Unit, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        errorMessage = null
        scope.launch {
            try {
                action()
                snapshot = dataSource.load()
                onSuccess()
            } catch (error: Exception) {
                errorMessage = if (error.message?.contains("23505") == true ||
                    error.message?.contains("логин уже занят", ignoreCase = true) == true) {
                    "Этот логин уже занят."
                } else {
                    "Не удалось сохранить изменения. Попробуйте ещё раз."
                }
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (loading && snapshot == null) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (snapshot == null) {
            Button(onClick = { reloadKey++ }, modifier = Modifier.fillMaxWidth()) {
                Text("Повторить загрузку")
            }
        } else {
            val current = snapshot
            if (current != null) {
                when (page) {
                    "edit" -> EditProfileContent(
                        avatarFile = avatarFile,
                        avatarVersion = avatarVersion,
                        onPickPhoto = onPickPhoto,
                        login = loginDraft,
                        onLoginChange = { loginDraft = it; errorMessage = null },
                        about = aboutDraft,
                        onAboutChange = { aboutDraft = it.take(500); errorMessage = null },
                        phone = phoneDraft,
                        onPhoneChange = { phoneDraft = it.take(40); errorMessage = null },
                        birthday = birthdayDraft,
                        onBirthdayChange = { birthdayDraft = it.take(10); errorMessage = null },
                        busy = busy,
                        errorMessage = errorMessage,
                        onSave = {
                            val birthday = birthdayDraft.trim().ifBlank { null }
                            val normalizedLogin = LoginRules.normalize(loginDraft)
                            if (!LoginRules.isValid(normalizedLogin)) {
                                errorMessage = "Логин должен содержать от 3 до 30 символов."
                            } else if (birthday != null && runCatching { LocalDate.parse(birthday) }.isFailure) {
                                errorMessage = "Введите дату рождения в формате ГГГГ-ММ-ДД."
                            } else {
                                change(
                                    action = {
                                        dataSource.saveInfo(current.info.copy(
                                            login = normalizedLogin,
                                            about = aboutDraft.trim(),
                                            phone = phoneDraft.trim().ifBlank { null },
                                            birthday = birthday,
                                        ))
                                    },
                                    onSuccess = { onPageChange("profile") },
                                )
                            }
                        },
                    )
                    "settings" -> SettingsContent(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        themeAccent = themeAccent,
                        onThemeAccentChange = onThemeAccentChange,
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsChange = { enabled ->
                            notificationsEnabled = enabled
                            preferences.edit().putBoolean("$profileKey.notifications", enabled).apply()
                        },
                        onFeedSettings = { onPageChange("feed_settings") },
                        onExitPreview = onExitPreview,
                        onSignOut = onSignOut,
                    )
                    "feed_settings" -> FeedSettingsContent(
                        archivePublic = current.archivePublic,
                        onArchivePublicChange = { enabled ->
                            change(action = { dataSource.setArchivePublic(current.info.id, enabled) })
                        },
                        newestFirst = newestFirst,
                        onNewestFirstChange = { enabled ->
                            newestFirst = enabled
                            preferences.edit().putBoolean("$profileKey.newest_first", enabled).apply()
                        },
                        busy = busy,
                        isPreview = previewTitle != null,
                    )
                    "new_post" -> NewPostContent(
                        title = titleDraft,
                        onTitleChange = { titleDraft = it.take(120) },
                        body = bodyDraft,
                        onBodyChange = { bodyDraft = it.take(10000) },
                        busy = busy,
                        onSave = {
                            if (titleDraft.isBlank()) {
                                errorMessage = "Введите название рецепта."
                            } else {
                                change(
                                    action = { dataSource.addPost(current.info.id, titleDraft.trim(), bodyDraft.trim()) },
                                    onSuccess = {
                                        titleDraft = ""
                                        bodyDraft = ""
                                        showArchive = false
                                        onPageChange("profile")
                                    },
                                )
                            }
                        },
                    )
                    else -> ProfileContent(
                        snapshot = current,
                        ownFeedPosts = ownFeedPosts,
                        feedDataSource = feedDataSource,
                        feedCategories = feedCategories,
                        feedLoading = feedLoading,
                        feedError = feedError,
                        onFeedRetry = { feedReloadKey++ },
                        previewTitle = previewTitle,
                        avatarFile = avatarFile,
                        avatarVersion = avatarVersion,
                        onPickPhoto = onPickPhoto,
                        onEdit = {
                            loginDraft = current.info.login
                            aboutDraft = current.info.about
                            phoneDraft = current.info.phone.orEmpty()
                            birthdayDraft = current.info.birthday.orEmpty()
                            onPageChange("edit")
                        },
                        onSettings = { onPageChange("settings") },
                        showArchive = showArchive,
                        onShowArchiveChange = { showArchive = it },
                        newestFirst = newestFirst,
                        onAddPost = { onPageChange("new_post") },
                        onSetArchived = { post ->
                            change(action = { dataSource.setArchived(current.info.id, post.id, !post.archived) })
                        },
                        onSetShowPublicly = { post ->
                            change(action = { dataSource.setShowPublicly(current.info.id, post.id, !post.showPublicly) })
                        },
                        onDeletePost = { post ->
                            change(action = { dataSource.deletePost(current.info.id, post.id) })
                        },
                        busy = busy,
                    )
                }
            }
        }
        errorMessage?.takeIf { page != "edit" || snapshot == null }?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProfileContent(
    snapshot: ProfileSnapshot,
    ownFeedPosts: List<FeedPost>,
    feedDataSource: FeedDataSource,
    feedCategories: List<RecipeCategory>,
    feedLoading: Boolean,
    feedError: Boolean,
    onFeedRetry: () -> Unit,
    previewTitle: String?,
    avatarFile: File,
    avatarVersion: Int,
    onPickPhoto: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    showArchive: Boolean,
    onShowArchiveChange: (Boolean) -> Unit,
    newestFirst: Boolean,
    onAddPost: () -> Unit,
    onSetArchived: (RecipePost) -> Unit,
    onSetShowPublicly: (RecipePost) -> Unit,
    onDeletePost: (RecipePost) -> Unit,
    busy: Boolean,
) {
    var openedFeedPost by remember { mutableStateOf<FeedPost?>(null) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AvatarBadge(
            avatarFile = avatarFile,
            avatarVersion = avatarVersion,
            size = 112.dp,
            modifier = Modifier.clickable(onClick = onPickPhoto),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "@${snapshot.info.login}",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (previewTitle != null) {
        Text(
            previewTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileAction("Выбрать фото", Icons.Outlined.CameraAlt, onPickPhoto, Modifier.weight(1f))
        ProfileAction("Изменить", Icons.Outlined.Edit, onEdit, Modifier.weight(1f))
        ProfileAction("Настройки", Icons.Outlined.Settings, onSettings, Modifier.weight(1f))
    }
    Spacer(Modifier.height(24.dp))
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ProfileInfoRow("Почта", snapshot.info.email ?: "Не указана")
            ProfileInfoRow("Телефон", snapshot.info.phone ?: "Не указан")
            ProfileInfoRow("О себе", snapshot.info.about.ifBlank { "Не заполнено" })
            ProfileInfoRow("Имя пользователя", "@${snapshot.info.login}")
            ProfileInfoRow("День рождения", snapshot.info.birthday ?: "Не указан")
        }
    }
    Spacer(Modifier.height(28.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        SectionChoice("Публикации", !showArchive, { onShowArchiveChange(false) }, Modifier.weight(1f))
        SectionChoice("Архив", showArchive, { onShowArchiveChange(true) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (showArchive) {
            when {
                previewTitle != null -> "Архив хранится только на этом устройстве"
                snapshot.archivePublic -> "Архив открыт; показ рецепта в ленте меняется через меню ⋮"
                else -> "Архив виден вам; показ рецепта в ленте меняется через меню ⋮"
            }
        } else {
            if (previewTitle != null) "Рецепты хранятся только на этом устройстве"
            else "Опубликованные рецепты профиля также видны в общей ленте"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    if (!showArchive && feedLoading && ownFeedPosts.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    if (!showArchive && feedError) {
        OutlinedButton(onClick = onFeedRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Не удалось загрузить публикации из ленты. Повторить")
        }
        Spacer(Modifier.height(12.dp))
    }
    val posts = buildList {
        if (!showArchive) ownFeedPosts.forEach { add(ProfileCardItem("feed:${it.id}", it.createdAt, feedPost = it)) }
        snapshot.posts.filter { it.archived == showArchive }.forEach {
            add(ProfileCardItem("recipe:${it.id}", it.createdAt, recipePost = it))
        }
    }.sortedByDescending { it.createdAt }.let { if (newestFirst) it else it.reversed() }
    if (posts.isEmpty() && !(!showArchive && feedLoading)) {
        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                if (showArchive) "Архив пока пуст" else "Здесь пока нет рецептов",
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        posts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { item ->
                    key(item.id) {
                        val feedPost = item.feedPost
                        val recipePost = item.recipePost
                        if (feedPost != null) {
                            FeedProfileCard(
                                post = feedPost,
                                dataSource = feedDataSource,
                                categoryName = feedCategories.firstOrNull { it.id == feedPost.categoryId }?.name,
                                onOpen = { openedFeedPost = feedPost },
                                modifier = Modifier.weight(1f),
                            )
                        } else if (recipePost != null) {
                            RecipePostCard(
                                post = recipePost,
                                onSetArchived = { onSetArchived(recipePost) },
                                onSetShowPublicly = { onSetShowPublicly(recipePost) },
                                onDelete = { onDeletePost(recipePost) },
                                busy = busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (!showArchive) {
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAddPost, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Добавить рецепт")
        }
    }
    openedFeedPost?.let { post ->
        FeedRecipeDetailDialog(
            post = post,
            dataSource = feedDataSource,
            categoryName = feedCategories.firstOrNull { it.id == post.categoryId }?.name,
            onDismiss = { openedFeedPost = null },
        )
    }
}

private data class ProfileCardItem(
    val id: String,
    val createdAt: String,
    val feedPost: FeedPost? = null,
    val recipePost: RecipePost? = null,
)

@Composable
private fun FeedProfileCard(
    post: FeedPost,
    dataSource: FeedDataSource,
    categoryName: String?,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    Card(onClick = onOpen, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column {
            FeedPostImage(dataSource, post.imagePath, height = 92.dp)
            Column(Modifier.padding(12.dp)) {
                Text(post.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                categoryName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Text(post.createdAt.take(10), style = MaterialTheme.typography.bodySmall)
                Text("Комментарии: ${post.commentCount}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProfileAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = modifier.height(82.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun RecipePostCard(
    post: RecipePost,
    onSetArchived: () -> Unit,
    onSetShowPublicly: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
    modifier: Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(92.dp).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(42.dp))
                if (post.archived) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Действия с публикацией")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (post.showPublicly) "Опубликован" else "Опубликовать") },
                                trailingIcon = {
                                    Checkbox(
                                        checked = post.showPublicly,
                                        onCheckedChange = null,
                                        enabled = !busy,
                                    )
                                },
                                onClick = { menuExpanded = false; onSetShowPublicly() },
                                enabled = !busy,
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; confirmDelete = true },
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
            Text(
                post.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
            )
            Text(
                if (post.showPublicly) "В общей ленте" else "Скрыт из ленты",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(post.createdAt.take(10), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSetArchived, enabled = !busy) {
                    Icon(
                        if (post.archived) Icons.Outlined.Restore else Icons.Outlined.Archive,
                        contentDescription = if (post.archived) "Вернуть публикацию" else "В архив",
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить публикацию?") },
            text = { Text("Рецепт будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun EditProfileContent(
    avatarFile: File,
    avatarVersion: Int,
    onPickPhoto: () -> Unit,
    login: String,
    onLoginChange: (String) -> Unit,
    about: String,
    onAboutChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    birthday: String,
    onBirthdayChange: (String) -> Unit,
    busy: Boolean,
    errorMessage: String?,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp).clickable(onClick = onPickPhoto)) {
                AvatarBadge(avatarFile = avatarFile, avatarVersion = avatarVersion, size = 104.dp,
                    modifier = Modifier.align(Alignment.TopCenter))
                Box(
                    Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = "Сменить фото профиля",
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Фото профиля", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onPickPhoto) { Text("Выбрать другое фото") }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Информация о пользователе", style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileEditRow(
                value = login,
                onValueChange = onLoginChange,
                label = "Имя пользователя",
                placeholder = "Имя пользователя",
                hint = "От 3 до 30 символов",
                icon = Icons.Outlined.Person,
            )
            ProfileEditRow(
                value = about,
                onValueChange = onAboutChange,
                label = "О себе",
                placeholder = "Расскажите о себе",
                singleLine = false,
                icon = Icons.Outlined.Edit,
            )
            ProfileEditRow(
                value = phone,
                onValueChange = onPhoneChange,
                label = "Телефон",
                placeholder = "Не указан",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                icon = Icons.Outlined.Phone,
            )
            ProfileEditRow(
                value = birthday,
                onValueChange = onBirthdayChange,
                label = "День рождения",
                placeholder = "ГГГГ-ММ-ДД",
                icon = Icons.Outlined.Cake,
                hint = "Формат: 2020-12-31",
            )
        }
    }
    errorMessage?.let { message ->
        Spacer(Modifier.height(14.dp))
        Text(message, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(22.dp))
    Button(onClick = onSave, enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text(if (busy) "Сохранение…" else "Сохранить")
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ProfileEditRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    hint: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            maxLines = if (singleLine) 1 else 5,
            keyboardOptions = keyboardOptions,
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                }
            },
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 5.dp))
        }
    }
}

@Composable
private fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onFeedSettings: () -> Unit,
    onExitPreview: (() -> Unit)?,
    onSignOut: (suspend () -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf(false) }
    Text("Внешний вид", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .selectable(selected = themeMode == mode, onClick = { onThemeModeChange(mode) }, role = Role.RadioButton)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.title, modifier = Modifier.weight(1f))
                    RadioButton(selected = themeMode == mode, onClick = null)
                }
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Text("Цвет интерфейса", style = MaterialTheme.typography.titleMedium)
    Text("По умолчанию — мягкий светло-пурпурный градиент", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeAccent.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { accent ->
                    ThemeSwatch(
                        accent = accent,
                        selected = themeAccent == accent,
                        onClick = { onThemeAccentChange(accent) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Text("Уведомления", style = MaterialTheme.typography.titleMedium)
    SettingSwitch("Разрешить уведомления приложения", notificationsEnabled, onNotificationsChange)
    Text(
        "Настройка применяется к будущим уведомлениям.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Card(onClick = onFeedSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Настройки ленты", style = MaterialTheme.typography.titleMedium)
            Text("Порядок рецептов и видимость архива", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(32.dp))
    if (onSignOut != null) {
        OutlinedButton(
            onClick = {
                signingOut = true
                signOutError = false
                scope.launch {
                    try {
                        onSignOut()
                    } catch (_: Exception) {
                        signOutError = true
                    } finally {
                        signingOut = false
                    }
                }
            },
            enabled = !signingOut,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (signingOut) "Подождите…" else "Выйти из аккаунта") }
    } else if (onExitPreview != null) {
        OutlinedButton(onClick = onExitPreview, modifier = Modifier.fillMaxWidth()) {
            Text("Перейти ко входу")
        }
    }
    if (signOutError) {
        Text("Не удалось выйти. Попробуйте ещё раз.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ThemeSwatch(
    accent: ThemeAccent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(50.dp)) {
                Column(Modifier.fillMaxSize().clip(CircleShape)) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(accent.lightContainer))
                    Box(Modifier.fillMaxWidth().weight(1f).background(accent.lightPrimary))
                }
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(19.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Выбрано",
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(accent.title, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeedSettingsContent(
    archivePublic: Boolean,
    onArchivePublicChange: (Boolean) -> Unit,
    newestFirst: Boolean,
    onNewestFirstChange: (Boolean) -> Unit,
    busy: Boolean,
    isPreview: Boolean,
) {
    Text("Публикации", style = MaterialTheme.typography.titleMedium)
    Text("Ваши обычные публикации видны только вам.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
    SettingSwitch("Сначала новые рецепты", newestFirst, onNewestFirstChange)
    Spacer(Modifier.height(26.dp))
    Text("Архив публикаций", style = MaterialTheme.typography.titleMedium)
    SettingSwitch("Сделать архив публичным", archivePublic, onArchivePublicChange, enabled = !busy && !isPreview)
    Text(
        if (isPreview) {
            "Войдите в аккаунт, чтобы открыть архив другим пользователям."
        } else {
            "Когда включено, другие пользователи видят архивные рецепты с галочкой «Показывать»."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun NewPostContent(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    busy: Boolean,
    onSave: () -> Unit,
) {
    Text("Опубликовать рецепт", style = MaterialTheme.typography.titleLarge)
    Text("После добавления рецепт появится в общей ленте без фото и категории.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Название") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = body,
        onValueChange = onBodyChange,
        label = { Text("Описание и приготовление") },
        minLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(22.dp))
    Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Сохранение…" else "Опубликовать рецепт")
    }
}

data class ProfileInfo(
    val id: String,
    val login: String,
    val email: String?,
    val about: String = "",
    val phone: String? = null,
    val birthday: String? = null,
)

data class RecipePost(
    val id: String,
    val title: String,
    val body: String,
    val archived: Boolean,
    val showPublicly: Boolean = true,
    val createdAt: String,
)

data class ProfileSnapshot(
    val info: ProfileInfo,
    val archivePublic: Boolean,
    val posts: List<RecipePost>,
)

interface ProfileDataSource {
    suspend fun load(): ProfileSnapshot
    suspend fun saveInfo(info: ProfileInfo)
    suspend fun setArchivePublic(ownerId: String, enabled: Boolean)
    suspend fun addPost(ownerId: String, title: String, body: String)
    suspend fun setArchived(ownerId: String, postId: String, archived: Boolean)
    suspend fun setShowPublicly(ownerId: String, postId: String, enabled: Boolean)
    suspend fun deletePost(ownerId: String, postId: String)
}

class RemoteProfileRepository(private val api: DemoApi) : ProfileDataSource {
    override suspend fun load(): ProfileSnapshot {
        val response = api.request("profile")
        val row = response.getJSONObject("user")
        val posts = response.getJSONArray("recipes").let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
            .map { post ->
            RecipePost(
                id = post.getLong("id").toString(),
                title = post.optString("title"),
                body = post.optString("body"),
                archived = post.optBoolean("archived"),
                showPublicly = post.optBoolean("show_publicly", true),
                createdAt = post.optString("created_at"),
            )
        }.sortedByDescending { it.createdAt }

        return ProfileSnapshot(
            info = ProfileInfo(
                id = row.getLong("id").toString(),
                login = row.optString("login"),
                email = row.optString("gmail"),
                about = row.optString("about"),
                phone = row.optString("phone").takeUnless { it == "null" || it.isBlank() },
                birthday = row.optString("birthday").takeUnless { it == "null" || it.isBlank() },
            ),
            archivePublic = response.optBoolean("archive_public"),
            posts = posts,
        )
    }

    override suspend fun saveInfo(info: ProfileInfo) {
        api.request("profile_save", JSONObject().put("login", info.login).put("about", info.about)
            .put("phone", info.phone).put("birthday", info.birthday))
        api.refreshIdentity()
    }

    override suspend fun setArchivePublic(ownerId: String, enabled: Boolean) {
        api.request("archive_visibility", JSONObject().put("enabled", enabled))
    }

    override suspend fun addPost(ownerId: String, title: String, body: String) {
        api.request("recipe_add", JSONObject().put("title", title).put("body", body))
    }

    override suspend fun setArchived(ownerId: String, postId: String, archived: Boolean) {
        api.request("recipe_archive", JSONObject().put("id", postId.toLong()).put("enabled", archived))
    }

    override suspend fun deletePost(ownerId: String, postId: String) {
        api.request("recipe_delete", JSONObject().put("id", postId.toLong()))
    }

    override suspend fun setShowPublicly(ownerId: String, postId: String, enabled: Boolean) {
        api.request("recipe_public", JSONObject().put("id", postId.toLong()).put("enabled", enabled))
    }
}

class LocalProfileRepository(
    context: Context,
    private val profileKey: String,
    private val loginHint: String,
    private val accountEmail: String?,
) : ProfileDataSource {
    private val preferences = context.getSharedPreferences("local_profiles", Context.MODE_PRIVATE)
    private val prefix = "$profileKey."

    override suspend fun load(): ProfileSnapshot = ProfileSnapshot(
        info = ProfileInfo(
            id = profileKey,
            login = preferences.getString("${prefix}login", loginHint).orEmpty(),
            email = accountEmail,
            about = preferences.getString("${prefix}about", "").orEmpty(),
            phone = preferences.getString("${prefix}phone", null),
            birthday = preferences.getString("${prefix}birthday", null),
        ),
        archivePublic = preferences.getBoolean("${prefix}archive_public", false),
        posts = readPosts(),
    )

    override suspend fun saveInfo(info: ProfileInfo) {
        preferences.edit()
            .putString("${prefix}login", info.login)
            .putString("${prefix}about", info.about)
            .putString("${prefix}phone", info.phone)
            .putString("${prefix}birthday", info.birthday)
            .apply()
    }

    override suspend fun setArchivePublic(ownerId: String, enabled: Boolean) {
        preferences.edit().putBoolean("${prefix}archive_public", enabled).apply()
    }

    override suspend fun addPost(ownerId: String, title: String, body: String) {
        val posts = readPosts() + RecipePost(
            id = newLocalId(),
            title = title,
            body = body,
            archived = false,
            createdAt = Instant.now().toString(),
        )
        writePosts(posts)
    }

    override suspend fun setArchived(ownerId: String, postId: String, archived: Boolean) {
        writePosts(readPosts().map { post ->
            if (post.id == postId) post.copy(archived = archived) else post
        })
    }

    override suspend fun deletePost(ownerId: String, postId: String) {
        writePosts(readPosts().filterNot { it.id == postId && it.archived })
    }

    override suspend fun setShowPublicly(ownerId: String, postId: String, enabled: Boolean) {
        writePosts(readPosts().map { post ->
            if (post.id == postId && post.archived) post.copy(showPublicly = enabled) else post
        })
    }

    private fun readPosts(): List<RecipePost> = try {
        val array = JSONArray(preferences.getString("${prefix}posts", "[]"))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            RecipePost(
                id = row.getString("id"),
                title = row.getString("title"),
                body = row.optString("body"),
                archived = row.optBoolean("archived"),
                showPublicly = row.optBoolean("show_publicly", true),
                createdAt = row.getString("created_at"),
            )
        }.sortedByDescending { it.createdAt }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writePosts(posts: List<RecipePost>) {
        val array = JSONArray()
        posts.forEach { post ->
            array.put(JSONObject()
                .put("id", post.id)
                .put("title", post.title)
                .put("body", post.body)
                .put("archived", post.archived)
                .put("show_publicly", post.showPublicly)
                .put("created_at", post.createdAt))
        }
        preferences.edit().putString("${prefix}posts", array.toString()).apply()
    }
}

object AvatarStorage {
    fun fileFor(context: Context, profileKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(profileKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(File(context.filesDir, "avatars"), "$digest.jpg")
    }

    suspend fun save(context: Context, profileKey: String, uri: Uri) = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val scale = (512f / max(width, height)).coerceAtMost(1f)
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
        val file = fileFor(context, profileKey)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                error("Не удалось сохранить изображение")
            }
        }
    }
}

class RemoteAvatarStorage(private val api: DemoApi) {
    private val mutex = Mutex()

    suspend fun sync(context: Context, userId: String): Boolean = mutex.withLock {
        val encoded = api.request("avatar_download").optString("image")
        val local = AvatarStorage.fileFor(context, userId)
        if (encoded.isBlank() || encoded == "null") {
            if (local.exists()) {
                upload(userId, local)
            }
            return@withLock false
        }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        withContext(Dispatchers.IO) {
            local.parentFile?.mkdirs()
            val temp = File(local.parentFile, "${local.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(local)) {
                temp.copyTo(local, overwrite = true)
                temp.delete()
            }
        }
        true
    }

    suspend fun save(context: Context, userId: String, uri: Uri) = mutex.withLock {
        AvatarStorage.save(context, userId, uri)
        upload(userId, AvatarStorage.fileFor(context, userId))
    }

    private suspend fun upload(userId: String, local: File) {
        val bytes = withContext(Dispatchers.IO) { local.readBytes() }
        api.uploadImage("avatar_upload", bytes)
    }
}
