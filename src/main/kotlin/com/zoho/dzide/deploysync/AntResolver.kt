package com.zoho.dzide.deploysync

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

object AntResolver {

    fun resolveAntExecutable(antHome: String): String {
        val execName = if (com.zoho.dzide.util.ShellUtil.isWindows) "ant.bat" else "ant"
        return Path.of(antHome, "bin", execName).toString()
    }

    private fun isValidAntHome(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return Path.of(resolveAntExecutable(candidate)).exists()
    }

    private fun detectAntHomeFromEnvironment(): String? {
        val antHome = System.getenv("ANT_HOME")
        return if (isValidAntHome(antHome)) antHome else null
    }

    private fun detectAntHomeFromAntSetup(projectPath: String): String? {
        val parentDir = Path.of(projectPath).toAbsolutePath().normalize().parent ?: return null
        val antSetupDir = parentDir.resolve(".antsetup")
        if (!antSetupDir.exists() || !antSetupDir.isDirectory()) return null

        return antSetupDir.listDirectoryEntries()
            .filter { it.isDirectory() && it.fileName.toString().lowercase().contains("ant") }
            .sortedBy { it.fileName.toString() }
            .firstOrNull { isValidAntHome(it.toString()) }
            ?.toString()
    }

    fun resolveAntHome(projectPath: String, persistedAntHome: String?): String? {
        if (isValidAntHome(persistedAntHome)) return persistedAntHome
        detectAntHomeFromEnvironment()?.let { return it }
        detectAntHomeFromAntSetup(projectPath)?.let { return it }
        return null
    }
}
