package com.example.myapplication67

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FeedCategory(val title: String) {
    MAIN("Главная"),
    SUBSCRIPTIONS("Подписки"),
    POPULAR("Популярное"),
    NEW("Новое"),
}

private val reactionChoices = listOf("❤️", "🔥", "😍", "👍", "👏", "😋")

private sealed interface FeedEntry {
    val key: String
    val createdAt: String
    val authorLogin: String

    data class PostItem(val post: FeedPost) : FeedEntry {
        override val key = "post:${post.id}"
        override val createdAt = post.createdAt
        override val authorLogin = post.authorLogin
    }

    data class ProfileRecipeItem(val recipe: PublishedRecipe) : FeedEntry {
        override val key = "recipe:${recipe.id}"
        override val createdAt = recipe.createdAt
        override val authorLogin = recipe.authorLogin
    }
}

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    selectedCategory: FeedCategory,
    onCategorySelected: (FeedCategory) -> Unit,
    dataSource: FeedDataSource,
    currentUserId: String,
    currentLogin: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val followPreferences = remember(context, currentUserId) {
        context.getSharedPreferences("feed_follows_$currentUserId", Context.MODE_PRIVATE)
    }
    var followedLogins by remember(followPreferences) {
        mutableStateOf(followPreferences.getStringSet("logins", emptySet()).orEmpty().toSet())
    }
    var page by rememberSaveable { mutableStateOf("feed") }
    var activePostId by rememberSaveable { mutableStateOf<String?>(null) }
    var posts by remember(dataSource) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var profileRecipes by remember(dataSource) { mutableStateOf<List<PublishedRecipe>>(emptyList()) }
    var loading by remember(dataSource) { mutableStateOf(true) }
    var loadError by remember(dataSource) { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var reactionBusy by remember { mutableStateOf<String?>(null) }
    var recipeCategories by remember(dataSource) { mutableStateOf<List<RecipeCategory>>(emptyList()) }
    var categoriesLoading by remember(dataSource) { mutableStateOf(true) }
    var categoriesError by remember(dataSource) { mutableStateOf(false) }
    var categoriesRefresh by remember { mutableIntStateOf(0) }

    var pickedImage by rememberSaveable { mutableStateOf<String?>(null) }
    var imageUrl by rememberSaveable { mutableStateOf("") }
    var selectedRecipeCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var recipe by rememberSaveable { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<FeedPost?>(null) }
    var deleteCandidate by remember { mutableStateOf<FeedPost?>(null) }
    var deleting by remember { mutableStateOf(false) }

    var comments by remember { mutableStateOf<List<FeedComment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var commentError by remember { mutableStateOf<String?>(null) }
    var sendingComment by remember { mutableStateOf(false) }
    var commentsRefresh by remember { mutableIntStateOf(0) }
    val availableAuthors = (posts.map { it.authorLogin } + profileRecipes.map { it.authorLogin })
        .filter { it.isNotBlank() && it != currentLogin }
        .distinct().sorted()
    val toggleFollow: (String) -> Unit = { login ->
        followedLogins = if (login in followedLogins) followedLogins - login else followedLogins + login
        followPreferences.edit().putStringSet("logins", followedLogins).apply()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedImage = uri.toString()
            imageUrl = ""
        }
    }

    LaunchedEffect(dataSource, refreshKey) {
        loading = posts.isEmpty() && profileRecipes.isEmpty()
        while (true) {
            val cardResult = runCatching { dataSource.loadPosts() }
            val recipeResult = runCatching { dataSource.loadPublishedProfileRecipes() }
            cardResult
                .onSuccess { posts = it; loadError = false }
            recipeResult.onSuccess { profileRecipes = it }
            loadError = cardResult.isFailure && recipeResult.isFailure && posts.isEmpty() && profileRecipes.isEmpty()
            loading = false
            delay(60_000)
        }
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
                dataSource = dataSource,
                editing = editingPost != null,
                existingImagePath = editingPost?.imagePath,
                pickedImage = pickedImage,
                imageUrl = imageUrl,
                onImageUrlChange = { value ->
                    imageUrl = value
                    if (value.isNotBlank()) pickedImage = null
                    saveError = null
                },
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
                    val image = when {
                        imageUrl.isNotBlank() -> FeedImageInput.Link(imageUrl.trim())
                        pickedImage != null -> FeedImageInput.Photo(Uri.parse(pickedImage))
                        else -> null
                    }
                    val categoryId = selectedRecipeCategoryId
                    when {
                        currentLogin.isBlank() -> saveError = "Подождите загрузки профиля"
                        image == null && editingPost == null -> saveError = "Выберите фото или вставьте ссылку на изображение"
                        image is FeedImageInput.Link && !FeedImageStorage.isValidExternalUrl(image.url) ->
                            saveError = "Введите корректную HTTPS-ссылку на изображение"
                        categoryId == null -> saveError = "Выберите категорию"
                        title.isBlank() || description.isBlank() || recipe.isBlank() ->
                            saveError = "Заполните название, описание и рецепт"
                        else -> {
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    val edited = editingPost
                                    if (edited == null) {
                                        dataSource.publish(context, currentUserId, currentLogin, image!!, title.trim(), description.trim(), recipe.trim(), categoryId)
                                    } else {
                                        dataSource.updatePost(context, edited, currentLogin, image, title.trim(), description.trim(), recipe.trim(), categoryId)
                                    }
                                    pickedImage = null
                                    imageUrl = ""
                                    selectedRecipeCategoryId = null
                                    title = ""
                                    description = ""
                                    recipe = ""
                                    editingPost = null
                                    page = "feed"
                                    refreshKey++
                                } catch (error: Exception) {
                                    saveError = when (error.message) {
                                        "Изображение слишком большое", "Неверный формат изображения",
                                        "Не удалось открыть изображение по ссылке", "Введите корректную HTTPS-ссылку на изображение" -> error.message
                                        else -> "Не удалось сохранить рецепт. Попробуйте ещё раз."
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
                    val allEntries = posts.map { FeedEntry.PostItem(it) } +
                        profileRecipes.map { FeedEntry.ProfileRecipeItem(it) }
                    val visibleEntries = when (selectedCategory) {
                        FeedCategory.SUBSCRIPTIONS -> allEntries.filter { it.authorLogin in followedLogins }
                            .sortedByDescending { it.createdAt }
                        FeedCategory.POPULAR -> allEntries.sortedWith(
                            compareByDescending<FeedEntry> { (it as? FeedEntry.PostItem)?.post?.reactions?.size ?: 0 }
                                .thenByDescending { it.createdAt },
                        )
                        FeedCategory.MAIN, FeedCategory.NEW -> allEntries.sortedByDescending { it.createdAt }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (selectedCategory == FeedCategory.SUBSCRIPTIONS) {
                            item { FeedHeading("Ваши подписки", "Авторы, за которыми вы следите") }
                            item {
                                Text("Авторы сообщества", style = MaterialTheme.typography.titleSmall)
                                if (availableAuthors.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(availableAuthors) { login ->
                                            FilterChip(
                                                selected = login in followedLogins,
                                                onClick = { toggleFollow(login) },
                                                label = { Text("@$login") },
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                if (followedLogins.isEmpty()) {
                                    FeedEmptyCard(Icons.Outlined.People, "Подписок пока нет", "Выберите автора выше.")
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        items(followedLogins.sorted()) { login -> FollowedAuthor(login) }
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
                        if (loading && posts.isEmpty() && profileRecipes.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        } else if (loadError) {
                            item {
                                OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Не удалось загрузить ленту. Повторить")
                                }
                            }
                        } else if (visibleEntries.isEmpty()) {
                            item {
                                FeedEmptyCard(
                                    Icons.AutoMirrored.Outlined.Feed,
                                    if (selectedCategory == FeedCategory.SUBSCRIPTIONS) "Публикаций подписок пока нет" else "Лента пока пуста",
                                    "Здесь появятся карточки рецептов.",
                                )
                            }
                        } else {
                            items(visibleEntries, key = { it.key }) { entry ->
                                when (entry) {
                                    is FeedEntry.ProfileRecipeItem -> PublishedRecipeCard(entry.recipe)
                                    is FeedEntry.PostItem -> {
                                        val post = entry.post
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
                                    onEdit = {
                                        editingPost = post
                                        pickedImage = null
                                        imageUrl = ""
                                        selectedRecipeCategoryId = post.categoryId
                                        title = post.title
                                        description = post.description
                                        recipe = post.recipe
                                        saveError = null
                                        page = "editor"
                                    },
                                    onDelete = { deleteCandidate = post },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = {
                        editingPost = null
                        pickedImage = null
                        imageUrl = ""
                        selectedRecipeCategoryId = null
                        title = ""
                        description = ""
                        recipe = ""
                        saveError = null
                        page = "editor"
                    },
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Создать рецепт")
                }
            }
        }
        deleteCandidate?.let { post ->
            AlertDialog(
                onDismissRequest = { if (!deleting) deleteCandidate = null },
                title = { Text("Удалить публикацию?") },
                text = { Text("Карточка и её комментарии исчезнут из ленты.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleting = true
                        scope.launch {
                            try {
                                dataSource.deletePost(post)
                                posts = posts.filterNot { it.id == post.id }
                                refreshKey++
                                deleteCandidate = null
                            } catch (_: Exception) {
                                Toast.makeText(context, "Не удалось удалить публикацию", Toast.LENGTH_SHORT).show()
                            } finally { deleting = false }
                        }
                    }, enabled = !deleting) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { deleteCandidate = null }, enabled = !deleting) { Text("Отмена") } },
            )
        }
    }
}

@Composable
private fun PublishedRecipeCard(recipe: PublishedRecipe) {
    var expanded by rememberSaveable(recipe.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("@${recipe.authorLogin}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("Рецепт из профиля", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(recipe.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (recipe.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(recipe.body, style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Свернуть рецепт" else "Показать рецепт")
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var reactionMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
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
                    if (post.authorId == currentUserId) {
                        Box {
                            IconButton(onClick = { actionMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Действия с публикацией")
                            }
                            DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Редактировать") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = { actionMenu = false; onEdit() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { actionMenu = false; onDelete() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedPostImage(dataSource: FeedDataSource, imagePath: String, height: Dp = 300.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, dataSource, imagePath) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                dataSource.imageFile(context, imagePath)?.let { FeedImageStorage.decodeSampled(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    FeedImageFrame(bitmap, height)
}

@Composable
private fun FeedImageFrame(bitmap: ImageBitmap?, height: Dp = 300.dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant),
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
    dataSource: FeedDataSource,
    editing: Boolean,
    existingImagePath: String?,
    pickedImage: String?,
    imageUrl: String,
    onImageUrlChange: (String) -> Unit,
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
    val previewUrl by produceState<String?>(null, imageUrl) {
        value = null
        val candidate = imageUrl.trim()
        if (FeedImageStorage.isValidExternalUrl(candidate)) {
            delay(500)
            value = candidate
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад") }
            Text(if (editing) "Редактировать рецепт" else "Новый рецепт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            if (editing) "Обновите фото, категорию или текст публикации" else "Добавьте фото и расскажите, как приготовить блюдо",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, bottom = 18.dp),
        )
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Фото блюда", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(16.dp)) {
                    if (pickedImage != null) FeedImageFrame(preview, 220.dp)
                    else if (previewUrl != null) FeedPostImage(dataSource, previewUrl!!, 220.dp)
                    else if (existingImagePath != null) FeedPostImage(dataSource, existingImagePath, 220.dp)
                    else FeedImageFrame(null, 220.dp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (editing || pickedImage != null) "Сменить фото" else "Выбрать фото")
                }
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = onImageUrlChange,
                    label = { Text("Или URL картинки") },
                    supportingText = { Text(if (editing) "Пустое поле сохранит текущее фото" else "Публичная HTTPS-ссылка на JPG, PNG или WebP") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("О рецепте", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
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
        OutlinedTextField(title, onTitleChange, label = { Text("Название") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(description, onDescriptionChange, label = { Text("Краткое описание") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(recipe, onRecipeChange, label = { Text("Ингредиенты и приготовление") }, minLines = 5, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Сохранение…" else if (editing) "Сохранить изменения" else "Опубликовать")
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
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
