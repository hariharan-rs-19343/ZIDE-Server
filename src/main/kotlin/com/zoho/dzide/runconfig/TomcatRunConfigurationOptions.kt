package com.zoho.dzide.runconfig

import com.intellij.execution.configurations.RunConfigurationOptions

class TomcatRunConfigurationOptions : RunConfigurationOptions() {
    var mode by string("run")
    var serverId by string("")
    var contextPath by string("ROOT")
    var warFilePath by string("")
    var resourcePath by string("")
}
