package dev.juhidamley.claudecling

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.Toolkit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class ClaudeClingService(
  private val project: Project,
) : Disposable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val attachedWidgets = ConcurrentHashMap.newKeySet<TerminalWidget>()
  private val started = AtomicBoolean(false)

  fun start() {
    if (!started.compareAndSet(false, true)) return

    val terminalManager = TerminalToolWindowManager.getInstance(project)
    terminalManager.terminalWidgets.forEach(::attachMonitor)
    terminalManager.addNewTerminalSetupHandler(::attachMonitor, this)
  }

  private fun attachMonitor(widget: TerminalWidget) {
    if (!attachedWidgets.add(widget)) return

    scope.launch {
      val detector = ClaudeSessionDetector()

      while (isActive) {
        val snapshot = try {
          readTerminalSnapshot(widget)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          attachedWidgets.remove(widget)
          thisLogger().debug("Stopping Claude Cling monitor for terminal widget", e)
          return@launch
        }

        detector.update(snapshot).forEach(::notify)
        delay(POLL_INTERVAL_MS)
      }
    }
  }

  private suspend fun readTerminalSnapshot(widget: TerminalWidget): TerminalSnapshot {
    return withContext(Dispatchers.Main) {
      val shellWidget = widget as? ShellTerminalWidget
        ?: return@withContext TerminalSnapshot("", false)
      val buffer = shellWidget.terminalTextBuffer
      val historyStart = -minOf(buffer.historyLinesCount, MAX_HISTORY_LINES)
      val text = buildString {
        for (row in historyStart until buffer.height) {
          append(buffer.getLine(row).getText())
          append('\n')
        }
      }
      val lastLine = text.trimEnd().substringAfterLast('\n', text.trimEnd())
      val running = text.trimEnd().isNotEmpty() && !SHELL_IDLE_REGEX.containsMatchIn(lastLine)
      TerminalSnapshot(text = text, running = running)
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

  companion object {
    private val SHELL_IDLE_REGEX = Regex("""[\$%#>]\s*$""")
    private const val MAX_HISTORY_LINES = 200
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