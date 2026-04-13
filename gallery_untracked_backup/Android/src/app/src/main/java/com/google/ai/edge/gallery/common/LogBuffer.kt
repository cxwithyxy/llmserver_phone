package com.google.ai.edge.gallery.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_LOG_ENTRIES = 500

data class LogEntry(
  val timestampMs: Long,
  val tag: String,
  val message: String,
)

object LogBuffer {
  private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
  val entries = _entries.asStateFlow()

  fun append(tag: String, message: String) {
    val entry = LogEntry(System.currentTimeMillis(), tag, message)
    _entries.update { (it + entry).takeLast(MAX_LOG_ENTRIES) }
  }

  fun clear() {
    _entries.value = emptyList()
  }
}
