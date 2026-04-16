package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.zoho.dzide.tomcat.TomcatServerProvider
import com.zoho.dzide.util.NotificationUtil

class RemoveServerAction : AnAction("Remove Server", "Remove this Tomcat server", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return

        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove server '${server.name}'?",
            "Remove Server",
            Messages.getWarningIcon()
        )
        if (confirm == Messages.YES) {
            TomcatServerProvider.getInstance(project).removeServer(server.id)
            NotificationUtil.info(project, "Server '${server.name}' removed successfully!")
        }
    }
}
