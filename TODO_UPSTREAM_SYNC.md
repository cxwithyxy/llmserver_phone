# TODO_UPSTREAM_SYNC.md - android_llm_server Upstream 同步待办事项清单

> **重要**:在处理任何待做事项之前,请务必先阅读 [todolistRole.md](./todolistRole.md)(文档路径:`/home/cx/.openclaw/workspace/android_llm_server/todolistRole.md`)
>
> 本文档依据 `todolistRole.md` 规范创建,用于指导后续的 upstream 特性集成工作。

---

## ⚠️ 必读说明(处理前请务必阅读)

1. **必须先阅读 [todolistRole.md](./todolistRole.md)**:本文件的所有处理规则基于此文档,不阅读就直接处理会导致混乱
2. **每个会话只处理一个待做事项**:从列表中选择一个未完成的事项进行处理,完成后更新其状态即可结束本次会话,不要连续处理多个事项
3. **目标与背景**:见下方"目标与历史原因"章节
4. **文件提交要求**:本文件必须提交到 `master` 分支
5. **分支要求**:所有同步工作必须在 `master-upstream-sync` 分支上进行

---

## 📌 目标与历史原因

### 目标
将 `google-ai-edge/gallery` 的新功能整合到 `android_llm_server` 项目中,同时保留现有的本地定制(Web Service、Inference Engine、后台保活等)。

### 历史原因
- `android_llm_server` 最初基于 `google-ai-edge/gallery` 改造而来
- `gallery` 后续持续迭代,新增了多个新功能模块(Agent Skills、Thinking Mode、Ask Image、Audio Scribe、Prompt Lab、Mobile Actions、Tiny Garden 等)
- 之前尝试过多次同步,但分支方案都不够理想(产生过多杂乱变更或丢失本地定制)
- 当前决定切换回 `master` 分支,按规范重新规划,确保后续迭代可维护

---

## 📊 当前状态总览

| 阶段 | 事项 | 状态 | 说明 |
|------|------|------|------|
| Phase 1 | 1. 下载 upstream 源码 | ✅ 已完成 | 1.0.11 版本,编译通过 |
| Phase 1 | 2. 升级 LiteRT SDK 到 0.10.0 | ✅ 已完成 | 编译通过 |
| Phase 1 | 3. 验证 Web Service 兼容性 | ✅ 已完成 | 确认定制代码在备份目录中 |
| Phase 2 | 4. Agent Skills 集成 | ✅ 已完成 | 上游源码已集成 |
| Phase 2 | 5. Thinking Mode 集成 | ⚠️ 部分完成 | UI 完整,编译修复进行中(5-8-1 已完成,剩余 5-8-2 ~ 5-8-9) |
| Phase 2 | 6-11. 其他功能模块 | ⏸️ 暂缓 | 按需选择,暂无优先级 |
| Phase 3 | 12-1~12-5. Web Service 合并 | ⏸️ 待评估 | 需先解决上游版本兼容性问题 |

---

## 📋 待做事项列表

> 每次会话只处理一个待做事项。已完成 `[v]` 或已取消 `[x]` 的事项不能再次选择。

---

### Phase 1: 环境与基础代码同步

> **状态**:✅ 全部完成

#### [v] 1. 下载 google-ai-edge/gallery 源码并建立同步起点
- **结论**:已完成。1.0.11 版本源码已下载,`master-upstream-sync` 分支编译通过。

#### [v] 2. 升级 LiteRT SDK 到 `0.10.0` 并修复编译错误
- **结论**:已完成。`litertlm` 升级到 0.10.0,编译通过。

#### [v] 3. 验证 Web Service 模块兼容性
- **结论**:已完成。确认 `master-upstream-sync` 是干净 upstream,本地定制代码在 `gallery_backup_20260413/` 中。

---

### Phase 2: 新功能模块集成

> **当前重点**:Thinking Mode(事项 5)的编译修复和 SDK 验证。其他模块暂缓。

#### [v] 4. Agent Skills 模块集成
- **结论**:已完成。上游源码已集成,编译通过。

#### [v] 5. Thinking Mode (思考模式) 集成

