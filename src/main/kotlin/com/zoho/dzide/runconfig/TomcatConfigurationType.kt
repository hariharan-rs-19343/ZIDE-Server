package com.zoho.dzide.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class TomcatConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = "SAS-ZIDE"
    override fun getConfigurationTypeDescription(): String = "Run or Debug on Tomcat Server"
    override fun getIcon(): Icon = AllIcons.Actions.Execute
    override fun getId(): String = "DzideTomcatRunConfiguration"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> =
        arrayOf(TomcatConfigurationFactory(this))
}
