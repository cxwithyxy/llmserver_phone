package com.google.ai.edge.gallery.webservice

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.common.LogBuffer
import com.google.ai.edge.gallery.common.processLlmResponse
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DEFAULT_WEB_SERVICE_ACCELERATOR
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.data.SegmentedButtonConfig
import com.google.ai.edge.gallery.data.EMPTY_MODEL
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private const val ALLOWLIST_TIMEOUT_MS = 30_000L
private const val INIT_TIMEOUT_MS = 90_000L
private const val INFERENCE_TIMEOUT_MS = 120_000L
private const val DEFAULT_CONVERSATION_PREFIX = "llmserver"

class LlmInferenceEngine
@Inject
constructor(
  private val context: Context,
  downloadRepository: DownloadRepository,
  private val dataStoreRepository: DataStoreRepository,
  private val lifecycleProvider: AppLifecycleProvider,
  private val customTasks: Set<@JvmSuppressWildcards CustomTask>,
) {
  private val modelManagerViewModel =
    BackgroundModelManagerViewModel(
      downloadRepository = downloadRepository,
      dataStoreRepository = dataStoreRepository,
      lifecycleProvider = lifecycleProvider,
      customTasks = customTasks,
      context = context,
    )
  private val mutex = Mutex()
  private val conversationCounter = AtomicInteger(0)
  private val textTaskPriority = listOf(BuiltInTaskId.LLM_CHAT, BuiltInTaskId.LLM_PROMPT_LAB)

  init {
    logInfo("Initializing inference engine; loading model allowlist")
    modelManagerViewModel.loadModelAllowlist()
  }

  suspend fun handleChatRequest(
    request: LlmWebRequest,
    preferredModelName: String?,
  ): LlmWebResponse {
    val trimmedMessage = request.message?.trim().orEmpty()
    require(trimmedMessage.isNotEmpty()) { "message must not be empty" }

    logInfo(
      "handleChatRequest messageLength=${trimmedMessage.length}, requestedModel=${request.model}, preferredModel=$preferredModelName",
    )

    awaitAllowlistReady()

    val model = resolveModel(requestedModelName = request.model ?: preferredModelName)
    val conversationId =
      request.conversationId ?: "$DEFAULT_CONVERSATION_PREFIX-${conversationCounter.incrementAndGet()}"
    logInfo("Selected model=${model.name}, conversationId=$conversationId")

    val task = findTaskForModel(model)
      ?: error("Unable to resolve a task for model ${model.name}")

    val downloadStatus =
      modelManagerViewModel.uiState.value.modelDownloadStatus[model.name]?.status
    if (downloadStatus != ModelDownloadStatusType.SUCCEEDED) {
      error("Model ${model.name} is not downloaded yet")
    }

    if (!model.isLlm) {
      error("Model ${model.name} is not an LLM model")
    }

    val acceleratorPreference = dataStoreRepository.getWebServiceAccelerator()
    applyAcceleratorPreference(model, acceleratorPreference)

    val supportImage = model.llmSupportImage && task.id == BuiltInTaskId.LLM_ASK_IMAGE
    val supportAudio = model.llmSupportAudio && task.id == BuiltInTaskId.LLM_ASK_AUDIO

    val response =
      mutex.withLock {
        ensureModelReady(task, model)
        if (request.resetConversation == true) {
          logInfo("Resetting conversation for model=${model.name}")
          resetConversation(model = model, supportImage = supportImage, supportAudio = supportAudio)
        }
        val requestToken = "${model.name}-${System.currentTimeMillis()}"
        val (result, latency) = runInference(model = model, input = trimmedMessage, requestToken = requestToken)
        logInfo(
          "Inference finished token=$requestToken model=${model.name}, latency=${latency}ms, responseLength=${result.length}",
        )
        LlmWebResponse(
          success = true,
          model = model.name,
          conversationId = conversationId,
          response = result,
          latencyMs = latency,
        )
      }
    return response
  }

  private suspend fun awaitAllowlistReady() {
    withTimeout(ALLOWLIST_TIMEOUT_MS) {
      while (modelManagerViewModel.uiState.value.loadingModelAllowlist) {
        delay(200)
      }
    }
    val errorMessage = modelManagerViewModel.uiState.value.loadingModelAllowlistError
    if (errorMessage.isNotEmpty()) {
      error("Failed to load model list: $errorMessage")
    }
    logDebug("Model allowlist ready; total=${modelManagerViewModel.uiState.value.tasks.size} tasks")
  }

  private fun resolveModel(requestedModelName: String?): Model {
    val explicitModel = requestedModelName?.let { modelManagerViewModel.getModelByName(it) }
    if (explicitModel != null) {
      logDebug("resolveModel matched explicit request: $requestedModelName")
      return explicitModel
    }

    val selectedModel = modelManagerViewModel.uiState.value.selectedModel
    if (selectedModel.name.isNotEmpty() && selectedModel != EMPTY_MODEL) {
      logDebug("resolveModel using selected model: ${selectedModel.name}")
      return selectedModel
    }

    val fallback =
      modelManagerViewModel.getAllDownloadedModels().firstOrNull()
        ?: error("No downloaded LLM model is available. Please download one in the app first.")
    logDebug("resolveModel fallback to downloaded model: ${fallback.name}")
    return fallback
  }

  private fun findTaskForModel(model: Model): Task? {
    val tasks = modelManagerViewModel.uiState.value.tasks
    textTaskPriority.forEach { taskId ->
      tasks.firstOrNull { task ->
        task.id == taskId && task.models.any { it.name == model.name }
      }?.let { task ->
        if (isTaskCompatible(task, model)) {
          return task
        }
      }
    }
    return tasks.firstOrNull { isTaskCompatible(it, model) }
  }

  private fun isTaskCompatible(task: Task, model: Model): Boolean {
    if (!task.models.any { it.name == model.name }) {
      return false
    }
    return when (task.id) {
      BuiltInTaskId.LLM_ASK_AUDIO -> model.llmSupportAudio
      BuiltInTaskId.LLM_ASK_IMAGE -> model.llmSupportImage
      else -> true
    }
  }

  private suspend fun ensureModelReady(task: Task, model: Model) {
    if (model.instance != null) {
      logDebug("Model ${model.name} already initialized; reusing instance")
      return
    }
    logInfo("Initializing model ${model.name} for task=${task.id}")
    modelManagerViewModel.initializeModel(context = context, task = task, model = model)
    withTimeout(INIT_TIMEOUT_MS) {
      while (true) {
        val status = modelManagerViewModel.uiState.value.modelInitializationStatus[model.name]
        if (model.instance != null &&
            status?.status == ModelInitializationStatusType.INITIALIZED) {
          logInfo("Model ${model.name} initialized successfully")
          return@withTimeout
        }
        if (status?.status == ModelInitializationStatusType.ERROR) {
          val message = status.error.ifEmpty { "Unknown initialization error" }
          logError("Model ${model.name} initialization failed: $message")
          error(message)
        }
        delay(200)
      }
    }
  }

  private fun resetConversation(model: Model, supportImage: Boolean, supportAudio: Boolean) {
    LlmChatModelHelper.resetConversation(
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
    )
  }

  private suspend fun runInference(
    model: Model,
    input: String,
    requestToken: String,
  ): Pair<String, Long> {
    return withTimeout(INFERENCE_TIMEOUT_MS) {
      suspendCancellableCoroutine { continuation ->
        val builder = StringBuilder()
        val start = System.currentTimeMillis()
        logInfo("Starting inference token=$requestToken model=${model.name}, inputLength=${input.length}")
        try {
          LlmChatModelHelper.runInference(
            model = model,
            input = input,
            requestToken = requestToken,
            resultListener = { partial, done ->
              if (partial.startsWith("<ctrl")) {
                return@runInference
              }
              if (partial.isNotEmpty()) {
                builder.append(partial)
                logDebug("token=$requestToken chunk len=${partial.length} done=$done")
              }
              if (done && !continuation.isCompleted) {
                val latency = System.currentTimeMillis() - start
                continuation.resume(
                  processLlmResponse(builder.toString()) to latency,
                )
              }
            },
            cleanUpListener = {
              logDebug("token=$requestToken cleanUpListener invoked")
              if (!continuation.isCompleted) {
                val latency = System.currentTimeMillis() - start
                continuation.resume(
                  processLlmResponse(builder.toString()) to latency,
                )
              }
            },
            onError = { message ->
              logError("Inference error token=$requestToken model=${model.name}: $message")
              if (!continuation.isCompleted) {
                continuation.resumeWithException(IllegalStateException(message))
              }
            },
          )
        } catch (throwable: Throwable) {
          logError("Inference threw exception token=$requestToken for model=${model.name}", throwable)
          if (!continuation.isCompleted) {
            continuation.resumeWithException(throwable)
          }
        }

        continuation.invokeOnCancellation {
          logInfo("Inference cancelled token=$requestToken for model=${model.name}")
          val instance = model.instance as? LlmModelInstance
          instance?.conversation?.cancelProcess()
        }
      }
    }
  }

  fun dispose() {
    logInfo("Disposing inference engine")
    modelManagerViewModel.dispose()
  }

  fun getPreferredModelName(): String {
    return dataStoreRepository.getWebServiceModelName()
  }

  private fun logInfo(message: String) {
    Log.i(TAG, message)
    LogBuffer.append(TAG, message)
  }

  private fun logDebug(message: String) {
    Log.d(TAG, message)
    LogBuffer.append(TAG, message)
  }

  private fun logError(message: String, throwable: Throwable? = null) {
    if (throwable == null) {
      Log.e(TAG, message)
      LogBuffer.append(TAG, message)
    } else {
      Log.e(TAG, message, throwable)
      LogBuffer.append(TAG, "$message: ${throwable.message}")
    }
  }

  private class BackgroundModelManagerViewModel(
    downloadRepository: DownloadRepository,
    dataStoreRepository: DataStoreRepository,
    lifecycleProvider: AppLifecycleProvider,
    customTasks: Set<@JvmSuppressWildcards CustomTask>,
    context: Context,
  ) :
    ModelManagerViewModel(
      downloadRepository = downloadRepository,
      dataStoreRepository = dataStoreRepository,
      lifecycleProvider = lifecycleProvider,
      customTasks = customTasks,
      context = context,
    ) {
    fun dispose() {
      super.onCleared()
    }
  }

  private fun applyAcceleratorPreference(model: Model, preferenceLabel: String?) {
    val config =
      model.configs.firstOrNull { it.key == ConfigKeys.ACCELERATOR } as? SegmentedButtonConfig
        ?: return
    if (config.options.isEmpty()) {
      return
    }
    val desired =
      preferenceLabel?.ifBlank { DEFAULT_WEB_SERVICE_ACCELERATOR }
        ?: DEFAULT_WEB_SERVICE_ACCELERATOR
    val resolved =
      config.options.firstOrNull { it.equals(desired, ignoreCase = true) }
        ?: config.options.firstOrNull { it.equals(DEFAULT_WEB_SERVICE_ACCELERATOR, ignoreCase = true) }
        ?: config.options.first()
    val current = model.configValues[ConfigKeys.ACCELERATOR.label] as? String
    if (current?.equals(resolved, ignoreCase = true) == true) {
      return
    }
    val newValues = model.configValues.toMutableMap()
    newValues[ConfigKeys.ACCELERATOR.label] = resolved
    model.prevConfigValues = model.configValues
    model.configValues = newValues
    logInfo("Applied accelerator=$resolved for model=${model.name}")
  }

  companion object {
    private const val TAG = "LlmInferenceEngine"
  }
}
