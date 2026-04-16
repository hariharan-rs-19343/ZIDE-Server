package com.zoho.dzide.model

data class ModuleZidePropsData(
    val zidePropertiesPath: String? = null,
    val zideFolderPath: String? = null,
    val launchVmArguments: String? = null,
    val hookTasksRaw: String? = null,
    val autoResourceCopyRaw: String? = null
)

data class HookTaskMapping(
    val folder: String,
    val antTarget: String
)

data class AutoCopyMapping(
    val sourcePath: String,
    val destinationPathTemplate: String
)

enum class LaunchMode {
    RUN, DEBUG
}

data class ServerExecutionSelection(
    val server: TomcatServer,
    val contextPath: String,
    val warFilePath: String? = null,
    val persistMapping: Boolean = false
)
