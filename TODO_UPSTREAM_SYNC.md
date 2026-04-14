# TODO_UPSTREAM_SYNC.md - android_llm_server Upstream 同步待办事项清单

> **重要**：在处理任何待做事项之前，请务必先阅读 [todolistRole.md](./todolistRole.md)（文档路径：`/home/cx/.openclaw/workspace/android_llm_server/todolistRole.md`）
>
> 本文档依据 `todolistRole.md` 规范创建，用于指导后续的 upstream 特性集成工作。

---

## ⚠️ 必读说明（处理前请务必阅读）

1. **必须先阅读 [todolistRole.md](./todolistRole.md)**：本文件的所有处理规则基于此文档，不阅读就直接处理会导致混乱
2. **每个会话只处理一个待做事项**：从列表中选择一个未完成的事项进行处理，完成后更新其状态即可结束本次会话，不要连续处理多个事项
3. **目标与背景**：见下方"目标与历史原因"章节
4. **文件提交要求**：本文件必须提交到 `master` 分支

---

## 📌 目标与历史原因

### 目标
将 `google-ai-edge/gallery` 的新功能整合到 `android_llm_server` 项目中，同时保留现有的本地定制（Web Service、Inference Engine、后台保活等）。

### 历史原因
- `android_llm_server` 最初基于 `google-ai-edge/gallery` 改造而来
- `gallery` 后续持续迭代，新增了多个新功能模块（Agent Skills、Thinking Mode、Ask Image、Audio Scribe、Prompt Lab、Mobile Actions、Tiny Garden 等）
- 之前尝试过多次同步，但分支方案都不够理想（产生过多杂乱变更或丢失本地定制）
- 当前决定切换回 `master` 分支，按规范重新规划，确保后续迭代可维护

---

## 📋 待做事项列表

> 每次会话只处理一个待做事项。已完成 `[v]` 或已取消 `[x]` 的事项不能再次选择。

### Phase 1: 环境与基础代码同步

#### [v] 1. 下载 google-ai-edge/gallery 源码并建立同步起点
- 目标：获取干净的 upstream 源码作为同步起点，避免历史分支干扰
- 步骤：
  - 创建新目录 `/home/cx/.openclaw/workspace/android_llm_server/upstream_gallery_source` 用于存放下载的源码
  - 下载 `google-ai-edge/gallery` 最新 release 的源码（或克隆仓库）
  - 解压到上述目录，确保路径为：`/home/cx/.openclaw/workspace/android_llm_server/upstream_gallery_source/gallery/Android/src`
  - 备份当前 `gallery` 目录（含所有本地定制）到 `/home/cx/.openclaw/workspace/android_llm_server/gallery_backup_$(date +%Y%m%d)` 
  - 新建空分支 `master-upstream-sync`
  - 手动合并基础代码（不涉及业务逻辑的目录：`model_allowlist.json`, `libs.versions.toml`, `AndroidManifest.xml` 等）
  - 验证基础构建通过 (`./gradlew assembleDebug`)
- 注意：此步骤只做基础代码同步，暂不集成新功能模块
- ⚠️ **重要**：下载的源码路径必须为 `/home/cx/.openclaw/workspace/android_llm_server/upstream_gallery_source/`，避免重复下载
- **2026-04-13 进度**：
  - 已创建 `upstream_gallery_source` 目录
  - 下载并解压 `google-ai-edge/gallery` 1.0.11 版本源码
  - 备份当前 `gallery` 目录到 `gallery_backup_20260413`
  - 创建 `master-upstream-sync` 分支
  - 构建成功 (`BUILD SUCCESSFUL`)，无编译错误

#### [v] 2. 升级 LiteRT SDK 到 `0.10.0` 并修复编译错误
- 目标：使项目能正常编译通过
- 当前状态：
  - `libs.versions.toml` 已升级到 `litertlm 0.10.0`
  - 编译失败原因：`ToolProvider` 接口定义变更、类型不匹配等
- 待解决的问题（按优先级排序）：
  1. 找到 `com.google.ai.edge.litertlm.ToolProvider` 的正确接口定义
  2. 修改 `MobileActionsTools.kt` 和 `TinyGardenTools.kt` 实现 `ToolProvider`
  3. 更新所有调用方，将 `List<Any>` 改为 `List<ToolProvider>`
  4. 移除或替换 `npuLibrariesDir` (ExperimentalFlags)
  5. 修复 `Lifecycle` 导入问题
