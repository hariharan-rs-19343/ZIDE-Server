package com.zoho.dzide.model

data class ProjectServerMapping(
    var projectPath: String = "",
    var serverId: String = "",
    var contextPath: String = "ROOT",
    var warFilePath: String? = null
)
