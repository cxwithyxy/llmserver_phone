# android_llm_server 上游同步规划（2026-04-10）

## 背景

`android_llm_server` 是基于 `google-ai-edge/gallery`（LiteRT Android 示例）的二次开发分支，当前维护了以下定制功能：

| 定制内容 | 文件/模块 | 说明 |
|---------|----------|------|
| Web Service | `LlmWebServerService` + `LlmNanoHttpServer` | 前台 Service + NanoHTTPD，监听 `/chat` 与 `/health` 接口 |
| 推理引擎 | `LlmInferenceEngine` | 封装模型初始化、对话、流式推理逻辑 |
| 设置中心 | `settings.proto` + DataStore | Web service 开关、默认模型、推理硬件（CPU/GPU）、下载源 |
| 保活策略 | `overlay_keep_alive_enabled` | 透明悬浮窗（1×1 View）防止后台冻结 |
| 日志面板 | `LogsPanel` + 缓冲区 | App Drawer 中展示推理/Web Service 日志 |
| Inference Watchdog | `LlmChatModelHelper` | 60 秒超时取消长时间无响应 |

---

## 上游变化分析（`google-ai-edge/gallery`）

### 版本对比

| 项目 | 当前本地 | 上游最新 | 变化 |
|------|---------|---------|------|
| `versionCode` | 19 | 23 | +4 |
| `versionName` | 1.0.10 | 1.0.11 | +0.0.1 |
| `litertlm` SDK | `0.9.0-alpha06` | `0.10.0` | **_MAJOR_** |

### 新增功能（上游 0.10.0）

- ✅ **LiteRT SDK 升级**：`0.9.0-alpha06` → `0.10.0`（需验证 API 兼容性）
- ✅ **FCM 推送集成**：支持远程消息推送
- ✅ **AICore 集成**：新模型加载策略
- ✅ **默认运行时 litertlm**：`--runtime=litertlm`（旧版可能默认 TFLite）
- ✅ **音频消息支持**：`Contents.of(audioClips)`
- ✅ **高 max tokens 警告**：ConfigDialog UI 增强
- ✅ **模板/示例 Task**：virtual-piano、mood-tracker、calculate-hash、interactive-map
- ✅ **BuildWorkflow 更新**：`.github/workflows/build_android.yaml`

### 文件变更统计

```
472 files changed, 19278 insertions(+), 3298 deletions(-)
```

主要新增路径：
- `skills/featured/virtual-piano/`
- `skills/featured/mood-tracker/`
- `skills/calculate-hash/`
- `skills/interactive-map/`

---

## 同步策略（方案 A）

### 目标

- **保留所有定制功能**（Web Service、Setting、Logs 等）
- **同步上游基础框架**（UI、Task 系统、LiteRT SDK 升级）
- **最小化冲突**（通过分阶段合并 + 详细 resolution 指南）

---

## 执行步骤

### Step 1：备份与分支

```bash
cd /home/cx/.openclaw/workspace/android_llm_server

# 备份当前 gallery 目录（用于回滚）
cp -r gallery gallery_backup_20260410

# 创建同步分支
cd gallery
git checkout -b sync-upstream-20260410
```

---

### Step 2：添加上游远程仓库（如未添加）

```bash
# 确认 upstream 已添加
git remote -v

# 如果没有 upstream，添加（已验证可用）
git remote add upstream https://github.com/google-ai-edge/gallery.git
git fetch upstream refs/heads/main --depth=1
```

---

### Step 3：合并上游主分支

```bash
# 合并上游 main 分支（会有冲突）
git merge upstream/main --no-edit
```

**预期冲突文件列表**：

| 文件 | 冲突类型 | 处理策略 |
|------|---------|---------|
| `Android/src/app/build.gradle.kts` | `versionCode`, `versionName`, `applicationId`, `litertlm` | 按本地版本为准 |
| `Android/src/app/src/main/AndroidManifest.xml` | `LlmWebServerService`, `overlay_keep_alive` | 保留定制组件 |
| `Android/src/gradle/libs.versions.toml` | `litertlm` 版本号 | 同步为 `0.10.0` |
| `gallery/Android/src/app/src/main/assets/model_allowlist.json` | 模型条目/字段 | 保留定制模型，同步字段 |
| `gallery/Android/src/app/src/main/res/values/strings.xml` | `app_name` | 保留 `llmserver` |

