package com.screentranslator.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val onProjectionStopped: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 0

    @Volatile
    private var pendingCapture: CancellableContinuation<Bitmap?>? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
            onProjectionStopped()
        }
    }

    init {
        mediaProjection.registerCallback(projectionCallback, handler)
        setupVirtualDisplay()
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        val (w, h, dpi) = readScreenMetrics()
        screenWidth = w
        screenHeight = h
        densityDpi = dpi

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val continuation = pendingCapture ?: return@setOnImageAvailableListener
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                pendingCapture = null
                val bitmap = runCatching { imageToBitmap(image) }.getOrNull()
                image.close()
                if (continuation.isActive) {
                    continuation.resume(bitmap)
                } else {
                    bitmap?.recycle()
                }
            }, handler)
        }

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenTranslatorCapture",
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )
    }

    suspend fun captureSingleFrame(): Bitmap? {
        val reader = imageReader ?: return null
        // 清理可能积压的旧帧，优先尝试直接取最新一帧，若暂无则等待下一帧回调
        reader.acquireLatestImage()?.let { latest ->
            val bitmap = runCatching { imageToBitmap(latest) }.getOrNull()
            latest.close()
            if (bitmap != null) return bitmap
        }

        return withTimeoutOrNull(800L) {
            suspendCancellableCoroutine { cont ->
                pendingCapture = cont
                cont.invokeOnCancellation {
                    if (pendingCapture === cont) {
                        pendingCapture = null
                    }
                }
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val paddedBitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            paddedBitmap
        } else {
            Bitmap.createBitmap(paddedBitmap, 0, 0, screenWidth, screenHeight).also {
                paddedBitmap.recycle()
            }
        }
    }

    private fun readScreenMetrics(): Triple<Int, Int, Int> {
        val dpi = context.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), dpi)
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also {
                windowManager.defaultDisplay.getRealMetrics(it)
            }
            Triple(metrics.widthPixels, metrics.heightPixels, dpi)
        }
    }

    fun release() {
        pendingCapture?.cancel()
        pendingCapture = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        runCatching { mediaProjection.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection.stop() }
    }
}
