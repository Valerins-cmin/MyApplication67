package com.example.myapplication67

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
