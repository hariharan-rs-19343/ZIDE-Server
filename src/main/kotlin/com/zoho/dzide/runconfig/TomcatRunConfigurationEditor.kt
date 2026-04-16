package com.zoho.dzide.runconfig

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.zoho.dzide.tomcat.TomcatServerProvider
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class TomcatRunConfigurationEditor(
    private val project: Project
) : SettingsEditor<TomcatRunConfiguration>() {

    private val modeCombo = ComboBox(arrayOf("run", "debug"))
    private val serverCombo = ComboBox<String>()
    private val contextPathField = JBTextField("ROOT")
    private val warFilePathField = JBTextField()

    override fun createEditor(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        var row = 0

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        panel.add(JLabel("Mode:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(modeCombo, gbc)

        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        panel.add(JLabel("Server:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        refreshServerList()
        panel.add(serverCombo, gbc)

        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        panel.add(JLabel("Context Path:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(contextPathField, gbc)

        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        panel.add(JLabel("WAR File:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(warFilePathField, gbc)

        return panel
    }

    private fun refreshServerList() {
        serverCombo.removeAllItems()
        val servers = TomcatServerProvider.getInstance(project).getServers()
        for (server in servers) {
            serverCombo.addItem("${server.name} (${server.id})")
        }
    }

    override fun applyEditorTo(config: TomcatRunConfiguration) {
        config.mode = modeCombo.selectedItem as? String ?: "run"
        val selectedServerText = serverCombo.selectedItem as? String ?: ""
        val idMatch = Regex("""\((\d+)\)$""").find(selectedServerText)
        config.serverId = idMatch?.groupValues?.get(1) ?: ""
        config.contextPath = contextPathField.text.ifBlank { "ROOT" }
        config.warFilePath = warFilePathField.text
    }

    override fun resetEditorFrom(config: TomcatRunConfiguration) {
        modeCombo.selectedItem = config.mode
        refreshServerList()
        // Select server by ID
        val servers = TomcatServerProvider.getInstance(project).getServers()
        val index = servers.indexOfFirst { it.id == config.serverId }
        if (index >= 0) serverCombo.selectedIndex = index
        contextPathField.text = config.contextPath
        warFilePathField.text = config.warFilePath
    }
}
