# TransSync

<p align="center">
  <img src="app/src/main/res/drawable/logo.png" width="100" alt="TransSync Logo" />
</p>

<p align="center">
  <b>Android 高颜值液态玻璃远程下载管理器</b><br/>
  <b>Modern Remote Download Manager for Android</b>
</p>

<p align="center">
  <a href="https://github.com/kuangru52/TransSync/releases/latest"><img src="https://img.shields.io/github/v/release/kuangru52/TransSync?color=00B0FF&label=Release" alt="Latest Release"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg" alt="Platform"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Jetpack Compose"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

---

## 📖 简介 / Introduction

**TransSync** 是一款采用 Jetpack Compose 构建的现代 Android 远程下载管理器。完美原生兼容 **Transmission** 与 **qBittorrent** 两大主流下载客户端，独创 **Liquid Glass（液态玻璃）UI 视觉架构**，提供极致平滑的交互体验与智能的 H&R 自动化管理。

**TransSync** is a modern, high-performance remote download manager for Android built with Jetpack Compose. It natively supports both **Transmission** and **qBittorrent** Web API v2, featuring a custom **Liquid Glass UI design system** and automated H&R lifecycle management.

---

## ✨ 核心特性 / Key Features

### 🚀 客户端支持 / Dual Client Support
- **双引擎无缝兼容**: 完美支持 Transmission RPC 与 qBittorrent Web API v2。
- ** Seamless Dual-Engine Compatibility**: Full support for both Transmission RPC and qBittorrent Web API v2.
- **极速无缝切换**: 切换服务器时自动识别客户端协议，跨客户端无缝自重启确保 100% 干净会话。
- **Seamless Server Switching**: Automatically handles cross-client switching (Transmission ↔ qBittorrent) with instant clean session reloading.

### 🎨 独创液态玻璃 UI / Liquid Glass Design System
- **物理折射 Shader 卡片**: 搭载拟真凸透镜折射 Shader 与 120 FPS 动态高斯模糊。
- **Realistic Refraction Shader**: Built with custom 1:1 lens refraction shaders and real-time 120 FPS Gaussian blur cards.
- **彩蛋开发者调参面板**: 在设置页连点 5 次版本号即可解锁调参 Inspector，实时滑动调节折射量、高度、模糊度与彩度！
- **Developer Shader Inspector**: Tap version number 5 times in Settings to unlock real-time Shader tuning panel!

### ⏳ H&R 自动化管理 / Automated H&R Lifecycle
- **下载完成自动重汇报**: 种子下载完成时自动向 Tracker 发送 Reannounce，即刻起算做种。
- **Auto Reannounce on Complete**: Automatically reannounces to trackers as soon as download finishes.
- **倒计时归零 10 分钟重汇报**: H&R 考核时间归零后 10 分钟自动二次汇报，确保 PT 站状态精准刷新。
- **Post-H&R Reannounce**: Automatically reannounces 10 minutes after required seeding time ends to ensure PT site status is updated.
- **缓冲期满系统通知**: 30 分钟缓冲期满并显示绿色勾选图标时，发送系统通知提醒考核通过。
- **H&R Passed Notification**: Sends system notification when the 30-minute cooling buffer expires.

### 🔒 隐私保护与自定义 Tracker 映射 / Privacy & Custom Tracker Mappings
- **一键隐私模糊**: 智能遮罩敏感情报与敏感 Tracker 域名。
- **Privacy Mode**: One-tap blur for sensitive torrent metadata and tracker domains.
- **自定义映射备份与恢复**: 支持 `.ini` 文件一键导入恢复与增量备份。
- **Tracker Mapping Backup & Restore**: Full `.ini` format backup and incremental restore support.

### ⚡ 备用网速与在线更新 / Speed Limits & Auto Updates
- **一键龟速模式**: 调起 Transmission 与 qBittorrent 全局备用限速。
- **One-Tap Alternative Speed Limits**: Native toggle for alternative speed limits mode (turtle icon).
- **GitHub Releases 在线更新**: 自动与 GitHub Releases 版本对比，推送更新升级弹窗。
- **GitHub Auto Update Checker**: Automatically checks latest GitHub Releases and prompts updates.

---

## 📱 界面预览 / Screenshots

| 首页任务列表 / Torrent List | 侧边栏与筛选 / Navigation Drawer | 设置与在线更新 / Settings & Updates |
| :---: | :---: | :---: |
| <img src="https://raw.githubusercontent.com/kuangru52/TransSync/master/app/src/main/res/drawable/logo.png" width="180" /> | <img src="https://raw.githubusercontent.com/kuangru52/TransSync/master/app/src/main/res/drawable/logo.png" width="180" /> | <img src="https://raw.githubusercontent.com/kuangru52/TransSync/master/app/src/main/res/drawable/logo.png" width="180" /> |

---

## 🛠️ 安装与使用 / Installation

1. 从 [GitHub Releases](https://github.com/kuangru52/TransSync/releases/latest) 页面下载最新的 `TransSync-v3.08.apk` 文件。
   Download the latest `TransSync-v3.08.apk` from [GitHub Releases](https://github.com/kuangru52/TransSync/releases/latest).
2. 在 Android 手机上安装并打开应用。
   Install and launch the application on your Android device (Android 8.0+).
3. 输入你的 Transmission 或 qBittorrent 服务器地址、端口及凭据，点击【测试连接】与【保存】即可开始使用！
   Enter your Transmission or qBittorrent server URL, credentials, test connection, and enjoy!

---

## 📄 开源协议 / License

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

This project is licensed under the [Apache License 2.0](LICENSE).
