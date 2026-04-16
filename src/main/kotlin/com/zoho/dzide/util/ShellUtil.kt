package com.zoho.dzide.util

import java.nio.file.Path

object ShellUtil {

    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    fun catalinaScript(tomcatPath: String): Path {
        val scriptName = if (isWindows) "catalina.bat" else "catalina.sh"
        return Path.of(tomcatPath, "bin", scriptName)
    }

    fun antExecutable(antHome: String): Path {
        val execName = if (isWindows) "ant.bat" else "ant"
        return Path.of(antHome, "bin", execName)
    }

    fun buildShellCommand(vararg parts: String): List<String> {
        return if (isWindows) {
            listOf("cmd", "/c") + parts.toList()
        } else {
            listOf("sh", "-c", parts.joinToString(" "))
        }
    }

    fun shellEscapeSingleQuoted(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}
