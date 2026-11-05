package com.example.myapplication67

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.util.Locale
import kotlinx.coroutines.launch

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
    profileKey: String,
    page: String,
    onPageChange: (String) -> Unit,
    previewTitle: String?,
    avatarFile: File,
    avatarVersion: Int,
    onPickPhoto: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onExitPreview: (() -> Unit)?,
    onSignOut: (suspend () -> Unit)?,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("profile_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var snapshot by remember(dataSource) { mutableStateOf<ProfileSnapshot?>(null) }
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
                errorMessage = if (error.message?.contains("23505") == true) {
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
                        login = loginDraft,
                        onLoginChange = { loginDraft = it.take(30) },
                        about = aboutDraft,
                        onAboutChange = { aboutDraft = it.take(500) },
                        phone = phoneDraft,
                        onPhoneChange = { phoneDraft = it.take(40) },
                        birthday = birthdayDraft,
                        onBirthdayChange = { birthdayDraft = it.take(10) },
                        busy = busy,
                        onSave = {
                            val birthday = birthdayDraft.trim().ifBlank { null }
                            val normalizedLogin = loginDraft.trim().lowercase(Locale.ROOT)
                            if (!Regex("^[a-z0-9_]{3,30}$").matches(normalizedLogin)) {
                                errorMessage = "Логин: 3–30 символов, латинские буквы, цифры и _."
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
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
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
                        busy = busy,
                    )
                }
            }
        }
        errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProfileContent(
    snapshot: ProfileSnapshot,
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
    busy: Boolean,
) {
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
                snapshot.archivePublic -> "Архив виден всем"
                else -> "Архив виден только вам"
            }
        } else {
            if (previewTitle != null) "Рецепты хранятся только на этом устройстве" else "Публикации видны только вам"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    val posts = snapshot.posts.filter { it.archived == showArchive }
        .let { if (newestFirst) it else it.reversed() }
    if (posts.isEmpty()) {
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
                pair.forEach { post ->
                    RecipePostCard(
                        post = post,
                        onSetArchived = { onSetArchived(post) },
                        busy = busy,
                        modifier = Modifier.weight(1f),
                    )
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
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecipePostCard(post: RecipePost, onSetArchived: () -> Unit, busy: Boolean, modifier: Modifier) {
    Card(shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(92.dp).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(42.dp))
            }
            Text(
                post.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
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
}

@Composable
private fun EditProfileContent(
    login: String,
    onLoginChange: (String) -> Unit,
    about: String,
    onAboutChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    birthday: String,
    onBirthdayChange: (String) -> Unit,
    busy: Boolean,
    onSave: () -> Unit,
) {
    Text("Информация о пользователе", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = login,
        onValueChange = onLoginChange,
        label = { Text("Логин") },
        supportingText = { Text("Латинские буквы, цифры и _; минимум 3 символа") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text("Телефон") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = about,
        onValueChange = onAboutChange,
        label = { Text("О себе") },
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = birthday,
        onValueChange = onBirthdayChange,
        label = { Text("День рождения") },
        supportingText = { Text("Формат: ГГГГ-ММ-ДД") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(22.dp))
    Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Сохранение…" else "Сохранить")
    }
}

@Composable
private fun SettingsContent(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
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
    SettingSwitch("Тёмная тема", darkTheme, onDarkThemeChange)
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
            "Когда включено, другие пользователи смогут читать рецепты из архива."
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
        Text(if (busy) "Сохранение…" else "Добавить рецепт")
    }
}
