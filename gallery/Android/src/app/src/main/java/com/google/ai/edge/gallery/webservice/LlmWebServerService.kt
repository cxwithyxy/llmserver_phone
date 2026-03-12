package com.google.ai.edge.gallery.webservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.common.LogBuffer
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

@AndroidEntryPoint
class LlmWebServerService : Service() {

  @Inject lateinit var downloadRepository: DownloadRepository
  @Inject lateinit var dataStoreRepository: DataStoreRepository
  @Inject lateinit var lifecycleProvider: AppLifecycleProvider
  @Inject lateinit var customTasks: Set<@JvmSuppressWildcards CustomTask>

  private var webServer: LlmNanoHttpServer? = null
  private lateinit var inferenceEngine: LlmInferenceEngine
  private lateinit var powerManager: PowerManager
  private var ipAddress: String = "0.0.0.0"

  override fun onCreate() {
    super.onCreate()
    ipAddress = resolveLocalIpAddress()
    powerManager = getSystemService(PowerManager::class.java) ?: error("PowerManager unavailable")
    inferenceEngine =
      LlmInferenceEngine(
        context = applicationContext,
        downloadRepository = downloadRepository,
        dataStoreRepository = dataStoreRepository,
        lifecycleProvider = lifecycleProvider,
        customTasks = customTasks,
      )
    logInfo("Web service created; resolved IP=$ipAddress")
    startForeground(NOTIFICATION_ID, createNotification())
    startServer()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_SERVICE -> {
        logInfo("Received stop intent")
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_RESTART_SERVICE -> {
        logInfo("Received restart intent")
        restartServer()
        return START_STICKY
      }
    }
    if (webServer == null) {
      logInfo("Server reference missing; starting a new instance")
      startServer()
    }
    return START_STICKY
  }

  override fun onDestroy() {
    stopServer()
    inferenceEngine.dispose()
    logInfo("LLM web service stopped")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startServer() {
    stopServer()
    try {
      webServer =
        LlmNanoHttpServer(port = DEFAULT_PORT, requestHandler = this::handleChatRequest).apply {
          this.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
      logInfo("LLM web service listening on ${ipAddress}:$DEFAULT_PORT")
      updateNotification()
    } catch (error: IOException) {
      logError("Unable to start embedded server", error)
      stopSelf()
    }
  }

  private fun stopServer() {
    if (webServer != null) {
      logInfo("Stopping embedded server")
    }
    webServer?.stop()
    webServer = null
  }

  private fun restartServer() {
    ipAddress = resolveLocalIpAddress()
    logInfo("Restarting server with IP=$ipAddress")
    startServer()
  }

  private suspend fun handleChatRequest(request: LlmWebRequest): LlmWebResponse {
    logInfo("Incoming /chat request reset=${request.resetConversation} requestedModel=${request.model}")
    return withWakeLock {
      val preferred = inferenceEngine.getPreferredModelName()
      val response = inferenceEngine.handleChatRequest(request = request, preferredModelName = preferred)
      logInfo("Request completed model=${response.model} latency=${response.latencyMs}ms")
      response
    }
  }

  private fun createNotification(): Notification {
    ensureNotificationChannel()
    val stopIntent =
      Intent(this, LlmWebServerService::class.java).apply { action = ACTION_STOP_SERVICE }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        0,
        stopIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.web_service_notification_title))
      .setContentText(
        getString(R.string.web_service_notification_content, ipAddress, DEFAULT_PORT),
      )
      .setSmallIcon(R.drawable.logo)
      .setOngoing(true)
      .addAction(0, getString(R.string.web_service_notification_stop_action), stopPendingIntent)
      .build()
  }

  private fun updateNotification() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, createNotification())
  }

  private suspend fun <T> withWakeLock(block: suspend () -> T): T {
    val wakeLock = acquireWakeLock()
    return try {
      block()
    } finally {
      releaseWakeLock(wakeLock)
    }
  }

  private fun acquireWakeLock(): PowerManager.WakeLock {
    val wakeLock =
      powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:WebServiceWakelock")
    wakeLock.setReferenceCounted(false)
    wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    logDebug("WakeLock acquired for up to ${WAKE_LOCK_TIMEOUT_MS}ms")
    return wakeLock
  }

  private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
    if (wakeLock?.isHeld == true) {
      logDebug("WakeLock released")
      wakeLock.release()
    }
  }

  private fun ensureNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.web_service_notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      )
    manager.createNotificationChannel(channel)
  }

  private fun resolveLocalIpAddress(): String {
    try {
      val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
      interfaces.forEach { intf ->
        if (!intf.isUp || intf.isLoopback) return@forEach
        val addresses = Collections.list(intf.inetAddresses)
        addresses.forEach { addr ->
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            return addr.hostAddress
          }
        }
      }
    } catch (error: Exception) {
      logError("Unable to resolve local IP", error)
    }
    return "0.0.0.0"
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

  companion object {
    private const val TAG = "LlmWebServerService"
    private const val CHANNEL_ID = "llmserver_web_service"
    private const val NOTIFICATION_ID = 0x1001
    private const val ACTION_START_SERVICE =
      "com.google.ai.edge.gallery.webservice.action.START"
    private const val ACTION_STOP_SERVICE =
      "com.google.ai.edge.gallery.webservice.action.STOP"
    private const val ACTION_RESTART_SERVICE =
      "com.google.ai.edge.gallery.webservice.action.RESTART"
    private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 1000L
    const val DEFAULT_PORT = 8081

    fun start(context: Context) {
      val intent = Intent(context, LlmWebServerService::class.java).apply {
        action = ACTION_START_SERVICE
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, LlmWebServerService::class.java)
      context.stopService(intent)
    }

    fun restart(context: Context) {
      val intent = Intent(context, LlmWebServerService::class.java).apply {
        action = ACTION_RESTART_SERVICE
      }
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
