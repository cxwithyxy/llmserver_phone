# android_llm_server / llmserver 架构说明

本项目基于 Google `google-ai-edge/gallery` 示例，目标是在 Android 端同时提供本地 UI 推理体验和局域网 POST /chat 接口。当前逻辑拆为两个主要部分：**UI 模块**（Compose + ViewModel）和 **Web Service 模块**（前台 Service + NanoHTTPD + LiteRT 引擎）。下述按模块串联运行流程，方便后续维护。

## 1. 运行概览

```
MainActivity
 ├─ UI 层：ModelManager + LlmChat/LlmSingleTurn ViewModel + Compose 页面
 └─ Web Service 模块：LlmWebServerService → NanoHTTPD → LlmInferenceEngine
      └─ 共用 ModelManagerViewModel / LiteRT LlmChatModelHelper
```

- **模型管理**：`ModelManagerViewModel` + `DownloadRepository` 负责模型下载、初始化、状态持久化；UI 与 Web Service 共享一套数据层（通过 Hilt 单例注入）。
- **网络接口**：系统设置页提供“Web service 开关 + 模型选择”，会写入 DataStore 并驱动 `LlmWebServerService` 自启/关停。Service 启动后在通知栏展示 IP:PORT + 停止按钮。
- **HTTP 路由**：NanoHTTPD 监听 8081，公开 `/health`（便捷调试）与 `/chat`（POST JSON）两个接口；`/chat` 请求通过 `LlmInferenceEngine` 序列化处理，内部复用 LiteRT 任务、会话和模型实例。

## 2. UI 端运行逻辑

### 2.1 模型生命周期
- `ModelManagerViewModel` 初始化时加载 model allowlist：
  - 优先读取 `/data/local/tmp/model_allowlist_test.json` → DataStore 缓存 → 资产 `Android/src/app/src/main/assets/model_allowlist.json`。
  - Settings Dialog 中提供“Fetch remote allowlist”按钮，手动更新 GitHub 版本。
- 用户在 UI 中下载模型后，模型实例信息保存在 `ModelInitializationStatus` map 内；UI 和 Web Service 都会调 `initializeModel()`，共享 `model.instance`。

### 2.2 Chat/Single Turn ViewModel
- `LlmChatViewModelBase.generateResponse()`：等待模型实例，就绪后调用 `LlmChatModelHelper.runInference()`，将 streaming 结果追加到 Compose 聊天气泡中。
- `LlmSingleTurnViewModel`：按模板一次性生成响应，同样通过 `runInference()` streaming 写入响应 Map。
- 若推理报错，ViewModel 会显示 `ChatMessageError` 并调用 `ModelManagerViewModel.cleanupModel()` 重新初始化。

### 2.3 设置 & 服务控制
- `SettingsDialog` 中 Web Service 区块：
  1. 启用开关写入 DataStore，并 `Context.startForegroundService()` 启动/停止 `LlmWebServerService`。
  2. 模型选择器写入 DataStore，Service 读取 `dataStoreRepository.getWebServiceModelName()` 作为默认模型。
  3. Allowlist 控制按钮触发网络刷新。

## 3. Web Service 端运行逻辑

### 3.1 Foreground Service
- **入口**：`LlmWebServerService`（Manifest 注册 + 前台通知）。
- **依赖注入**：注入 `DownloadRepository`、`DataStoreRepository`、`AppLifecycleProvider`、`Set<CustomTask>`，用于构建后台专用的 `LlmInferenceEngine`。
- **生命周期**：
  - `onCreate()` → 解析本机 IP → 初始化 `LlmInferenceEngine` → 创建通知（含停止按钮）→ 启动 NanoHTTPD。
  - `ACTION_STOP_SERVICE` / `ACTION_RESTART_SERVICE` 通过 Intent 控制；`stopServer()` 会拆除 HTTP 实例，`restartServer()` 会重新解析 IP。
  - 每次 `/chat` 请求使用 `withWakeLock { ... }` 包装，保证屏幕熄灭或后台时 CPU 保持工作，wakelock 超时时间 2 分钟。

