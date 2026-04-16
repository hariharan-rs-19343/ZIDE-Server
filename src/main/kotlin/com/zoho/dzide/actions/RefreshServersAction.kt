package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.tomcat.TomcatServerProvider

class RefreshServersAction : AnAction("Refresh", "Refresh server status", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tomcatManager = TomcatManager.getInstance(project)
        tomcatManager.refreshAllServerStatus()
    }
}
