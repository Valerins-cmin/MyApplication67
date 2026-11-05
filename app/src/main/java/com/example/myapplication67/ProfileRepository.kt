package com.example.myapplication67

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

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
}

class RemoteProfileRepository(private val client: SupabaseClient) : ProfileDataSource {
    override suspend fun load(): ProfileSnapshot {
        val row = client.postgrest["User"].select().decodeList<JsonObject>().singleOrNull()
            ?: error("Профиль не найден. Попробуйте войти заново.")
        val id = row.string("id") ?: error("У профиля отсутствует ID")
        val visibility = client.postgrest["archive_visibility"].select {
            filter { eq("owner_id", id) }
        }.decodeList<JsonObject>().firstOrNull()
        val posts = client.postgrest["recipe_posts"].select {
            filter { eq("owner_id", id) }
        }.decodeList<JsonObject>().map { post ->
            RecipePost(
                id = post.string("id").orEmpty(),
                title = post.string("title").orEmpty(),
                body = post.string("body").orEmpty(),
                archived = post.boolean("archived"),
                createdAt = post.string("created_at").orEmpty(),
            )
        }.sortedByDescending { it.createdAt }

        return ProfileSnapshot(
            info = ProfileInfo(
                id = id,
                login = row.string("login").orEmpty(),
                email = row.string("gmail"),
                about = row.string("about").orEmpty(),
                phone = row.string("phone"),
                birthday = row.string("birthday"),
            ),
            archivePublic = visibility?.boolean("is_public") ?: false,
            posts = posts,
        )
    }

    override suspend fun saveInfo(info: ProfileInfo) {
        client.postgrest["User"].update({
            set("login", info.login)
            set("about", info.about)
            set("phone", info.phone)
            set("birthday", info.birthday)
        }) {
            filter { eq("id", info.id) }
        }
    }

    override suspend fun setArchivePublic(ownerId: String, enabled: Boolean) {
        client.postgrest["archive_visibility"].upsert(
            buildJsonObject {
                put("owner_id", ownerId)
                put("is_public", enabled)
            },
        ) { onConflict = "owner_id" }
    }

    override suspend fun addPost(ownerId: String, title: String, body: String) {
        client.postgrest["recipe_posts"].insert(buildJsonObject {
            put("owner_id", ownerId)
            put("title", title)
            put("body", body)
        })
    }

    override suspend fun setArchived(ownerId: String, postId: String, archived: Boolean) {
        client.postgrest["recipe_posts"].update({ set("archived", archived) }) {
            filter {
                eq("id", postId)
                eq("owner_id", ownerId)
            }
        }
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
            id = UUID.randomUUID().toString(),
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

    private fun readPosts(): List<RecipePost> = try {
        val array = JSONArray(preferences.getString("${prefix}posts", "[]"))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            RecipePost(
                id = row.getString("id"),
                title = row.getString("title"),
                body = row.optString("body"),
                archived = row.optBoolean("archived"),
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
                .put("created_at", post.createdAt))
        }
        preferences.edit().putString("${prefix}posts", array.toString()).apply()
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false
