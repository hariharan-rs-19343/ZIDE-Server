package com.zoho.dzide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DzidePlugin : ProjectActivity {

    private val log = Logger.getInstance(DzidePlugin::class.java)

    override suspend fun execute(project: Project) {
        log.info("DZIDE plugin initialized for project: ${project.name}")
    }
}
