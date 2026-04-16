package com.zoho.dzide.model

data class TomcatServer(
    val id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var path: String = "",
    var status: String = "stopped",
    var port: Int = 8080,
    var debugPort: Int? = null,
    var description: String? = null,
    var deployConfiguredWarOnRun: Boolean = false,
    var configuredWarFilePath: String? = null,
    var manualLaunchArgs: String? = null,
    // ZIDE-specific fields
    var zideServiceKey: String? = null,
    var zideFolderPath: String? = null,
    var zidePropertiesPath: String? = null,
    var zideLaunchVmArguments: String? = null,
    var zideHookTasksRaw: String? = null,
    var zideAutoResourceCopyRaw: String? = null,
    var repositoryModuleDir: String? = null,
    var deployType: String? = null,
    var zideRuntimeProperties: Map<String, String>? = null,
    var zideBuildXmlPath: String? = null,
    var zideBuildBaseDir: String? = null,
    var antHomeResolvedPath: String? = null
)