**当前状态**:UI 层面完整,但存在编译错误和 SDK 调用待验证问题。

##### 已完成的子事项

- **[v] 5-1. 验证 Thinking Mode 实现完整性**
  - UI 层面完整:`ChatMessageThinking`、`MessageBodyThinking`、ViewModel 逻辑均存在
  - SDK 调用层面:`LlmChatModelHelper.runInference` 的 `onMessage` 第三个参数仍为 null,需进一步确认

- **[v] 5-2. 测试模型支持情况**
  - `model_allowlist.json` 中已存在 `llmSupportThinking: true` 字段(Gemma-3n-E2B/E4B 模型)

- **[v] 5-3. 集成 Thinking Mode UI 到 LLM Chat 页面**
  - `ChatPanel.kt` 已包含 `ChatMessageThinking` 处理逻辑
  - `MessageBodyThinking.kt` 已实现展开/收起功能
  - 字符串资源 `show_thinking` 已定义

- **[v] 5-4. 验证 LiteRT SDK 支持情况**
  - 已确认 `Message.channels` 字段存在,代码已修改为从 `channels` 提取 `thinking` 字段
  - `Conversation.sendMessageAsync` 的 `extraContext` 参数已正确传递
  - **待实测**:SDK 是否真的在 `channels` 中返回 thinking text

##### 待处理的子事项(按优先级排序)

- **[v] 5-5. 补充缺失的字符串资源**
  - **结论**：已完成。从 upstream `strings.xml` 中补充了 65 个缺失字符串资源到 `gallery/Android/src/app/src/main/res/values/strings.xml`，覆盖 Agent Skills、Config Dialog、Skill Manager 等模块。当前所有 `R.string.*`（及 `R.dimen/drawable/color/integer/array/plurals`）引用均已定义。剩余编译错误属于代码标识符未定义（如 `LLM_AGENT_CHAT`、`isFirstInitialization`、`dataStoreRepository` 等），归入 5-6 处理。

- **[ ] 5-6. 修复类型错误和 API 参数不匹配**
  - **目标**:解决编译器报错的类型推断问题
  - **错误位置**:`ChatPanel.kt:495`, `HomeScreen.kt:863-872`, `LlmSingleTurnTaskModule.kt:48`, `GalleryNavGraph.kt:216,240,344,531`
  - **主要问题**:
    - `isFirstInitialization` 未定义
    - `LLM_AGENT_CHAT` 未定义
    - `instanceToCleanUp` 参数不存在
    - `dataStoreRepository` 访问权限问题(private)
  - **解决方案**:
    - 检查上游 gallery 的对应代码,确认这些参数/属性的正确定义方式
    - 可能需要同步其他相关类或接口定义
  - **注意**:这些问题可能与上游代码版本差异有关,需确认使用的 gallery 版本

