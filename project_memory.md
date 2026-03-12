# android_llm_server 项目记忆

## 目标
- 利用 `google-ai-edge/gallery` 仓库提供的 Android 示例，实现一次完整的 APK 构建（`./gradlew assembleRelease`），为 Android 端 LLM 服务做环境验证。

## 目前准备/进展
1. **仓库拉取**：在 `/home/cx/.openclaw/workspace/android_llm_server/gallery` 克隆 upstream 仓库，Android 工程位于 `Android/src`。
2. **构建脚本**：GitHub Actions workflow (`.github/workflows/build_android.yaml`) 显示：Ubuntu 环境下安装 Temurin Java 21，进入 `Android/src` 运行 `./gradlew assembleRelease`。
3. **依赖准备**：
   - apt 安装 openjdk-21-jdk 多次因网络卡住/锁冲突失败。
   - 改为手动下载 Temurin JDK：在 `/home/cx/.openclaw/workspace/tools` 用 SOCKS5 代理获取 `OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz`（198MB），解压到 `tools/jdk-21.0.5+11` 并通过 `JAVA_HOME` 配置。
4. **打包尝试**：
   - 环境变量使用 `JAVA_HOME=/home/cx/.openclaw/workspace/tools/jdk-21.0.5+11`，启动 `./gradlew assembleRelease`。
   - 当前 Gradle 正在下载 8.10.2 的分发包（日志显示 “Downloading … gradle-8.10.2-bin.zip … 10% … 20% …”），尚未进入项目构建阶段；等待下载完成后会继续进行并根据输出安装 Android SDK 组件。

## 下一步
- 等 Gradle 8.10.2 下载完毕，观察 `./gradlew assembleRelease` 的下一步输出。
- 若提示缺失 Android SDK/NDK、许可等，按提示安装或配置。
- 成功生成 APK 后，将产物路径与步骤记录至本文件。
- 2026-03-12：在 `gradle.properties` 配置 SOCKS5 代理，运行 `ALL_PROXY=socks5h://172.27.34.8:1080 ./gradlew assembleRelease --info --console=plain` 下载了 Gradle 8.10.2 和大量依赖；构建失败原因：未找到 Android SDK（提示设置 ANDROID_HOME 或 local.properties 的 sdk.dir）。下一步需安装/配置 Android SDK（含 build-tools、platforms、cmdline-tools）并重跑。
- 2026-03-12：下载 cmdline-tools + 接受所有 SDK 许可证，使用 SOCKS5 代理安装 build-tools 35.0.0、platforms;android-35、platform-tools；配置 local.properties 指向 /home/cx/.openclaw/workspace/android_sdk；在代理下执行 `./gradlew assembleRelease` 成功生成 `app/build/outputs/apk/release/app-release.apk`（约 107MB）。随后在 `/home/cx/.openclaw/workspace/android_llm_server/gallery/Android/src/app/build/outputs/apk/release` 启动 `python3 -m http.server 8000 --bind 0.0.0.0`，通过 `http://172.27.34.13:8000/app-release.apk` 提供 APK 下载给手机进行测试。根据新提供的插画，使用 Pillow 将图片裁成正方形并生成五档 mipmap（mdpi~xxxhdpi）的 `ic_launcher*.png`（foreground/background/monochrome），替换了旧图标资产。随后重新运行 `ALL_PROXY=socks5h://172.27.34.8:1080 ./gradlew assembleRelease`（JAVA_HOME/ANDROID_HOME 已配置）完成新的 release 打包（BUILD SUCCESSFUL，约 56s），并重新在 release 目录下起 `python3 -m http.server 8000 --bind 0.0.0.0` 让手机拉取最新 APK（文件时间 3/12 11:55，大小 107MB，`http://172.27.34.13:8000/app-release.apk`）。再次根据需求将应用名改为 “llmserver”：`strings.xml` 中的 `app_name` 及首/次行文案统一为 “llmserver”，`AndroidManifest.xml` 的 `android:label` 指向 `@string/app_name`。完成修改后再次运行 `./gradlew assembleRelease`（17s 完成），新的 APK 时间 3/12 12:01，大小 107MB，仍通过 `http://172.27.34.13:8000/app-release.apk` 提供下载（临时 HTTP 服务已重启）。新增 `web_service_plan.md`（项目根），整理“在 App 内嵌 POST 对话接口 Web 服务”的整体规划：HTTP server 选型、路由/认证方案、LLM 调用桥接、服务守护/配置 UI、依赖与测试策略等。2026-03-12 下午按规划落地步骤 1～3：引入 NanoHTTPD 作为内置 HTTP server，新建 `webservice/` 模块（`LlmWebContracts`、`LlmWebServerController`、`LlmNanoHttpServer`、`LlmWebServerService`），在 `MainActivity` 启动前台 Service，并在 Manifest/strings 中注册通知渠道；Controller 复用 `ModelManagerViewModel` 推理逻辑、自动选择/初始化模型、暴露 `/chat` + `/health`；成功通过 `./gradlew assembleRelease`（24s 完成）生成最新 APK（时间 3/12 13:29，111,950,929 bytes），再次在 release 目录下开 `python3 -m http.server 8000 --bind 0.0.0.0` 供手机下载。后续根据测试反馈新增纯文本优先策略和服务配置界面：`settings.proto` 扩展 web service 字段、Settings 对话框加入开关/模型下拉并调用 `LlmWebServerService.start/stop/restart`；`MainActivity` 仅在设置开启时自启服务；通知栏显示本机 IP+端口并附“停止服务”按钮；`LlmWebServerController` 固定优先选择 LLM_CHAT/Prompt Lab 任务避免音频依赖；`LlmWebServerService` 从 DataStore 读取默认模型、允许通知按钮关闭、支持 restart。重新打包（`./gradlew assembleRelease`，约 24s 完成）生成最新版 APK（时间 3/12 14:27，111,986,349 bytes），临时下载链接保持为 `http://172.27.34.13:8000/app-release.apk`。针对“后台推理失败”新增 `background_web_service_plan.md`，说明下一步方案：抽象独立 `LlmInferenceEngine`、让 Service 自管模型生命周期、推理时持有 wakelock、彻底脱离 UI ViewModel，确保即使 App 在后台也能响应 HTTP 请求。2026-03-12（下午后半段）：按该方案落地首轮改造，新增 `LlmInferenceEngine`（封装模型选择/初始化/对话逻辑）、让 Service 通过 wake lock 维护 CPU 唤醒、HTTP server 直接调用 engine；移除旧 `LlmWebServerController`，`LlmNanoHttpServer` 支持挂载 suspend handler，并重新打包验证通过。
