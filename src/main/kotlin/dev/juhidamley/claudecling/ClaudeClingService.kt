package dev.juhidamley.claudecling

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.Toolkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class ClaudeClingService(
  private val project: Project,
) : Disposable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val attachedWidgets = ConcurrentHashMap.newKeySet<TerminalWidget>()
  @Volatile
  private var started = false

  fun start() {
    if (started) return
    started = true

    val terminalManager = TerminalToolWindowManager.getInstance(project)
    terminalManager.terminalWidgets.forEach(::attachMonitor)
    terminalManager.addNewTerminalSetupHandler(::attachMonitor, this)
  }

  private fun attachMonitor(widget: TerminalWidget) {
    if (!attachedWidgets.add(widget)) return

    scope.launch {
      val detector = ClaudeSessionDetector()

      while (isActive) {
        val snapshot = runCatching {
          TerminalSnapshot(
            text = readTerminalText(widget),
            running = widget.isCommandRunning(),
          )
        }.getOrElse { error ->
          attachedWidgets.remove(widget)
          thisLogger().debug("Stopping Claude Cling monitor for terminal widget", error)
          return@launch
        }

        detector.update(snapshot).forEach(::notify)
        delay(POLL_INTERVAL_MS)
      }
    }
  }

  private fun notify(event: ClaudeClingEvent) {
    runCatching { Toolkit.getDefaultToolkit().beep() }

    NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(event.title, event.message, event.severity.toNotificationType())
      .notify(project)
  }

  override fun dispose() {
    scope.cancel()
    attachedWidgets.clear()
  }

  private fun readTerminalText(widget: TerminalWidget): String {
    val future = CompletableFuture<String>()
    ApplicationManager.getApplication().invokeLater {
      future.complete(widget.text.toString())
    }
    return future.get(5, TimeUnit.SECONDS)
  }

  companion object {
    private const val POLL_INTERVAL_MS = 3000L
    private const val NOTIFICATION_GROUP_ID = "Claude Cling"

    fun getInstance(project: Project): ClaudeClingService = project.service()
  }
}

private fun ClaudeClingSeverity.toNotificationType(): NotificationType {
  return when (this) {
    ClaudeClingSeverity.INFO -> NotificationType.INFORMATION
    ClaudeClingSeverity.WARNING -> NotificationType.WARNING
  }
}
