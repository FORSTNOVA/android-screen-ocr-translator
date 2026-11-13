package com.screentranslator.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.screentranslator.app.engine.CloudConfig
import com.screentranslator.app.engine.CloudConfigStore
import com.screentranslator.app.engine.CloudVisionTranslator
import com.screentranslator.app.engine.LanguageOption
import com.screentranslator.app.engine.MlKitTranslatorEngine
import com.screentranslator.app.service.FloatingTranslationService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val translatorEngine = MlKitTranslatorEngine()
    private val cloudVisionTranslator = CloudVisionTranslator()
    private lateinit var cloudConfigStore: CloudConfigStore

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var sourceLanguage by mutableStateOf(LanguageOption.ENGLISH)
    private var targetLanguage by mutableStateOf(LanguageOption.CHINESE)
    private var cloudConfig by mutableStateOf(CloudConfig())
    private var availableModels by mutableStateOf<List<String>>(emptyList())
    private var isFetchingModels by mutableStateOf(false)
    private var fetchModelsMessage by mutableStateOf<String?>(null)
    private var isDownloadingModel by mutableStateOf(false)
    private var modelStatusMessage by mutableStateOf<String?>(null)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissions()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val startIntent = Intent(this, FloatingTranslationService::class.java).apply {
                action = FloatingTranslationService.ACTION_START
                putExtra(FloatingTranslationService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingTranslationService.EXTRA_RESULT_DATA, result.data)
                putExtra(FloatingTranslationService.EXTRA_SOURCE_LANG, sourceLanguage.name)
                putExtra(FloatingTranslationService.EXTRA_TARGET_LANG, targetLanguage.name)
            }
            ContextCompat.startForegroundService(this, startIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cloudConfigStore = CloudConfigStore(this)
        cloudConfig = cloudConfigStore.load()
        refreshPermissions()

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshPermissions()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val isRunning by FloatingTranslationService.isRunning.collectAsState()
            val scope = rememberCoroutineScope()

            MaterialTheme {
                MainScreen(
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    isServiceRunning = isRunning,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    cloudConfig = cloudConfig,
                    availableModels = availableModels,
                    isFetchingModels = isFetchingModels,
                    fetchModelsMessage = fetchModelsMessage,
                    isDownloadingModel = isDownloadingModel,
                    modelStatusMessage = modelStatusMessage,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onSourceLanguageChange = { lang ->
                        sourceLanguage = lang
                        syncLanguagesIfRunning()
                    },
                    onTargetLanguageChange = { lang ->
                        targetLanguage = lang
                        syncLanguagesIfRunning()
                    },
                    onSwapLanguages = {
                        val oldSource = sourceLanguage
                        sourceLanguage = targetLanguage
                        targetLanguage = oldSource
                        syncLanguagesIfRunning()
                    },
                    onCloudConfigChange = { updated ->
                        cloudConfig = updated
                        cloudConfigStore.save(updated)
                        syncConfigIfRunning()
                    },
                    onFetchModels = {
                        scope.launch {
                            isFetchingModels = true
                            fetchModelsMessage = "正在从上游拉取模型列表..."
                            runCatching {
                                cloudVisionTranslator.fetchAvailableModels(
                                    baseUrl = cloudConfig.baseUrl,
                                    apiKey = cloudConfig.apiKey
                                )
                            }.onSuccess { models ->
                                availableModels = models
                                fetchModelsMessage = if (models.isEmpty()) {
                                    "上游未返回模型，可继续手动输入"
                                } else {
                                    "已拉取 ${models.size} 个模型，点击输入框右侧箭头选择"
                                }
                            }.onFailure { err ->
                                fetchModelsMessage = "拉取失败: ${err.localizedMessage ?: "请检查 URL/Key"}"
                            }
                            isFetchingModels = false
                        }
                    },
                    onDownloadModels = {
                        scope.launch {
                            isDownloadingModel = true
                            modelStatusMessage = "正在检查/下载 ${sourceLanguage.displayName} → ${targetLanguage.displayName} 离线包..."
                            runCatching {
                                translatorEngine.ensureModelReady(sourceLanguage, targetLanguage)
                            }.onSuccess {
                                modelStatusMessage = "离线翻译模型已就绪，可离线使用"
                            }.onFailure { err ->
                                modelStatusMessage = "下载失败: ${err.localizedMessage ?: "请检查网络"}"
                            }
                            isDownloadingModel = false
                        }
                    },
                    onToggleService = {
                        if (isRunning) {
                            stopService(Intent(this, FloatingTranslationService::class.java))
                        } else {
                            requestMediaProjection()
                        }
                    }
                )
            }
        }
    }

    private fun refreshPermissions() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun syncLanguagesIfRunning() {
        if (!FloatingTranslationService.isRunning.value) return
        val updateIntent = Intent(this, FloatingTranslationService::class.java).apply {
            action = FloatingTranslationService.ACTION_UPDATE_LANGUAGES
            putExtra(FloatingTranslationService.EXTRA_SOURCE_LANG, sourceLanguage.name)
            putExtra(FloatingTranslationService.EXTRA_TARGET_LANG, targetLanguage.name)
        }
        startService(updateIntent)
    }

    private fun syncConfigIfRunning() {
        if (!FloatingTranslationService.isRunning.value) return
        val updateIntent = Intent(this, FloatingTranslationService::class.java).apply {
            action = FloatingTranslationService.ACTION_UPDATE_CONFIG
        }
        startService(updateIntent)
    }

    override fun onDestroy() {
        translatorEngine.close()
        super.onDestroy()
    }
}
