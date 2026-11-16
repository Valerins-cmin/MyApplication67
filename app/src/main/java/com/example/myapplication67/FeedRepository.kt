package com.example.myapplication67

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

data class FeedReaction(val userId: String, val emoji: String)

internal fun newLocalId(): String =
    "${System.currentTimeMillis()}-${java.lang.Long.toUnsignedString(SecureRandom().nextLong(), 16)}"

sealed interface FeedImageInput {
    data class Photo(val uri: Uri) : FeedImageInput
    data class Link(val url: String) : FeedImageInput
}

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

data class PublishedRecipe(
    val id: String,
    val authorId: String,
    val authorLogin: String,
    val title: String,
    val body: String,
    val createdAt: String,
)

data class FeedComment(
    val id: String,
    val authorLogin: String,
    val body: String,
    val createdAt: String,
)

interface FeedDataSource {
    suspend fun loadPosts(): List<FeedPost>
    suspend fun loadPublishedProfileRecipes(): List<PublishedRecipe> = emptyList()
    suspend fun loadOwnPosts(authorId: String): List<FeedPost>
    suspend fun loadCategories(): List<RecipeCategory>
    suspend fun loadCategoryPosts(categoryId: Int): List<FeedPost>
    suspend fun searchPosts(query: String): List<FeedPost>
    suspend fun publish(context: Context, authorId: String, authorLogin: String, image: FeedImageInput, title: String, description: String, recipe: String, categoryId: Int)
    suspend fun updatePost(context: Context, post: FeedPost, authorLogin: String, image: FeedImageInput?, title: String, description: String, recipe: String, categoryId: Int)
    suspend fun deletePost(post: FeedPost)
    suspend fun toggleReaction(postId: String, userId: String, emoji: String)
    suspend fun loadComments(postId: String): List<FeedComment>
    suspend fun addComment(postId: String, authorId: String, authorLogin: String, body: String)
    suspend fun imageFile(context: Context, imagePath: String): File?
}