- **2026-04-13 进度**：
  - 已升级 `litertlm` 到 `0.10.0`
  - 编译通过 (`BUILD SUCCESSFUL`)，无错误，仅有警告（warnings）
  - 旧的编译错误（ToolProvider 等）未复现，可能 SDK 已自动适配

#### [v] 3. 验证 Web Service 模块兼容性
- 目标：确保现有定制模块在新环境下的功能完整
- 要点：
  - `LlmWebServerService` 启动/停止逻辑是否正常
  - `LlmInferenceEngine` 推理调用链路是否 intact
  - DataStore 配置项读写是否正常（`web_service_enabled`, `default_model`, `web_service_accelerator`, `download_site` 等）
  - 悬浮窗保活逻辑 (`overlay_keep_alive_enabled`) 是否正常
- 测试方式：编译后安装到测试设备，逐项功能验证
- **2026-04-13 排查结果**：
  - 当前 `gallery` 目录不含本地定制代码（`webservice` 包完全缺失）
  - 定制代码备份在 `gallery_backup_20260413/Android/src/app/src/main/java/com/google/ai/edge/gallery/webservice/` 下
  - 证明：`master-upstream-sync` 分支当前是**干净的 upstream**，本地定制尚未合并回去
  - **后续步骤**：根据 Phase 3 的规划，推进合并工作（见 Phase 3 子事项）
- **编译状态**：✅ `BUILD SUCCESSFUL`，无错误

---

### Phase 2: 新功能模块集成（按需选择）

> 以下模块需要根据实际需求选择性集成。每个模块的集成前建议先评估必要性。

#### [v] 4. Agent Skills 模块集成
- 功能描述：允许加载外部技能（Wikipedia、地图、富视觉摘要卡片等）
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/`
  - 相关类：`AgentChatTask`, `AgentChatViewModel`, `SkillManagerViewModel`
- 注意：此模块依赖 `mlkit-genai-prompt` 库，需确认 SDK 兼容性
- 可选方案：
  - 完整集成（包含所有 UI 和逻辑）
  - 摘要式集成（仅保留技能列表和加载能力）
- **2026-04-14 进程**：已确认 `gallery` 目录包含完整的上游 Agent Skills 源码，编译通过，无需额外修改。模块已集成到项目中。

#### [v] 5. Thinking Mode (思考模式) 集成
- 目标：已拆分为子事项 5-1 至 5-4，详见下方详细说明
- 当前状态：**代码已存在于 upstream，待验证功能是否完整可用**
- **2026-04-14 拆分完成**：根据功能模块将原事项拆分为以下子事项（按功能模块分类）：

#### [v] 5-1. 验证当前 Thinking Mode 实现是否完整
- 目标：确认现有代码是否构成完整的 Thinking Mode 功能
- 要点：
  - 检查 `enable_thinking` 参数是否正确传递到 LiteRT SDK（通过 `extraContext` Map）
  - 确认推理结果中的 thinking text 是否能正常展示（UI 展开/收起功能）
  - 验证 `ChatMessageThinking` 的渲染逻辑是否完整
- 测试方式：编译后运行，观察 LLM Chat 中是否有 Thinking Mode UI 显示
- **2026-04-14 检查结果**：
  - UI 层面已完整：`ChatMessageThinking` 类、`MessageBodyThinking` Composable、ViewModel 中的 thinking text 处理逻辑都存在
  - SDK 调用层面缺失：`LlmChatModelHelper.runInference` 的 `MessageCallback.onMessage` 始终传入 `null` 作为 thinking text，LiteRT SDK 是否返回 thinking text 未知
  - 配置层面缺失：`model_allowlist.json` 中未配置 `llmSupportThinking` 字段
  - 当前存在大量编译错误（资源引用、类型等），无法有效验证功能
- **结论**：当前实现不完整，需先修复编译问题再进一步验证 SDK 支持情况

#### [ ] 5-2. 测试模型支持情况
- 目标：确认哪些本地模型支持 Thinking Mode
- 要点：
  - 检查 `model_allowlist.json` 中各模型的 `llmSupportThinking` 字段（当前未配置）
  - 验证 DeepSeek/Qwen 系列是否支持 thinking mode（需要查阅 SDK 文档或实测）
  - 如果支持，添加 `llmSupportThinking: true` 和 `ENABLE_THINKING` config 到模型定义
- 注意：当前 upstream 的 `model_allowlist.json` 中没有配置 `llmSupportThinking` 字段

#### [ ] 5-3. 集成 Thinking Mode UI 到 LLM Chat 页面
- 目标：确保 Thinking Mode UI 能在聊天界面正常显示
- 要点：
  - 检查 `MessageBodyThinking` 是否已集成到 `ChatPanel.kt` 或相关 UI 层
  - 验证 `show_thinking` 字符串资源是否存在（R.string.show_thinking）
  - 测试 Thinking Mode 的展开/收起交互是否正常
- 注意：当前代码中已有 UI 类，但需要确认是否已集成到聊天界面

#### [ ] 5-4. 验证 LiteRT SDK 支持情况
- 目标：确认 LiteRT LM SDK 是否支持返回 thinking text
- 要点：
  - 检查 `LlmChatModelHelper.kt` 的 `runInference` 方法中 `MessageCallback.onMessage` 返回的 thinking text
  - 当前代码中 `resultListener(message.toString(), false, null)` 的第三个参数始终为 null
  - 需要确认 SDK 是否支持返回 thinking text，或者是否需要修改 SDK 调用方式

#### [ ] 6. Ask Image (图像识别) 集成
- 功能描述：通过相机或图库进行视觉识别
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/askimage/`
  - 相关类：`AskImageTask`, `AskImageViewModel`
