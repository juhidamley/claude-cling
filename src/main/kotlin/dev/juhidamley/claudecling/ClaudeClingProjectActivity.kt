package dev.juhidamley.claudecling

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ClaudeClingProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    ClaudeClingService.getInstance(project).start()
  }
}

