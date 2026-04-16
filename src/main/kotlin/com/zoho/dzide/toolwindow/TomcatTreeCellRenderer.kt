package com.zoho.dzide.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.zoho.dzide.model.TomcatServer
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class TomcatTreeCellRenderer : DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        val node = value as? DefaultMutableTreeNode ?: return this
        val server = node.userObject as? TomcatServer ?: return this

        text = server.name
        toolTipText = "${server.name}\nPath: ${server.path}\nPort: ${server.port}\nStatus: ${server.status}"

        when (server.status) {
            "running" -> {
                icon = AllIcons.Actions.Execute
                foreground = if (sel) foreground else JBColor.GREEN.darker()
                text = "${server.name}  [running :${server.port}]"
            }
            "stopped" -> {
                icon = AllIcons.Actions.Suspend
                foreground = if (sel) foreground else JBColor.RED.darker()
                text = "${server.name}  [stopped :${server.port}]"
            }
            else -> {
                icon = AllIcons.General.QuestionDialog
                text = "${server.name}  [unknown :${server.port}]"
            }
        }

        return this
    }
}
