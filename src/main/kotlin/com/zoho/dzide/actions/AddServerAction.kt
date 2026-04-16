package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ui.Messages
import com.zoho.dzide.model.ProjectServerMapping
import com.zoho.dzide.model.TomcatServer
import com.zoho.dzide.tomcat.TomcatServerProvider
import com.zoho.dzide.util.NotificationUtil
import com.zoho.dzide.zide.ZideConfigParser
import com.zoho.dzide.zide.ZideSetupWizard
import java.nio.file.Path
import kotlin.io.path.exists

class AddServerAction : AnAction("Add Tomcat Server", "Add a new Tomcat server", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val serverProvider = TomcatServerProvider.getInstance(project)
        val projectPath = project.basePath ?: return

        val hasZideConfig = ZideConfigParser.detectZideConfigInProject(projectPath)

        if (hasZideConfig) {
            val choice = Messages.showYesNoCancelDialog(
                project,
                "ZIDE configuration detected. Auto-configure from ZIDE?",
                "Configure Tomcat Server",
                "Auto-configure from ZIDE",
                "Configure Manually",
                "Cancel",
                Messages.getQuestionIcon()
            )
            when (choice) {
                Messages.YES -> {
                    val server = ZideSetupWizard.runZideSetupWizard(project, projectPath) ?: return
                    serverProvider.addServer(server)
                    serverProvider.setProjectMapping(
                        ProjectServerMapping(
                            projectPath = projectPath,
                            serverId = server.id,
                            contextPath = "/",
                            warFilePath = null
                        )
                    )
                    NotificationUtil.info(project, "Tomcat server '${server.name}' added from ZIDE configuration!")
                    return
                }
                Messages.NO -> { /* fall through to manual */ }
                else -> return
            }
        }

        // Manual configuration
        val name = Messages.showInputDialog(
            project,
            "Enter server name:",
            "Add Tomcat Server",
            null,
            "Tomcat 9 Development",
            null
        ) ?: return

        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Tomcat Home Directory")
        val folders = FileChooserFactory.getInstance()
            .createFileChooser(descriptor, project, null)
            .choose(project)
        val serverPath = folders.firstOrNull()?.path ?: return

        val catalinaScript = Path.of(serverPath, "bin",
            if (com.zoho.dzide.util.ShellUtil.isWindows) "catalina.bat" else "catalina.sh")
        if (!catalinaScript.exists()) {
            NotificationUtil.error(project, "Invalid Tomcat directory. Ensure it contains bin/catalina.sh")
            return
        }

        val portStr = Messages.showInputDialog(
            project,
            "Enter server port:",
            "Add Tomcat Server",
            null,
            "8080",
            null
        ) ?: return
        val port = portStr.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            NotificationUtil.error(project, "Invalid port number: $portStr")
            return
        }

        val description = Messages.showInputDialog(
            project,
            "Enter server description (optional):",
            "Add Tomcat Server",
            null,
            "",
            null
        )

        val manualLaunchArgs = Messages.showInputDialog(
            project,
            "Enter additional launch VM arguments (optional):",
            "Add Tomcat Server",
            null,
            "",
            null
        )

        val server = TomcatServer(
            id = System.currentTimeMillis().toString(),
            name = name,
            path = serverPath,
            status = "stopped",
            port = port,
            description = description?.ifBlank { null },
            manualLaunchArgs = manualLaunchArgs?.trim()?.ifBlank { null }
        )

        serverProvider.addServer(server)
        NotificationUtil.info(project, "Tomcat server '$name' added successfully!")
    }
}
