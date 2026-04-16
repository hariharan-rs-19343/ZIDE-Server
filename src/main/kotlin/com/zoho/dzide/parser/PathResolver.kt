package com.zoho.dzide.parser

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

object PathResolver {

    fun normalizePathSlashes(value: String): String = value.replace('\\', '/')

    fun isSubPath(parentPath: String, childPath: String): Boolean {
        val parent = Path.of(parentPath).toAbsolutePath().normalize()
        val child = Path.of(childPath).toAbsolutePath().normalize()
        return child.startsWith(parent)
    }

    fun findNearestProjectRoot(filePath: String): String? {
        var currentDir = Path.of(filePath).toAbsolutePath().normalize().parent ?: return null
        while (true) {
            val zideResourcePath = currentDir.resolve(".zide_resources").resolve("service.xml")
            if (zideResourcePath.exists()) {
                return currentDir.toString()
            }
            val parentDir = currentDir.parent ?: return null
            if (parentDir == currentDir) return null
            currentDir = parentDir
        }
    }

    fun applyProjectNamePlaceholder(value: String, projectName: String): String =
        value.replace("{PROJECT_NAME}", projectName)

    fun normalizeRelativeForMatch(relativePath: String): String =
        normalizePathSlashes(relativePath).trimStart('/')

    fun stripProjectPrefix(relativePath: String, projectName: String?): String {
        val normalized = normalizeRelativeForMatch(relativePath)
        if (projectName.isNullOrBlank()) return normalized
        if (normalized == projectName) return ""
        if (normalized.startsWith("$projectName/")) {
            return normalized.substring(projectName.length + 1)
        }
        return normalized
    }

    fun toProjectRelativePath(projectRoot: String, filePath: String): String {
        val relative = Path.of(projectRoot).relativize(Path.of(filePath)).toString()
        return normalizeRelativeForMatch(relative)
    }

    fun findDefaultZideFolder(projectPath: String): String? {
        val parentPath = Path.of(projectPath).toAbsolutePath().normalize().parent ?: return null
        val candidate = parentPath.resolve("zide")
        return if (candidate.exists() && candidate.isDirectory()) candidate.toString() else null
    }
}
