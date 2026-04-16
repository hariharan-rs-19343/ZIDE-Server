package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ui.Messages
import com.zoho.dzide.tomcat.TomcatServerProvider
import com.zoho.dzide.util.NotificationUtil
import com.zoho.dzide.util.ShellUtil
import java.nio.file.Path
import kotlin.io.path.exists

class EditServerAction : AnAction("Edit Server", "Edit Tomcat server settings", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return
        val serverProvider = TomcatServerProvider.getInstance(project)

        var updatedPath = server.path

        val updatePathChoice = Messages.showYesNoDialog(
            project,
            "Current Tomcat path: ${server.path}\n\nChange Tomcat directory?",
            "Edit Server",
            "Change Folder",
            "Keep Current",
            Messages.getInformationIcon()
        )
        if (updatePathChoice == Messages.YES) {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Tomcat Home Directory")
            val folders = FileChooserFactory.getInstance()
                .createFileChooser(descriptor, project, null)
                .choose(project)
            val candidate = folders.firstOrNull()?.path ?: return
            val catalinaScript = Path.of(candidate, "bin",
                if (ShellUtil.isWindows) "catalina.bat" else "catalina.sh")
            if (!catalinaScript.exists()) {
                NotificationUtil.error(project, "Invalid Tomcat directory. Ensure it contains bin/catalina.sh")
                return
            }
            updatedPath = candidate
        }

        val name = Messages.showInputDialog(
            project, "Enter server name:", "Edit Server", null, server.name, null
        ) ?: return

        val portStr = Messages.showInputDialog(
            project, "Enter server port:", "Edit Server", null, server.port.toString(), null
        ) ?: return
        val port = portStr.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            NotificationUtil.error(project, "Invalid port number: $portStr")
            return
        }

        val description = Messages.showInputDialog(
            project, "Enter server description (optional):", "Edit Server",
            null, server.description ?: "", null
        )

        val manualLaunchArgs = Messages.showInputDialog(
            project, "Enter additional launch VM arguments (optional):", "Edit Server",
            null, server.manualLaunchArgs ?: "", null
        )

        serverProvider.updateServer(server.id, mapOf(
            "name" to name,
            "path" to updatedPath,
            "port" to port,
            "description" to description?.ifBlank { null },
            "manualLaunchArgs" to manualLaunchArgs?.trim()?.ifBlank { null }
        ))

        NotificationUtil.info(project, "Server '${server.name}' updated successfully!")
    }
}
