package com.example.myapplication67

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication67.ui.theme.LocalAppBackgroundBrush
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray

private val featuredIds = listOf(1, 2, 3, 15)
private val fallbackCategories = listOf(
    RecipeCategory(1, "Завтраки"), RecipeCategory(2, "Обеды"),
    RecipeCategory(3, "Ужины"), RecipeCategory(15, "Выпечка"),
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    login: String,
    profileKey: String,
    avatarFile: File,
    avatarVersion: Int,
    onProfileClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    dataSource: FeedDataSource,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("home_search_history", Context.MODE_PRIVATE) }
    val historyKey = "$profileKey.queries"
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val background = LocalAppBackgroundBrush.current
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    val textColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    var expanded by rememberSaveable(profileKey) { mutableStateOf(false) }
    var submittedQuery by rememberSaveable(profileKey) { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable(profileKey) { mutableIntStateOf(1) }
    var recent by remember(profileKey) { mutableStateOf(readRecentQueries(preferences.getString(historyKey, null))) }
    var categories by remember(dataSource) { mutableStateOf<List<RecipeCategory>>(emptyList()) }
    var suggestionsSource by remember(dataSource) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var recommendations by remember(dataSource) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var recommendationLoading by remember(dataSource) { mutableStateOf(true) }
    var recommendationError by remember(dataSource) { mutableStateOf(false) }
    var recommendationsCategoryId by remember(dataSource) { mutableStateOf<Int?>(null) }
    var recommendationRefresh by remember { mutableIntStateOf(0) }
    var liveRefresh by remember { mutableIntStateOf(0) }
    var results by remember(dataSource) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var searchLoading by remember(dataSource) { mutableStateOf(false) }
    var searchError by remember(dataSource) { mutableStateOf(false) }
    var searchRefresh by remember { mutableIntStateOf(0) }
    var openedPost by remember { mutableStateOf<FeedPost?>(null) }
    val hour by produceState(initialValue = LocalTime.now().hour) {
        while (true) {
            delay(60_000)
            value = LocalTime.now().hour
        }
    }
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..16 -> "Добрый день"
        in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    LaunchedEffect(dataSource) {
        while (true) {
            runCatching { dataSource.loadCategories() }.onSuccess { categories = it }
            runCatching { dataSource.loadPosts() }.onSuccess { suggestionsSource = it }
            liveRefresh++
            delay(60_000)
        }
    }
    LaunchedEffect(dataSource, profileKey, selectedCategoryId, recommendationRefresh, liveRefresh) {
        val requestedCategoryId = selectedCategoryId
        recommendationLoading = recommendationsCategoryId != requestedCategoryId
        recommendationError = false
        try {
            val posts = dataSource.loadCategoryPosts(requestedCategoryId)
            val others = posts.filter { it.authorId != profileKey }
            recommendations = (others.ifEmpty { posts }).shuffled().take(8)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recommendations = emptyList()
            recommendationError = true
        }
        recommendationsCategoryId = requestedCategoryId
        recommendationLoading = false
    }
    LaunchedEffect(dataSource, submittedQuery, categories, searchRefresh, liveRefresh) {
        val term = submittedQuery ?: return@LaunchedEffect
        searchLoading = results.isEmpty()
        val category = categories.firstOrNull { it.name.equals(term, ignoreCase = true) }
        runCatching { if (category == null) dataSource.searchPosts(term) else dataSource.loadCategoryPosts(category.id) }
            .onSuccess { results = it; searchError = false }
            .onFailure { searchError = results.isEmpty() }
        searchLoading = false
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(320)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun submitSearch(value: String) {
        val term = value.trim()
        if (term.isEmpty()) return
        onSearchQueryChange(term)
        submittedQuery = term
        recent = (listOf(term) + recent.filterNot { it.equals(term, ignoreCase = true) }).take(8)
        preferences.edit().putString(historyKey, JSONArray(recent).toString()).apply()
        keyboard?.hide()
        focusManager.clearFocus()
    }

    val shownCategories = (categories.ifEmpty { fallbackCategories }).sortedWith(
        compareBy<RecipeCategory> { featuredIds.indexOf(it.id).let { index -> if (index < 0) 100 + it.id else index } },
    )
    val selectedName = shownCategories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Рецепты"
    val suggestions = if (searchQuery.isBlank()) recent else {
        (recent + categories.map { it.name } + suggestionsSource.map { it.title })
            .filter { it.contains(searchQuery.trim(), ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(8)
    }

    Column(
        modifier = modifier.background(background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$greeting, $login", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text("Что готовим сегодня?", color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onProfileClick) {
                    AvatarBadge(avatarFile = avatarFile, avatarVersion = avatarVersion, size = 40.dp)
                }
            }
        }
        Spacer(Modifier.height(if (expanded) 0.dp else 14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width by animateDpAsState(
                targetValue = if (expanded) maxWidth else 52.dp,
                animationSpec = tween(300),
                label = "search width",
            )
            Surface(Modifier.width(width).height(48.dp), shape = RoundedCornerShape(13.dp), color = cardColor) {
                if (!expanded || width < 160.dp) {
                    Box(Modifier.fillMaxSize().clickable { expanded = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Search, contentDescription = "Открыть поиск", tint = mutedColor)
                    }
                } else {
                    Row(Modifier.fillMaxSize().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { submitSearch(searchQuery) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Search, contentDescription = "Найти", tint = mutedColor, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { onSearchQueryChange(it); submittedQuery = null },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = TextStyle(color = textColor, fontSize = 14.sp),
                            cursorBrush = SolidColor(textColor),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch(searchQuery) }),
                            decorationBox = { field ->
                                Box {
                                    if (searchQuery.isEmpty()) Text("Найти рецепт или ингредиент…", color = mutedColor, fontSize = 13.sp)
                                    field()
                                }
                            },
                        )
                        IconButton(onClick = {
                            expanded = false
                            submittedQuery = null
                            onSearchQueryChange("")
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Закрыть поиск", tint = mutedColor) }
                    }
                }
            }
        }

        AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Spacer(Modifier.height(28.dp))
                Text("Категории", color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tileWidth = (maxWidth - 24.dp) / 4
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shownCategories, key = { it.id }) { category ->
                            val selected = category.id == selectedCategoryId
                            Card(
                                onClick = { selectedCategoryId = category.id },
                                modifier = Modifier.width(tileWidth).height(76.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = if (selected) CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) else CardDefaults.cardColors(),
                            ) {
                                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) {
                                    Text(category.name,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Рецепты: $selectedName", color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                when {
                    recommendationLoading || recommendationsCategoryId != selectedCategoryId ->
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    recommendationError -> OutlinedButton(onClick = { recommendationRefresh++ }) { Text("Повторить загрузку рецептов") }
                    recommendations.isEmpty() -> HomeEmptyCard("В этой категории пока нет рецептов", cardColor, textColor)
                    else -> recommendations.forEach { post ->
                        HomeRecipeCard(post, dataSource, cardColor, textColor, mutedColor) { openedPost = post }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Spacer(Modifier.height(18.dp))
                if (submittedQuery == null) {
                    if (suggestions.isEmpty()) {
                        Text("Недавних запросов пока нет", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                    } else suggestions.forEach { suggestion ->
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable { submitSearch(suggestion) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                            Text(suggestion, modifier = Modifier.weight(1f).padding(start = 10.dp),
                                color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (suggestion in recent) IconButton(onClick = {
                                recent = recent.filterNot { it == suggestion }
                                preferences.edit().putString(historyKey, JSONArray(recent).toString()).apply()
                            }) { Icon(Icons.Outlined.Close, contentDescription = "Удалить запрос", tint = textColor, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider(color = textColor.copy(alpha = 0.16f))
                    }
                } else {
                    Text("Результаты: $submittedQuery", color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    when {
                        searchLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        searchError -> OutlinedButton(onClick = { searchRefresh++ }) { Text("Повторить поиск") }
                        results.isEmpty() -> HomeEmptyCard("По запросу ничего не найдено", cardColor, textColor)
                        else -> results.forEach { post ->
                            HomeRecipeCard(post, dataSource, cardColor, textColor, mutedColor) { openedPost = post }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
    openedPost?.let { post ->
        FeedRecipeDetailDialog(
            post = post, dataSource = dataSource,
            categoryName = categories.firstOrNull { it.id == post.categoryId }?.name,
            onDismiss = { openedPost = null },
        )
    }
}

@Composable
private fun HomeRecipeCard(post: FeedPost, dataSource: FeedDataSource, cardColor: Color,
                           textColor: Color, mutedColor: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Column {
            FeedPostImage(dataSource, post.imagePath, height = 158.dp)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(post.title, color = textColor, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.description, color = mutedColor, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@${post.authorLogin}", color = mutedColor, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = mutedColor, modifier = Modifier.size(15.dp))
                    Text(post.commentCount.toString(), color = mutedColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyCard(message: String, cardColor: Color, textColor: Color) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text(message, color = textColor, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
internal fun FeedRecipeDetailDialog(post: FeedPost, dataSource: FeedDataSource,
                                    categoryName: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(post.title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                FeedPostImage(dataSource, post.imagePath, height = 180.dp)
                Spacer(Modifier.height(12.dp))
                categoryName?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Text(post.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Рецепт", style = MaterialTheme.typography.titleMedium)
                Text(post.recipe, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Комментарии: ${post.commentCount}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun readRecentQueries(raw: String?): List<String> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}.getOrDefault(emptyList())
