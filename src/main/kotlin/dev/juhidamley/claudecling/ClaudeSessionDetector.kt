package dev.juhidamley.claudecling

data class TerminalSnapshot(
  val text: String,
  val running: Boolean,
)

enum class ClaudeClingSeverity {
  INFO,
  WARNING,
}

data class ClaudeClingEvent(
  val title: String,
  val message: String,
  val severity: ClaudeClingSeverity,
)

internal class ClaudeSessionDetector {
  private var lastSnapshot: TerminalSnapshot? = null
  private var claudeActive = false
  private var attentionNotified = false

  fun update(snapshot: TerminalSnapshot): List<ClaudeClingEvent> {
    val previous = lastSnapshot
    lastSnapshot = snapshot

    if (previous == null) {
      claudeActive = snapshot.running && snapshot.text.recentClaudeCommand()
      return emptyList()
    }

    if (!claudeActive && snapshot.running && (newText(previous.text, snapshot.text).recentClaudeCommand() || snapshot.text.recentClaudeCommand())) {
      claudeActive = true
      attentionNotified = false
    }

    if (claudeActive && !attentionNotified && attentionNeeded(previous.text, snapshot.text)) {
      attentionNotified = true
      return listOf(
        ClaudeClingEvent(
          title = "Claude Code needs attention",
          message = "Claude Code printed a prompt that likely needs input in the integrated terminal.",
          severity = ClaudeClingSeverity.WARNING,
        ),
      )
    }

    if (claudeActive && previous.running && !snapshot.running) {
      claudeActive = false
      attentionNotified = false
      return listOf(
        ClaudeClingEvent(
          title = "Claude Code finished",
          message = "Claude Code appears to have completed in the integrated terminal.",
          severity = ClaudeClingSeverity.INFO,
        ),
      )
    }

    if (!snapshot.running && !snapshot.text.recentClaudeCommand()) {
      claudeActive = false
      attentionNotified = false
    }

    return emptyList()
  }

  private fun attentionNeeded(previousText: String, currentText: String): Boolean {
    val delta = newText(previousText, currentText)
    val candidate = if (delta.isNotBlank()) delta else currentText.takeLast(MAX_SCAN_CHARS)
    return ATTENTION_REGEX.containsMatchIn(candidate)
  }

  private fun newText(previousText: String, currentText: String): String {
    return if (currentText.startsWith(previousText)) {
      currentText.substring(previousText.length)
    }
    else {
      currentText.takeLast(MAX_SCAN_CHARS)
    }
  }

  private fun String.recentClaudeCommand(): Boolean {
    return CLAUDE_COMMAND_REGEX.containsMatchIn(takeLast(MAX_SCAN_CHARS))
  }

  companion object {
    private const val MAX_SCAN_CHARS = 4000
    private val CLAUDE_COMMAND_REGEX = Regex(
      pattern = """(?im)(?:^|[\r\n]).{0,24}\b(?:claude(?:\s+code)?|claude-code)\b.*$""",
    )
    private val ATTENTION_REGEX = Regex(
      pattern = """(?im)\b(needs your attention|press enter to continue|waiting for input|select an option|choose an option|confirm|approval|approve|continue\?)\b""",
    )
  }
}