- **[ ] 5-8. 修复编译错误(2026-04-18 记录)**
  - **目标**:解决 `master-upstream-sync` 分支当前所有编译错误,使项目可正常编译通过
  - **编译命令**: `cd gallery/Android/src && ./gradlew :app:compileDebugKotlin`
  - **错误总数**: 约 30 个错误,涉及 10 个文件,分为 9 类
  - **拆分方式**:按错误类别拆分为 5-8-1 ~ 5-8-9,逐个修复

  **错误分类与详细列表**:

  **A. Composable 函数参数名不匹配 (`AgentChatScreen.kt`, 21 个错误)**
  - 文件: `customtasks/agentchat/AgentChatScreen.kt`
  - 错误: 调用 `LlmChatScreen` Composable 函数时使用了不存在的参数名
  - 缺失参数: `taskId`, `onFirstToken`, `onSkillClicked`, `showImagePicker`, `showAudioPicker`, `allowEditingSystemPrompt`, `curSystemPrompt`, `onSystemPromptChanged`, `emptyStateComposable`, `sendMessageTrigger`, `supportImage`, `supportAudio`, `onDone`, `enableConversationConstrainedDecoding`
  - 根因: `AgentChatScreen` 调用 `LlmChatScreen` 时使用的参数名与上游 `LlmChatScreen` 签名不一致
  - 修复方案: 将 `LlmChatScreen.kt` 同步为上游版本(已添加新参数)

  **B. 字符串资源缺失 (`SkillManagerBottomSheet.kt`, 4 个错误)**
  - 文件: `customtasks/agentchat/SkillManagerBottomSheet.kt`
  - 缺失资源: `selected_custom_skills_count`, `skills_count`, `delete_selected_skills_content`
  - 修复方案: 从上游 `strings.xml` 补充缺失字符串

  **C. 接口实现缺失 (`DataStoreRepository.kt`, 1 个错误)**
  - 文件: `data/DataStoreRepository.kt`
  - 错误: `DefaultDataStoreRepository` 未实现 `DataStoreRepository` 接口新增的 10 个抽象方法(Web Service 配置存取)
  - 修复方案: 删除接口中多余的 Web Service 方法声明(已在本地同步上游版本)

  **D. Dagger/Hilt 依赖注入参数缺失 (`AppModule.kt`, 1 个错误)**
  - 文件: `di/AppModule.kt`
  - 错误: `provideDataStoreRepository` 缺少 `skillsDataStore` 参数
  - 修复方案: 同步上游 AppModule,添加 `SkillsSerializer` 和 `DataStore<Skills>` 的 `@Provides` 方法

  **E. 返回类型不匹配 (`ModelHelperExt.kt`, 1 个错误)**
  - 文件: `runtime/ModelHelperExt.kt`
  - 错误: 返回 `'LlmModelHelper'`,实际返回 `'LlmChatModelHelper'`
  - 修复方案: 同步上游版本,`LlmChatModelHelper` 实现了 `LlmModelHelper` 接口

  **F. sendMessageAsync 回调签名不匹配 (`LlmChatModelHelper.kt`, 2 个错误)**
  - 文件: `ui/llmchat/LlmChatModelHelper.kt`
  - 错误: `sendMessageAsync` 参数不匹配,`getChannels()` 未定义,参数类型不匹配
  - 根因: LiteRT SDK API 变更,`Message` 对象的 `channels` 字段访问方式改变
  - 修复方案: 同步上游版本,使用 `LlmModelHelper` 接口和新的 channels 访问方式

  **G. ViewModel 中禁止 return (`LlmChatViewModel.kt`, 1 个错误)**
  - 文件: `ui/llmchat/LlmChatViewModel.kt`
  - 错误: 在协程/Composable 上下文中使用了 return
  - 修复方案: 同步上游版本,使用 `coroutineScope.launch` 和 `return@launch`

  **H. dataStoreRepository 私有访问 (`GalleryNavGraph.kt`, 4 个错误)**
  - 文件: `ui/navigation/GalleryNavGraph.kt`
  - 错误: `dataStoreRepository` 为 private 成员,`instanceToCleanUp` 参数不存在
  - 修复方案: 同步上游版本,调整访问权限和参数

  **I. Web Service 回调签名不匹配 (`LlmInferenceEngine.kt`, 1 个错误)**
  - 文件: `webservice/LlmInferenceEngine.kt`
  - 错误: `Function2<String, Boolean, Unit>` 不匹配期望的 `Function3<String, Boolean, String?, Unit>`
  - 根因: `MessageCallback.onMessage` 签名变更,新增第三个参数 (thinking text)
  - 修复方案: 更新回调函数签名以匹配新版 SDK

  - **注意**: 大部分错误源于上游 `gallery` 版本升级后 API 变更,需逐个文件对照上游源码修复

##### 拆分后的子事项(按优先级排序,建议逐个处理)

- **[ ] 5-8-1. 修复 LlmChatScreen 签名不匹配 (AgentChatScreen.kt, 21 个错误)**
  - **目标**:使 `AgentChatScreen.kt` 调用 `LlmChatScreen` 的参数与上游签名一致
  - **文件**: `customtasks/agentchat/AgentChatScreen.kt`
  - **错误**: `taskId`, `onFirstToken`, `onSkillClicked`, `showImagePicker`, `showAudioPicker`, `allowEditingSystemPrompt`, `curSystemPrompt`, `onSystemPromptChanged`, `emptyStateComposable`, `sendMessageTrigger`, `supportImage`, `supportAudio`, `onDone`, `enableConversationConstrainedDecoding` 等参数不存在
  - **根因**: 本地 `LlmChatScreen.kt` 缺少上游新增的参数
  - **已执行**: 已将 `LlmChatScreen.kt` 同步为上游版本
  - **待验证**: 同步后是否还有其他调用方需要调整
  - **优先级**: ⭐⭐⭐ (最高,阻塞 AgentChat 模块)

