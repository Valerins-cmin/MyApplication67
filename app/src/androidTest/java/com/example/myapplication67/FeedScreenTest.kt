package com.example.myapplication67

import android.content.Context
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun recipeExpandsAndCommentsOpen() {
        val source = object : FeedDataSource {
            override suspend fun loadPosts() = listOf(
                FeedPost(
                    id = "post-1",
                    authorId = "author-1",
                    authorLogin = "cook",
                    title = "Пирог",
                    description = "Короткое описание",
                    recipe = "Шаг 1: замесить тесто",
                    imagePath = "missing",
                    createdAt = "2026-11-05T00:00:00Z",
                ),
            )
            override suspend fun publish(context: Context, authorId: String, authorLogin: String, image: Uri, title: String, description: String, recipe: String) = Unit
            override suspend fun toggleReaction(postId: String, userId: String, emoji: String) = Unit
            override suspend fun loadComments(postId: String) = emptyList<FeedComment>()
            override suspend fun addComment(postId: String, authorId: String, authorLogin: String, body: String) = Unit
            override suspend fun imageFile(context: Context, imagePath: String): File? = null
        }
        composeRule.setContent {
            var category by remember { mutableStateOf(FeedCategory.MAIN) }
            MaterialTheme {
                FeedScreen(
                    selectedCategory = category,
                    onCategorySelected = { category = it },
                    dataSource = source,
                    currentUserId = "viewer",
                    currentLogin = "viewer",
                )
            }
        }

        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithText("Пирог").assertExists() }.isSuccess
        }
        composeRule.onNodeWithText("Рецепт").performScrollTo().performClick()
        composeRule.onNodeWithText("Шаг 1: замесить тесто").assertExists()
        composeRule.onNodeWithContentDescription("Комментарии").performScrollTo().performClick()
        composeRule.onNodeWithText("Чат публикации").assertExists()
    }
}
