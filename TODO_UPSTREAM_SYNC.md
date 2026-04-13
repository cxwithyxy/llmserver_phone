# android_llm_server Upstream 同步待办事项清单

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

#### [ ] 1. 创建新的 master 分支并同步 upstream 基础代码
- 目标：建立干净的同步起点，避免历史分支干扰
- 步骤：
  - 备份当前 `gallery` 目录（含所有本地定制）
  - 新建空分支 `master-upstream-sync`
  - 从 `upstream_git_clone` 或直接克隆 `google-ai-edge/gallery` 主干
  - 手动合并基础代码（不涉及业务逻辑的目录：`model_allowlist.json`, `libs.versions.toml`, `AndroidManifest.xml` 等）
  - 验证基础构建通过 (`./gradlew assembleDebug`)
- 注意：此步骤只做基础代码同步，暂不集成新功能模块

#### [ ] 2. 升级 LiteRT SDK 到 `0.10.0` 并修复编译错误
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

#### [ ] 3. 验证 Web Service 模块兼容性
- 目标：确保现有定制模块在新环境下的功能完整
- 要点：
  - `LlmWebServerService` 启动/停止逻辑是否正常
  - `LlmInferenceEngine` 推理调用链路是否 intact
  - DataStore 配置项读写是否正常（`web_service_enabled`, `default_model`, `web_service_accelerator`, `download_site` 等）
  - 悬浮窗保活逻辑 (`overlay_keep_alive_enabled`) 是否正常
- 测试方式：编译后安装到测试设备，逐项功能验证

---

### Phase 2: 新功能模块集成（按需选择）

> 以下模块需要根据实际需求选择性集成。每个模块的集成前建议先评估必要性。

#### [ ] 4. Agent Skills 模块集成
- 功能描述：允许加载外部技能（Wikipedia、地图、富视觉摘要卡片等）
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/`
  - 相关类：`AgentChatTask`, `AgentChatViewModel`, `SkillManagerViewModel`
- 注意：此模块依赖 `mlkit-genai-prompt` 库，需确认 SDK 兼容性
- 可选方案：
  - 完整集成（包含所有 UI 和逻辑）
  - 摘要式集成（仅保留技能列表和加载能力）

#### [ ] 5. Thinking Mode (思考模式) 集成
- 功能描述：可切换的多步推理过程展示
- 关键文件：
  - `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/modeling/thinking/`
  - 相关类：`ThinkingModeViewModel`, `ThinkingScreen`
- 注意：此功能需要模型支持（Gemma 4 及后续版本）
- 当前限制：本地模型列表中的 DeepSeek/Qwen 系列是否支持需验证

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

*本文档由 AI 技术助理自动生成，日期：2026-04-13*
