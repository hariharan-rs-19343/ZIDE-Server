package com.zoho.dzide.actions

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.zoho.dzide.deploysync.AntResolver
import com.zoho.dzide.model.TomcatServer
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.util.NotificationUtil
import com.zoho.dzide.util.PortUtil
import com.zoho.dzide.util.ProcessUtil
import com.zoho.dzide.util.ShellUtil
import com.zoho.dzide.zide.ZideConfigParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.io.path.exists
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

        // Resolve deployment folder
        val deploymentFolder = server.zideRuntimeProperties?.get("ZIDE.DEPLOYMENT_FOLDER")
        if (deploymentFolder.isNullOrBlank()) {
            NotificationUtil.error(project, "ZIDE.DEPLOYMENT_FOLDER not configured for this server.")
            return
        }
        val deployDir = Path.of(deploymentFolder)

        // Resolve required paths
        val projectPath = project.basePath
        val repositoryPath = readRepositoryPath(projectPath)
        val deploymentPath = server.path

        if (repositoryPath == null) {
            NotificationUtil.error(project, "Could not read 'repositorypath' from .zide_resources/repository.properties.")
            return
        }

        // Read PARENT_SERVICE from service.xml ZIDE.PARENT_SERVICE property
        val zideConfig = ZideConfigParser.readZideConfig(repositoryPath)
        val parentService = zideConfig?.service?.properties?.get("ZIDE.PARENT_SERVICE")
        if (parentService.isNullOrBlank()) {
            NotificationUtil.error(project, "Could not read ZIDE.PARENT_SERVICE from service.xml.")
            return
        }

        // Read DB params from zide_properties.xml
        val zideProps = zideConfig.properties?.properties ?: emptyMap()
        val dbUser = zideProps["ZIDE.DB_USER"] ?: "root"
        val dbName = zideProps["ZIDE.DB_NAME"] ?: parentService.lowercase()
        val dbPass = zideProps["ZIDE.DB_PASS"] ?: ""

        // Switch to Output tab
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("SAS-ZIDE")
        toolWindow?.show {
            val outputContent = toolWindow.contentManager.findContent("Output")
            if (outputContent != null) {
                toolWindow.contentManager.setSelectedContent(outputContent)
            }
        }

        console.clear()
        printToConsole(console, project, "=== Update Deployment ===\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        printToConsole(console, project, "Zip file: $zipPath\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        printToConsole(console, project, "Deploy to: $deployDir\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        printToConsole(console, project, "Repository: $repositoryPath\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        printToConsole(console, project, "Service: $parentService\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Step 1: Stop server if running
                if (PortUtil.isPortInUse(server.port)) {
                    printToConsole(console, project, "[Stop] Server ${server.name} is running. Stopping...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            tomcatManager.stopServer(server)
                        }
                    }
                    val stopped = PortUtil.waitForPortRelease(server.port, 30000)
                    if (stopped) {
                        printToConsole(console, project, "[Stop] Server stopped.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                    } else {
                        printToConsole(console, project, "[Stop] WARNING: Server may still be running. Proceeding anyway.\n\n", ConsoleViewContentType.LOG_WARNING_OUTPUT)
                    }
                }

                // Step 2: Copy zip to deployment folder and extract
                Files.createDirectories(deployDir)
                val destZip = deployDir.resolve(zipPath.name)

                printToConsole(console, project, "[1/5] Copying ${zipPath.name} to $deployDir...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                Files.copy(zipPath, destZip, StandardCopyOption.REPLACE_EXISTING)
                printToConsole(console, project, "Copied successfully.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

                printToConsole(console, project, "[2/5] Extracting ${zipPath.name}...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
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
                    printToConsole(console, project, "\nExtract FAILED (exit code ${unzipResult.exitCode})\n", ConsoleViewContentType.ERROR_OUTPUT)
                    NotificationUtil.error(project, "Extraction failed.")
                    return@executeOnPooledThread
                }
                printToConsole(console, project, "Extracted successfully.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

                // Resolve ANT
                val antHome = AntResolver.resolveAntHome(repositoryPath, server.antHomeResolvedPath)
                if (antHome == null) {
                    printToConsole(console, project, "ERROR: ANT home not found. Cannot run hooks.\n", ConsoleViewContentType.ERROR_OUTPUT)
                    NotificationUtil.error(project, "ANT home not resolved. Hooks skipped.")
                    return@executeOnPooledThread
                }
                val antExec = AntResolver.resolveAntExecutable(antHome)
                val hookBaseDir = Path.of(repositoryPath, ".zide_resources", "zide_hook").toString()
                val buildXml = Path.of(hookBaseDir, "build.xml").toString()

                if (!Path.of(buildXml).exists()) {
                    printToConsole(console, project, "ERROR: build.xml not found at $buildXml. Hooks skipped.\n", ConsoleViewContentType.ERROR_OUTPUT)
                    NotificationUtil.error(project, "build.xml not found. Hooks skipped.")
                    return@executeOnPooledThread
                }

                // Step 3: Pre-creation hook
                printToConsole(console, project, "[3/5] Running pre-creation hook...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                val preCreationOk = runAntHook(
                    console, project, antExec, buildXml, hookBaseDir,
                    "precreationhook", repositoryPath, deploymentPath, parentService,
                    emptyMap()
                )
                if (!preCreationOk) {
                    printToConsole(console, project, "Pre-creation hook FAILED.\n\n", ConsoleViewContentType.ERROR_OUTPUT)
                } else {
                    printToConsole(console, project, "Pre-creation hook completed.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                }

                // Step 4: Post-creation hook (uses zide_build basedir)
                val postHookBaseDir = Path.of(repositoryPath, ".zide_resources", "zide_build").toString()
                val postBuildXml = Path.of(postHookBaseDir, "build.xml").toString()
                printToConsole(console, project, "[4/5] Running post-creation hook...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                val postCreationOk = runAntHook(
                    console, project, antExec, postBuildXml, postHookBaseDir,
                    "postservicetarget", repositoryPath, deploymentPath, parentService,
                    mapOf(
                        "ZIDE_DB_USER" to dbUser,
                        "ZIDE_DB_NAME" to dbName,
                        "ZIDE_DB_PASS" to dbPass
                    )
                )
                if (!postCreationOk) {
                    printToConsole(console, project, "Post-creation hook FAILED.\n\n", ConsoleViewContentType.ERROR_OUTPUT)
                } else {
                    printToConsole(console, project, "Post-creation hook completed.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                }

                // Step 5: Zide module hook
                printToConsole(console, project, "[5/5] Running zide module hook...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                val moduleHookOk = runAntHook(
                    console, project, antExec, buildXml, hookBaseDir,
                    "zidemodulehook", repositoryPath, deploymentPath, parentService,
                    emptyMap()
                )
                if (!moduleHookOk) {
                    printToConsole(console, project, "Zide module hook FAILED.\n\n", ConsoleViewContentType.ERROR_OUTPUT)
                } else {
                    printToConsole(console, project, "Zide module hook completed.\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                }

                printToConsole(console, project, "=== Deployment update completed ===\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                NotificationUtil.info(project, "Deployment updated successfully.")

            } catch (ex: Exception) {
                printToConsole(console, project, "Error: ${ex.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                NotificationUtil.error(project, "Update deployment failed: ${ex.message}")
            }
        }
    }

    private fun readRepositoryPath(projectPath: String?): String? {
        if (projectPath == null) return null
        val repoPropsFile = Path.of(projectPath, ".zide_resources", "repository.properties")
        if (!repoPropsFile.exists()) return null
        return try {
            val props = Properties()
            Files.newInputStream(repoPropsFile).use { props.load(it) }
            props.getProperty("repositorypath")?.trim()?.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun runAntHook(
        console: ConsoleView,
        project: Project,
        antExec: String,
        buildXml: String,
        buildBaseDir: String,
        target: String,
        repositoryPath: String,
        deploymentPath: String,
        parentService: String,
        extraProps: Map<String, String>
    ): Boolean {
        val extraArgs = extraProps.entries.joinToString(" ") {
            "-D${it.key}=${ShellUtil.shellEscapeSingleQuoted(it.value)}"
        }

        val command = ShellUtil.buildShellCommand(
            "\"$antExec\"",
            "-f", "\"$buildXml\"",
            "-Dbasedir=\"$buildBaseDir\"",
            "clone",
            "-Dtarget=$target",
            "-DREPOSITORY_PATH=$repositoryPath",
            "-DDEPLOYMENT_PATH=$deploymentPath",
            "-DZIDE.PARENT_SERVICE=$parentService",
            extraArgs
        )

        printToConsole(console, project, "$ ${command.last()}\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        val result = ProcessUtil.executeCapturing(
            command = command,
            workingDir = repositoryPath,
            timeoutMs = 300_000
        )

        if (result.stdout.isNotBlank()) {
            printToConsole(console, project, result.stdout + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
        if (result.stderr.isNotBlank()) {
            printToConsole(console, project, result.stderr + "\n", ConsoleViewContentType.ERROR_OUTPUT)
        }

        return result.exitCode == 0
    }

    private fun printToConsole(
        console: ConsoleView,
        project: Project,
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