- **[ ] 5-8-2. 补充缺失字符串资源 (SkillManagerBottomSheet.kt, 4 个错误)**
  - **目标**:从上游 `strings.xml` 补充缺失字符串
  - **文件**: `res/values/strings.xml`
  - **缺失资源**:
    - `selected_custom_skills_count` (L295)
    - `skills_count` (L410, L588)
    - `delete_selected_skills_content` (L615)
  - **修复方式**: 从上游 `strings.xml` 复制对应条目
  - **优先级**: ⭐⭐ (低,纯资源补充)

- **[ ] 5-8-3. 修复 DataStoreRepository 接口不一致 (1 个错误)**
  - **目标**:解决 `DefaultDataStoreRepository` 未实现接口新增方法的编译错误
  - **文件**: `data/DataStoreRepository.kt`
  - **已执行**: 已删除接口中多余的 10 个 Web Service 方法声明(这些方法上游已移除)
  - **优先级**: ⭐⭐⭐ (高,阻塞编译)

- **[ ] 5-8-4. 修复 AppModule 依赖注入缺失 (1 个错误)**
  - **目标**:补充 `skillsDataStore` 依赖,解决 Dagger/Hilt 编译错误
  - **文件**: `di/AppModule.kt`
  - **修复方案**: 同步上游 AppModule,添加以下内容:
    - `@Provides @Singleton fun provideSkillsSerializer(): Serializer<Skills>`
    - `@Provides @Singleton fun provideSkillsDataStore(...): DataStore<Sills>`
    - `provideDataStoreRepository` 增加 `skillsDataStore` 参数
  - **优先级**: ⭐⭐⭐ (高,阻塞编译)

- **[ ] 5-8-5. 修复 ModelHelperExt 返回类型不匹配 (1 个错误)**
  - **目标**:修正返回类型,解决 `'LlmModelHelper'` vs `'LlmChatModelHelper'` 错误
  - **文件**: `runtime/ModelHelperExt.kt`
  - **修复方案**: 同步上游版本,`LlmChatModelHelper` 已实现 `LlmModelHelper` 接口
  - **优先级**: ⭐⭐⭐ (高,阻塞编译)

- **[ ] 5-8-6. 修复 LlmChatModelHelper SDK API 不兼容 (2 个错误)**
  - **目标**:适配新版 LiteRT SDK API,解决 `sendMessageAsync` 参数和 `getChannels()` 错误
  - **文件**: `ui/llmchat/LlmChatModelHelper.kt`
  - **错误**:
    - L287: `sendMessageAsync` 参数不匹配 (Contents 类型)
    - L295: `getChannels()` 未定义
    - L296: 参数类型不匹配 (MatchGroup? vs String?)
  - **修复方案**: 同步上游版本,使用 `LlmModelHelper` 接口和新的 channels 访问方式
  - **优先级**: ⭐⭐⭐ (高,阻塞编译)

- **[ ] 5-8-7. 修复 LlmChatViewModel return 用法错误 (1 个错误)**
  - **目标**:修正协程/Composable 上下文中的 return 用法
  - **文件**: `ui/llmchat/LlmChatViewModel.kt`
  - **错误**: L93 在协程中使用了 return 而非 return@launch
  - **修复方案**: 同步上游版本,改用 `coroutineScope.launch { ... return@launch }`
  - **优先级**: ⭐⭐⭐ (高,阻塞编译)

