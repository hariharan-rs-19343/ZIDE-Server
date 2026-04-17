package com.zoho.dzide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Toolbar button that re-runs AppLogsAction to refresh the App Logs tab content.
 */
class RefreshAppLogsAction : AnAction("Refresh App Logs", "Reload application log file", null) {

    override fun actionPerformed(e: AnActionEvent) {
        AppLogsAction().actionPerformed(e)
    }
}
