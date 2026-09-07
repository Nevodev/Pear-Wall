<p align="center">
  <img src="readme/pear-wall.png" alt="Pear Wall" width="100%" />
</p>

<h1 align="center">Pear Wall</h1>

<p align="center">
  A dynamic Android wallpaper that reacts to music album art and audio rhythm.
</p>

<p align="center">
  <a href="https://github.com/Nevodev/Pear-Wall/releases/download/v1.0/pear-wall-1.0.APK">
    <img src="https://img.shields.io/badge/Download-v1.0 APK-blue?style=for-the-badge" alt="Download APK" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Android 10+" />
  <img src="https://img.shields.io/badge/OpenGL_ES-3.0-5586A4?logo=opengl&logoColor=white" alt="OpenGL ES 3.0" />
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Rust-000000?logo=rust&logoColor=white" alt="Rust" />
</p>

<p align="center">
  <strong>Language:</strong> <a href="#english">English</a> | <a href="#中文">简体中文</a>
</p>

---

<h2 id="english">English</h2>

## Overview

Pear Wall transforms the album artwork of currently playing media into an Apple Music-inspired animated mesh gradient wallpaper. It supports real-time audio-driven animations, providing dynamic visual feedback to bass and rhythm.

## Features

- **Dynamic Album Wallpaper**: Reads the album artwork from media notifications and updates the wallpaper in real-time.
- **Pear Mesh Rendering**: A fluid mesh effect powered by OpenGL ES 3.0.
- **Audio Visualization**: Analyzes system audio to detect low frequencies, rhythm, and transients, driving wallpaper animations.
- **Iridescent Glass Effect**: Optional, with multiple configurations (narrow, wide, smooth).
- **Media Player Filtering**: Blocks media notifications and artwork from players in the built-in blacklist.
- **Screenshot Export**: Tap the title area in the settings page to export the current wallpaper to `Pictures/PearWall`.
- **Screen Lock & Battery Optimization**: Automatically pauses rendering when the screen is off or locked to minimize power consumption.

## Installation

### Direct APK Installation

Currently only `arm64-v8a` architecture is available. Requires Android 10 (API 29) or higher.

### First Time Setup

1. Open Pear Wall.
2. Grant **Notification Access** in settings to read currently playing media information and artwork.
3. (Optional) Grant **Microphone Permission** for audio visualization. This permission is only used to capture system audio for rhythm analysis. The app does not connect to the internet or collect any personal data through this permission.
4. Tap "Set as Live Wallpaper" and confirm in the system wallpaper selector.

## Permissions

| Permission                 | Purpose                                          |
|----------------------------|--------------------------------------------------|
| Notification Access        | Read currently playing media info and artwork   |
| `RECORD_AUDIO`             | Capture audio data for visualization analysis   |
| `MODIFY_AUDIO_SETTINGS`    | Support audio capture and analysis              |
| Live Wallpaper Permissions | Register and run the Pear Wall wallpaper service|

Audio visualization is optional and can be disabled to skip audio analysis.

## License

This project is licensed under the [Apache License 2.0](LICENSE). See the `LICENSE` file in the project root for details.

## Acknowledgments

Special thanks to: Lyricify Backgrounds.

For complete dependency information, see the app or `app/config/libraries/`.

---

<h2 id="中文">简体中文</h2>

## 介绍

Pear Wall 将正在播放的媒体封面渲染成受 Apple Music 启发的网格渐变动态壁纸。支持使用系统音频驱动实时动画，为低音和节奏添加动态视觉反馈。

## 功能

- **动态封面壁纸**：从媒体通知读取当前播放内容的专辑封面，并实时更新壁纸。
- **Pear Mesh 渲染**：基于 OpenGL ES 3.0 的流动网格效果。
- **音频可视化**：分析系统输出音频，检测低频、节奏和瞬态变化，驱动壁纸动画。
- **长虹玻璃效果**：可选功能，提供多种配置方案（窄、宽、平滑等）。
- **媒体播放器过滤**：屏蔽内置黑名单中的媒体通知和封面。
- **截图导出**：在设置页点击标题区域，将当前壁纸画面导出到 `Pictures/PearWall`。
- **息屏与省电处理**：屏幕关闭或锁定时自动暂停渲染，减少不必要的功耗。

## 安装

### 直接安装 APK

当前仅打包 `arm64-v8a` 架构，要求 Android 10（API 29）或更高版本。

### 首次使用

1. 打开 Pear Wall 应用。
2. 在设置页授予**通知访问权限**，用于读取正在播放的媒体信息和封面。
3. （可选）授予**麦克风权限**以启用音频可视化。该权限仅用于捕获系统音频进行节奏分析。应用不会联网，也不会通过此权限收集任何个人信息。
4. 点击"设为动态壁纸"，在系统壁纸选择器中确认。

## 权限说明

| 权限                    | 用途                              |
|-------------------------|-----------------------------------|
| 通知访问权限            | 读取正在播放的媒体信息与封面      |
| `RECORD_AUDIO`          | 捕获音频数据用于可视化分析        |
| `MODIFY_AUDIO_SETTINGS` | 配合音频采集与分析                |
| 动态壁纸系统权限        | 注册并运行 Pear Wall 壁纸服务     |

音频可视化是可选功能，禁用后不会启动音频分析。

## 开源许可

本项目采用 [Apache License 2.0](LICENSE) 许可证。详见项目根目录的 `LICENSE` 文件。

## 致谢

特别感谢：Lyricify Backgrounds。

完整的依赖信息可在应用内或 `app/config/libraries/` 中查看。
