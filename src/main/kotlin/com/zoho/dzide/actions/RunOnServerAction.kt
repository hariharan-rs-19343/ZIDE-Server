package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.zoho.dzide.model.ServerExecutionSelection
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.tomcat.TomcatServerProvider
import com.zoho.dzide.util.NotificationUtil

class RunOnServerAction : AnAction("Run on Tomcat Server", "Run project on Tomcat", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectPath = project.basePath ?: return
        val serverProvider = TomcatServerProvider.getInstance(project)
        val tomcatManager = TomcatManager.getInstance(project)

        val selection = resolveExecutionSelection(e, serverProvider, projectPath) ?: return

        val warFile = tomcatManager.runProjectOnServer(
            selection.server, projectPath, selection.contextPath, selection.warFilePath
        )
        if (warFile != null && selection.persistMapping) {
            serverProvider.setProjectMapping(
                com.zoho.dzide.model.ProjectServerMapping(
                    projectPath = projectPath,
                    serverId = selection.server.id,
                    contextPath = selection.contextPath,
                    warFilePath = warFile
                )
            )
        }
        tomcatManager.refreshAllServerStatus()
    }
}

class DebugOnServerAction : AnAction("Debug on Tomcat Server", "Debug project on Tomcat", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectPath = project.basePath ?: return
        val serverProvider = TomcatServerProvider.getInstance(project)
        val tomcatManager = TomcatManager.getInstance(project)

        val selection = resolveExecutionSelection(e, serverProvider, projectPath) ?: return

        val debugPort = tomcatManager.debugProjectOnServer(
            selection.server, projectPath, selection.contextPath, selection.warFilePath
        )

        if (debugPort != null) {
            // Attach debugger using IntelliJ's Remote JVM Debug
            val runManager = com.intellij.execution.RunManager.getInstance(project)
            val remoteConfigType = com.intellij.execution.configurations.ConfigurationTypeUtil
                .findConfigurationType("Remote")
            if (remoteConfigType != null) {
                val factory = remoteConfigType.configurationFactories.firstOrNull()
                if (factory != null) {
                    val settings = runManager.createConfiguration(
                        "Attach ${selection.server.name}", factory
                    )
                    val remoteConfig = settings.configuration as? com.intellij.execution.remote.RemoteConfiguration
                    if (remoteConfig != null) {
                        remoteConfig.HOST = "localhost"
                        remoteConfig.PORT = debugPort.toString()
                        settings.isTemporary = true
                        runManager.addConfiguration(settings)
                        runManager.selectedConfiguration = settings
                        com.intellij.execution.ProgramRunnerUtil.executeConfiguration(
                            settings,
                            com.intellij.execution.executors.DefaultDebugExecutor.getDebugExecutorInstance()
                        )
                        NotificationUtil.info(project, "Debugger attaching to ${selection.server.name} on port $debugPort.")
                    }
                }
            }
        }

        tomcatManager.refreshAllServerStatus()
    }
}

internal fun resolveExecutionSelection(
    e: AnActionEvent,
    serverProvider: TomcatServerProvider,
    projectPath: String
): ServerExecutionSelection? {
    val project = e.project ?: return null
    val mapping = serverProvider.getProjectMapping(projectPath)

    if (mapping != null) {
        val mappedServer = serverProvider.getServer(mapping.serverId)
        if (mappedServer != null) {
            return ServerExecutionSelection(
                server = mappedServer,
                contextPath = mapping.contextPath,
                warFilePath = mapping.warFilePath
                    ?: resolveServerWarPreference(mappedServer),
                persistMapping = true
            )
        }
    }

    val servers = serverProvider.getServers()
    if (servers.isEmpty()) {
        NotificationUtil.error(project, "No Tomcat servers configured. Please add a server first.")
        return null
    }

    // Quick selection for single server
    if (servers.size == 1) {
        val server = servers[0]
        val contextPath = Messages.showInputDialog(
            project, "Enter context path (ROOT for base URL):", "Context Path",
            null, "ROOT", null
        ) ?: return null

        return ServerExecutionSelection(
            server = server,
            contextPath = normalizeContextPath(contextPath),
            warFilePath = resolveServerWarPreference(server),
            persistMapping = true
        )
    }

    // Multiple servers - show picker
    val serverNames = servers.map { "${it.name} (port ${it.port})" }
    val selectedIndex = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(serverNames)
        .setTitle("Select Tomcat Server")
        .createPopup()

    // For simplicity in the initial version, use the first server
    val server = servers[0]
    val contextPath = Messages.showInputDialog(
        project, "Enter context path (ROOT for base URL):", "Context Path",
        null, mapping?.contextPath ?: "ROOT", null
    ) ?: return null

    return ServerExecutionSelection(
        server = server,
        contextPath = normalizeContextPath(contextPath),
        warFilePath = resolveServerWarPreference(server),
        persistMapping = true
    )
}

private fun normalizeContextPath(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed == "/") return "ROOT"
    return trimmed.trimStart('/').ifEmpty { "ROOT" }
}

private fun resolveServerWarPreference(server: com.zoho.dzide.model.TomcatServer): String? {
    if (server.deployConfiguredWarOnRun && server.configuredWarFilePath != null) {
        return server.configuredWarFilePath
    }
    return null
}
