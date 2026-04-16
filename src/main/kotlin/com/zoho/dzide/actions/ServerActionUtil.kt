package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.zoho.dzide.model.TomcatServer
import com.zoho.dzide.tomcat.TomcatServerProvider
import com.zoho.dzide.util.NotificationUtil
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

object ServerActionUtil {

    /**
     * Gets the selected server from the tool window tree.
     * If nothing is selected, shows a popup picker to choose a server.
     */
    fun getSelectedServer(e: AnActionEvent): TomcatServer? {
        val project = e.project ?: return null

        // Try to get from tree selection in tool window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DZIDE Tomcat")
        if (toolWindow != null) {
            val content = toolWindow.contentManager.selectedContent
            if (content != null) {
                val tree = findTree(content.component)
                if (tree != null) {
                    val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
                    val server = node?.userObject as? TomcatServer
                    if (server != null) return server
                }
            }
        }

        // Fallback: pick from all servers
        val serverProvider = TomcatServerProvider.getInstance(project)
        val servers = serverProvider.getServers()
        if (servers.isEmpty()) {
            NotificationUtil.error(project, "No Tomcat servers configured. Please add a server first.")
            return null
        }
        if (servers.size == 1) return servers[0]

        // Show picker for multiple servers
        var chosen: TomcatServer? = null
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(servers)
            .setTitle("Select Tomcat Server")
            .setRenderer { _, value, _, _, _ ->
                javax.swing.JLabel("${value.name} (port ${value.port}) — ${value.status}")
            }
            .setItemChosenCallback { chosen = it }
            .createPopup()
            .showInFocusCenter()
        return chosen
    }

    private fun findTree(component: java.awt.Component): JTree? {
        if (component is JTree) return component
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findTree(child)
                if (found != null) return found
            }
        }
        return null
    }
}
