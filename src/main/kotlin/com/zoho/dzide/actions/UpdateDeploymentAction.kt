package com.zoho.dzide.actions

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.util.NotificationUtil
import com.zoho.dzide.util.ProcessUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

class UpdateDeploymentAction : AnAction("Update Deployment", "Deploy a zip file to the server", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return
        val tomcatManager = TomcatManager.getInstance(project)
        val console = tomcatManager.consoleView

        if (console == null) {
            NotificationUtil.error(project, "Console not available. Open the SAS-ZIDE tool window first.")
            return
        }

        // Let user choose a zip file
        val descriptor = FileChooserDescriptor(true, false, true, true, false, false)
            .withTitle("Select Zip File to Deploy")
            .withDescription("Choose a .zip file to deploy to the server")
            .withFileFilter { it.extension == "zip" }

        val files = FileChooserFactory.getInstance()
            .createFileChooser(descriptor, project, null)
            .choose(project)
        val selectedFile = files.firstOrNull() ?: return
        val zipPath = Path.of(selectedFile.path)

        // Resolve deployment folder from ZIDE.DEPLOYMENT_FOLDER
        val deploymentFolder = server.zideRuntimeProperties?.get("ZIDE.DEPLOYMENT_FOLDER")
        if (deploymentFolder.isNullOrBlank()) {
            NotificationUtil.error(project, "ZIDE.DEPLOYMENT_FOLDER not configured for this server. Add the server via ZIDE auto-configuration.")
            return
        }
        val deployDir = Path.of(deploymentFolder)

        // Switch to Output tab
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("SAS-ZIDE")
        toolWindow?.show {
            val outputContent = toolWindow.contentManager.findContent("Output")
            if (outputContent != null) {
                toolWindow.contentManager.setSelectedContent(outputContent)
            }
        }

        console.clear()
        console.print("=== Update Deployment ===\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print("Zip file: $zipPath\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print("Deploy to: $deployDir\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Ensure deploy directory exists
                Files.createDirectories(deployDir)

                // Copy zip to deployment folder
                val destZip = deployDir.resolve(zipPath.name)
                printToConsole(console, project, "[1/2] Copying ${zipPath.name} to $deployDir...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                Files.copy(zipPath, destZip, StandardCopyOption.REPLACE_EXISTING)
                printToConsole(console, project, "Copied successfully.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

                // Unzip
                printToConsole(console, project, "[2/2] Unzipping ${zipPath.name}...\n", ConsoleViewContentType.SYSTEM_OUTPUT)

                val unzipResult = ProcessUtil.executeCapturing(
                    command = listOf("unzip", "-o", destZip.toString(), "-d", deployDir.toString()),
                    workingDir = deployDir.toString(),
                    timeoutMs = 120_000
                )

                if (unzipResult.stdout.isNotBlank()) {
                    printToConsole(console, project, unzipResult.stdout + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
                }
                if (unzipResult.stderr.isNotBlank()) {
                    printToConsole(console, project, unzipResult.stderr + "\n", ConsoleViewContentType.ERROR_OUTPUT)
                }

                if (unzipResult.exitCode != 0) {
                    printToConsole(console, project, "\nUnzip FAILED (exit code ${unzipResult.exitCode})\n", ConsoleViewContentType.ERROR_OUTPUT)
                    NotificationUtil.error(project, "Unzip failed with exit code ${unzipResult.exitCode}")
                    return@executeOnPooledThread
                }

                // Clean up the zip from deploy dir
                Files.deleteIfExists(destZip)

                printToConsole(console, project, "\n=== Deployment updated ===\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                NotificationUtil.info(project, "Deployment updated to $deployDir")

            } catch (ex: Exception) {
                printToConsole(console, project, "Error: ${ex.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                NotificationUtil.error(project, "Update deployment failed: ${ex.message}")
            }
        }
    }

    private fun printToConsole(
        console: com.intellij.execution.ui.ConsoleView,
        project: com.intellij.openapi.project.Project,
        text: String,
        type: ConsoleViewContentType
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                console.print(text, type)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
