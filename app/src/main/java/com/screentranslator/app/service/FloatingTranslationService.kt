package com.screentranslator.app.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.screentranslator.app.R
import com.screentranslator.app.engine.CloudConfig
import com.screentranslator.app.engine.CloudConfigStore
import com.screentranslator.app.engine.CloudVisionTranslator
import com.screentranslator.app.engine.EngineMode
import com.screentranslator.app.engine.LanguageOption
import com.screentranslator.app.engine.MlKitOcrEngine
import com.screentranslator.app.engine.MlKitTranslatorEngine
import com.screentranslator.app.engine.OcrEngine
import com.screentranslator.app.engine.TranslatedBlock
import com.screentranslator.app.engine.TranslatorEngine
import com.screentranslator.app.overlay.FloatingBallView
import com.screentranslator.app.overlay.RegionSelectionOverlayView
import com.screentranslator.app.overlay.TranslationOverlayView
import com.screentranslator.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingTranslationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var captureManager: ScreenCaptureManager? = null
    private var floatingBallView: FloatingBallView? = null
    private var floatingBallParams: WindowManager.LayoutParams? = null
    private var overlayView: TranslationOverlayView? = null
    private var regionSelectionView: RegionSelectionOverlayView? = null

    private val ocrEngine: OcrEngine = MlKitOcrEngine()
    private val translatorEngine: TranslatorEngine = MlKitTranslatorEngine()
    private val cloudVisionTranslator = CloudVisionTranslator()
    private lateinit var cloudConfigStore: CloudConfigStore

    private var cloudConfig: CloudConfig = CloudConfig()
    private var sourceLanguage: LanguageOption = LanguageOption.ENGLISH
    private var targetLanguage: LanguageOption = LanguageOption.CHINESE
    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        cloudConfigStore = CloudConfigStore(this)
        cloudConfig = cloudConfigStore.load()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_LANGUAGES -> {
                sourceLanguage = intent.getStringExtra(EXTRA_SOURCE_LANG)
                    ?.let { runCatching { LanguageOption.valueOf(it) }.getOrNull() }
                    ?: sourceLanguage
                targetLanguage = intent.getStringExtra(EXTRA_TARGET_LANG)
                    ?.let { runCatching { LanguageOption.valueOf(it) }.getOrNull() }
                    ?: targetLanguage
                cloudConfig = cloudConfigStore.load()
                return START_STICKY
            }

            ACTION_UPDATE_CONFIG -> {
                cloudConfig = cloudConfigStore.load()
                return START_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                sourceLanguage = intent.getStringExtra(EXTRA_SOURCE_LANG)
                    ?.let { runCatching { LanguageOption.valueOf(it) }.getOrNull() }
                    ?: LanguageOption.ENGLISH
                targetLanguage = intent.getStringExtra(EXTRA_TARGET_LANG)
                    ?.let { runCatching { LanguageOption.valueOf(it) }.getOrNull() }
                    ?: LanguageOption.CHINESE

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundWithType()
                initCaptureAndFloatingBall(resultCode, resultData)
                _isRunning.value = true
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initCaptureAndFloatingBall(resultCode: Int, resultData: Intent) {
        captureManager?.release()
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Toast.makeText(this, "无法获取录屏权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        captureManager = ScreenCaptureManager(
            context = this,
            mediaProjection = projection,
            onProjectionStopped = {
                serviceScope.launch {
                    Toast.makeText(this@FloatingTranslationService, "屏幕共享已结束", Toast.LENGTH_SHORT).show()
                    stopSelf()
                }
            }
        )

        if (floatingBallView == null) {
            attachFloatingBall()
        }
    }

    private fun attachFloatingBall() {
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (260 * density).toInt()
        }

        val ball = FloatingBallView(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            screenWidthProvider = { resources.displayMetrics.widthPixels },
            onTap = { onFloatingBallTapped() },
            onLongPress = { onFloatingBallLongPressed() }
        )

        floatingBallParams = params
        floatingBallView = ball
        windowManager.addView(ball, params)
    }

    private fun onFloatingBallTapped() {
        if (isProcessing) return
        if (overlayView != null) {
            dismissTranslationOverlay()
            return
        }
        performCaptureAndTranslate(regionRect = null)
    }

    private fun onFloatingBallLongPressed() {
        if (isProcessing) return
        dismissTranslationOverlay()
        showRegionSelectionOverlay()
    }

    private fun showRegionSelectionOverlay() {
        dismissRegionSelectionOverlay()
        floatingBallView?.visibility = View.GONE

        val selector = RegionSelectionOverlayView(
            context = this,
            onRegionSelected = { rect ->
                dismissRegionSelectionOverlay()
                performCaptureAndTranslate(regionRect = rect)
            },
            onCancelled = {
                dismissRegionSelectionOverlay()
                floatingBallView?.visibility = View.VISIBLE
            }
        )

        val params = buildFullScreenOverlayParams()
        regionSelectionView = selector
        windowManager.addView(selector, params)
    }

    private fun performCaptureAndTranslate(regionRect: Rect?) {
        serviceScope.launch {
            isProcessing = true
            val ball = floatingBallView
            ball?.visibility = View.GONE
            delay(95L)

            val fullBitmap = captureManager?.captureSingleFrame()
            ball?.visibility = View.VISIBLE

            if (fullBitmap == null) {
                isProcessing = false
                Toast.makeText(this@FloatingTranslationService, "截屏失败，请重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            var targetBitmap: Bitmap = fullBitmap
            var offsetX = 0
            var offsetY = 0
            if (regionRect != null) {
                val safeLeft = regionRect.left.coerceIn(0, (fullBitmap.width - 1).coerceAtLeast(0))
                val safeTop = regionRect.top.coerceIn(0, (fullBitmap.height - 1).coerceAtLeast(0))
                val safeRight = regionRect.right.coerceIn(safeLeft + 1, fullBitmap.width)
                val safeBottom = regionRect.bottom.coerceIn(safeTop + 1, fullBitmap.height)
                val cropW = (safeRight - safeLeft).coerceAtLeast(1)
                val cropH = (safeBottom - safeTop).coerceAtLeast(1)
                targetBitmap = Bitmap.createBitmap(fullBitmap, safeLeft, safeTop, cropW, cropH)
                fullBitmap.recycle()
                offsetX = safeLeft
                offsetY = safeTop
            }

            ball?.isTranslating = true
            try {
                val activeConfig = cloudConfigStore.load().also { cloudConfig = it }
                val translatedBlocks = withContext(Dispatchers.Default) {
                    val blocks = if (activeConfig.mode == EngineMode.CLOUD_VISION_LLM) {
                        try {
                            cloudVisionTranslator.recognizeAndTranslate(
                                bitmap = targetBitmap,
                                sourceLanguage = sourceLanguage,
                                targetLanguage = targetLanguage,
                                config = activeConfig
                            )
                        } catch (visionErr: Exception) {
                            // 若模型不支持多模态识图（如返回 HTTP 400），自动回退为：本地 ML Kit OCR + 云端纯文本翻译
                            if (visionErr.message?.startsWith("HTTP 400") == true) {
                                val ocrBlocks = ocrEngine.recognize(targetBitmap, sourceLanguage)
                                if (ocrBlocks.isEmpty()) {
                                    emptyList()
                                } else {
                                    cloudVisionTranslator.translateTextBlocksWithLlm(
                                        blocks = ocrBlocks,
                                        sourceLanguage = sourceLanguage,
                                        targetLanguage = targetLanguage,
                                        config = activeConfig
                                    )
                                }
                            } else {
                                throw visionErr
                            }
                        }
                    } else {
                        val ocrBlocks = ocrEngine.recognize(targetBitmap, sourceLanguage)
                        if (ocrBlocks.isEmpty()) {
                            emptyList()
                        } else {
                            translatorEngine.translateBlocks(ocrBlocks, sourceLanguage, targetLanguage)
                        }
                    }

                    if (offsetX == 0 && offsetY == 0) {
                        blocks
                    } else {
                        blocks.map { block ->
                            block.copy(
                                boundingBox = Rect(block.boundingBox).apply { offset(offsetX, offsetY) }
                            )
                        }
                    }
                }

                if (translatedBlocks.isEmpty()) {
                    Toast.makeText(this@FloatingTranslationService, "未识别到可翻译文本", Toast.LENGTH_SHORT).show()
                } else {
                    showTranslationOverlay(translatedBlocks)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@FloatingTranslationService,
                    "翻译失败: ${e.localizedMessage ?: "未知错误"}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                targetBitmap.recycle()
                ball?.isTranslating = false
                isProcessing = false
            }
        }
    }

    private fun showTranslationOverlay(blocks: List<TranslatedBlock>) {
        dismissTranslationOverlay()
        val overlay = TranslationOverlayView(
            context = this,
            onDismissRequest = { dismissTranslationOverlay() }
        ).apply {
            setTranslatedBlocks(blocks)
        }

        overlayView = overlay
        windowManager.addView(overlay, buildFullScreenOverlayParams())
    }

    private fun buildFullScreenOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dismissRegionSelectionOverlay() {
        regionSelectionView?.let { view ->
            runCatching { windowManager.removeView(view) }
            regionSelectionView = null
        }
    }

    private fun dismissTranslationOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
            overlayView = null
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingTranslationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        dismissRegionSelectionOverlay()
        dismissTranslationOverlay()
        floatingBallView?.let { runCatching { windowManager.removeView(it) } }
        floatingBallView = null
        captureManager?.release()
        captureManager = null
        ocrEngine.close()
        translatorEngine.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "screen_translator_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.screentranslator.app.ACTION_START"
        const val ACTION_STOP = "com.screentranslator.app.ACTION_STOP"
        const val ACTION_UPDATE_LANGUAGES = "com.screentranslator.app.ACTION_UPDATE_LANGUAGES"
        const val ACTION_UPDATE_CONFIG = "com.screentranslator.app.ACTION_UPDATE_CONFIG"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SOURCE_LANG = "extra_source_lang"
        const val EXTRA_TARGET_LANG = "extra_target_lang"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