---

### Step 4：冲突 resolution 详细指南

#### 4.1 `build.gradle.kts`

**本地定制**：
```kotlin
android {
  applicationId = "com.cx.llmserver"
  versionCode = 19
  versionName = "1.0.10"
}

dependencies {
  implementation(libs.litertlm)  // 0.9.0-alpha06
}
```

**上游版本**：
```kotlin
android {
  applicationId = "com.google.ai.edge.gallery"  // 需覆盖
  versionCode = 23  // 需覆盖
  versionName = "1.0.11"  // 需覆盖
}

dependencies {
  implementation(libs.litertlm)  // 0.10.0 → 同步
}
```

** Resolution **：
```kotlin
android {
  applicationId = "com.cx.llmserver"
  versionCode = 19  // 保留本地值
  versionName = "1.0.10"  // 保留本地值
}

dependencies {
  implementation(libs.litertlm)  // 将在 libs.versions.toml 中同步为 0.10.0
}
```

---

#### 4.2 `AndroidManifest.xml`

**本地定制**：
```xml
<application android:label="llmserver">
  <activity ... />
  
  <!-- Web Service -->
  <service android:name=".webservice.LlmWebServerService" />
  
  <!-- Overlay Keep-Alive -->
  <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
</application>
```

**上游版本**（新增/修改）：
```xml
<application android:label="Gallery">
  <!-- 新增 FCM 推送服务 -->
  <service android:name=".FcmMessagingService" android:exported="false">
    <intent-filter android:priority="500">
      <action android:name="com.google.firebase.messaging.message_text" />
    </intent-filter>
  </service>
</application>
```

** Resolution **：
```xml
<application android:label="llmserver">
  <!-- 保留本地定制 -->
  <service android:name=".webservice.LlmWebServerService" />
  
  <!-- 保留权限 -->
  <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
  
  <!-- 合并上游 FCM 服务（如不需要可注释） -->
  <!-- <service android:name=".FcmMessagingService" ... /> -->
</application>
```

---

#### 4.3 `libs.versions.toml`

**修改项**：
```toml
[versions]
# 保留其他版本不变
litertlm = "0.10.0"  # 从 0.9.0-alpha06 升级
```

---

#### 4.4 `model_allowlist.json`

**本地定制**（示例）：
```json
{
  "models": [
    {
      "name": "Gemma-3n-E2B-it-int4",
      "modelFile": "gemma-3n-E2B-it-int4.task",
      ...
    },
    {
      "name": "Qwen2.5-1.5B-Instruct q8",
      "modelFile": "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
      ...
    }
  ]
}
```

**上游新增**（示例）：
```json
{
  "models": [
    {
      "name": "Gemma3-1B-IT q4",
      "modelId": "litert-community/Gemma3-1B-IT",
      "modelFile": "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
      "taskTypes": ["llm_chat", "llm_prompt_lab"]
    }
  ]
}
```

** Resolution **：
```json
{
  "models": [
    // 保留本地定制模型
    {
      "name": "Gemma-3n-E2B-it-int4",
      "modelId": "google/gemma-3n-E2B-it-litert-preview",
      "modelFile": "gemma-3n-E2B-it-int4.task",
      ...
    },
    {
      "name": "Qwen2.5-1.5B-Instruct q8",
      "modelId": "litert-community/Qwen2.5-1.5B-Instruct",
      "modelFile": "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
      ...
    },
    // 同步上游新增模型
    {
      "name": "Gemma3-1B-IT q4",
      "modelId": "litert-community/Gemma3-1B-IT",
      "modelFile": "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
      "taskTypes": ["llm_chat", "llm_prompt_lab"]
    }
  ]
}
```

---

### Step 5：编译验证

```bash
cd /home/cx/.openclaw/workspace/android_llm_server/gallery/Android/src

# 确保环境变量
export JAVA_HOME=/home/cx/.openclaw/workspace/tools/jdk-21.0.5+11
export ANDROID_HOME=/home/cx/.openclaw/workspace/android_sdk

# 清理旧构建
./gradlew clean

# 编译 debug 包（快速验证）
./gradlew assembleDebug
```

**预期输出**：
```
BUILD SUCCESSFUL in Xs
X actionable tasks: X executed
```

---

### Step 6：功能测试矩阵