- **[ ] 5-8-8. 修复 GalleryNavGraph 访问权限问题 (4 个错误)**
  - **目标**:解决 `dataStoreRepository` 私有访问和 `instanceToCleanUp` 参数不存在
  - **文件**: `ui/navigation/GalleryNavGraph.kt`
  - **错误**:
    - L216, L240: `dataStoreRepository` 为 private 成员
    - L344, L531: `instanceToCleanUp` 参数不存在
  - **修复方案**: 同步上游版本,调整访问权限和参数
  - **优先级**: ⭐⭐⭐ (高,阻塞编译)

- **[ ] 5-8-9. 修复 LlmInferenceEngine 回调签名不匹配 (1 个错误)**
  - **目标**:更新回调函数签名以匹配新版 SDK
  - **文件**: `webservice/LlmInferenceEngine.kt`
  - **错误**: L236 `Function2<String, Boolean, Unit>` 不匹配期望的 `Function3<String, Boolean, String?, Unit>`
  - **根因**: `MessageCallback.onMessage` 签名变更,新增第三个参数 (thinking text)
  - **修复方案**: 更新回调函数签名,新增 `thinkingText: String?` 参数
  - **优先级**: ⭐⭐ (中,Web Service 模块)

#### [ ] 6. Ask Image (图像识别) 集成 - ⏸️ 暂缓
- **功能描述**:通过相机或图库进行视觉识别
- **关键文件**:`customtasks/askimage/`
- **注意**:需要多模态模型支持,暂无优先级

#### [ ] 7. Audio Scribe (语音转写) 集成 - ⏸️ 暂缓
- **功能描述**:实时语音识别与翻译
- **关键文件**:`customtasks/audioscribe/`
- **注意**:依赖音频输入,需确认 Android 权限和设备支持

#### [ ] 8. Prompt Lab (提示词实验室) 集成 - ⏸️ 暂缓
- **功能描述**:独立的工作区测试不同 prompt 和参数
- **关键文件**:`customtasks/promptlab/`
- **注意**:与现有 UI 推理流程重复度较高

#### [ ] 9. Mobile Actions (移动端自动化) 集成 - ⏸️ 暂缓
- **功能描述**:基于 FunctionGemma 270M 微调的设备控制任务
- **关键文件**:`customtasks/mobileactions/`
- **注意**:需要额外微调模型,评估必要性后再决定是否集成

#### [ ] 10. Tiny Garden (微型花园) 集成 - ⏸️ 暂缓
- **功能描述**:基于 FunctionGemma 270M 的趣味小游戏
- **关键文件**:`customtasks/tinygarden/`
- **注意**:实验性 mini-game,评估必要性后再决定是否集成

#### [ ] 11. Model Management & Benchmark 模块升级 - ⏸️ 暂缓
- **功能描述**:模型管理、下载、基准测试
- **关键文件**:`benchmark/`
- **注意**:`model_allowlist.json` 已同步,但 UI 和下载逻辑可能需要升级

---

### Phase 3: 本地定制代码合并

> **当前状态**:⏸️ 待评估。需先解决上游版本兼容性问题(见 5-5、5-6),再推进合并。

#### [ ] 12-1. 评估合并必要性与范围
- **目标**:确认是否需要将 Web Service 模块合并回 upstream,以及合并范围
- **要点**:
  - 当前 `master-upstream-sync` 是干净的 upstream 分支,无本地定制代码
  - 合并后会导致分支与 upstream 偏离,需评估后续同步成本
  - 如果仅用于本地部署(非发布到 Google Play),可考虑保持独立分支或 tag
  - 如果需要随 upstream 迭代更新,建议提取为独立模块或通过 build variant 支持
- **可选方案**:
  - **方案 A**:完全合并到 `gallery` 目录,后续手动同步 upstream 变更
  - **方案 B**:保持 Web Service 在独立模块(如 `webservice/`),通过 Gradle dependency 引入
  - **方案 C**:暂不合并,仅在本地分支维护定制代码
- **注意**:需用户明确是否需要 Web Service 功能随 upstream 迭代更新

#### [v] 12-2. 创建 webservice 模块的 Android Library 结构
- **结论**:已尝试但放弃。直接集成到 `app` 目录更可行,但当前因上游版本不兼容导致构建失败。
- **后续步骤**:需先解决 5-5、5-6 的编译问题,再推进合并。

