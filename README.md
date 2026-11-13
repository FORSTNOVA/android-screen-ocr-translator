# Android 屏幕悬浮翻译 (ScreenTranslator)

一款基于 **Kotlin + Jetpack Compose + 原生 Canvas 悬浮层** 的 Android 屏幕实时 OCR 翻译工具，支持**纯离线端侧识别翻译（Google ML Kit）**与**云端多模态大模型（OpenAI 兼容接口）**双引擎切换。

## 核心特性

- **全局悬浮球 + 截屏翻译**：通过 `MediaProjection` + `ForegroundService` 截取当前屏幕画面，并在原文位置直接叠加译文遮罩。
- **全屏 / 区域框选（ROI）双模式**：
  - **单击悬浮球**：翻译全屏内容，点击任意处或再次点击悬浮球关闭遮罩。
  - **长按悬浮球**：进入半透明区域框选模式，拖动手指仅翻译指定区域（适合字幕、漫画气泡、游戏界面）。
- **智能段落合并**：按行高、垂直间距与水平对齐度将零碎的 OCR 文本行聚类合并为自然段落，减少断行导致的语序错乱。
- **双引擎自由切换**：
  - **纯离线模式**：Google ML Kit（支持英、日、韩、简中端侧识别与离线翻译模型）。
  - **云端大模型模式**：支持任意 OpenAI 兼容 `/chat/completions` 接口、自定义 `Base URL` / `API Key` / 模型名称，并支持一键从上游 `/models` 拉取可用模型列表。若模型不支持多模态图片输入，会自动回退为“本地 ML Kit OCR + 云端纯文本翻译”。

## 目录结构

```text
app/src/main/java/com/screentranslator/app/
├── engine/      # OCR 识别、段落合并、离线翻译与云端大模型接口
├── overlay/     # 悬浮球、区域框选蒙层、译文 Canvas 覆盖层
├── service/     # MediaProjection 截屏管理与前台悬浮服务
└── ui/          # Jetpack Compose 主界面与权限/配置管理
```

## 构建环境

- Android Studio（AGP 9.4.1 / Gradle 9.7.1）
- `minSdk = 26` (Android 8.0+)，`targetSdk = 35`，`compileSdk = 36`
