/*
 * Images.kt
 * Shows campsite and profile pictures and prepares photos for upload. A
 * picture is either a web link (loaded with Coil) or an uploaded image
 * stored in Firestore and referenced as "img:{id}".
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.example.code.R
import com.example.code.data.FirestoreImageRepository
import com.example.code.model.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Decoded uploaded images, so scrolling a list does not reload them. */
private val imageCache = LruCache<String, ImageBitmap>(40)

/**
 * Shows the picture at [ref]: an uploaded image ("img:{id}"), a web link,
 * or the placeholder when there is none.
 *
 * @param contentDescription description read by screen readers.
 */
@Composable
fun RemoteImage(
    ref: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = painterResource(R.drawable.campsite_placeholder)
    val uploadedId = StoredImage.idFromRef(ref)
    if (uploadedId == null) {
        AsyncImage(
            model = ref?.takeIf { it.isNotBlank() },
            contentDescription = contentDescription,
            placeholder = placeholder,
            fallback = placeholder,
            error = placeholder,
            contentScale = contentScale,
            modifier = modifier,
        )
        return
    }
    var bitmap by remember(uploadedId) { mutableStateOf(imageCache.get(uploadedId)) }
    LaunchedEffect(uploadedId) {
        if (bitmap == null) {
            bitmap = try {
                FirestoreImageRepository().load(uploadedId)?.let { decodeBase64Image(it) }
                    ?.also { imageCache.put(uploadedId, it) }
            } catch (e: Exception) {
                null
            }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription, modifier, contentScale = contentScale)
    } else {
        Image(placeholder, contentDescription, modifier, contentScale = contentScale)
    }
}

/** Decodes a Base64 JPEG into an image, or null if the data is not a picture. */
suspend fun decodeBase64Image(data: String): ImageBitmap? = withContext(Dispatchers.Default) {
    if (data.isBlank()) return@withContext null
    val bytes = try {
        Base64.decode(data, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        return@withContext null
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}

/**
 * Reads the photo at [uri], shrinks it so its longest side is at most
 * [maxSide] pixels and returns it as Base64 JPEG text small enough for one
 * Firestore document.
 *
 * @throws IllegalArgumentException if the file is not a picture.
 */
suspend fun compressPhoto(context: Context, uri: Uri, maxSide: Int = 1024): String =
    withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("That file is not a picture.")
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        var quality = 80
        do {
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > 600_000 && quality > 30)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