class RemoteFeedRepository(
    private val client: SupabaseClient,
    private val api: DemoApi? = null,
) : FeedDataSource {
    private val publicApi = api ?: DemoApi()

    override suspend fun loadPublishedProfileRecipes(): List<PublishedRecipe> {
        val rows = publicApi.request("published_recipes", authenticated = false).getJSONArray("recipes")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            PublishedRecipe(
                id = row.getLong("id").toString(),
                authorId = row.getLong("author_id").toString(),
                authorLogin = row.optString("author_login"),
                title = row.optString("title"),
                body = row.optString("body"),
                createdAt = row.optString("created_at"),
            )
        }
    }

    override suspend fun loadCategories(): List<RecipeCategory> =
        client.postgrest["categories"].select().decodeList<JsonObject>()
            .mapNotNull { row ->
                val id = row.field("id")?.toIntOrNull() ?: return@mapNotNull null
                RecipeCategory(id, row.field("name").orEmpty())
            }.sortedBy { it.id }

    override suspend fun loadPosts(): List<FeedPost> {
        val posts = client.postgrest["posts"].select().decodeList<JsonObject>()
        val reactions = client.postgrest["reactions"].select().decodeList<JsonObject>()
            .groupBy { it.field("post_id") }
        return posts.map { row ->
            row.toFeedPost(reactions[row.field("id")].orEmpty().map {
                FeedReaction(it.field("user_id").orEmpty(), it.field("emoji").orEmpty())
            })
        }.sortedByDescending { it.createdAt }.take(100)
    }

    override suspend fun loadOwnPosts(authorId: String): List<FeedPost> =
        client.postgrest["posts"].select {
            filter { eq("author_id", authorId) }
        }.decodeList<JsonObject>()
            .map { it.toFeedPost() }
            .sortedByDescending { it.createdAt }

    override suspend fun loadCategoryPosts(categoryId: Int): List<FeedPost> =
        client.postgrest["posts"].select {
            filter { eq("category_id", categoryId) }
        }.decodeList<JsonObject>()
            .map { it.toFeedPost() }

    override suspend fun searchPosts(query: String): List<FeedPost> {
        val literal = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$literal%"
        return client.postgrest["posts"].select {
            filter {
                or {
                    ilike("title", pattern)
                    ilike("description", pattern)
                    ilike("recipe", pattern)
                    ilike("author_login", pattern)
                }
            }
        }.decodeList<JsonObject>()
            .map { it.toFeedPost() }
            .sortedByDescending { it.createdAt }
            .take(30)
    }

    override suspend fun publish(
        context: Context,
        authorId: String,
        authorLogin: String,
        image: FeedImageInput,
        title: String,
        description: String,
        recipe: String,
        categoryId: Int,
    ) {
        val path = uploadImage(context, image)
        try {
            requireNotNull(api) { "Войдите в аккаунт" }.request("post_create", JSONObject()
                .put("title", title).put("description", description).put("recipe", recipe)
                .put("category_id", categoryId).put("image_path", path))
        } catch (error: Exception) {
            removeOwnedImage(path)
            throw error
        }
    }

    override suspend fun updatePost(
        context: Context, post: FeedPost, authorLogin: String, image: FeedImageInput?, title: String,
        description: String, recipe: String, categoryId: Int,
    ) {
        val path = image?.let { uploadImage(context, it) } ?: post.imagePath
        try {
            requireNotNull(api) { "Войдите в аккаунт" }.request("post_update", JSONObject()
                .put("id", post.id.toLong()).put("title", title).put("description", description)
                .put("recipe", recipe).put("category_id", categoryId).put("image_path", path))
        } catch (error: Exception) {
            if (path != post.imagePath) removeOwnedImage(path)
            throw error
        }
    }

    override suspend fun deletePost(post: FeedPost) {
        requireNotNull(api) { "Войдите в аккаунт" }.request("post_delete", JSONObject().put("id", post.id.toLong()))
    }

    private suspend fun removeOwnedImage(path: String) {
        if (api != null && path.startsWith("${api.currentAccount?.id}/")) {
            runCatching { api?.request("image_delete", JSONObject().put("path", path)) }
        }
    }

    private suspend fun uploadImage(context: Context, image: FeedImageInput): String = when (image) {
            is FeedImageInput.Link -> {
                val url = image.url.trim()
                require(FeedImageStorage.isValidExternalUrl(url)) { "Введите корректную HTTPS-ссылку на изображение" }
                require(FeedImageStorage.remoteImageFile(context, url) != null) { "Не удалось открыть изображение по ссылке" }
                url
            }
            is FeedImageInput.Photo -> {
                val remote = requireNotNull(api) { "Войдите в аккаунт для загрузки фото" }
                val file = FeedImageStorage.prepare(context, image.uri)
                try {
                    val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                    remote.uploadImage("image_upload", bytes).getString("path")
                } finally {
                    file.delete()
                }
            }
        }

    override suspend fun toggleReaction(postId: String, userId: String, emoji: String) {
        requireNotNull(api) { "Войдите в аккаунт" }.request("reaction_toggle", JSONObject()
            .put("post_id", postId.toLong()).put("emoji", emoji))
    }

    override suspend fun loadComments(postId: String): List<FeedComment> =
        client.postgrest["comments"].select {
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
        requireNotNull(api) { "Войдите в аккаунт" }.request("comment_add", JSONObject()
            .put("post_id", postId.toLong()).put("body", body))
    }

    override suspend fun imageFile(context: Context, imagePath: String): File? =
        FeedImageStorage.remoteImageFile(
            context,
            if (imagePath.startsWith("https://", ignoreCase = true)) imagePath
            else client.storage["feed-images"].publicUrl(imagePath),
        )
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

    override suspend fun loadOwnPosts(authorId: String): List<FeedPost> =
        loadPosts().filter { it.authorId == authorId }

    override suspend fun loadCategoryPosts(categoryId: Int): List<FeedPost> =
        loadPosts().filter { it.categoryId == categoryId }

    override suspend fun searchPosts(query: String): List<FeedPost> {
        val term = query.trim()
        return loadPosts().filter { post ->
            listOf(post.title, post.description, post.recipe, post.authorLogin)
                .any { it.contains(term, ignoreCase = true) }
        }.take(30)
    }

    override suspend fun publish(
        context: Context,
        authorId: String,
        authorLogin: String,
        image: FeedImageInput,
        title: String,
        description: String,
        recipe: String,
        categoryId: Int,
    ) {
        val imagePath = when (image) {
            is FeedImageInput.Link -> {
                val url = image.url.trim()
                require(FeedImageStorage.isValidExternalUrl(url)) { "Введите корректную HTTPS-ссылку на изображение" }
                require(FeedImageStorage.remoteImageFile(context, url) != null) { "Не удалось открыть изображение по ссылке" }
                url
            }
            is FeedImageInput.Photo -> {
                val prepared = FeedImageStorage.prepare(context, image.uri)
                val destination = File(imageDirectory, "${newLocalId()}.jpg")
                try {
                    withContext(Dispatchers.IO) { prepared.copyTo(destination) }
                } finally {
                    prepared.delete()
                }
                destination.absolutePath
            }
        }
        val posts = readArray("posts")
        posts.put(JSONObject()
            .put("id", newLocalId())
            .put("author_id", authorId)
            .put("author_login", authorLogin)
            .put("title", title)
            .put("description", description)
            .put("recipe", recipe)
            .put("category_id", categoryId)
            .put("image_path", imagePath)
            .put("created_at", Instant.now().toString()))
        writeArray("posts", posts)
    }

    override suspend fun updatePost(
        context: Context, post: FeedPost, authorLogin: String, image: FeedImageInput?, title: String,
        description: String, recipe: String, categoryId: Int,
    ) {
        val imagePath = when (image) {
            null -> post.imagePath
            is FeedImageInput.Link -> {
                require(FeedImageStorage.isValidExternalUrl(image.url)) { "Введите корректную HTTPS-ссылку на изображение" }
                require(FeedImageStorage.remoteImageFile(context, image.url) != null) { "Не удалось открыть изображение по ссылке" }
                image.url.trim()
            }
            is FeedImageInput.Photo -> {
                val prepared = FeedImageStorage.prepare(context, image.uri)
                val destination = File(imageDirectory, "${newLocalId()}.jpg")
                try { withContext(Dispatchers.IO) { prepared.copyTo(destination) } }
                finally { prepared.delete() }
                destination.absolutePath
            }
        }
        val updated = JSONArray()
        readArray("posts").objects().forEach { row ->
            if (row.optString("id") == post.id && row.optString("author_id") == post.authorId) {
                row.put("author_login", authorLogin).put("title", title).put("description", description).put("recipe", recipe)
                    .put("category_id", categoryId).put("image_path", imagePath)
            }
            updated.put(row)
        }
        writeArray("posts", updated)
        if (imagePath != post.imagePath && !post.imagePath.startsWith("https://")) File(post.imagePath).delete()
    }

    override suspend fun deletePost(post: FeedPost) {
        val updated = JSONArray()
        readArray("posts").objects().filterNot {
            it.optString("id") == post.id && it.optString("author_id") == post.authorId
        }.forEach(updated::put)
        writeArray("posts", updated)
        listOf("comments", "reactions").forEach { key ->
            val remaining = JSONArray()
            readArray(key).objects().filterNot { it.optString("post_id") == post.id }.forEach(remaining::put)
            writeArray(key, remaining)
        }
        if (!post.imagePath.startsWith("https://")) File(post.imagePath).delete()
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
            .put("id", newLocalId())
            .put("post_id", postId)
            .put("author_id", authorId)
            .put("author_login", authorLogin)
            .put("body", body)
            .put("created_at", Instant.now().toString()))
        writeArray("comments", comments)
    }

    override suspend fun imageFile(context: Context, imagePath: String): File? =
        if (imagePath.startsWith("https://", ignoreCase = true)) FeedImageStorage.remoteImageFile(context, imagePath)
        else File(imagePath).takeIf { it.exists() }

    internal fun previewReactions(): Map<String, List<FeedReaction>> =
        readArray("reactions").objects().groupBy { it.optString("post_id") }
            .mapValues { (_, rows) -> rows.map { FeedReaction(it.optString("user_id"), it.optString("emoji")) } }

    internal fun previewCommentCounts(): Map<String, Int> =
        readArray("comments").objects().groupingBy { it.optString("post_id") }.eachCount()

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrDefault(JSONArray())

    private fun writeArray(key: String, value: JSONArray) {
        preferences.edit().putString(key, value.toString()).apply()
    }
}

