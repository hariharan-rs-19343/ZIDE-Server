package com.zoho.dzide.toolwindow

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.tomcat.TomcatServerProvider
import javax.swing.JPanel
import javax.swing.JScrollPane
import java.awt.BorderLayout

class TomcatToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val serverProvider = TomcatServerProvider.getInstance(project)
        val tomcatManager = TomcatManager.getInstance(project)

        // Create tree panel
        val treeModel = TomcatTreeModel(serverProvider)
        val tree = Tree(treeModel)
        tree.cellRenderer = TomcatTreeCellRenderer()
        tree.isRootVisible = false
        tree.showsRootHandles = true

        // Set up context menu
        TomcatTreePopupHandler.install(tree, project, serverProvider, treeModel)

        val treePanel = JPanel(BorderLayout())
        treePanel.add(JScrollPane(tree), BorderLayout.CENTER)

        // Add toolbar
        val toolbar = TomcatToolbar.create(project, serverProvider, treeModel)
        treePanel.add(toolbar.component, BorderLayout.NORTH)

        val contentFactory = ContentFactory.getInstance()
        val treeContent = contentFactory.createContent(treePanel, "Servers", false)
        toolWindow.contentManager.addContent(treeContent)

        // Create console panel
        val console: ConsoleView = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
        tomcatManager.consoleView = console

        val consoleContent = contentFactory.createContent(console.component, "Output", false)
        toolWindow.contentManager.addContent(consoleContent)

        // Listen for changes
        serverProvider.addChangeListener {
            treeModel.reload()
        }
    }
}
