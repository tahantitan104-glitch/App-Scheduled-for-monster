package com.example.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes a user-selected Photo Picker image safely with EXIF orientation + subsampling,
 * center-crops to square, compresses to JPEG, and encodes as compact Base64 (<= ~147 KB)
 * so it saves locally and syncs reliably across devices in Firestore.
 */
object AvatarImageProcessor {

    private const val TARGET_SIZE_PX = 224
    private const val MAX_RAW_BYTES = 110_000 // ~147 KB Base64, safely under 160,000 rules bound

    suspend fun encodeAvatarFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val decoded = decodeWithImageDecoder(appContext, uri)
                ?: decodeWithBitmapFactory(appContext, uri)
                ?: error("Unable to open or decode selected image.")

            val square = centerCropSquare(decoded)
            if (square !== decoded) decoded.recycle()

            val scaled = if (square.width != TARGET_SIZE_PX || square.height != TARGET_SIZE_PX) {
                Bitmap.createScaledBitmap(square, TARGET_SIZE_PX, TARGET_SIZE_PX, true).also {
                    if (it !== square) square.recycle()
                }
            } else {
                square
            }

            try {
                val out = ByteArrayOutputStream()
                var quality = 86
                do {
                    out.reset()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    quality -= 10
                } while (out.size() > MAX_RAW_BYTES && quality >= 26)

                val bytes = out.toByteArray()
                require(bytes.isNotEmpty() && bytes.size <= MAX_RAW_BYTES) {
                    "Image could not be compressed cleanly."
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                scaled.recycle()
            }
        }
    }

    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                val w = info.size.width
                val h = info.size.height
                if (w > 0 && h > 0) {
                    val minDim = min(w, h).coerceAtLeast(1)
                    if (minDim > TARGET_SIZE_PX * 2) {
                        val scale = (TARGET_SIZE_PX * 2f) / minDim.toFloat()
                        val targetW = max(1, (w * scale).roundToInt())
                        val targetH = max(1, (h * scale).roundToInt())
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            }
        }.getOrNull()
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sampleSize = 1
        while ((srcW / (sampleSize * 2)) >= TARGET_SIZE_PX && (srcH / (sampleSize * 2)) >= TARGET_SIZE_PX) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }

    private val STRICT_BASE64_REGEX = Regex("^[A-Za-z0-9+/=\\s]+$")

    fun decodeAvatarBitmap(base64: String): Bitmap? {
        val trimmed = base64.trim()
        if (trimmed.isEmpty() || !STRICT_BASE64_REGEX.matches(trimmed)) return null
        return runCatching {
            val bytes = Base64.decode(trimmed, Base64.DEFAULT)
            if (!looksLikeSupportedImage(bytes)) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun looksLikeSupportedImage(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val isJpeg = (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8
        val isPng = (bytes[0].toInt() and 0xFF) == 0x89 &&
            bytes[1].toInt() == 0x50 &&
            bytes[2].toInt() == 0x4E &&
            bytes[3].toInt() == 0x47
        val isWebp = bytes[0].toInt() == 0x52 &&
            bytes[1].toInt() == 0x49 &&
            bytes[2].toInt() == 0x46 &&
            bytes[3].toInt() == 0x46
        return isJpeg || isPng || isWebp
    }

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }
}