| 测试项 | 原因 | 验证方式 |
|--------|------|----------|
| UI 推理流程 | LiteRT SDK 升级可能影响 API | 下载模型 → 初始化 → 聊天 → 流式输出 |
| Web Service `/chat` | 定制功能优先级高 | POST `http://<device>:8081/chat` |
| Settings 开关 | DataStore 逻辑需保留 | Web service 开关 → Service 启停 |
| inference watchdog | 定制功能 | 模拟长时间无响应 → 观察日志 |
| overlay keep-alive | 定制功能 | 启用悬浮窗 → 验证 Service 不冻结 |
| 日志面板 | 定制功能 | Drawer → Logs → 查看/复制日志 |

---

### Step 7：后续迭代建议

#### 7.1 LiteRT SDK 升级检查清单（`0.9.0` → `0.10.0`）

| 项目 | 检查点 | 链接 |
|------|--------|------|
| `EngineConfig` 构造 | `backend`, `visionBackend`, `audioBackend` 参数是否变更 | LiteRT changelog |
| `ConversationConfig` | `samplerConfig`, `systemInstruction` 是否新增字段 | LiteRT changelog |
| `Content` 类型 | `Text`, `ImageBytes`, `AudioBytes` API 是否稳定 | LiteRT changelog |
| `ExperimentalFlags` | 是否有 breaking change | LiteRT changelog |

**代码迁移检查点**（`LlmChatModelHelper.kt`）：
```kotlin
// 可能需要调整的部分（仅示意，以实际 API 为准）
val engineConfig = EngineConfig(
  modelPath = modelPath,
  backend = preferredBackend,
  visionBackend = if (shouldEnableImage) Backend.GPU() else null,
  audioBackend = if (shouldEnableAudio) Backend.CPU() else null,
  maxNumTokens = maxTokens,
  cacheDir = if (modelPath.startsWith("/data/local/tmp")) ... else null,
)
```

---

#### 7.2 新功能选型（可选）

| 功能 | 是否启用 | 说明 |
|------|---------|------|
| FCM 推送 | ❌（暂不启用） | 需要 Firebase 配置，非当前必需 |
| AICore 集成 | ❌（暂不启用） | 新模型加载策略，当前 `litertlm` 已满足需求 |
| 新模板 Task | ❌（暂不启用） | virtual-piano/mood-tracker 属于示例代码，非核心功能 |

> 以上功能可根据后续需求逐步集成，当前优先保证 Web Service + Settings 稳定。

---

## 风险与回滚方案

### 风险项

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| LiteRT SDK API 不兼容 | 中 | 高 | Step 6 测试覆盖 |
| Git 合并冲突解决错误 | 低 | 中 | 分阶段合并 + 备份 |
| 覆盖本地定制导致功能失效 | 中 | 高 | 严格按 Step 4 resolution 指南 |

### 回滚方案

```bash
# 如果合并后发现严重问题，回滚到备份分支
cd /home/cx/.openclaw/workspace/android_llm_server/gallery

# 保留备份目录（已存在）
cp -r gallery_backup_20260410 gallery_rollback

# 回滚到备份
rm -rf gallery/*
cp -r gallery_rollback/* gallery/

# 重新提交（如果已 push）
git reset --hard HEAD
```

---

## 下一步行动项（等待用户确认）

1. ✅ 本规划文档已保存为 `UPSTREAM_SYNC_PLAN.md`
2. ⏳ **等待用户确认**：如果可行，请回复 "确认同步"，我将执行 Step 1～Step 4 的合并操作
3. ⏳ **后续步骤**：用户确认后，我将：
   - 执行 `git merge upstream/main`
   - 指导冲突 resolution（按 Step 4 指南）
   - 验证编译通过
   - 提供测试包下载链接

---

## 附录：关键命令速查

```bash
# 查看差异
git diff HEAD upstream/main --stat

# 查看具体文件差异
git diff HEAD...upstream/main -- Android/src/app/build.gradle.kts

# 查看上游新增 commit
git log --oneline HEAD..upstream/main -20

# 强制回滚到当前分支最新提交（未合并前）
git reset --hard HEAD

# 回滚到备份目录
cp -r gallery_backup_20260410/* gallery/
```

---

## 文档版本

- **创建时间**：2026-04-10
- **版本**：v1.0
- **维护者**：技术助理
- **关联 issue**：None
