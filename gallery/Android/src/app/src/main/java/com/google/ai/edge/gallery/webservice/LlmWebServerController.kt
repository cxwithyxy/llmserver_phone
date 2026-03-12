package com.google.ai.edge.gallery.webservice

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.gallery.common.processLlmResponse
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.EMPTY_MODEL
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ALLOWLIST_TIMEOUT_MS = 30_000L
private const val INIT_TIMEOUT_MS = 90_000L
private const val INFERENCE_TIMEOUT_MS = 120_000L
private const val DEFAULT_CONVERSATION_PREFIX = "llmserver"

/**
 * Orchestrates request validation, model preparation, and inference for the embedded web service.
 */
class LlmWebServerController(
  private val context: Context,
  private val modelManagerViewModel: ModelManagerViewModel,
  private val preferredModelName: String? = null,
) {
  private val textTaskPriority = listOf(BuiltInTaskId.LLM_CHAT, BuiltInTaskId.LLM_PROMPT_LAB)
  private val mutex = Mutex()
  private val conversationCounter = AtomicInteger(0)

  suspend fun handleChatRequest(request: LlmWebRequest): LlmWebResponse {
    val trimmedMessage = request.message?.trim().orEmpty()
    require(trimmedMessage.isNotEmpty()) { "message must not be empty" }

    awaitAllowlistReady()

    val model = resolveModel(request.model)
    val conversationId =
      request.conversationId ?: "$DEFAULT_CONVERSATION_PREFIX-${conversationCounter.incrementAndGet()}"

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

    val response =
      mutex.withLock {
        ensureModelReady(task, model)
        if (request.resetConversation == true) {
          resetConversation(task, model)
        }
        val (result, latency) = runInference(model = model, input = trimmedMessage)
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
  }

  private fun resolveModel(name: String?): Model {
    val explicitModel = name?.let { modelManagerViewModel.getModelByName(it) }
    if (explicitModel != null) {
      return explicitModel
    }

    val preferredModel =
      preferredModelName?.takeIf { it.isNotBlank() }?.let { modelManagerViewModel.getModelByName(it) }
    if (preferredModel != null) {
      return preferredModel
    }

    val selectedModel = modelManagerViewModel.uiState.value.selectedModel
    if (selectedModel.name.isNotEmpty() && selectedModel != EMPTY_MODEL) {
      return selectedModel
    }

    return modelManagerViewModel.getAllDownloadedModels().firstOrNull()
      ?: error("No downloaded LLM model is available. Please download one in the app first.")
  }

  private fun findTaskForModel(model: Model): Task? {
    val tasks = modelManagerViewModel.uiState.value.tasks
    fun isCompatible(task: Task): Boolean {
      if (!task.models.any { it.name == model.name }) {
        return false
      }
      return when (task.id) {
        BuiltInTaskId.LLM_ASK_AUDIO -> model.llmSupportAudio
        BuiltInTaskId.LLM_ASK_IMAGE -> model.llmSupportImage
        else -> true
      }
    }
    textTaskPriority.forEach { taskId ->
      tasks.firstOrNull { task -> task.id == taskId && isCompatible(task) }?.let { return it }
    }
    return tasks.firstOrNull { task -> isCompatible(task) }
  }

  private suspend fun ensureModelReady(task: Task, model: Model) {
    if (model.instance != null) {
      return
    }
    modelManagerViewModel.initializeModel(context = context, task = task, model = model)
    withTimeout(INIT_TIMEOUT_MS) {
      while (true) {
        val status = modelManagerViewModel.uiState.value.modelInitializationStatus[model.name]
        if (model.instance != null &&
            status?.status == ModelInitializationStatusType.INITIALIZED) {
          return@withTimeout
        }
        if (status?.status == ModelInitializationStatusType.ERROR) {
          val message = status.error.ifEmpty { "Unknown initialization error" }
          error(message)
        }
        delay(200)
      }
    }
  }

  private fun resetConversation(task: Task, model: Model) {
    val supportImage = model.llmSupportImage && task.id == BuiltInTaskId.LLM_ASK_IMAGE
    val supportAudio = model.llmSupportAudio && task.id == BuiltInTaskId.LLM_ASK_AUDIO
    LlmChatModelHelper.resetConversation(
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
    )
  }

  private suspend fun runInference(model: Model, input: String): Pair<String, Long> {
    return withTimeout(INFERENCE_TIMEOUT_MS) {
      suspendCancellableCoroutine { continuation ->
        val builder = StringBuilder()
        val start = SystemClock.elapsedRealtime()
        try {
          LlmChatModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done ->
              if (partial.startsWith("<ctrl")) {
                return@runInference
              }
              if (partial.isNotEmpty()) {
                builder.append(partial)
              }
              if (done && !continuation.isCompleted) {
                val latency = SystemClock.elapsedRealtime() - start
                continuation.resume(
                  processLlmResponse(builder.toString()) to latency,
                )
              }
            },
            cleanUpListener = {
              if (!continuation.isCompleted) {
                val latency = SystemClock.elapsedRealtime() - start
                continuation.resume(
                  processLlmResponse(builder.toString()) to latency,
                )
              }
            },
            onError = { message ->
              if (!continuation.isCompleted) {
                continuation.resumeWithException(IllegalStateException(message))
              }
            },
          )
        } catch (throwable: Throwable) {
          if (!continuation.isCompleted) {
            continuation.resumeWithException(throwable)
          }
        }

        continuation.invokeOnCancellation {
          val instance = model.instance as? LlmModelInstance
          instance?.conversation?.cancelProcess()
        }
      }
    }
  }
}
