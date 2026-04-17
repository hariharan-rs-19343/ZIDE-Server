package com.zoho.dzide.actions

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.util.NotificationUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class AppLogsAction : AnAction("App Logs", "Show application logs from server logs directory", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return
        val tomcatManager = TomcatManager.getInstance(project)

        tomcatManager.ensureToolWindow {
            val console = tomcatManager.appLogsConsoleView ?: return@ensureToolWindow

            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("SAS-ZIDE")
            val appLogsContent = toolWindow?.contentManager?.findContent("App Logs")
            if (appLogsContent != null) {
                toolWindow.contentManager.setSelectedContent(appLogsContent)
            }

            val logsDir = Path.of(server.path).parent.resolve("logs")
            if (!logsDir.exists()) {
                NotificationUtil.error(project, "Logs directory not found: $logsDir")
                return@ensureToolWindow
            }

            // Find the latest *.application0.txt file
            val logFile = Files.list(logsDir).use { stream ->
                stream.filter { it.isRegularFile() && it.name.endsWith("application0.txt") }
                    .sorted(Comparator.comparingLong<Path> { Files.getLastModifiedTime(it).toMillis() }.reversed())
                    .findFirst()
                    .orElse(null)
            }

            if (logFile == null) {
                NotificationUtil.error(project, "No *application0.txt log files found in $logsDir")
                return@ensureToolWindow
            }

            console.clear()
            console.print("=== Log file: $logFile ===\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            // Read and display the entire file
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    Files.readAllLines(logFile, Charsets.UTF_8).forEach { line ->
                        val contentType = when {
                            line.contains("ERROR") || line.contains("SEVERE") ->
                                ConsoleViewContentType.ERROR_OUTPUT
                            line.contains("WARN") ->
                                ConsoleViewContentType.LOG_WARNING_OUTPUT
                            else -> ConsoleViewContentType.NORMAL_OUTPUT
                        }
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) {
                                console.print("$line\n", contentType)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            console.print("Error reading log file: ${ex.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                        }
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
