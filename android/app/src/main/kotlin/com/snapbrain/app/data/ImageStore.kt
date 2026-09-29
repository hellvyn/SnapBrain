package com.snapbrain.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException

/** Keeps our own copy of every screenshot, so Detail still works after the original is deleted. */
class ImageStore(private val context: Context) {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }

    fun save(uri: Uri, id: String): File {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = (resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("decode failed")
        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val file = File(dir, "$id.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    fun delete(path: String) {
        File(path).delete()
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}
