package com.zoho.dzide.tomcat

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.zoho.dzide.model.TomcatServer
import com.zoho.dzide.parser.ModuleZidePropsParser
import com.zoho.dzide.util.NotificationUtil
import com.zoho.dzide.util.PortUtil
import com.zoho.dzide.util.ShellUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class TomcatManager(private val project: Project) {

    var consoleView: ConsoleView? = null
    var appLogsConsoleView: ConsoleView? = null
    private val serverProcesses = mutableMapOf<String, OSProcessHandler>()
    private val serverProvider: TomcatServerProvider
        get() = TomcatServerProvider.getInstance(project)

    private fun log(message: String) {
        val timestamped = "[${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}] $message\n"
        consoleView?.print(timestamped, ConsoleViewContentType.NORMAL_OUTPUT)
    }

    private fun logError(message: String) {
        val timestamped = "[${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}] $message\n"
        consoleView?.print(timestamped, ConsoleViewContentType.ERROR_OUTPUT)
    }

    fun normalizeContextPath(contextPath: String?): String {
        val raw = (contextPath ?: "ROOT").trim()
        if (raw.isEmpty() || raw == "/") return "ROOT"
        val cleaned = raw.trimStart('/')
        return cleaned.ifEmpty { "ROOT" }
    }

    fun getApplicationUrl(server: TomcatServer, contextPath: String): String {
        val normalized = normalizeContextPath(contextPath)
        return if (normalized == "ROOT") {
            "http://localhost:${server.port}"
        } else {
            "http://localhost:${server.port}/$normalized"
        }
    }

    private fun resolveEffectiveLaunchArgs(server: TomcatServer): String? {
        val latestFromProperties = server.zidePropertiesPath?.let {
            ModuleZidePropsParser.readLaunchVmArgumentsFromProperties(it)
        }
        if (latestFromProperties != null && latestFromProperties != server.zideLaunchVmArguments) {
            serverProvider.updateServer(server.id, mapOf("zideLaunchVmArguments" to latestFromProperties))
        }
        val zideArgs = (latestFromProperties ?: server.zideLaunchVmArguments ?: "").trim()
        val manualArgs = (server.manualLaunchArgs ?: "").trim()
        val merged = listOf(zideArgs, manualArgs).filter { it.isNotEmpty() }.joinToString(" ").trim()
        return merged.ifEmpty { null }
    }

    private fun buildCatalinaEnvVars(server: TomcatServer, debugPort: Int? = null): Map<String, String> {
        val env = mutableMapOf("CATALINA_PID" to "pid.file")
        if (debugPort != null) {
            env["JPDA_ADDRESS"] = debugPort.toString()
            env["JPDA_TRANSPORT"] = "dt_socket"
        }
        val launchArgs = resolveEffectiveLaunchArgs(server)
        if (launchArgs != null) {
            env["CATALINA_OPTS"] = launchArgs
            log("Applying launch VM arguments for ${server.name}.")
        }
        return env
    }

    fun startServer(server: TomcatServer) {
        val script = ShellUtil.catalinaScript(server.path)
        if (!script.exists()) {
            NotificationUtil.error(project, "Startup script not found at $script")
            return
        }

        if (PortUtil.isPortInUse(server.port)) {
            log("Server ${server.name} is already running on port ${server.port}")
            NotificationUtil.warn(project, "Server ${server.name} is already running!")
            serverProvider.updateServer(server.id, mapOf("status" to "running"))
            return
        }

        log("======================================")
        log("Starting Tomcat server: ${server.name}")
        log("Script path: $script")
        log("Port: ${server.port}")
        log("======================================")
        NotificationUtil.info(project, "Starting Tomcat server: ${server.name}...")

        val env = buildCatalinaEnvVars(server)
        val command = ShellUtil.buildShellCommand(
            *env.map { "export ${it.key}=${ShellUtil.shellEscapeSingleQuoted(it.value)}" }.toTypedArray(),
            "&&", "chmod", "+x", "\"$script\"",
            "&&", "sh", "\"$script\"", "run"
        )

        com.zoho.dzide.util.ProcessUtil.executeStreaming(
            command = command,
            workingDir = server.path,
            onStdout = { log(it) },
            onStderr = { logError("STDERR: $it") },
            onExit = { exitCode ->
                serverProcesses.remove(server.id)
                log("Server process exited with code: $exitCode")
                serverProvider.updateServer(server.id, mapOf("status" to "stopped"))
                log("Server ${server.name} stopped.")
                NotificationUtil.info(project, "Tomcat server ${server.name} stopped.")
            }
        ).also { handler ->
            serverProcesses[server.id] = handler
            // Wait for port to confirm startup
            Thread {
                val running = PortUtil.waitForPort(server.port, 45000)
                if (running) {
                    serverProvider.updateServer(server.id, mapOf("status" to "running"))
                    log("Server ${server.name} started successfully!")
                    NotificationUtil.info(project, "Tomcat server ${server.name} started successfully!")
                } else {
                    logError("Server ${server.name} failed to start - no process on port ${server.port}")
                    NotificationUtil.error(project, "Server ${server.name} failed to start.")
                }
            }.start()
        }
    }

    fun startServerInDebug(server: TomcatServer, debugPort: Int) {
        val script = ShellUtil.catalinaScript(server.path)
        if (!script.exists()) {
            throw IllegalStateException("Startup script not found at $script")
        }

        if (PortUtil.isPortInUse(server.port)) {
            log("Server ${server.name} is already running. Skipping debug start.")
            serverProvider.updateServer(server.id, mapOf("status" to "running", "debugPort" to debugPort))
            return
        }

        if (PortUtil.isPortInUse(debugPort)) {
            throw IllegalStateException("Debug port $debugPort is already in use.")
        }

        log("======================================")
        log("Starting Tomcat server in debug mode: ${server.name}")
        log("HTTP port: ${server.port}, Debug port: $debugPort")
        log("======================================")

        val env = buildCatalinaEnvVars(server, debugPort)
        val command = ShellUtil.buildShellCommand(
            *env.map { "export ${it.key}=${ShellUtil.shellEscapeSingleQuoted(it.value)}" }.toTypedArray(),
            "&&", "chmod", "+x", "\"$script\"",
            "&&", "sh", "\"$script\"", "jpda", "run"
        )

        com.zoho.dzide.util.ProcessUtil.executeStreaming(
            command = command,
            workingDir = server.path,
            onStdout = { log(it) },
            onStderr = { logError("STDERR: $it") },
            onExit = { _ ->
                serverProcesses.remove(server.id)
                serverProvider.updateServer(server.id, mapOf("status" to "stopped"))
                log("Server ${server.name} (debug) stopped.")
                NotificationUtil.info(project, "Tomcat server ${server.name} stopped.")
            }
        ).also { handler ->
            serverProcesses[server.id] = handler
            // Wait for ports to confirm startup
            Thread {
                val httpRunning = PortUtil.waitForPort(server.port, 45000)
                val debugRunning = PortUtil.waitForPort(debugPort, 45000)
                if (httpRunning && debugRunning) {
                    serverProvider.updateServer(server.id, mapOf("status" to "running", "debugPort" to debugPort))
                    log("Server ${server.name} started in debug mode.")
                } else {
                    logError("Server failed to start in debug mode. HTTP: $httpRunning, debug ($debugPort): $debugRunning")
                }
            }.start()
        }
    }

    fun stopServer(server: TomcatServer) {
        if (!PortUtil.isPortInUse(server.port)) {
            log("Server ${server.name} is not running on port ${server.port}")
            NotificationUtil.warn(project, "Server ${server.name} is not running!")
            serverProcesses.remove(server.id)
            serverProvider.updateServer(server.id, mapOf("status" to "stopped"))
            return
        }

        log("======================================")
        log("Stopping Tomcat server: ${server.name}")
        log("======================================")
        NotificationUtil.info(project, "Stopping Tomcat server: ${server.name}...")

        val handler = serverProcesses.remove(server.id)
        if (handler != null && !handler.isProcessTerminated) {
            handler.destroyProcess()
            log("Destroyed foreground Tomcat process for ${server.name}.")
        } else {
            // Fallback: use catalina.sh stop if we don't have the process handle
            val script = ShellUtil.catalinaScript(server.path)
            if (!script.exists()) {
                NotificationUtil.error(project, "Shutdown script not found at $script")
                return
            }
            log("No attached process found. Falling back to catalina.sh stop.")
            val command = ShellUtil.buildShellCommand(
                "export", "CATALINA_PID=pid.file",
                "&&", "chmod", "+x", "\"$script\"",
                "&&", "sh", "\"$script\"", "stop", "-force"
            )
            com.zoho.dzide.util.ProcessUtil.executeStreaming(
                command = command,
                workingDir = server.path,
                onStdout = { log(it) },
                onStderr = { logError("STDERR: $it") },
                onExit = { _ -> }
            )
        }

        // Verify shutdown, fallback to lsof + kill if still running
        Thread {
            Thread.sleep(3000)
            var stillRunning = PortUtil.isPortInUse(server.port)
            if (stillRunning) {
                log("Server still running on port ${server.port}. Attempting force kill via lsof...")
                forceKillByPort(server.port)
                Thread.sleep(2000)
                stillRunning = PortUtil.isPortInUse(server.port)
            }
            if (!stillRunning) {
                serverProvider.updateServer(server.id, mapOf("status" to "stopped"))
                log("Server ${server.name} stopped successfully!")
                NotificationUtil.info(project, "Tomcat server ${server.name} stopped successfully!")
            } else {
                serverProvider.updateServer(server.id, mapOf("status" to "running"))
                logError("Server ${server.name} could not be stopped on port ${server.port}")
                NotificationUtil.error(project, "Failed to stop server ${server.name}. Manual intervention required.")
            }
        }.start()
    }

    private fun forceKillByPort(port: Int) {
        try {
            val lsofResult = com.zoho.dzide.util.ProcessUtil.executeCapturing(
                command = listOf("lsof", "-ti", ":$port"),
                timeoutMs = 5000
            )
            val pids = lsofResult.stdout.trim().lines().filter { it.isNotBlank() }
            if (pids.isEmpty()) {
                log("No PIDs found via lsof for port $port")
                return
            }
            for (pid in pids) {
                log("Killing PID $pid on port $port")
                com.zoho.dzide.util.ProcessUtil.executeCapturing(
                    command = listOf("kill", "-9", pid),
                    timeoutMs = 5000
                )
            }
            log("Force kill sent for PIDs: ${pids.joinToString(", ")}")
        } catch (ex: Exception) {
            logError("Force kill failed: ${ex.message}")
        }
    }

    fun refreshAllServerStatus() {
        log("Refreshing status for all servers...")
        for (server in serverProvider.getServers()) {
            val isRunning = PortUtil.isPortInUse(server.port)
            val newStatus = if (isRunning) "running" else "stopped"
            if (server.status != newStatus) {
                log("${server.name}: Status updated from ${server.status} to $newStatus")
                serverProvider.updateServer(server.id, mapOf("status" to newStatus))
            } else {
                log("${server.name}: Status confirmed as ${server.status}")
            }
        }
        log("Status refresh completed.")
    }

    fun deployWarFile(server: TomcatServer, warFile: String, contextPath: String) {
        val webappsDir = Path.of(server.path, "webapps")
        val normalized = normalizeContextPath(contextPath)
        val deployedDir = webappsDir.resolve(normalized)
        val targetWarName = if (normalized == "ROOT") "ROOT.war" else "$normalized.war"
        val targetWarFile = webappsDir.resolve(targetWarName)

        log("Deploying ${Path.of(warFile).fileName} to ${server.name} as $targetWarName")
        NotificationUtil.info(project, "Deploying application to ${server.name}...")

        if (deployedDir.exists()) {
            deployedDir.toFile().deleteRecursively()
        }
        Files.deleteIfExists(targetWarFile)
        Files.copy(Path.of(warFile), targetWarFile, StandardCopyOption.REPLACE_EXISTING)

        NotificationUtil.info(project, "Deployment completed on ${server.name}.")
    }

    fun runProjectOnServer(
        server: TomcatServer,
        projectPath: String,
        contextPath: String,
        preferredWarFilePath: String?
    ): String? {
        val warFile = resolveConfiguredWarFile(preferredWarFilePath)
        val isRunning = PortUtil.isPortInUse(server.port)

        if (warFile != null) {
            deployWarFile(server, warFile, contextPath)
        } else {
            log("No WAR configured for ${server.name}. Proceeding without deployment.")
        }

        if (!isRunning) {
            startServer(server)
        }
        return warFile
    }

    fun debugProjectOnServer(
        server: TomcatServer,
        projectPath: String,
        contextPath: String,
        preferredWarFilePath: String?
    ): Int? {
        val debugPort = PortUtil.findAvailablePort(server.debugPort ?: 5005)
        val warFile = resolveConfiguredWarFile(preferredWarFilePath)

        if (warFile != null) {
            deployWarFile(server, warFile, contextPath)
        } else {
            log("No WAR configured for ${server.name}. Proceeding with debug start without deployment.")
        }

        val isRunning = PortUtil.isPortInUse(server.port)
        if (!isRunning) {
            startServerInDebug(server, debugPort)
        } else {
            val debugActive = PortUtil.isPortInUse(debugPort)
            if (!debugActive) {
                stopServer(server)
                Thread.sleep(2000)
                startServerInDebug(server, debugPort)
            } else {
                serverProvider.updateServer(server.id, mapOf("debugPort" to debugPort))
            }
        }
        return debugPort
    }

    private fun resolveConfiguredWarFile(preferredWarFilePath: String?): String? {
        if (preferredWarFilePath != null && Path.of(preferredWarFilePath).exists()) {
            log("Using saved WAR path: $preferredWarFilePath")
            return preferredWarFilePath
        }
        if (preferredWarFilePath != null && !Path.of(preferredWarFilePath).exists()) {
            NotificationUtil.warn(project, "Configured WAR file path is no longer valid. Continuing without deployment.")
        }
        return null
    }

    companion object {
        fun getInstance(project: Project): TomcatManager =
            project.getService(TomcatManager::class.java)
    }
}
