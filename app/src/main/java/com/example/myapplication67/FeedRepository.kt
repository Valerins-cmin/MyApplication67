package com.example.myapplication67

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

data class FeedReaction(val userId: String, val emoji: String)

data class RecipeCategory(val id: Int, val name: String)

private val localRecipeCategories = listOf(
    "Завтраки", "Обеды", "Ужины", "Супы", "Салаты", "Закуски",
    "Основные блюда", "Гарниры", "Блюда из мяса", "Блюда из рыбы",
    "Вегетарианское", "Десерты", "Напитки", "Соусы", "Выпечка",
).mapIndexed { index, name -> RecipeCategory(index + 1, name) }

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorLogin: String,
    val title: String,
    val description: String,
    val recipe: String,
    val imagePath: String,
    val createdAt: String,
    val categoryId: Int? = null,
    val commentCount: Int = 0,
    val reactions: List<FeedReaction> = emptyList(),
)

data class FeedComment(
    val id: String,
    val authorLogin: String,
    val body: String,
    val createdAt: String,
)

interface FeedDataSource {
    suspend fun loadPosts(): List<FeedPost>
    suspend fun loadCategories(): List<RecipeCategory>
    suspend fun publish(context: Context, authorId: String, authorLogin: String, image: Uri, title: String, description: String, recipe: String, categoryId: Int)
    suspend fun toggleReaction(postId: String, userId: String, emoji: String)
    suspend fun loadComments(postId: String): List<FeedComment>
    suspend fun addComment(postId: String, authorId: String, authorLogin: String, body: String)
    suspend fun imageFile(context: Context, imagePath: String): File?
}

class RemoteFeedRepository(private val client: SupabaseClient) : FeedDataSource {
    override suspend fun loadCategories(): List<RecipeCategory> =
        client.postgrest["feed_categories"].select().decodeList<JsonObject>()
            .mapNotNull { row ->
                val id = row.field("id")?.toIntOrNull() ?: return@mapNotNull null
                RecipeCategory(id, row.field("name").orEmpty())
            }.sortedBy { it.id }

    override suspend fun loadPosts(): List<FeedPost> {
        val posts = client.postgrest["feed_posts"].select().decodeList<JsonObject>()
        val reactions = client.postgrest["feed_reactions"].select().decodeList<JsonObject>()
            .groupBy { it.field("post_id") }
        return posts.map { row ->
            val id = row.field("id").orEmpty()
            FeedPost(
                id = id,
                authorId = row.field("author_id").orEmpty(),
                authorLogin = row.field("author_login").orEmpty(),
                title = row.field("title").orEmpty(),
                description = row.field("description").orEmpty(),
                recipe = row.field("recipe").orEmpty(),
                imagePath = row.field("image_path").orEmpty(),
                createdAt = row.field("created_at").orEmpty(),
                categoryId = row.field("category_id")?.toIntOrNull(),
                commentCount = row.field("comment_count")?.toIntOrNull() ?: 0,
                reactions = reactions[id].orEmpty().map {
                    FeedReaction(it.field("user_id").orEmpty(), it.field("emoji").orEmpty())
                },
            )
        }.sortedByDescending { it.createdAt }.take(100)
    }

    override suspend fun publish(
        context: Context,
        authorId: String,
        authorLogin: String,
        image: Uri,
        title: String,
        description: String,
        recipe: String,
        categoryId: Int,
    ) {
        val file = FeedImageStorage.prepare(context, image)
        try {
            val path = "$authorId/${UUID.randomUUID()}.jpg"
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            client.storage["feed-images"].upload(path, bytes) {
                contentType = ContentType.Image.JPEG
            }
            client.postgrest["feed_posts"].insert(buildJsonObject {
                put("author_id", authorId)
                put("author_login", authorLogin)
                put("title", title)
                put("description", description)
                put("recipe", recipe)
                put("category_id", categoryId)
                put("image_path", path)
            })
        } finally {
            file.delete()
        }
    }

    override suspend fun toggleReaction(postId: String, userId: String, emoji: String) {
        val existing = client.postgrest["feed_reactions"].select {
            filter {
                eq("post_id", postId)
                eq("user_id", userId)
            }
        }.decodeList<JsonObject>().firstOrNull()?.field("emoji")
        if (existing == emoji) {
            client.postgrest["feed_reactions"].delete {
                filter {
                    eq("post_id", postId)
                    eq("user_id", userId)
                }
            }
        } else {
            client.postgrest["feed_reactions"].upsert(buildJsonObject {
                put("post_id", postId)
                put("user_id", userId)
                put("emoji", emoji)
            }) { onConflict = "post_id,user_id" }
        }
    }

    override suspend fun loadComments(postId: String): List<FeedComment> =
        client.postgrest["feed_comments"].select {
            filter { eq("post_id", postId) }
        }.decodeList<JsonObject>().map { row ->
            FeedComment(
                id = row.field("id").orEmpty(),
                authorLogin = row.field("author_login").orEmpty(),
                body = row.field("body").orEmpty(),
                createdAt = row.field("created_at").orEmpty(),
            )
        }.sortedBy { it.createdAt }

