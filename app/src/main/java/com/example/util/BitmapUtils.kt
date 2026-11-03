package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max

object BitmapUtils {

    fun createTempCameraUri(context: Context): Pair<Uri, File> {
        val storageDir = File(context.cacheDir, "camera_captures").apply {
            if (!exists()) mkdirs()
        }
        val tempFile = File(storageDir, "camera_rough_bill_${System.currentTimeMillis()}.jpg").apply {
            if (!exists()) createNewFile()
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, tempFile)
        return Pair(uri, tempFile)
    }

    suspend fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            // Calculate inSampleSize
            var inSampleSize = 1
            var halfWidth = origWidth
            var halfHeight = origHeight
            while (halfWidth / 2 >= maxDimension || halfHeight / 2 >= maxDimension) {
                halfWidth /= 2
                halfHeight /= 2
                inSampleSize *= 2
            }

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null

            // Correct orientation from EXIF
            val rotatedBitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    applyExifRotation(sampledBitmap, orientation)
                } ?: sampledBitmap
            } catch (_: Exception) {
                sampledBitmap
            }

            // If still larger than maxDimension, downscale
            val largestDim = max(rotatedBitmap.width, rotatedBitmap.height)
            if (largestDim > maxDimension) {
                val scale = maxDimension.toFloat() / largestDim.toFloat()
                val targetW = (rotatedBitmap.width * scale).toInt()
                val targetH = (rotatedBitmap.height * scale).toInt()
                val finalScaled = Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
                if (finalScaled != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                finalScaled
            } else {
                rotatedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
