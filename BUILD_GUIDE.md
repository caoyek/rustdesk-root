# RustDesk Root 增强版与私有化编译定制指南

本目录包含为 **RustDesk Android 端增加原生 Root 模式（底层高性能输入模拟、免无障碍服务、免录屏弹窗、Root 终端）** 的全套源码补丁、GitHub Actions 云端编译工作流以及私有中继服务端部署配置。

---

## 📁 目录结构说明

```
rustdesk-custom/
├── android-root-patch/             # Android 端 Root 核心驱动与集成补丁
│   ├── RootInputManager.kt         # 持久化 su 输入管道管理器（tap/swipe/key/text）
│   ├── RootTerminalSession.kt      # 交互式 Root 远程终端会话模块
│   └── MainServiceRootIntegration.kt # 主服务输入分发逻辑（优先走 Root）
├── workflows/
│   └── build-android.yml           # GitHub Actions 云端全自动编译 APK 配置文件
├── server-deploy/
│   ├── docker-compose.yml          # 私有信令与中继服务器配置 (hbbs/hbbr)
│   └── deploy.sh                   # Linux 服务器一键部署脚本
└── BUILD_GUIDE.md                  # 本说明指南
```

---

## 🚀 极速编译流程（推荐：GitHub Actions 云端编译）

通过云端自动编译，**无需在本地下载安装数十 GB 的 NDK、Rust 交叉编译链与 Flutter SDK**。

### 第一步：Fork 官方仓库
1. 访问 RustDesk 官方仓库：[https://github.com/rustdesk/rustdesk](https://github.com/rustdesk/rustdesk)
2. 点击右上角的 **Fork**，将其复制到你自己的 GitHub 账号下。

### 第二步：将定制文件推送到你的仓库
1. 将 `android-root-patch/` 中的 `.kt` 文件复制到你的仓库目录：
   `libs/flutter_hbb/android/app/src/main/kotlin/com/carriez/flutter_hbb/`
2. 将 `workflows/build-android.yml` 复制到你仓库的 `.github/workflows/build-android.yml`。
3. 提交并推送代码（`git commit & git push`）。

### 第三步：自动触发云端打包
1. 进入你 GitHub 仓库的 **Actions** 标签页。
2. 选择 **Build Custom Android APK with Root Support** 工作流，点击 **Run workflow**。
3. 等待约 10~15 分钟，编译完成后即可在 Artifacts 中直接下载 **`rustdesk-root-arm64-release.apk`** 安装包！

---

## 🖥️ 搭建私有中继服务器（脱离官方服务器）

为了保证连接不卡顿且隐私完全可控：

1. 登录你的 Linux 云服务器（Ubuntu/Debian/CentOS）。
2. 将 `server-deploy/deploy.sh` 脚本上传到服务器并执行：
   ```bash
   bash deploy.sh
   ```
3. 脚本会自动拉起 `hbbs`（ID信令服务器）和 `hbbr`（中继服务器），并输出专属 **公钥 (Key)** 与 **IP 端口**。
4. 在电脑控制端和手机受控端的 **设置 -> ID/网络服务器** 中填入你的服务器 IP 和 Key 即可！

---

## 📱 手机端使用效果验证

1. **彻底关闭手机 USB 调试**（通过一切风控/银行/游戏检测）；
2. 安装编译好的定制版 RustDesk（并在 Magisk 中确认已授予 Root 权限）；
3. 电脑端连接手机后：
   * **远程控制**：直接通过 Root 底层注入触控，无需开启 Android「无障碍服务」；
   * **远程终端**：直接在终端模式下执行 `su` 命令，与有线 ADB 体验完全一致；
   * **静默运行**：配合 Extinguish / 极暗遮罩，手机完全黑屏且前端毫无动作。