    override suspend fun addComment(postId: String, authorId: String, authorLogin: String, body: String) {
        client.postgrest["feed_comments"].insert(buildJsonObject {
            put("post_id", postId)
            put("author_id", authorId)
            put("author_login", authorLogin)
            put("body", body)
        })
    }

    override suspend fun imageFile(context: Context, imagePath: String): File? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "feed_images").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256")
            .digest(imagePath.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(directory, "$name.jpg")
        if (target.exists() && target.length() > 0) return@withContext target
        val url = client.storage["feed-images"].publicUrl(imagePath)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val temporary = File(directory, "$name.tmp")
            connection.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target.takeIf { it.exists() && it.length() > 0 }
        } finally {
            connection.disconnect()
        }
    }
}

class LocalFeedRepository(context: Context, profileKey: String) : FeedDataSource {
    private val preferences = context.getSharedPreferences("local_feed_$profileKey", Context.MODE_PRIVATE)
    private val imageDirectory = File(context.filesDir, "local_feed_images").apply { mkdirs() }

    override suspend fun loadCategories(): List<RecipeCategory> = localRecipeCategories

    override suspend fun loadPosts(): List<FeedPost> {
        val reactions = readArray("reactions").objects().groupBy { it.optString("post_id") }
        val commentCounts = readArray("comments").objects().groupingBy { it.optString("post_id") }.eachCount()
        return readArray("posts").objects().map { row ->
            val id = row.optString("id")
            FeedPost(
                id = id,
                authorId = row.optString("author_id"),
                authorLogin = row.optString("author_login"),
                title = row.optString("title"),
                description = row.optString("description"),
                recipe = row.optString("recipe"),
                imagePath = row.optString("image_path"),
                createdAt = row.optString("created_at"),
                categoryId = row.optInt("category_id").takeIf { it > 0 },
                commentCount = commentCounts[id] ?: 0,
                reactions = reactions[id].orEmpty().map {
                    FeedReaction(it.optString("user_id"), it.optString("emoji"))
                },
            )
        }.sortedByDescending { it.createdAt }
    }

    override suspend fun publish(
        context: Context,
        authorId: String,
        authorLogin: String,
        image: Uri,
        title: String,
        description: String,
        recipe: String,
        categoryId: Int,
    ) {
        val prepared = FeedImageStorage.prepare(context, image)
        val destination = File(imageDirectory, "${UUID.randomUUID()}.jpg")
        try {
            withContext(Dispatchers.IO) { prepared.copyTo(destination) }
        } finally {
            prepared.delete()
        }
        val posts = readArray("posts")
        posts.put(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("author_id", authorId)
            .put("author_login", authorLogin)
            .put("title", title)
            .put("description", description)
            .put("recipe", recipe)
            .put("category_id", categoryId)
            .put("image_path", destination.absolutePath)
            .put("created_at", Instant.now().toString()))
        writeArray("posts", posts)
    }

    override suspend fun toggleReaction(postId: String, userId: String, emoji: String) {
        val current = readArray("reactions").objects()
        val existing = current.firstOrNull { it.optString("post_id") == postId && it.optString("user_id") == userId }
        val next = JSONArray()
        current.filterNot { it === existing }.forEach(next::put)
        if (existing?.optString("emoji") != emoji) {
            next.put(JSONObject().put("post_id", postId).put("user_id", userId).put("emoji", emoji))
        }
        writeArray("reactions", next)
    }

    override suspend fun loadComments(postId: String): List<FeedComment> =
        readArray("comments").objects().filter { it.optString("post_id") == postId }.map { row ->
            FeedComment(
                id = row.optString("id"),
                authorLogin = row.optString("author_login"),
                body = row.optString("body"),
                createdAt = row.optString("created_at"),
            )
        }.sortedBy { it.createdAt }

    override suspend fun addComment(postId: String, authorId: String, authorLogin: String, body: String) {
        val comments = readArray("comments")
        comments.put(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("post_id", postId)
            .put("author_id", authorId)
            .put("author_login", authorLogin)
            .put("body", body)
            .put("created_at", Instant.now().toString()))
        writeArray("comments", comments)
    }

    override suspend fun imageFile(context: Context, imagePath: String): File? =
        File(imagePath).takeIf { it.exists() }

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrDefault(JSONArray())

    private fun writeArray(key: String, value: JSONArray) {
        preferences.edit().putString(key, value.toString()).apply()
    }
}

private fun JsonObject.field(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

object FeedImageStorage {
    suspend fun prepare(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val decoded = decode(context, uri)
        val width = decoded.width
        val height = decoded.height
        val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        decoded.recycle()
        val file = File(context.cacheDir, "feed_upload_${UUID.randomUUID()}.jpg")
        file.outputStream().use { flattened.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        flattened.recycle()
        if (file.length() !in 1..5_242_880) {
            file.delete()
            error("Изображение слишком большое")
        }
        file
    }

    suspend fun preview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri) }.getOrNull()
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0) { "Неверный формат изображения" }
            val scale = minOf(1f, 1600f / maxOf(width, height))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}