#### [ ] 12-3. 解决 Web Service 模块的依赖关系
- **目标**:确保 `webservice` 能正确编译并访问 `gallery` 的类
- **关键依赖**:
  - `com.google.ai.edge.gallery`(主应用模块)
  - `dagger-hilt`(依赖注入)
  - `nanohttpd`(HTTP 服务器,已通过 `libs.versions.toml` 配置)
  - `kotlinx-coroutines`(协程支持)
- **解决方案**:
  - 在 `webservice/build.gradle.kts` 中添加 `implementation project(":Android")` 依赖
  - 确保 `AndroidManifest.xml` 声明必要的权限与 service 组件
  - 处理 `@AndroidEntryPoint` 和 `@Inject` 注入逻辑
- **注意**:可能需要调整 `LlmInferenceEngine.kt` 的构造函数参数,避免循环依赖

#### [ ] 12-4. 整合 Web Service 到主应用
- **目标**:在 `gallery` 应用中启用 Web Service 功能
- **步骤**:
  - 在 `Android/app/build.gradle.kts` 中添加 `implementation project(":webservice")`
  - 在 `AndroidManifest.xml` 中注册 `LlmWebServerService`
  - 验证悬浮窗权限 (`SYSTEM_ALERT_WINDOW`) 和前台服务权限 (`FOREGROUND_SERVICE`)
  - 测试 HTTP 接口 `/health`、`/chat` 的响应

#### [ ] 12-5. 验证 Web Service 功能完整性
- **目标**:确保合并后的 Web Service 模块功能与备份版本一致
- **测试项**:
  - Service 启动/停止逻辑是否正常
  - HTTP 接口 `/chat` 是否能正确调用推理引擎并返回结果
  - 悬浮窗保活是否正常工作
  - 前台服务通知是否显示 IP 地址和端口号
  - WakeLock 是否正确 acquire/release

---

## 📌 规则说明(参考 [todolistRole.md](./todolistRole.md))

1. **每次只处理一个待做事项**:从列表中选择一个未完成的事项进行处理,完成后更新状态即可结束本次会话
2. **不能删除待做事项**:只能将状态改为 `[v]` (已完成) 或 `[x]` (已取消)
3. **待做事项描述要详细**:包括接口文档链接、参数、返回值等(参考上述格式)
4. **超过 30 分钟的处理需拆分**:如果某事项耗时过长,更新其状态并拆分成多个子事项
5. **新建事项放于相关项之后**:保持列表结构清晰

---

## 📝 问题汇总(如有)

> 如果遇到无法解决的问题,请在此处记录:

- [ ] (暂无)

---

## 📁 下游源码目录约定

为避免重复下载和混淆,以下目录路径必须严格遵守:

| 目录 | 用途 | 是否提交到 git |
|------|------|----------------|
| `/home/cx/.openclaw/workspace/android_llm_server/gallery/` | 当前项目源码(含本地定制) | 是 |
| `/home/cx/.openclaw/workspace/android_llm_server/upstream_gallery_source/` | 下游 `google-ai-edge/gallery` 源码 | 否(已 .gitignore) |
| `/home/cx/.openclaw/workspace/android_llm_server/gallery_backup_YYYYMMDD/` | 备份目录(按日期命名) | 否 |

⚠️ **重要**:
- 下游源码必须放在 `upstream_gallery_source/` 目录,禁止直接克隆到项目根目录或其他位置
- 该目录已加入 `.gitignore`,避免污染主仓库历史
- 每次同步前先检查此目录是否存在,存在则跳过下载步骤

---

*本文档由 AI 技术助理自动生成,日期:2026-04-13*

---

**2026-04-17 更新**:
- 重构文档结构,消除冗余和散乱的待办事项
- 已完成事项只保留关键结论,删除冗余进度记录
- 5-5/5-6/5-7 归位到 Thinking Mode 下,按优先级排序
- Phase 2 模块(6-11)标记为 ⏸️ 暂缓,明确当前重点是 Thinking Mode 编译修复
- Phase 3 标注需先解决上游版本兼容性问题,再推进合并
- 新增"当前状态总览"表格,一目了然
