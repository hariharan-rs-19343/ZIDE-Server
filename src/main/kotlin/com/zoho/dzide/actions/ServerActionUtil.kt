package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.zoho.dzide.model.TomcatServer
import com.zoho.dzide.tomcat.TomcatServerProvider
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

object ServerActionUtil {

    fun getSelectedServer(e: AnActionEvent): TomcatServer? {
        val project = e.project ?: return null
        // Try to get from tree selection in tool window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DZIDE Tomcat") ?: return null
        val content = toolWindow.contentManager.selectedContent ?: return null
        val tree = findTree(content.component) ?: return null
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? TomcatServer
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
