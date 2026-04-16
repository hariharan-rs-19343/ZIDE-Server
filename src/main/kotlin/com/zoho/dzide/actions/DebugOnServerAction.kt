package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.util.NotificationUtil

class DebugOnServerAction : AnAction("Debug", "Start server in debug mode and attach debugger", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return
        val tomcatManager = TomcatManager.getInstance(project)

        val debugPort = server.debugPort?.takeIf { it > 0 }
            ?: com.zoho.dzide.util.PortUtil.findAvailablePort(8000)

        try {
            tomcatManager.startServerInDebug(server, debugPort)

            // Attach IntelliJ remote debugger
            val runManager = com.intellij.execution.RunManager.getInstance(project)
            val remoteConfigType = com.intellij.execution.configurations.ConfigurationTypeUtil
                .findConfigurationType("Remote")
            if (remoteConfigType != null) {
                val factory = remoteConfigType.configurationFactories.firstOrNull()
                if (factory != null) {
                    val settings = runManager.createConfiguration(
                        "Debug ${server.name}", factory
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
                        NotificationUtil.info(project, "Debugger attaching to ${server.name} on port $debugPort.")
                    }
                }
            }
        } catch (ex: Exception) {
            NotificationUtil.error(project, "Debug failed: ${ex.message}")
        }
    }
}