/** Guests can read public recipes while their test posts and interactions stay on this device. */
class PreviewFeedRepository(
    private val remote: FeedDataSource,
    private val local: LocalFeedRepository,
) : FeedDataSource {
    override suspend fun loadPublishedProfileRecipes(): List<PublishedRecipe> =
        remote.loadPublishedProfileRecipes()

    override suspend fun loadPosts(): List<FeedPost> {
        val localPosts = local.loadPosts()
        val sharedPosts = try {
            remote.loadPosts()
        } catch (error: Exception) {
            if (localPosts.isEmpty()) throw error
            emptyList()
        }
        val reactions = local.previewReactions()
        val commentCounts = local.previewCommentCounts()
        return (sharedPosts.map { post ->
            post.copy(
                reactions = post.reactions + reactions[post.id].orEmpty(),
                commentCount = post.commentCount + (commentCounts[post.id] ?: 0),
            )
        } + localPosts).distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

    override suspend fun loadOwnPosts(authorId: String): List<FeedPost> = local.loadOwnPosts(authorId)

    override suspend fun loadCategories(): List<RecipeCategory> =
        runCatching { remote.loadCategories() }.getOrElse { local.loadCategories() }

    override suspend fun loadCategoryPosts(categoryId: Int): List<FeedPost> =
        loadPosts().filter { it.categoryId == categoryId }

    override suspend fun searchPosts(query: String): List<FeedPost> {
        val term = query.trim()
        return loadPosts().filter { post ->
            listOf(post.title, post.description, post.recipe, post.authorLogin)
                .any { it.contains(term, ignoreCase = true) }
        }.take(30)
    }

    override suspend fun publish(
        context: Context, authorId: String, authorLogin: String, image: FeedImageInput,
        title: String, description: String, recipe: String, categoryId: Int,
    ) = local.publish(context, authorId, authorLogin, image, title, description, recipe, categoryId)

    override suspend fun updatePost(
        context: Context, post: FeedPost, authorLogin: String, image: FeedImageInput?, title: String,
        description: String, recipe: String, categoryId: Int,
    ) = local.updatePost(context, post, authorLogin, image, title, description, recipe, categoryId)

    override suspend fun deletePost(post: FeedPost) = local.deletePost(post)

    override suspend fun toggleReaction(postId: String, userId: String, emoji: String) =
        local.toggleReaction(postId, userId, emoji)

    override suspend fun loadComments(postId: String): List<FeedComment> {
        val ownComments = local.loadComments(postId)
        val sharedComments = try {
            remote.loadComments(postId)
        } catch (error: Exception) {
            if (ownComments.isEmpty()) throw error
            emptyList()
        }
        return (sharedComments + ownComments).sortedBy { it.createdAt }
    }

    override suspend fun addComment(postId: String, authorId: String, authorLogin: String, body: String) =
        local.addComment(postId, authorId, authorLogin, body)

    override suspend fun imageFile(context: Context, imagePath: String): File? =
        local.imageFile(context, imagePath) ?: remote.imageFile(context, imagePath)
}

private fun JsonObject.field(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.toFeedPost(reactions: List<FeedReaction> = emptyList()): FeedPost = FeedPost(
    id = field("id").orEmpty(),
    authorId = field("author_id").orEmpty(),
    authorLogin = field("author_login").orEmpty(),
    title = field("title").orEmpty(),
    description = field("description").orEmpty(),
    recipe = field("recipe").orEmpty(),
    imagePath = field("image_path").orEmpty(),
    createdAt = field("created_at").orEmpty(),
    categoryId = field("category_id")?.toIntOrNull(),
    commentCount = field("comment_count")?.toIntOrNull() ?: 0,
    reactions = reactions,
)

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

object FeedImageStorage {
    private const val maxRemoteBytes = 8_388_608L

    fun isValidExternalUrl(value: String): Boolean {
        if (value.length !in 12..2048 || value.any(Char::isWhitespace)) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    suspend fun remoteImageFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        if (!isValidExternalUrl(url)) return@withContext null
        val directory = File(context.cacheDir, "feed_images").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(directory, "$name.img")
        if (target.exists() && target.length() > 0) return@withContext target

        var currentUrl = url
        for (redirect in 0..3) {
            if (!isValidExternalUrl(currentUrl)) return@withContext null
            val host = URI(currentUrl).host ?: return@withContext null
            if (!isPublicHost(host)) return@withContext null
            val connection = try {
                URL(currentUrl).openConnection() as HttpURLConnection
            } catch (_: Exception) {
                return@withContext null
            }
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            val temporary = File(directory, "$name-${newLocalId()}.tmp")
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return@withContext null
                    currentUrl = URL(connection.url, location).toString()
                    continue
                }
                if (status !in 200..299 || connection.contentLengthLong > maxRemoteBytes) return@withContext null
                var total = 0L
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxRemoteBytes) return@withContext null
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val decoded = decodeSampled(temporary) ?: return@withContext null
                decoded.recycle()
                if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
                return@withContext target
            } catch (_: Exception) {
                return@withContext null
            } finally {
                temporary.delete()
                connection.disconnect()
            }
        }
        null
    }

    fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1600) sampleSize *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun isPublicHost(host: String): Boolean {
        val normalized = host.lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized.endsWith(".local")) return false
        return runCatching {
            InetAddress.getAllByName(host).all { address ->
                !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress && !address.isSiteLocalAddress && !address.isMulticastAddress &&
                    !(address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)
            }
        }.getOrDefault(false)
    }

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
        val file = File(context.cacheDir, "feed_upload_${newLocalId()}.jpg")
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
