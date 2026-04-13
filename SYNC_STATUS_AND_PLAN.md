## 📅 Last Update
**Date:** 2026-04-13 11:35 (Asia/Shanghai)

## 🎯 Project Goal
Perform a **Manual Refactoring Merge (Scheme B)** to upgrade the `android_llm_server` project from LiteRT SDK `0.9.0-alpha06` to `0.10.0`, integrating upstream features from `google-ai-edge/gallery` while preserving local customizations (Web Service, Inference Engine, etc.).

## 🛠️ Progress Report

### ✅ Completed Tasks
1.  **Environment Setup**
    *   [x] Successfully fetched/extracted upstream code into `/home/cx/.openclaw/workspace/android_llm_server/upstream_temp`.
    *   [x] Installed `openjdk-21-jdk` and resolved Toolchain detection issues via `gradle.properties`.
2.  **Configuration Synchronization (`libs.versions.toml`)**
    *   [x] Upgraded `litertlm` to `0.10.0`.
    *   [x] Upgraded `kotlin` to `2.2.0`.
    *   [x] Upgraded `ksp` to `2.3.6`.
    *   [x] Added `mlkit-genai-prompt` (`1.0.0-beta2`).
3.  **Manifest Merging (`AndroidManifest.xml`)**
    *   [x] Added `com.google.android.apps.aicore.service.BIND_SERVICE` permission.
    *   [x] Preserved local `LlmWebServerService` and `com.cx.llmserver` deep link scheme.
4.  **Model List Sync (`model_allowlist.json`)**
    *   [x] Verified local and upstream lists are identical.

### ❌ Current Blockers (Compilation Errors)
The project fails to build with `compileDebugKotlin` due to API breaking changes in LiteRT SDK `0.10.0`.

**Key Errors:**
*   **Type Mismatch:** `List<Any>` is no longer accepted where `List<ToolProvider>` is required.
*   **Missing Implementations:** Custom tool classes (`MobileActionsTools`, `TinyGardenTools`) do not implement the `ToolProvider` interface correctly (visibility/signature issues).
*   **Broken References:** `npuLibrariesDir` (ExperimentalFlags) and `Lifecycle` (import issue) are unresolved.

---

## 📋 Pending TODOs (Prioritized)

### Phase 1: Fix Type Implementation (High Priority)
- [ ] **Locate true definition of `com.google.ai.edge.litertlm.ToolProvider`** (it is not in the expected local path).
- [ ] **Implement `ToolProvider` interface** for `MobileActionsTools.kt`.
- [ ] **Implement `ToolProvider` interface** for `TinyGardenTools.kt`.
- [ ] **Update Call Sites** to use explicit `List<ToolProvider>` instead of `List<Any>` in:
    *   `LlmChatViewModel.kt`
    *   `MobileActionsTask.kt`
    *   `MobileActionsViewModel.kt`
    *   `TinyGardenTask.kt`
    *   `TinyGardenViewModel.kt`

### Phase 2: Resolve Broken References (Medium Priority)
- [ ] **Remove/Replace `npuLibrariesDir`** in `BenchmarkViewModel.kt`.
- [ ] **Fix `Lifecycle` import** in `SettingsDialog.kt`.

### Phase 3: Final Verification
- [ ] Run `./gradlew clean assembleDebug` to confirm successful build.
- [ ] Verify that local `LlmWebServerService` still functions correctly.

---

## 📌 Technical Context & Constraints
*   **Project Path:** `/home/cx/.openclaw/workspace/android_llm_server/`
*   **Merge Strategy:** Manual incremental merge (Avoid `git merge`).
*   **JDK:** `21.0.5+11`
*   **Android SDK:** `/home/cx/.openclaw/workspace/android_sdk`
*   **Proxy:** `socks5h://172.27.34.8:1080`
