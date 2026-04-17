package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.util.NotificationUtil
import com.zoho.dzide.util.PortUtil

class RestartServerAction : AnAction("Restart", "Restart Tomcat server", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = ServerActionUtil.getSelectedServer(e) ?: return
        val manager = TomcatManager.getInstance(project)

        if (!PortUtil.isPortInUse(server.port)) {
            // Not running — just start
            manager.startServer(server)
            return
        }

        manager.stopServer(server)

        // Wait for stop to complete, then start
        Thread {
            val maxWait = 15_000L
            val interval = 500L
            var waited = 0L
            while (waited < maxWait && PortUtil.isPortInUse(server.port)) {
                Thread.sleep(interval)
                waited += interval
            }
            if (!PortUtil.isPortInUse(server.port)) {
                manager.startServer(server)
            } else {
                NotificationUtil.error(project, "Restart failed: server did not stop within ${maxWait / 1000}s")
            }
        }.start()
    }
}
