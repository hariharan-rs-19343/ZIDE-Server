package com.zoho.dzide.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

class TomcatConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        TomcatRunConfiguration(project, this, "DZIDE Tomcat")

    override fun getId(): String = "DzideTomcatConfigurationFactory"

    override fun getOptionsClass(): Class<out BaseState> = TomcatRunConfigurationOptions::class.java
}
