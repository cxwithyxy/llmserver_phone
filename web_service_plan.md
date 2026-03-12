# Android LLM Server – 内置 Web 服务规划

> 目标：在现有 Android App 内嵌一个可被局域网 POST 调用的大模型对话 Web 服务，暂不实现，仅先规划改动点。

## 1. Web 服务底座

| 项目 | 说明 |
| --- | --- |
| HTTP Server 选型 | 引入轻量级嵌入式服务器，如 [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) 或 Android Jetty/OkHttp Server。要支持 Android API 31+，占用内存低。 |
| 启动方式 | 在 `Application` 或 Foreground Service 中初始化单例服务器；需在应用进入前台或用户显式开启时启动。 |
| 路由 & 协议 | 预留 `POST /chat`（对话）、`GET /health`（健康检查）。Body 使用 JSON，编码 UTF-8。 |
| 认证/访问控制 | 可选：简单 token、Basic Auth、或局域网白名单，防止未授权访问。 |
| 配置项 | 端口（默认 8081/0 动态）、开关、凭证。存储在 `SharedPreferences` 或本地配置文件。 |

## 2. 与现有大模型交互

1. **Session 复用**：复用 app 内已有大模型加载/推理模块（LiteRT/Llama.cpp 等），对外暴露无 UI 的 `LLMService`。
2. **多轮状态**：
   - Web 客户端按 `conversation_id` 或 `session_id` 传输入参。
   - App 维护一个 `Map<String, ConversationState>`（内存+可选持久化）。
3. **异步执行**：POST 请求转到后台协程 / `HandlerThread`，推理完成后返回 JSON：
   ```json
   {
     "conversation_id": "abc",
     "response": "...",
     "usage": {"prompt_tokens": 123, "completion_tokens": 456}
   }
   ```
4. **错误处理**：模型未加载、输入过长、推理超时、内存不足等需返回明确错误码（4xx/5xx）。

## 3. 应用层结构改动

| 模块 | 需要的改动 |
| --- | --- |
| Foreground Service / WorkManager | 提供“Web 服务进程守护”，在锁屏/后台保持运行并展示常驻通知。 |
| 设置入口 | 在应用设置页或隐藏调试菜单中增加 Web 服务开关、端口、token 配置。 |
| 权限 & Manifest | 确认已有 `android.permission.INTERNET`；若在后台监听局域网端口，需考虑 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（可选）。 |
| 日志 & 监控 | 记录请求耗时、来源 IP、错误栈，便于调试。 |

## 4. 依赖与开发流程

1. **Gradle**：在 `app/build.gradle.kts` 添加 HTTP server 依赖（如 `implementation("org.nanohttpd:nanohttpd:<version>")`）。
2. **接口文档**：在项目 `docs/` 或 README 中新增 API 说明（请求/响应、示例 curl、错误码）。
3. **测试策略**：
   - **单元测试**：模型交互接口的输入输出验证。
   - **仪表测试**：在设备/模拟器上确保 HTTP 路由可用、后台稳定。
   - **负载测试**：局域网环境下模拟多客户端并发。
4. **安全/性能**：
   - 限制最大请求体（如 1 MB），防止内存溢出。
   - 并发控制（排队或拒绝策略）。
   - 可选 TLS（局域网一般明文，若需加密可考虑自签证书 + OkHttp H2）。

## 5. 开发里程碑建议

1. **基础设施**：嵌入 HTTP server，提供 `/health`，验证可在局域网访问。
2. **模型桥接**：封装后台 LLMService，完成 `POST /chat` -> 模型 -> 返回。
3. **会话 & 配置**：会话管理、配置 UI、认证机制。
4. **打包发布**：更新文档，提供测试脚本，验证在真实设备上稳定运行。

---
*后续开发方向待定，本规划作为初版思路文档。*