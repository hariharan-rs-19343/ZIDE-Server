package com.zoho.dzide.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.zoho.dzide.model.LaunchMode
import com.zoho.dzide.tomcat.TomcatManager
import com.zoho.dzide.tomcat.TomcatServerProvider

class TomcatRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<TomcatRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): TomcatRunConfigurationOptions =
        super.getOptions() as TomcatRunConfigurationOptions

    var mode: String
        get() = options.mode ?: "run"
        set(value) { options.mode = value }

    var serverId: String
        get() = options.serverId ?: ""
        set(value) { options.serverId = value }

    var contextPath: String
        get() = options.contextPath ?: "ROOT"
        set(value) { options.contextPath = value }

    var warFilePath: String
        get() = options.warFilePath ?: ""
        set(value) { options.warFilePath = value }

    var resourcePath: String
        get() = options.resourcePath ?: ""
        set(value) { options.resourcePath = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        TomcatRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val launchMode = if (mode == "debug") LaunchMode.DEBUG else LaunchMode.RUN
        return TomcatRunState(environment, this, launchMode)
    }
}
