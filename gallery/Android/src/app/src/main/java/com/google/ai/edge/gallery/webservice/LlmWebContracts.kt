package com.google.ai.edge.gallery.webservice

/**
 * Request payload for the embedded LLM web service.
 */
data class LlmWebRequest(
  val message: String? = null,
  val model: String? = null,
  val conversationId: String? = null,
  val resetConversation: Boolean? = null,
)

/**
 * Standardized response returned by the embedded LLM web service.
 */
data class LlmWebResponse(
  val success: Boolean,
  val model: String,
  val conversationId: String,
  val response: String? = null,
  val latencyMs: Long? = null,
  val error: String? = null,
)