### 3.2 NanoHTTPD 路由
- `LlmNanoHttpServer` 将所有 POST `/chat` 请求转给 Service 的 `handleChatRequest()`；返回 JSON：`{success, model, conversationId, response, latencyMs}`。
- `/health` 用于调试（简单返回服务状态）。

### 3.3 LlmInferenceEngine 细节
- 实例化 `BackgroundModelManagerViewModel`（与 UI 相同实现，只是脱离 Compose 生命周期）。
- `handleChatRequest()` 关键步骤：
  1. 确认模型列表加载完毕，输出日志。
  2. 根据请求/偏好选择模型 → 校验是否下载 → 判断任务类型（优先 `LLM_CHAT`）。
  3. 在 `Mutex` 内串行执行：
     - `ensureModelReady()`：若 `model.instance == null`，调用 `modelManagerViewModel.initializeModel()` 并等待 `ModelInitializationStatusType.INITIALIZED`。
     - 按需 `resetConversation()`（支持图像/音频任务）。
     - `runInference()`：启动 LiteRT streaming 推理，统计延迟并输出日志。
- **日志与 Watchdog**：
  - LlmInferenceEngine 用 token 追踪请求（handle / init / run / finish / error）。
  1. `LlmChatModelHelper` 新增 60 秒 watchdog，定期 `conversation.cancelProcess()`，若 LiteRT 长时间无响应会写日志并触发 `onError("Inference timed out")`。
  2. 所有 streaming chunk、`onDone`、`onError` 都会打印 `token + length` 信息。

### 3.4 DataStore / 设置的影响
- `DataStoreRepository.getWebServiceModelName()` 提供 Service 选择模型的默认值。
- Settings 中切换开关时发起 `LlmWebServerService.stop(context)` 或 `start(context)`，并在 `MainActivity` 里监听 DataStore 以实现 App 启动时自动恢复。

## 4. 构建 / 调试要点

### 4.1 构建
```
cd android_llm_server/gallery/Android/src
JAVA_HOME=/home/cx/.openclaw/workspace/tools/jdk-21.0.5+11 ./gradlew assembleRelease
```
- APK 输出：`gallery/Android/src/app/build/outputs/apk/release/app-release.apk`。
- 通过 `python3 -m http.server 8000 --bind 0.0.0.0` 提供下载，或使用 `./gradlew :app:installRelease` + `adb connect` 直接安装。

### 4.2 ADB over Wi-Fi（可选）
- 通过 WireGuard VPN 将手机和开发机置于 10.23.0.0/24；手机端 `adb tcpip 5555` 后 `adb pair 10.23.0.2:44007`（code 172817），再 `adb connect 10.23.0.2:44333`。
- 现阶段 VPN 服务已关闭；如需再次测试，重新启用 `wg-quick@wg0` 即可。

### 4.3 抓日志
```
adb logcat -s LlmInferenceEngine LlmWebServerService AGLlmChatModelHelper
```
- 可在 `/chat` 请求时同时 curl + logcat 观察，从 handle → init → run → chunk → watchdog 的全链路信息。

## 5. 后续待办
- 继续排查 LiteRT 在后台被系统限制导致 `/chat` 没响应的问题（可能是省电策略或 LiteRT 任务 bug）。现有 watchdog 仅能超时取消，尚未解决根因。
- 计划在 `/health` 接口返回更多状态（当前模型、初始化是否成功、最近一次推理时间、是否触发 watchdog）。
- 若需要完全后台运行，应在 Android Manifest 中申请电池优化白名单，并考虑通过 WorkManager/Foreground Service 互相唤醒。

---
以上总结了项目的运行逻辑，涵盖 UI 层与 Web Service 层的关键路径，方便日后查阅。欢迎在此文档基础上继续扩展细节（如流程图、配置指南等）。
