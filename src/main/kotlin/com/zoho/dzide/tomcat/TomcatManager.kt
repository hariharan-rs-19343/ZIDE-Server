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
import com.zoho.dzide.zide.DeploymentConfigPatcher
import com.zoho.dzide.zide.ZideConfigParser
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

    fun ensureToolWindow(callback: () -> Unit) {
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("SAS-ZIDE")
        if (toolWindow == null) {
            com.zoho.dzide.util.NotificationUtil.error(project, "SAS-ZIDE tool window not found.")
            return
        }
        toolWindow.activate {
            callback()
        }
    }

    private fun log(message: String) {
        val timestamped = "[${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}] $message\n"
        consoleView?.print(timestamped, ConsoleViewContentType.NORMAL_OUTPUT)
    }

    private fun logError(message: String) {
        val timestamped = "[${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}] $message\n"
        consoleView?.print(timestamped, ConsoleViewContentType.ERROR_OUTPUT)
    }

    private val suppressedStderrPatterns = listOf(
        "too many arguments",
        "Picked up JDK_JAVA_OPTIONS",
        "validation was turned on but",
        "Document root element",
        "Document is invalid: no grammar found"
    )

    private fun shouldSuppressStderr(line: String): Boolean {
        return suppressedStderrPatterns.any { line.contains(it, ignoreCase = true) }
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

    private fun resolveEffectiveLaunchArgs(server: TomcatServer): String {
        val manualArgs = (server.manualLaunchArgs ?: "").trim()
        return if (manualArgs.isNotEmpty()) "$DEFAULT_VM_ARGS $manualArgs" else DEFAULT_VM_ARGS
    }

    private fun buildCatalinaEnvVars(server: TomcatServer, debugPort: Int? = null): Map<String, String> {
        val env = mutableMapOf("CATALINA_PID" to "pid.file")
        if (debugPort != null) {
            env["JPDA_ADDRESS"] = debugPort.toString()
            env["JPDA_TRANSPORT"] = "dt_socket"
        }
        val launchArgs = resolveEffectiveLaunchArgs(server)
        env["CATALINA_OPTS"] = launchArgs
        log("Applying VM arguments for ${server.name}.")
        return env
    }

    /**
     * Patches deployment config files before server start, replicating Eclipse ZIDE behavior.
     * Reads ZIDE config from the project and patches server.xml, persistence-configurations.xml,
     * and security-properties.xml with values from zide_properties.xml.
     */
    fun patchDeploymentConfigs(server: TomcatServer) {
        val projectPath = project.basePath ?: return
        val zideConfig = ZideConfigParser.readZideConfig(projectPath) ?: return
        val serviceProps = zideConfig.service?.properties ?: return
        val zideProps = zideConfig.properties?.properties ?: emptyMap()

        val patchCtx = DeploymentConfigPatcher.buildPatchContext(serviceProps, zideProps)
        if (patchCtx == null) {
            log("Skipping config patching: missing DEPLOYMENT_FOLDER or PARENT_SERVICE.")
            return
        }

        log("Patching deployment configs for ${patchCtx.parentService}...")
        val result = DeploymentConfigPatcher.patchAll(patchCtx)

        if (result.serverXmlPatched) log("  Patched server.xml (Context element, shutdown port)")
        if (result.webXmlPatched) log("  Patched web.xml (JSP servlet for dynamic compilation)")
        if (result.persistencePatched) log("  Patched persistence-configurations.xml (DBName, StartDBServer)")
        if (result.securityPatched) log("  Patched security-properties.xml (IAM server, service name, logout URL)")
        for (err in result.errors) {
            logError("  Patch error: $err")
        }
        if (!result.serverXmlPatched && !result.webXmlPatched && !result.persistencePatched && !result.securityPatched && result.errors.isEmpty()) {
            log("  Config files already up to date.")
        }
    }

    /**
     * Runs postzidedeploy.sh from the project's resources/zide-scripts/ directory.
     * This script copies app.properties into the deployment's WEB-INF/conf/ folder.
     */
    private fun runPostZideDeployScript(server: TomcatServer) {
        val projectPath = project.basePath ?: return
        val scriptPath = Path.of(projectPath, "resources", "zide-scripts", "postzidedeploy.sh")
        if (!scriptPath.exists()) {
            log("postzidedeploy.sh not found at $scriptPath, skipping.")
            return
        }

        val deploymentFolder = server.zideRuntimeProperties?.get("ZIDE.DEPLOYMENT_FOLDER") ?: return
        val deploymentBase = Path.of(deploymentFolder, "AdventNet", "Sas").toString()

        log("Running postzidedeploy.sh...")
        val command = ShellUtil.buildShellCommand(
            "chmod", "+x", "\"$scriptPath\"",
            "&&", "sh", "\"$scriptPath\"", "\"$deploymentBase\""
        )
        val result = com.zoho.dzide.util.ProcessUtil.executeCapturing(
            command = command,
            workingDir = deploymentFolder,
            timeoutMs = 30_000
        )
        if (result.stdout.isNotBlank()) log(result.stdout.trim())
        if (result.stderr.isNotBlank()) logError(result.stderr.trim())
        if (result.exitCode == 0) {
            log("postzidedeploy.sh completed successfully.")
        } else {
            logError("postzidedeploy.sh failed with exit code ${result.exitCode}")
        }
    }

    /**
     * Copies server.xml files to the correct locations before Tomcat starts.
     *
     * Step 1: Copy tomcat/conf/server.xml → webapps/{parentService}/WEB-INF/conf/server.xml
     *         (the app needs a copy of the current tomcat conf server.xml)
     * Step 2: Copy Servers/{parentService}-config/server.xml → tomcat/conf/server.xml
     *         (Eclipse's managed server.xml with Context element, SSL, etc. becomes the active tomcat config)
     *
     * These two server.xml files have different content — each copy is verified before proceeding.
     */
    private fun syncServerXmlFiles(server: TomcatServer) {
        val deploymentFolder = server.zideRuntimeProperties?.get("ZIDE.DEPLOYMENT_FOLDER") ?: return
        val parentService = server.zideRuntimeProperties?.get("ZIDE.PARENT_SERVICE")
            ?: run {
                val projectPath = project.basePath ?: return
                val zideConfig = ZideConfigParser.readZideConfig(projectPath) ?: return
                zideConfig.service?.properties?.get("ZIDE.PARENT_SERVICE") ?: return
            }

        val tomcatConfDir = Path.of(deploymentFolder, "AdventNet", "Sas", "tomcat", "conf")
        val tomcatConfServerXml = tomcatConfDir.resolve("server.xml")
        val webappConfDir = Path.of(deploymentFolder, "AdventNet", "Sas", "tomcat", "webapps", parentService, "WEB-INF", "conf")
        val webappConfServerXml = webappConfDir.resolve("server.xml")

        // Resolve Servers/{parentService}-config/ relative to workspace root
        // Deployment folder is {workspace}/deployment/{service}, so workspace = deployment/../..
        val workspaceRoot = Path.of(deploymentFolder).parent?.parent
        val serversConfigDir = workspaceRoot?.resolve("Servers")?.resolve("$parentService-config")
        val serversServerXml = serversConfigDir?.resolve("server.xml")

        log("Syncing server.xml files...")

        // Step 1: Copy tomcat/conf/server.xml → webapps/{parentService}/WEB-INF/conf/server.xml
        if (tomcatConfServerXml.exists() && webappConfDir.exists()) {
            Files.copy(tomcatConfServerXml, webappConfServerXml, StandardCopyOption.REPLACE_EXISTING)
            log("  Copied tomcat/conf/server.xml → webapps/$parentService/WEB-INF/conf/server.xml")
        } else {
            if (!tomcatConfServerXml.exists()) logError("  tomcat/conf/server.xml not found, skipping copy to webapp.")
            if (!webappConfDir.exists()) logError("  webapps/$parentService/WEB-INF/conf/ not found, skipping copy.")
        }

        // Step 2: Copy Servers/{parentService}-config/server.xml → tomcat/conf/server.xml
        if (serversServerXml != null && serversServerXml.exists()) {
            Files.copy(serversServerXml, tomcatConfServerXml, StandardCopyOption.REPLACE_EXISTING)
            log("  Copied Servers/$parentService-config/server.xml → tomcat/conf/server.xml")
        } else {
            log("  Servers/$parentService-config/server.xml not found, skipping. Tomcat conf server.xml unchanged.")
        }
    }

    /**
     * Runs all pre-start setup steps in order before launching Tomcat:
     * 1. Execute postzidedeploy.sh (copies app.properties)
     * 2. Copy tomcat/conf/server.xml → webapp WEB-INF/conf/
     * 3. Copy Servers/{service}-config/server.xml → tomcat/conf/
     */
    fun runPreStartSetup(server: TomcatServer) {
        log("--- Pre-start setup ---")
        runPostZideDeployScript(server)
        syncServerXmlFiles(server)
        log("--- Pre-start setup complete ---")
    }

    fun startServer(server: TomcatServer) {
        val script = ShellUtil.catalinaScript(server.path)
        if (!script.exists()) {
            NotificationUtil.error(project, "Startup script not found at $script")
            return
        }

        if (PortUtil.isPortInUse(server.port)) {
            log("Server ${server.name} is already running on port ${server.port}. Stopping before restart...")
            stopServer(server)
            val maxWait = 15_000L
            val interval = 500L
            var waited = 0L
            while (waited < maxWait && PortUtil.isPortInUse(server.port)) {
                Thread.sleep(interval)
                waited += interval
            }
            if (PortUtil.isPortInUse(server.port)) {
                logError("Server did not stop within ${maxWait / 1000}s. Cannot start.")
                NotificationUtil.error(project, "Server ${server.name} did not stop. Cannot restart.")
                return
            }
        }

        patchDeploymentConfigs(server)
        runPreStartSetup(server)

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
            onStdout = { consoleView?.print(it, ConsoleViewContentType.NORMAL_OUTPUT) },
            onStderr = { if (!shouldSuppressStderr(it)) consoleView?.print(it, ConsoleViewContentType.ERROR_OUTPUT) },
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
            log("Server ${server.name} is already running on port ${server.port}. Stopping before debug restart...")
            stopServer(server)
            val maxWait = 15_000L
            val interval = 500L
            var waited = 0L
            while (waited < maxWait && PortUtil.isPortInUse(server.port)) {
                Thread.sleep(interval)
                waited += interval
            }
            if (PortUtil.isPortInUse(server.port)) {
                throw IllegalStateException("Server did not stop within ${maxWait / 1000}s. Cannot start in debug mode.")
            }
        }

        if (PortUtil.isPortInUse(debugPort)) {
            throw IllegalStateException("Debug port $debugPort is already in use.")
        }

        patchDeploymentConfigs(server)
        runPreStartSetup(server)

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
            onStdout = { consoleView?.print(it, ConsoleViewContentType.NORMAL_OUTPUT) },
            onStderr = { if (!shouldSuppressStderr(it)) consoleView?.print(it, ConsoleViewContentType.ERROR_OUTPUT) },
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
        private const val DEFAULT_VM_ARGS = "-Dcatalina.base=. -Dcatalina.home=. -Djava.io.tmpdir=./temp " +
            "-Duse.apache=false -Duse.compression=false -Dserver.stats=10000 -Dlog.dir=. " +
            "-Ddb.home=./../mysql -Dcheck.tomcatport=false -Dcom.adventnet.workengine.serverid= " +
            "-Dfile.encoding=utf8 -Djava.awt.headless=true " +
            "-Dcom.adventnet.mfw.bean.BeanProxy=com.adventnet.sas.share.ExtendedBeanProxy " +
            "-Djava.util.logging.config.file=./conf/logging.properties " +
            "-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager " +
            "-Ddb.vendor.name=mysql -Dis.proto=true " +
            "-DsocksHost=zcode-socksproxy -DsocksPort=5050 " +
            "-Xmx700M -Xms300M -XX:MetaspaceSize=256M -XX:MaxMetaspaceSize=512M " +
            "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3128 " +
            "-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=3128"

        fun getInstance(project: Project): TomcatManager =
            project.getService(TomcatManager::class.java)
    }
}
