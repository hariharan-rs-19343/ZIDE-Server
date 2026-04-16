package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.util.NotificationUtil

class RedeployWarAction : AnAction("Redeploy WAR", "Deploy a WAR file to the server", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return

        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Select WAR File to Deploy")
            .withDescription("Choose a WAR file")
            .withFileFilter { it.extension == "war" }

        val files = FileChooserFactory.getInstance()
            .createFileChooser(descriptor, project, null)
            .choose(project)
        val warFile = files.firstOrNull()?.path ?: return
        val contextPath = warFile.substringAfterLast('/').removeSuffix(".war")

        TomcatManager.getInstance(project).deployWarFile(server, warFile, contextPath)
    }
}
