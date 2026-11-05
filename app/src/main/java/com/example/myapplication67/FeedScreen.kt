package com.example.myapplication67

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Feed
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FeedCategory(val title: String) {
    MAIN("Главная"),
    SUBSCRIPTIONS("Подписки"),
    POPULAR("Популярное"),
    NEW("Новое"),
}

private val reactionChoices = listOf("❤️", "🔥", "😍", "👍", "👏", "😋")

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    selectedCategory: FeedCategory,
    onCategorySelected: (FeedCategory) -> Unit,
    dataSource: FeedDataSource,
    currentUserId: String,
    currentLogin: String,
    followedLogins: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("feed") }
    var activePostId by rememberSaveable { mutableStateOf<String?>(null) }
    var posts by remember(dataSource) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember(dataSource) { mutableStateOf(true) }
    var loadError by remember(dataSource) { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var reactionBusy by remember { mutableStateOf<String?>(null) }
    var recipeCategories by remember(dataSource) { mutableStateOf<List<RecipeCategory>>(emptyList()) }
    var categoriesLoading by remember(dataSource) { mutableStateOf(true) }
    var categoriesError by remember(dataSource) { mutableStateOf(false) }
    var categoriesRefresh by remember { mutableIntStateOf(0) }

    var pickedImage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecipeCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var recipe by rememberSaveable { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var comments by remember { mutableStateOf<List<FeedComment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var commentError by remember { mutableStateOf<String?>(null) }
    var sendingComment by remember { mutableStateOf(false) }
    var commentsRefresh by remember { mutableIntStateOf(0) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedImage = uri.toString()
    }

    LaunchedEffect(dataSource, refreshKey) {
        loading = true
        runCatching { dataSource.loadPosts() }
            .onSuccess { posts = it; loadError = false }
            .onFailure { loadError = true }
        loading = false
    }
    LaunchedEffect(dataSource, categoriesRefresh) {
        categoriesLoading = true
        runCatching { dataSource.loadCategories() }
            .onSuccess { recipeCategories = it; categoriesError = false }
            .onFailure { categoriesError = true }
        categoriesLoading = false
    }
    LaunchedEffect(dataSource, page, activePostId, commentsRefresh) {
        val postId = activePostId
        if (page == "comments" && postId != null) {
            commentsLoading = true
            runCatching { dataSource.loadComments(postId) }
                .onSuccess { comments = it; commentError = null }
                .onFailure { commentError = "Не удалось загрузить комментарии" }
            commentsLoading = false
        }
    }

    BackHandler(page != "feed") { page = "feed" }

    Box(modifier) {
        when (page) {
            "editor" -> FeedEditor(
                pickedImage = pickedImage,
                categories = recipeCategories,
                selectedCategoryId = selectedRecipeCategoryId,
                onCategorySelected = { selectedRecipeCategoryId = it; saveError = null },
                categoriesLoading = categoriesLoading,
                categoriesError = categoriesError,
                onRetryCategories = { categoriesRefresh++ },
                title = title,
                onTitleChange = { title = it.take(120) },
                description = description,
                onDescriptionChange = { description = it.take(1000) },
                recipe = recipe,
                onRecipeChange = { recipe = it.take(10000) },
                saving = saving,
                error = saveError,
                onBack = { page = "feed" },
                onPickImage = { photoPicker.launch("image/*") },
                onSave = {
                    val uri = pickedImage?.let(Uri::parse)
                    val categoryId = selectedRecipeCategoryId
                    when {
                        currentLogin.isBlank() -> saveError = "Подождите загрузки профиля"
                        uri == null -> saveError = "Выберите фотографию рецепта"
                        categoryId == null -> saveError = "Выберите категорию"
                        title.isBlank() || description.isBlank() || recipe.isBlank() ->
                            saveError = "Заполните название, описание и рецепт"
                        else -> {
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    dataSource.publish(context, currentUserId, currentLogin, uri, title.trim(), description.trim(), recipe.trim(), categoryId)
                                    pickedImage = null
                                    selectedRecipeCategoryId = null
                                    title = ""
                                    description = ""
                                    recipe = ""
                                    page = "feed"
                                    refreshKey++
                                } catch (error: Exception) {
                                    saveError = when (error.message) {
                                        "Изображение слишком большое", "Неверный формат изображения" -> error.message
                                        else -> "Не удалось опубликовать рецепт. Попробуйте ещё раз."
                                    }
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                },
            )
            "comments" -> {
                val post = posts.firstOrNull { it.id == activePostId }
                if (post != null) {
                    FeedComments(
                        post = post,
                        comments = comments,
                        loading = commentsLoading,
                        text = commentText,
                        onTextChange = { commentText = it.take(2000) },
                        error = commentError,
                        sending = sendingComment,
                        onBack = { page = "feed" },
                        onRefresh = { commentsRefresh++ },
                        onSend = {
                            if (commentText.isBlank()) return@FeedComments
                            if (currentLogin.isBlank()) {
                                commentError = "Подождите загрузки профиля"
                                return@FeedComments
                            }
                            sendingComment = true
                            scope.launch {
                                try {
                                    dataSource.addComment(post.id, currentUserId, currentLogin, commentText.trim())
                                    commentText = ""
                                    commentsRefresh++
                                    refreshKey++
                                } catch (_: Exception) {
                                    commentError = "Не удалось отправить комментарий"
                                } finally {
                                    sendingComment = false
                                }
                            }
                        },
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(FeedCategory.entries) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(category.title) },
                            )
                        }
                    }
                    HorizontalDivider()
                    val visiblePosts = when (selectedCategory) {
                        FeedCategory.SUBSCRIPTIONS -> posts.filter { it.authorLogin in followedLogins }
                        FeedCategory.POPULAR -> posts.sortedByDescending { it.reactions.size }
                        FeedCategory.MAIN, FeedCategory.NEW -> posts
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (selectedCategory == FeedCategory.SUBSCRIPTIONS) {
                            item { FeedHeading("Ваши подписки", "Авторы, за которыми вы следите") }
                            item {
                                if (followedLogins.isEmpty()) {
                                    FeedEmptyCard(Icons.Outlined.People, "Подписок пока нет", "Авторы появятся здесь после подписки.")
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        items(followedLogins) { login -> FollowedAuthor(login) }
                                    }
                                }
                            }
                            item { FeedHeading("Публикации подписок", "Новые рецепты ваших авторов") }
                        } else {
                            item {
                                FeedHeading(
                                    when (selectedCategory) {
                                        FeedCategory.MAIN -> "Для вас"
                                        FeedCategory.POPULAR -> "Популярное"
                                        else -> "Новое"
                                    },
                                    "Рецепты и идеи от сообщества",
                                )
                            }
                        }
                        if (loading && posts.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        } else if (loadError) {
                            item {
                                OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Не удалось загрузить ленту. Повторить")
                                }
                            }
                        } else if (visiblePosts.isEmpty()) {
                            item {
                                FeedEmptyCard(
                                    Icons.AutoMirrored.Outlined.Feed,
                                    if (selectedCategory == FeedCategory.SUBSCRIPTIONS) "Публикаций подписок пока нет" else "Лента пока пуста",
                                    "Здесь появятся карточки рецептов.",
                                )
                            }
                        } else {
                            items(visiblePosts, key = { it.id }) { post ->
                                FeedPostCard(
                                    post = post,
                                    categoryName = recipeCategories.firstOrNull { it.id == post.categoryId }?.name,
                                    dataSource = dataSource,
                                    currentUserId = currentUserId,
                                    reactionBusy = reactionBusy == post.id,
                                    onReaction = { emoji ->
                                        reactionBusy = post.id
                                        scope.launch {
                                            try {
                                                dataSource.toggleReaction(post.id, currentUserId, emoji)
                                                refreshKey++
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Не удалось сохранить реакцию", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                reactionBusy = null
                                            }
                                        }
                                    },
                                    onComments = {
                                        activePostId = post.id
                                        comments = emptyList()
                                        commentsLoading = true
                                        commentText = ""
                                        commentError = null
                                        page = "comments"
                                    },
                                    onShare = {
                                        val text = "${post.title}\n\n${post.description}\n\n${post.recipe}"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Поделиться рецептом"))
                                    },
                                )
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { saveError = null; page = "editor" },
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Создать рецепт")
                }
            }
        }
    }
}

@Composable
private fun FeedPostCard(
    post: FeedPost,
    categoryName: String?,
    dataSource: FeedDataSource,
    currentUserId: String,
    reactionBusy: Boolean,
    onReaction: (String) -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
) {
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var reactionMenu by remember { mutableStateOf(false) }
    val reactionCounts = post.reactions.groupingBy { it.emoji }.eachCount()
    val myReaction = post.reactions.firstOrNull { it.userId == currentUserId }?.emoji
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            FeedPostImage(dataSource, post.imagePath)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("@${post.authorLogin}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (categoryName != null) {
                    Text(categoryName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(post.description, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Свернуть рецепт" else "Рецепт")
                }
                if (expanded) {
                    Text(post.recipe, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { reactionMenu = true }, enabled = !reactionBusy) {
                            Image(
                                painter = painterResource(R.drawable.add_react),
                                contentDescription = "Добавить реакцию",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        DropdownMenu(expanded = reactionMenu, onDismissRequest = { reactionMenu = false }) {
                            reactionChoices.forEach { emoji ->
                                DropdownMenuItem(
                                    text = { Text(emoji, style = MaterialTheme.typography.titleLarge) },
                                    onClick = { reactionMenu = false; onReaction(emoji) },
                                )
                            }
                        }
                    }
                    LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(reactionCounts.toList()) { (emoji, count) ->
                            TextButton(onClick = { onReaction(emoji) }, enabled = !reactionBusy) {
                                Text("$emoji $count", color = if (myReaction == emoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onComments) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Комментарии: ${post.commentCount}")
                        }
                        Text(post.commentCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Репост") }
                }
            }
        }
    }
}

@Composable
private fun FeedPostImage(dataSource: FeedDataSource, imagePath: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, dataSource, imagePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                dataSource.imageFile(context, imagePath)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    FeedImageFrame(bitmap)
}

@Composable
private fun FeedImageFrame(bitmap: ImageBitmap?) {
    Box(
        modifier = Modifier.fillMaxWidth().height(300.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Icon(Icons.Outlined.Image, contentDescription = "Фото рецепта", modifier = Modifier.size(52.dp))
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
            Image(
                bitmap = bitmap,
                contentDescription = "Фото рецепта",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FeedEditor(
    pickedImage: String?,
    categories: List<RecipeCategory>,
    selectedCategoryId: Int?,
    onCategorySelected: (Int) -> Unit,
    categoriesLoading: Boolean,
    categoriesError: Boolean,
    onRetryCategories: () -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    recipe: String,
    onRecipeChange: (String) -> Unit,
    saving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val preview by produceState<ImageBitmap?>(null, pickedImage) {
        value = pickedImage?.let { path ->
            runCatching { FeedImageStorage.preview(context, Uri.parse(path))?.asImageBitmap() }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад") }
            Text("Новый рецепт", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        if (pickedImage != null) FeedImageFrame(preview)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text(if (pickedImage == null) "Выбрать фото" else "Сменить фото")
        }
        Spacer(Modifier.height(14.dp))
        Box {
            OutlinedButton(
                onClick = { categoryMenuExpanded = true },
                enabled = categories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Выбрать категорию")
            }
            DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = { onCategorySelected(category.id); categoryMenuExpanded = false },
                    )
                }
            }
        }
        if (categoriesLoading) {
            Text("Загрузка категорий…", style = MaterialTheme.typography.bodySmall)
        } else if (categoriesError || categories.isEmpty()) {
            TextButton(onClick = onRetryCategories) { Text("Не удалось загрузить категории. Повторить") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(title, onTitleChange, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(description, onDescriptionChange, label = { Text("Краткое описание") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(recipe, onRecipeChange, label = { Text("Рецепт") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Публикация…" else "Опубликовать")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeedComments(
    post: FeedPost,
    comments: List<FeedComment>,
    loading: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    error: String?,
    sending: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSend: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад к ленте") }
            Column {
                Text("Чат публикации", style = MaterialTheme.typography.titleMedium)
                Text(post.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Обновить комментарии") }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                item { CircularProgressIndicator() }
            } else if (comments.isEmpty()) {
                item { Text("Пока нет комментариев. Начните обсуждение.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(comments, key = { it.id }) { comment ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("@${comment.authorLogin}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Написать комментарий") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            IconButton(onClick = onSend, enabled = text.isNotBlank() && !sending) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun FeedHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedEmptyCard(icon: ImageVector, title: String, description: String) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FollowedAuthor(login: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(login.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text("@$login", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
