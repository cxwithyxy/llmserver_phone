package com.google.ai.edge.gallery.webservice

import fi.iki.elonen.NanoHTTPD
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject

typealias IHTTPSession = NanoHTTPD.IHTTPSession
typealias Response = NanoHTTPD.Response
typealias ResponseException = NanoHTTPD.ResponseException
typealias Method = NanoHTTPD.Method

private const val MIME_JSON = "application/json"

class LlmNanoHttpServer(
  port: Int,
  private val requestHandler: suspend (LlmWebRequest) -> LlmWebResponse,
) : NanoHTTPD(port) {

  override fun serve(session: IHTTPSession): Response {
    return when {
      session.method == Method.GET && session.uri == "/health" ->
        newFixedLengthResponse(Response.Status.OK, MIME_JSON, "{\"status\":\"ok\"}")
      session.method == Method.POST && session.uri == "/chat" -> handleChat(session)
      else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"not_found\"}")
    }
  }

  private fun handleChat(session: IHTTPSession): Response {
    return try {
      val requestBody = readBody(session)
      val request = parseRequest(requestBody)
      val response = runBlocking { requestHandler.invoke(request) }
      jsonResponse(Response.Status.OK, response)
    } catch (ex: IllegalArgumentException) {
      jsonError(Response.Status.BAD_REQUEST, ex.message ?: "Invalid request")
    } catch (ex: IllegalStateException) {
      jsonError(Response.Status.BAD_REQUEST, ex.message ?: "Invalid state")
    } catch (ex: JSONException) {
      jsonError(Response.Status.BAD_REQUEST, "Malformed JSON body")
    } catch (ex: ResponseException) {
      jsonError(Response.Status.BAD_REQUEST, ex.message ?: "Malformed body")
    } catch (ex: Throwable) {
      jsonError(Response.Status.INTERNAL_ERROR, ex.message ?: "Unexpected error")
    }
  }

  private fun parseRequest(body: String): LlmWebRequest {
    val json = JSONObject(body.ifBlank { "{}" })
    val message = json.optNullableString("message")
    val model = json.optNullableString("model")
    val conversationId =
      json.optNullableString("conversation_id") ?: json.optNullableString("conversationId")
    val resetConversation =
      when {
        json.has("resetConversation") -> json.optBoolean("resetConversation")
        json.has("reset_conversation") -> json.optBoolean("reset_conversation")
        else -> null
      }
    return LlmWebRequest(
      message = message,
      model = model,
      conversationId = conversationId,
      resetConversation = resetConversation,
    )
  }

  private fun readBody(session: IHTTPSession): String {
    val body = mutableMapOf<String, String>()
    session.parseBody(body)
    return body["postData"] ?: ""
  }

  private fun jsonResponse(status: Response.Status, payload: LlmWebResponse): Response {
    val json = JSONObject()
    json.put("success", payload.success)
    json.put("model", payload.model)
    json.put("conversation_id", payload.conversationId)
    payload.response?.let { json.put("response", it) }
    payload.latencyMs?.let { json.put("latency_ms", it) }
    payload.error?.let { json.put("error", it) }
    return newFixedLengthResponse(status, MIME_JSON, json.toString())
  }

  private fun jsonError(status: Response.Status, message: String): Response {
    val json = JSONObject()
    json.put("success", false)
    json.put("error", message)
    json.put("status", status.name.lowercase(Locale.US))
    return newFixedLengthResponse(status, MIME_JSON, json.toString())
  }
}

private fun JSONObject.optNullableString(key: String): String? {
  if (!has(key)) {
    return null
  }
  val value = optString(key, "")
  return value.takeIf { it.isNotBlank() }
}
