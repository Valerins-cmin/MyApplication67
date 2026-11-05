package com.example.myapplication67

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Feed
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class FeedCategory(val title: String) {
    MAIN("Главная"),
    SUBSCRIPTIONS("Подписки"),
    POPULAR("Популярное"),
    NEW("Новое"),
}

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    selectedCategory: FeedCategory,
    onCategorySelected: (FeedCategory) -> Unit,
    followedLogins: List<String> = emptyList(),
) {
    Column(modifier) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (selectedCategory) {
                FeedCategory.MAIN -> {
                    item { FeedHeading("Для вас", "Интересные рецепты и публикации сообщества") }
                    item {
                        FeedEmptyCard(
                            icon = Icons.AutoMirrored.Outlined.Feed,
                            title = "Лента пока пуста",
                            description = "Здесь появятся публикации от разных авторов.",
                        )
                    }
                }
                FeedCategory.SUBSCRIPTIONS -> {
                    item { FeedHeading("Ваши подписки", "Авторы, за которыми вы следите") }
                    item {
                        if (followedLogins.isEmpty()) {
                            FeedEmptyCard(
                                icon = Icons.Outlined.People,
                                title = "Подписок пока нет",
                                description = "Когда вы подпишетесь на авторов, они появятся здесь.",
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(followedLogins) { login -> FollowedAuthor(login) }
                            }
                        }
                    }
                    item { FeedHeading("Публикации подписок", "Новые посты ваших авторов") }
                    item {
                        FeedEmptyCard(
                            icon = Icons.AutoMirrored.Outlined.Feed,
                            title = "Публикаций пока нет",
                            description = "Посты авторов, на которых вы подписаны, будут ниже списка подписок.",
                        )
                    }
                }
                FeedCategory.POPULAR -> {
                    item { FeedHeading("Популярное", "Рецепты, которые понравились сообществу") }
                    item {
                        FeedEmptyCard(
                            icon = Icons.AutoMirrored.Outlined.Feed,
                            title = "Пока нет популярных публикаций",
                            description = "Они появятся, когда лента наполнится рецептами.",
                        )
                    }
                }
                FeedCategory.NEW -> {
                    item { FeedHeading("Новое", "Свежие публикации авторов") }
                    item {
                        FeedEmptyCard(
                            icon = Icons.AutoMirrored.Outlined.Feed,
                            title = "Новых публикаций пока нет",
                            description = "Здесь будут появляться последние рецепты.",
                        )
                    }
                }
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
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FollowedAuthor(login: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Box(
            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(login.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text("@$login", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
