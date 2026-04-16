package com.zoho.dzide.toolwindow

import com.zoho.dzide.model.TomcatServer
import com.zoho.dzide.tomcat.TomcatServerProvider
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class TomcatTreeModel(private val serverProvider: TomcatServerProvider) : DefaultTreeModel(DefaultMutableTreeNode("Tomcat Servers")) {

    init {
        reload()
    }

    override fun reload() {
        val rootNode = root as DefaultMutableTreeNode
        rootNode.removeAllChildren()
        for (server in serverProvider.getServers()) {
            rootNode.add(DefaultMutableTreeNode(server))
        }
        super.reload()
    }

    fun getServerAt(node: DefaultMutableTreeNode): TomcatServer? {
        return node.userObject as? TomcatServer
    }
}
