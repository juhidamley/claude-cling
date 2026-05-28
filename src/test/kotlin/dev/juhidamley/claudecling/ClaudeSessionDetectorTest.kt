package dev.juhidamley.claudecling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeSessionDetectorTest {
  @Test
  fun `notifies when claude run finishes`() {
    val detector = ClaudeSessionDetector()

    assertTrue(detector.update(TerminalSnapshot("$ claude\n", false)).isEmpty())
    assertTrue(detector.update(TerminalSnapshot("$ claude\nWorking...\n", true)).isEmpty())

    val events = detector.update(TerminalSnapshot("$ claude\nDone\n$ ", false))

    assertEquals(listOf("Claude Code finished"), events.map { it.title })
    assertEquals(listOf(ClaudeClingSeverity.INFO), events.map { it.severity })
  }

  @Test
  fun `notifies when claude asks for attention`() {
    val detector = ClaudeSessionDetector()

    detector.update(TerminalSnapshot("$ claude code\n", false))
    detector.update(TerminalSnapshot("$ claude code\nWorking...\n", true))

    val events = detector.update(
      TerminalSnapshot(
        "$ claude code\nWorking...\nPress Enter to continue\n",
        true,
      ),
    )

    assertEquals(listOf("Claude Code needs attention"), events.map { it.title })
    assertEquals(listOf(ClaudeClingSeverity.WARNING), events.map { it.severity })
  }

  @Test
  fun `ignores non claude commands`() {
    val detector = ClaudeSessionDetector()

    detector.update(TerminalSnapshot("$ npm test\n", false))
    detector.update(TerminalSnapshot("$ npm test\nRunning...\n", true))

    assertTrue(detector.update(TerminalSnapshot("$ npm test\nDone\n$ ", false)).isEmpty())
  }
}
