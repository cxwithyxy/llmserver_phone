# Android LLM Server – 后台 Web 服务增强方案

## 问题
当前 `LlmWebServerService` 只保证 HTTP 服务器常驻，模型推理仍依赖 UI 层的 `ModelManagerViewModel` / `LlmChatModelHelper`。当 App 退到后台后，ViewModel 可能被系统挂起或其内部的 LiteRT 实例被释放，导致 `/chat` 请求没有任何推理执行，最终 HTTP 超时。

## 改造目标
- 即使 App 在后台（屏幕熄灭、Activity 被系统回收），Web 服务仍能调用模型完成推理。
- 允许 Service 自行管理模型初始化 / 会话，并在推理期间保持 CPU 唤醒，避免 Doze/休眠导致推理中断。

## 设计要点

### 1. 后台推理引擎
- 新增 `LlmInferenceEngine`（或等价的 Repository），由 `LlmWebServerService` 实例化并保持在 Service 生命周期内。
- 作用：加载模型列表、下载状态、初始化指定模型、管理对话上下文、对外暴露 `suspend fun runChat(request)`。
- 不依赖 Compose/Activity ViewModel。只使用 `applicationContext`、`DownloadRepository`、`DataStoreRepository` 等基础依赖。

### 2. 模型生命周期
- Service 启动时：
  - 读取 DataStore 的默认模型名。
  - 通过 `LlmInferenceEngine.prepareModel(modelName)` 下载/初始化。
- Service 销毁时：
  - 主动调用 `engine.shutdown()`，释放 LiteRT 资源。
- 若 Settings 切换模型：
  - 直接调用 Service 的 `restart()`，新的模型名称通过 DataStore/Intent 传入，`engine` 重建。

### 3. 推理执行线程
- 在 Service 内创建专用 `CoroutineScope(Dispatchers.Default + SupervisorJob())` 或 `HandlerThread`。
- 所有 LiteRT 推理 (包括流式回调) 在该线程进行，避免依赖 UI 主线程。
- `runInference()` 通过 `suspendCancellableCoroutine` 或 Channel 把结果返回 HTTP Handler。

### 4. 唤醒锁管理
- 为防止 CPU 休眠导致推理中断，Service 需要：
  - 申请 `android.permission.WAKE_LOCK`。
  - 在每次 `/chat` 请求开始前获取 `PowerManager.newWakeLock(PARTIAL_WAKE_LOCK)`，设置超时时间（如 2 分钟）。
  - 推理结束后释放 wakelock。
- 可选：在设置页，让用户选择“允许后台推理”或“仅前台推理”。

### 5. Foreground Service & 通知
- 保持 `startForeground()`，现有通知展示 IP:Port + 停止按钮。
- 若加入 wakelock，通知可追加提示“后台推理中”。

### 6. LiteRT 兼容性
- 确认 LiteRT 的 `LlmChatModelHelper` 可以在没有 UI 的上下文独立运行；必要时复制/精简 helper，只保留模型会话部分。
- 对于设备限制后台 GPU/NPU 的情况，可在设置里提示“后台推理可能受限”。

## 开发步骤
1. 抽象 `LlmInferenceEngine`
   - 封装模型选择、下载状态监听、初始化、对话缓存。
   - 公开 `suspend fun execute(request: LlmWebRequest): LlmWebResponse`。
2. Service 改造
   - 替换掉 `BackgroundModelManagerViewModel`，改为持有 `LlmInferenceEngine`。
   - 引入 wakelock 支持、CoroutineScope、restart/stop 流程。
3. HTTP Handler 更新
   - `LlmNanoHttpServer` 调用新的 Engine；请求进入时获取 wakelock，完成后释放。
   - 错误处理保持一致（下载未完成、模型不支持等）。
4. 设置 & 权限
   - 在 Manifest 中添加 `WAKE_LOCK` 权限。
   - 设置页增加“允许后台推理”开关（可选）。
5. 测试
   - 验证前台/后台、屏幕熄灭情况下都能成功响应 `/chat`。
   - 观察 wakelock 释放、通知交互、错误处理。

---
*以上方案待确认后实施。*