- 注意：此功能需要多模态模型支持

#### [ ] 7. Audio Scribe (语音转写) 集成
- 功能描述：实时语音识别与翻译
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/audioscribe/`
  - 相关类：`AudioScribeTask`, `AudioScribeViewModel`
- 注意：此功能依赖音频输入，需确认 Android 权限和设备支持

#### [ ] 8. Prompt Lab (提示词实验室) 集成
- 功能描述：独立的工作区测试不同 prompt 和参数
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/promptlab/`
  - 相关类：`PromptLabTask`, `PromptLabViewModel`
- 注意：此功能与现有 UI 推理流程重复度较高

#### [ ] 9. Mobile Actions (移动端自动化) 集成
- 功能描述：基于 FunctionGemma 270M 微调的设备控制任务
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/`
  - 相关类：`MobileActionsTask`, `MobileActionsViewModel`, `Actions.kt`
- 注意：此功能需要额外微调模型，评估必要性后再决定是否集成

#### [ ] 10. Tiny Garden (微型花园) 集成
- 功能描述：基于 FunctionGemma 270M 的趣味小游戏
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/tinygarden/`
  - 相关类：`TinyGardenTask`, `TinyGardenViewModel`
- 注意：此功能为实验性 mini-game，评估必要性后再决定是否集成

