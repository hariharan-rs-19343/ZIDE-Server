package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.zoho.dzide.tomcat.TomcatManager

class StartServerAction : AnAction("Start Server", "Start Tomcat server", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return
        TomcatManager.getInstance(project).startServer(server)
    }
}
