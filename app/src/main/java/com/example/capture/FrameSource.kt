package com.example.capture

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.example.bot.FrameSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

interface FrameSource : Closeable {
    suspend fun capture(): FrameSnapshot?
    override fun close() = Unit
}

class NoOpFrameSource : FrameSource {
    override suspend fun capture(): FrameSnapshot? {
        delay(60L)
        return null
    }
}

class MediaProjectionPermissionController(private val context: Context) {
    private val manager: MediaProjectionManager? =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager

    fun createScreenCaptureIntent(): Intent? = manager?.createScreenCaptureIntent()
}

class ScreenCaptureFrameSource(
    context: Context,
    resultCode: Int,
    data: Intent,
) : FrameSource {
    private val appContext = context.applicationContext
    private val metrics = appContext.resources.displayMetrics
    private val width = metrics.widthPixels.coerceAtLeast(1)
    private val height = metrics.heightPixels.coerceAtLeast(1)
    private val densityDpi = metrics.densityDpi
    private val isClosed = AtomicBoolean(false)
    private val projectionManager =
        appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val projection: MediaProjection = projectionManager.getMediaProjection(resultCode, data)
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            close()
        }
    }
    private val virtualDisplay = projection.createVirtualDisplay(
        "AutoFishScreenCapture",
        width,
        height,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface,
        null,
        null,
    )

    init {
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    override suspend fun capture(): FrameSnapshot? = withContext(Dispatchers.Default) {
        if (isClosed.get()) return@withContext null
        val image = imageReader.acquireLatestImage() ?: return@withContext null
        image.use { frame ->
            val rgba = frame.toRgbaBytes(width, height)
            FrameSnapshot(
                bytes = rgba,
                width = width,
                height = height,
                timestampMillis = System.currentTimeMillis(),
            )
        }
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        runCatching { virtualDisplay.release() }
        runCatching { imageReader.close() }
        runCatching { projection.unregisterCallback(callback) }
        runCatching { projection.stop() }
    }
}

private fun Image.toRgbaBytes(width: Int, height: Int): ByteArray {
    val plane = planes.first()
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val output = ByteArray(width * height * 4)

    if (pixelStride == 4 && rowStride == width * 4) {
        buffer.rewind()
        buffer.get(output, 0, output.size.coerceAtMost(buffer.remaining()))
        return output
    }

    var outputIndex = 0
    for (row in 0 until height) {
        val rowStart = row * rowStride
        for (col in 0 until width) {
            val pixelStart = rowStart + col * pixelStride
            output[outputIndex++] = buffer.get(pixelStart)
            output[outputIndex++] = buffer.get(pixelStart + 1)
            output[outputIndex++] = buffer.get(pixelStart + 2)
            output[outputIndex++] = buffer.get(pixelStart + 3)
        }
    }
    return output
}
