# llmserver_phone

`llmserver_phone` 旨在让 Android 真机成为一个本地大语言模型服务端。手机端加载离线模型后，通过内置的 HTTP Web Service 暴露 `/chat` 接口，供 Macrodroid、Tasker 等自动化工具在同一局域网或本机上直接调用，实现“手机即 LLM 节点”的使用体验。

项目基于 [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) 的 LiteRT Android 示例，保留原生 UI 推理体验的同时，按需扩展了 Service、配置项与日志能力。

---

## 功能特性

- **本地 UI 推理**：沿用 gallery 的 LLM Chat / Single-turn 流程，可下载/初始化模型、实时流式输出、查看历史对话。
- **HTTP Web Service**：应用内置 NanoHTTPD，前台 Service 监听端口并开放 `/chat` 与 `/health`，方便在自动化脚本中直接 POST 消息获取模型回答。
- **配置总控**：Settings 对话框新增 Web Service 开关、默认模型、推理硬件（CPU/GPU）、下载站点（官方 HF / hf-mirror.com）以及远端 allowlist 刷新按钮。
- **后台保活**：除了前台通知 + wakelock，还可启用 1×1 透明悬浮窗（Overlay keep-alive）来避免系统冻结 Service，实测可稳定保持后台推理。
- **内置日志面板**：App Drawer 中新增 Logs 页面，汇总 `LlmWebServerService`、`LlmInferenceEngine` 等模块日志，支持复制/清空，便于在手机上直接排查问题。
- **自定义分发**：应用名、图标资源已替换为 "llmserver"，release APK 可通过 `python3 -m http.server` 临时托管，方便侧载测试。

---

## 构建与安装

1. **准备依赖**
   - JDK：`JAVA_HOME=/home/cx/.openclaw/workspace/tools/jdk-21.0.5+11`
   - Android SDK：位于 `/home/cx/.openclaw/workspace/android_sdk`，`local.properties` 已指向该路径。
2. **编译 release APK**
   ```bash
   cd android_llm_server/gallery/Android/src
   ./gradlew assembleRelease
   ```
   成功后产物位于 `app/build/outputs/apk/release/app-release.apk`。
3. **安装或分享 APK**
   - 通过 `adb install app-release.apk` 或 `adb connect <device-ip>:<port>` 安装到测试机。
   - 或在 release 目录运行 `python3 -m http.server 8000 --bind 0.0.0.0` 对外提供下载链接。

---

## HTTP Web Service

- **默认端口**：`8081`，部署在手机本地。若通过 USB/Wi-Fi 端口转发或 VPN，可在外部访问。
- **健康检查**：`GET http://<device-ip>:8081/health` 返回简要状态。
- **对话接口**：`POST http://<device-ip>:8081/chat`

请求示例：
```bash
curl -X POST "http://127.0.0.1:8081/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好呀，我今天心情不错，你呢",
    "conversation_id": "test-session-1",
    "model": "DeepSeek-R1-Distill-Qwen-1.5B"
  }'
```

响应示例（截取）：
```json
{
  "success": true,
  "model": "DeepSeek-R1-Distill-Qwen-1.5B",
  "conversation_id": "test-session-1",
  "response": "你好呀，今天心情不错，谢谢你的关心！有什么我可以帮你的吗？",
  "latency_ms": 11688.0
}
```

> `conversation_id` 会复用旧对话；若留空则由服务端分配。模型名称需与本地 allowlist 匹配，未下载会返回错误提示。

---

## 设置与保活选项

- **Web Service 开关**：在 Settings 中启用后，`LlmWebServerService` 会作为前台服务运行，并在通知栏展示 IP + 端口（含“停止服务”按钮）。
- **默认模型 / 推理硬件**：DataStore 中持久化默认模型名及 CPU/GPU 下拉，Service 会在每次请求前按配置初始化模型并强制 accelerator。
- **下载站点**：默认使用 `https://hf-mirror.com/`，可切换回官方 Hugging Face 域。
- **远端 allowlist**：一键抓取最新 JSON 并写入 DataStore，方便动态更新可选模型列表。
- **Overlay keep-alive**：开启后会引导授予 `SYSTEM_ALERT_WINDOW` 权限，并在屏幕左上角放置 1×1 透明 View。实测该策略可阻止系统在后台冻结前台服务，使 `/chat` 请求无须唤醒 UI 亦可即时响应。

---

## 日志面板

- Drawer → **Logs** 可实时查看 Web Service、推理引擎、LiteRT streaming 的关键信息。
- 支持复制任意日志文本，与测试团队共享。
- “Clear” 按钮用于快速清空缓冲，便于复现问题时截取最小日志片段。

---

## 默认下载镜像

- 初始配置已将模型下载主站设置为 `https://hf-mirror.com/`，适合大陆网络环境。
- 若需要切换，可在 Settings 中选择 “Hugging Face 官方” 或自建镜像（后续可在 allowlist 中追加域名）。

> 以上内容将随项目持续更新。如需更多实现细节，请查阅 `project_architecture.md` 与 `project_memory.md`。