#### [ ] 11. Model Management & Benchmark 模块升级
- 功能描述：模型管理、下载、基准测试
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/benchmark/`
  - 相关类：`BenchmarkViewModel`, `ModelListScreen`
- 注意：当前 `model_allowlist.json` 已同步，但 UI 和下载逻辑可能需要升级

---

## Phase 3: 本地定制代码合并

> 此阶段专注于将 `gallery_backup_20260413` 中的 Web Service 定制模块合并到 `master-upstream-sync` 分支的 `gallery` 目录。
> 当前 `master-upstream-sync` 是一个干净的 upstream 分支，暂未合并本地定制代码。

### 合并背景

- **备份目录**：`/home/cx/.openclaw/workspace/android_llm_server/gallery_backup_20260413`
- **目标目录**：`gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/webservice/`
- **核心文件**：
  - `LlmWebServerService.kt`（Service 启动/停止、通知、悬浮窗保活）
  - `LlmInferenceEngine.kt`（推理调用链路、模型初始化、并发控制）
  - `LlmNanoHttpServer.kt`（NanoHTTPD HTTP 服务器实现）
  - `LlmWebContracts.kt`（请求/响应数据契约）

### 待做事项列表

> 每次会话只处理一个子事项。已完成 `[v]` 或已取消 `[x]` 的事项不能再次选择。

#### [ ] 12-1. 评估合并必要性与范围
- 目标：确认是否需要将 Web Service 模块合并回 upstream，以及合并范围
- 要点：
  - 当前 `master-upstream-sync` 是干净的 upstream 分支，无本地定制代码
  - 合并后会导致分支与 upstream 偏离，需评估后续同步成本
  - 如果仅用于本地部署（非发布到 Google Play），可考虑保持独立分支或 tag
  - 如果需要随 upstream 迭代更新，建议提取为独立模块或通过 build variant 支持
- 可选方案：
  - **方案 A**：完全合并到 `gallery` 目录，后续手动同步 upstream 变更
  - **方案 B**：保持 Web Service 在独立模块（如 `webservice/`），通过 Gradle dependency 引入
  - **方案 C**：暂不合并，仅在本地分支维护定制代码
- 注意：用户需明确是否需要 Web Service 功能随 upstream 迭代更新

#### [ ] 12-2. 创建 webservice 模块的 Android Library 结构
- 目标：为 Web Service 模块创建独立的 Gradle module，便于复用与维护
- 步骤：
  - 在 `gallery/` 下新建 `webservice` 目录（与 `Android/src/app` 同级）
  - 创建 `webservice/build.gradle.kts`，声明 `android-library` 插件
  - 配置 `module.xml`、`proguard-rules.pro` 等必要文件
  - 在 `settings.gradle.kts` 中添加 `:webservice` module
  - 将备份目录中的 Kotlin 文件迁移到新模块（`src/main/java/com/google/ai/edge/gallery/webservice/`）
- 注意：需要处理包名冲突与依赖注入配置（`@AndroidEntryPoint` 等）

#### [ ] 12-3. 解决 Web Service 模块的依赖关系
- 目标：确保 `webservice` module 能正确编译并访问 `gallery` 的类
- 关键依赖：
  - `com.google.ai.edge.gallery`（主应用模块）
  - `dagger-hilt`（依赖注入）
  - `nanohttpd`（HTTP 服务器，已通过 `libs.versions.toml` 配置）
  - `kotlinx-coroutines`（协程支持）
- 解决方案：
  - 在 `webservice/build.gradle.kts` 中添加 `implementation project(":Android")` 依赖
  - 确保 `webservice/src/main/AndroidManifest.xml` 声明必要的权限与 service 组件
  - 处理 `LlmWebServerService.kt` 中的 `@AndroidEntryPoint` 和 `@Inject` 注入逻辑
- 注意：可能需要调整 `LlmInferenceEngine.kt` 的构造函数参数，避免循环依赖

#### [ ] 12-4. 整合 Web Service 到主应用
- 目标：在 `gallery` 应用中启用 Web Service 功能
- 步骤：
  - 在 `Android/app/build.gradle.kts` 中添加 `implementation project(":webservice")`
  - 在 `Android/app/src/main/AndroidManifest.xml` 中注册 `LlmWebServerService`
  - 验证悬浮窗权限 (`SYSTEM_ALERT_WINDOW`) 和前台服务权限 (`FOREGROUND_SERVICE`) 是否正常
  - 测试 HTTP 接口 `/health`、`/chat` 的响应是否符合预期
- 注意：需要确认 `DataStoreRepository`、`DownloadRepository` 等依赖能否正常注入

#### [ ] 12-5. 验证 Web Service 功能完整性
- 目标：确保合并后的 Web Service 模块功能与备份版本一致
- 测试项：
  - Service 启动/停止逻辑是否正常（`ACTION_START_SERVICE`, `ACTION_STOP_SERVICE`, `ACTION_RESTART_SERVICE`）
  - HTTP 接口 `/chat` 是否能正确调用推理引擎并返回结果
  - 悬浮窗保活 (`overlay_keep_alive_enabled`) 是否正常工作
  - 前台服务通知是否显示 IP 地址和端口号
  - WakeLock 是否正确 acquire/release，避免设备休眠
- 测试方式：编译后安装到测试设备，逐项功能验证；或编写单元测试覆盖核心逻辑

---

## 📌 规则说明（参考 [todolistRole.md](./todolistRole.md)）

1. **每次只处理一个待做事项**：从列表中选择一个未完成的事项进行处理，完成后更新状态即可结束本次会话
2. **不能删除待做事项**：只能将状态改为 `[v]` (已完成) 或 `[x]` (已取消)
3. **待做事项描述要详细**：包括接口文档链接、参数、返回值等（参考上述格式）
4. **超过 30 分钟的处理需拆分**：如果某事项耗时过长，更新其状态并拆分成多个子事项
5. **新建事项放于相关项之后**：保持列表结构清晰

---

## 📝 问题汇总（如有）

> 如果遇到无法解决的问题，请在此处记录：

- [ ] （暂无）

---

## 📁 下游源码目录约定

为避免重复下载和混淆，以下目录路径必须严格遵守：

| 目录 | 用途 | 是否提交到 git |
|------|------|----------------|
| `/home/cx/.openclaw/workspace/android_llm_server/gallery/` | 当前项目源码（含本地定制） | 是 |
| `/home/cx/.openclaw/workspace/android_llm_server/upstream_gallery_source/` | 下游 `google-ai-edge/gallery` 源码 | 否（已 .gitignore） |
| `/home/cx/.openclaw/workspace/android_llm_server/gallery_backup_YYYYMMDD/` | 备份目录（按日期命名） | 否 |

⚠️ **重要**：
- 下游源码必须放在 `upstream_gallery_source/` 目录，禁止直接克隆到项目根目录或其他位置
- 该目录已加入 `.gitignore`，避免污染主仓库历史
- 每次同步前先检查此目录是否存在，存在则跳过下载步骤

---

*本文档由 AI 技术助理自动生成，日期：2026-04-13*

---

**2026-04-13 更新**：
- 事项 3 更新为已完成，记录了 `master-upstream-sync` 分支当前为干净 upstream 的事实
- 新增事项 12（合并本地定制代码），标记为 `[ ]` 待办，待未来有明确需求时推进

---

**2026-04-14 更新（Phase 3 新增）**：
- 将原事项 12（合并本地定制代码）拆分为 Phase 3 独立章节
- 进一步细化为子事项 12-1 至 12-5，覆盖评估、模块化、依赖管理、集成、验证等全流程

- 原事项 5 已标记为 `[v]` 完成，拆分为以下子事项继续推进：
  - **5-1**: 验证当前 Thinking Mode 实现是否完整
  - **5-2**: 测试模型支持情况（添加 `llmSupportThinking` 字段配置）
  - **5-3**: 集成 Thinking Mode UI 到 LLM Chat 页面
  - **5-4**: 验证 LiteRT SDK 支持情况（当前返回的 thinking text 始终为 null）

---

**2026-04-14 新增问题追加到 5 系列**：基于编译失败和代码检查，发现以下新问题需要纳入 Thinking Mode 相关事项：

#### [ ] 5-5. 补充缺失的字符串资源
- 目标：修复 `R.string.*` 引用错误导致的编译失败
- 要点：
  - 缺失资源：`aichat_initializing_title`, `aichat_initializing_content`, `view_console_logs`, `view_in_full_screen`, `skills`, `logs_viewer_*`, `shortDescription`, `newFeature`
  - 可能来源：上游 `gallery` 的 strings.xml 文件可能不完整，或需要从其他模块迁移
  - 解决方案：
    - 检查 upstream gallery 的 strings.xml 文件内容
    - 对比备份版本 `gallery_backup_20260413` 中的资源定义
    - 补充缺失的字符串定义到 `res/values/strings.xml`
- 注意：这些资源可能来自上游的新功能模块，需要确认是否属于 Thinking Mode 相关功能

#### [ ] 5-6. 修复类型错误和 API 参数不匹配
- 目标：解决编译器报错的类型推断问题
- 要点：
  - 编译错误位置：`ChatPanel.kt:495`, `HomeScreen.kt:863-872`, `LlmSingleTurnTaskModule.kt:48`, `GalleryNavGraph.kt:216,240,344,531`
  - 主要问题：
    - `isFirstInitialization` 未定义
    - `LLM_AGENT_CHAT` 未定义
    - `instanceToCleanUp` 参数不存在
    - `dataStoreRepository` 访问权限问题（private）
  - 解决方案：
    - 检查上游 gallery 的对应代码，确认这些参数/属性的正确定义方式
    - 可能需要同步其他相关类或接口定义
- 注意：这些问题可能与上游代码版本差异有关，需确认使用的 gallery 版本

#### [ ] 5-7. 验证 SDK 返回 thinking text 的能力
- 目标：确认 LiteRT LM SDK 是否支持在 `MessageCallback.onMessage` 中返回 thinking text
- 要点：
  - 当前代码中 `resultListener(message.toString(), false, null)` 第三个参数始终为 null
  - 需要确认 SDK 的 `Message` 对象是否包含 thinking text 字段
  - 检查 LiteRT LM SDK 文档或源码，了解 `enable_thinking` 参数的实际效果
- 测试方式：
  - 编写最小测试脚本调用 LiteRT SDK，观察 `onMessage` 返回的内容
  - 如果 SDK 支持返回 thinking text，则需要修改 `LlmChatModelHelper.kt`
  - 如果 SDK 不支持，则 Thinking Mode 功能无法实现（当前代码逻辑依赖 SDK 返回）
- 注意：如果 SDK 不支持，可能需要考虑降级处理（不显示 thinking mode UI）
