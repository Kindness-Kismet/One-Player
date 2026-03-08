<div align="center">

# One Player

**现代化的 Android 本地视频播放器**

[![English](https://img.shields.io/badge/English-red)](../../README.md)
[![简体中文](https://img.shields.io/badge/简体中文-blue)](README.zh-CN.md)

[![Android 31+](https://img.shields.io/badge/Android-31+-34A853?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Media3](https://img.shields.io/badge/Media3-FF6F00?logo=android&logoColor=white)](https://developer.android.com/media/media3)

</div>

> 基于 **Kotlin**、**Jetpack Compose**、**Hilt** 和 **Media3 / ExoPlayer** 构建。
> 延续自 [原始 Next Player 项目](https://github.com/anilbeesetti/nextplayer)，补强了应用内语言切换、设置备份恢复和 ASS 特效字幕。
>
> 感谢 [Next Player](https://github.com/anilbeesetti/nextplayer) 项目及所有上游贡献者提供的基础能力与长期维护。

---

## ✨ 核心亮点

| | 特性 | 说明 |
|:---:|---|---|
| 🎬 | **智能媒体库** | 文件夹 / 树状 / 纯列表三种视图，搜索，网格与列表切换 |
| ▶️ | **完整播控** | 断点续播、自动连播、画中画、手势操控、单文件轨道记忆 |
| 🔤 | **丰富字幕** | ASS 特效渲染、外挂字幕、编码与外观调节 |
| 🎨 | **Material You** | 动态取色、应用内切换语言、完整主题定制 |
| 💾 | **备份恢复** | 导出 / 导入全部设置，跨设备迁移 |
| 🚀 | **CI/CD 就绪** | 签名自动验证与发布产物输出 |

---

<details>
<summary>📖 <strong>功能总览</strong></summary>

### 媒体库

- 支持文件夹模式、树状目录模式、纯视频模式
- 支持搜索媒体内容
- 支持列表 / 网格布局切换
- 支持按标题、时长、大小、日期等方式排序
- 支持排除指定文件夹
- 支持忽略 `.nomedia` 并强制扫描对应目录
- Debug 构建带调试命令入口，便于验证媒体刷新和 `.nomedia` 行为

### 播放功能

- 支持从媒体库或外部 Intent 打开视频
- 支持断点续播
- 支持同目录自动播放下一个视频
- 支持画中画模式
- 支持后台播放相关配置
- 支持播放速度、长按倍速、缩放和内容比例调整
- 支持跳转、亮度、音量、缩放、双击等手势
- 支持记住每个文件的音轨 / 字幕轨选择

### 字幕与音频

- 支持内嵌音轨和字幕轨切换
- 支持通过系统文档选择器加载外挂字幕
- 支持 ASS 字幕特效渲染，不只是普通文本显示
- 支持首选音频语言和首选字幕语言
- 支持字幕字体、粗细、字号、背景、延迟、速度、文字编码等设置
- 支持解码器优先级与相关播放策略调整

### 个性化与设置

- 支持应用内语言切换
- 支持 Material 3 与动态取色
- 支持手势、播放器控件、方向、解码器、缩略图等配置
- 支持将应用设置和播放器设置备份到文件
- 支持从备份文件恢复设置
- 支持重置设置

### 发布与调试

- CI 会生成 debug 构建产物供分支验证
- 正式版工作流会在构建前验证签名配置可行性

</details>

<details>
<summary>📱 <strong>使用说明书</strong></summary>

### 1. 首次启动

- 安装 debug 或 release APK
- 首次启动时授予媒体访问权限
- 如果你启用了忽略 `.nomedia`，还需要授予所有文件访问权限

### 2. 浏览媒体库

- 打开首页后，可在文件夹、树状目录、视频列表之间切换
- 使用搜索快速定位媒体
- 在媒体页快捷设置中调整排序、布局和显示模式
- 在设置里排除不需要扫描的目录

### 3. 播放视频

- 点击任意视频即可进入播放器
- 横向滑动用于快进或快退
- 纵向滑动可调节亮度或音量
- 双击执行已配置的跳转动作
- 启用缩放手势后可双指缩放画面
- 如已开启相关设置，可使用画中画或后台播放

### 4. 使用字幕

- 在播放器内选择内嵌字幕轨
- 通过字幕选择器加载外挂字幕文件
- ASS 字幕支持特效渲染，不只是普通白字
- 字幕外观和行为可在"设置 > 字幕"里细调

### 5. 备份与恢复

- 打开"设置 > 通用"
- 导出当前应用设置与播放器设置
- 需要时重新导入到当前设备或另一台设备
- 如要重新开始，也可以直接重置设置

### 6. 常见排查

- 媒体库内容不全时，先检查排除文件夹和 `.nomedia` 设置
- 播放失败时，先确认媒体权限是否授予完整
- 字幕显示异常时，优先检查文字编码和 ASS 轨道选择
- CI 的正式版构建失败时，先看签名验证步骤输出

</details>

---

## 🏗️ 项目目录

```text
app/                  应用入口、Manifest、构建变体
core/common/          日志、调度器、通用工具
core/data/            Repository 实现与数据映射
core/database/        Room 数据库、DAO、Schema
core/datastore/       DataStore 数据源与序列化
core/domain/          用例层
core/media/           媒体扫描与播放服务
core/model/           共享模型
core/ui/              通用 Compose UI、字符串、主题
feature/player/       播放器功能与播放流程
feature/settings/     设置页面与偏好逻辑
feature/videopicker/  媒体浏览、搜索、快捷设置
.github/workflows/    CI、版本、发布工作流
```

---

<details>
<summary>🔧 <strong>构建与验证</strong></summary>

### 前置要求

- Android Studio 或 Android SDK 命令行工具
- JDK `25`

### 常用命令

```bash
./gradlew assembleDebug
./gradlew test
./gradlew ktlintCheck
./gradlew connectedAndroidTest
./gradlew ktlintCheck test assembleDebug --warning-mode=fail
```

</details>

<details>
<summary>🧑‍💻 <strong>开发教程</strong></summary>

### 第一步：克隆并打开项目

```bash
git clone https://github.com/Kindness-Kismet/One-Player.git
cd One-Player
```

用 Android Studio 打开项目，并确认使用 JDK 25。

### 第二步：构建 Debug APK

```bash
./gradlew assembleDebug
```

生成的常用产物在：

- `app/build/outputs/apk/debug/app-universal-debug.apk`
- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- `app/build/outputs/apk/debug/app-x86_64-debug.apk`

### 第三步：按顺序阅读代码

推荐入口顺序：

1. `README.md`
2. `app/src/main/java/one/next/player/MainActivity.kt`
3. `feature/videopicker/`
4. `feature/player/`
5. `feature/settings/`
6. `core/` 各模块

### 第四步：新增或修改功能

常见流程是：

1. 在 `core/model` 调整模型
2. 在 `core/data`、`core/database`、`core/datastore` 补数据层
3. 需要时在 `core/domain` 补用例逻辑
4. 在对应 `feature/*` 模块接 UI 和交互
5. 在 `core/ui/src/main/res` 同步文案与多语言资源

### 第五步：提交前验证

```bash
./gradlew ktlintCheck test assembleDebug --warning-mode=fail
```

如果改动涉及播放器行为，还应在真机或模拟器上做一次实际播放验证。

### 第六步：准备发布

1. 手动更新 `app/build.gradle.kts` 中的目标版本号
2. 推送 `release/vx.y.z` 分支，或手动触发发布工作流
3. 等待 CI 验证签名和构建产物
4. 检查自动生成的 GitHub Release 日志

</details>
