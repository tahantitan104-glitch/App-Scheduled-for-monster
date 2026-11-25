package com.example.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes a user-selected image safely with EXIF orientation + subsampling + OOM fallback,
 * center-crops to square, compresses to JPEG, and encodes as compact Base64 (<= ~147 KB)
 * so it saves locally and syncs reliably across devices in Firestore (SDK and REST).
 */
object AvatarImageProcessor {

    private const val TARGET_SIZE_PX = 224
    private const val FALLBACK_SIZE_PX = 160
    private const val MAX_RAW_BYTES = 110_000 // ~147 KB Base64, safely under 160,000 rules bound
    private const val MAX_INPUT_STREAM_BYTES = 15L * 1024L * 1024L
    private const val MAX_REMOTE_PHOTO_BYTES = 512 * 1024

    private val remotePhotoCache = object : LruCache<String, Bitmap>(8) {}

    suspend fun encodeAvatarFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val decoded = decodeWithImageDecoder(appContext, uri)
                ?: decodeWithLegacyFileAndExif(appContext, uri)
                ?: error("Unable to open or decode selected image.")

            val square = centerCropSquare(decoded)
            if (square !== decoded) decoded.recycle()

            val scaledPrimary = if (square.width != TARGET_SIZE_PX || square.height != TARGET_SIZE_PX) {
                Bitmap.createScaledBitmap(square, TARGET_SIZE_PX, TARGET_SIZE_PX, true).also {
                    if (it !== square) square.recycle()
                }
            } else {
                square
            }

            try {
                val compressedPrimary = compressUnderLimit(scaledPrimary)
                if (compressedPrimary != null) {
                    return@runCatching Base64.encodeToString(compressedPrimary, Base64.NO_WRAP)
                }

                // Fallback resize for dense noisy images on older devices
                val scaledSmall = Bitmap.createScaledBitmap(scaledPrimary, FALLBACK_SIZE_PX, FALLBACK_SIZE_PX, true)
                try {
                    val compressedSmall = compressUnderLimit(scaledSmall)
                        ?: error("Image could not be compressed cleanly.")
                    Base64.encodeToString(compressedSmall, Base64.NO_WRAP)
                } finally {
                    if (scaledSmall !== scaledPrimary) scaledSmall.recycle()
                }
            } finally {
                scaledPrimary.recycle()
            }
        }
    }

    private fun compressUnderLimit(bitmap: Bitmap): ByteArray? {
        val out = ByteArrayOutputStream()
        var quality = 86
        do {
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > MAX_RAW_BYTES && quality >= 20)
        val bytes = out.toByteArray()
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_RAW_BYTES }
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

    private fun decodeWithLegacyFileAndExif(context: Context, uri: Uri): Bitmap? {
        val tempFile = runCatching {
            File.createTempFile("avatar_pick_", ".tmp", context.cacheDir)
        }.getOrNull() ?: return decodeDirectStreamFallback(context, uri)

        return try {
            val copiedBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_INPUT_STREAM_BYTES) {
                            error("Selected image file is too large.")
                        }
                        output.write(buffer, 0, read)
                    }
                    total
                }
            } ?: return null
            if (copiedBytes <= 0L) return null

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOpts)
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var sampleSize = 1
            while ((srcW / (sampleSize * 2)) >= TARGET_SIZE_PX && (srcH / (sampleSize * 2)) >= TARGET_SIZE_PX) {
                sampleSize *= 2
            }

            var rawBitmap: Bitmap? = null
            var attempt = 0
            while (rawBitmap == null && attempt < 3) {
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = if (attempt == 0) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                }
                rawBitmap = try {
                    BitmapFactory.decodeFile(tempFile.absolutePath, decodeOpts)
                } catch (_: OutOfMemoryError) {
                    sampleSize *= 2
                    null
                }
                attempt++
            }
            val bitmap = rawBitmap ?: return null
            applyExifOrientation(tempFile.absolutePath, bitmap)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun decodeDirectStreamFallback(context: Context, uri: Uri): Bitmap? {
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

    private fun applyExifOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        var transformApplied = true
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> transformApplied = false
        }
        if (!transformApplied) return bitmap

        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }.getOrDefault(bitmap)
    }

    suspend fun fetchGooglePhotoBitmap(context: Context, photoUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val trimmed = photoUrl.trim()
        if (trimmed.isEmpty()) return@withContext null
        remotePhotoCache.get(trimmed)?.let { return@withContext it }

        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return@withContext null
        if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrBlank()) {
            return@withContext null
        }

        runCatching {
            Tls12SocketFactory.ensureProviderInstalled(context.applicationContext)
            val conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) {
                    Tls12SocketFactory.applyIfNeeded(this)
                }
                connectTimeout = 5_000
                readTimeout = 6_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buf = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_REMOTE_PHOTO_BYTES) break
                        out.write(buf, 0, read)
                    }
                }
                val bytes = out.toByteArray()
                if (!looksLikeSupportedImage(bytes)) return@runCatching null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val cropped = centerCropSquare(bmp)
                if (cropped !== bmp) bmp.recycle()
                remotePhotoCache.put(trimmed, cropped)
                cropped
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
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
