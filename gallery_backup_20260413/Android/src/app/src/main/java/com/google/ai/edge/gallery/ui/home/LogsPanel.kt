package com.google.ai.edge.gallery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.common.LogBuffer
import com.google.ai.edge.gallery.common.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsPanel(onDismissed: () -> Unit) {
  val entries by LogBuffer.entries.collectAsState()
  val clipboardManager = LocalClipboardManager.current
  val formatter = rememberDateFormatter()
  Dialog(onDismissRequest = onDismissed) {
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = stringResource(R.string.logs_dialog_title),
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = {
              val text = entries.joinToString(separator = "\n") { entry ->
                "${formatter.format(Date(entry.timestampMs))} [${entry.tag}] ${entry.message}"
              }
              clipboardManager.setText(AnnotatedString(text))
            },
            enabled = entries.isNotEmpty(),
          ) {
            Text(stringResource(R.string.logs_dialog_copy_all))
          }
          TextButton(
            onClick = { LogBuffer.clear() },
            enabled = entries.isNotEmpty(),
          ) {
            Text(stringResource(R.string.logs_dialog_clear))
          }
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        if (entries.isEmpty()) {
          Text(
            text = stringResource(R.string.logs_dialog_empty),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            items(entries.reversed()) { entry ->
              LogEntryRow(entry = entry, formatter = formatter)
            }
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismissed, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
          Text(stringResource(R.string.logs_dialog_close))
        }
      }
    }
  }
}

@Composable
private fun rememberDateFormatter(): SimpleDateFormat {
  val locale = Locale.getDefault()
  return remember(locale) { SimpleDateFormat("HH:mm:ss.SSS", locale) }
}

@Composable
private fun LogEntryRow(entry: LogEntry, formatter: SimpleDateFormat) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Text(
      text = "${formatter.format(Date(entry.timestampMs))} [${entry.tag}]",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = entry.message, style = MaterialTheme.typography.bodyMedium)
  }
}
