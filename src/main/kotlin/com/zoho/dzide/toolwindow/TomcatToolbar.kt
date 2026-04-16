package com.zoho.dzide.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.zoho.dzide.tomcat.TomcatServerProvider

object TomcatToolbar {

    fun create(project: Project, serverProvider: TomcatServerProvider, treeModel: TomcatTreeModel): ActionToolbar {
        val group = DefaultActionGroup()
        group.add(ActionManager.getInstance().getAction("dzide.AddServer"))
        group.add(ActionManager.getInstance().getAction("dzide.RefreshServers"))
        val toolbar = ActionManager.getInstance().createActionToolbar("DzideTomcatToolbar", group, true)
        toolbar.targetComponent = null
        return toolbar
    }
}
