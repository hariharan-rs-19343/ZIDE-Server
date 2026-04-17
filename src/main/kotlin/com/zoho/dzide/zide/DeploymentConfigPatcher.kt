package com.zoho.dzide.zide

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Patches deployment config files to replicate what Eclipse ZIDE does during server setup.
 *
 * Eclipse's WTP server adapter and ZIDE plugin modify several config files that the ANT hooks
 * do not cover. This class handles those modifications:
 *
 * 1. server.xml — Injects a <Context> element so Tomcat deploys the webapp at root path
 * 2. persistence-configurations.xml — Sets DBName and StartDBServer
 * 3. security-properties.xml — Sets IAM server, service name, logout page with hostname
 */
object DeploymentConfigPatcher {

    data class PatchContext(
        val deploymentFolder: String,   // e.g. /Users/.../deployment/zharehub
        val parentService: String,      // e.g. zharehub
        val iamServer: String?,         // e.g. https://accounts.csez.zohocorpin.com
        val iamServiceName: String?,    // e.g. ZhareHub
        val hostName: String?,          // e.g. hari-19343.csez.zohocorpin.com
        val httpsPort: String?,         // e.g. 8443
        val dbName: String?,            // e.g. zharehub (from ZIDE_DB_NAME)
        val schemaName: String?         // e.g. jbossdb (from ZIDE.SCHEMA_NAME)
    )

    data class PatchResult(
        val serverXmlPatched: Boolean = false,
        val webXmlPatched: Boolean = false,
        val persistencePatched: Boolean = false,
        val securityPatched: Boolean = false,
        val errors: List<String> = emptyList()
    )

    fun patchAll(ctx: PatchContext): PatchResult {
        val errors = mutableListOf<String>()
        val serverXmlOk = try {
            patchServerXml(ctx)
        } catch (e: Exception) {
            errors.add("server.xml: ${e.message}")
            false
        }
        val webXmlOk = try {
            patchWebXml(ctx)
        } catch (e: Exception) {
            errors.add("web.xml: ${e.message}")
            false
        }
        val persistenceOk = try {
            patchPersistenceConfig(ctx)
        } catch (e: Exception) {
            errors.add("persistence-configurations.xml: ${e.message}")
            false
        }
        val securityOk = try {
            patchSecurityProperties(ctx)
        } catch (e: Exception) {
            errors.add("security-properties.xml: ${e.message}")
            false
        }
        return PatchResult(serverXmlOk, webXmlOk, persistenceOk, securityOk, errors)
    }

    /**
     * Patches server.xml to add a <Context> element inside <Host> if not already present.
     * This tells Tomcat to deploy the webapp (parentService) at root path "/".
     * Also sets the shutdown port to a non-negative value if it's currently -1.
     */
    fun patchServerXml(ctx: PatchContext): Boolean {
        val serverXml = Path.of(ctx.deploymentFolder, "AdventNet", "Sas", "tomcat", "conf", "server.xml")
        if (!serverXml.exists()) return false

        var content = serverXml.readText()
        var modified = false

        // 1. Add <Context> element if not present
        if (!content.contains("<Context ")) {
            val hostCloseTag = "</Host>"
            val contextElement = """<Context docBase="${ctx.parentService}" path="" reloadable="false"/>"""
            content = content.replace(hostCloseTag, "$contextElement$hostCloseTag")
            modified = true
        }

        // 2. Set shutdown port if it's -1 (disabled)
        val shutdownPortRegex = Regex("""<Server\s([^>]*?)port="-1"([^>]*?)>""")
        if (shutdownPortRegex.containsMatchIn(content)) {
            content = shutdownPortRegex.replace(content) { match ->
                val before = match.groupValues[1]
                val after = match.groupValues[2]
                """<Server ${before}port="9285"${after}>"""
            }
            modified = true
        }

        // 3. Set deployOnStartup="false" on Host if not present
        if (content.contains("<Host ") && !content.contains("deployOnStartup=")) {
            content = content.replace(
                Regex("""(<Host\s[^>]*?)(\s*>)"""),
                "$1 deployOnStartup=\"false\"$2"
            )
            modified = true
        }

        if (modified) {
            serverXml.writeText(content)
        }
        return modified
    }

    private const val JSP_SERVLET_MARKER =
        "<!-- DEFAULT JSP SERVLET AND ITS MAPPING ADDED BY ZIDE TO ENABLE DYNAMIC JSP COMPILATION FOR DEVELOPMENT SETUP -->"

    private const val JSP_SERVLET_BLOCK = """       
       
$JSP_SERVLET_MARKER       
       
<servlet>       
        <servlet-name>jsp</servlet-name>       
        <servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>       
        <init-param>       
            <param-name>fork</param-name>       
            <param-value>false</param-value>       
        </init-param>
        <init-param>       
            <param-name>xpoweredBy</param-name>       
            <param-value>false</param-value>       
        </init-param>       
        <load-on-startup>3</load-on-startup>       
</servlet>       
       
<servlet-mapping>       
        <servlet-name>jsp</servlet-name>       
        <url-pattern>*.jsp</url-pattern>       
        <url-pattern>*.jspx</url-pattern>       
</servlet-mapping>
       
       
$JSP_SERVLET_MARKER"""

    /**
     * Patches tomcat/conf/web.xml to add JSP servlet and mapping for dynamic JSP compilation.
     * Eclipse/ZIDE adds this block so JSPs can be compiled on the fly during development.
     */
    fun patchWebXml(ctx: PatchContext): Boolean {
        val webXml = Path.of(ctx.deploymentFolder, "AdventNet", "Sas", "tomcat", "conf", "web.xml")
        if (!webXml.exists()) return false

        var content = webXml.readText()

        // Already patched — the ZIDE marker comment is present
        if (content.contains(JSP_SERVLET_MARKER)) return false

        // Find the Built In Servlet Definitions comment and inject after it
        val servletDefsComment = "Built In Servlet Definitions"
        val idx = content.indexOf(servletDefsComment)
        if (idx == -1) return false

        // Find the end of that comment line (-->)
        val commentEnd = content.indexOf("-->", idx)
        if (commentEnd == -1) return false
        val insertPos = commentEnd + 3

        // Replace the comment to indicate modification, then insert the JSP block
        val originalComment = content.substring(content.lastIndexOf("<!--", idx), insertPos)
        val modifiedComment = originalComment.replace(
            "Built In Servlet Definitions",
            "Built In Servlet Definitions (modified)"
        )

        content = content.substring(0, content.lastIndexOf("<!--", idx)) +
                modifiedComment + JSP_SERVLET_BLOCK +
                content.substring(insertPos)

        webXml.writeText(content)
        return true
    }

    /**
     * Patches persistence-configurations.xml to set DBName and StartDBServer values.
     * Eclipse sets DBName=postgres and StartDBServer=false for PGSQL dev environments.
     */
    fun patchPersistenceConfig(ctx: PatchContext): Boolean {
        val webappDir = Path.of(ctx.deploymentFolder, "AdventNet", "Sas", "tomcat", "webapps", ctx.parentService)
        val persistenceXml = webappDir.resolve("WEB-INF").resolve("conf").resolve("Persistence")
            .resolve("persistence-configurations.xml")
        if (!persistenceXml.exists()) return false

        var content = persistenceXml.readText()
        var modified = false

        // Set DBName — use "postgres" as default for PGSQL environments
        val dbNameValue = "postgres"
        val dbNameRegex = Regex("""(<configuration\s+name="DBName"\s+value=")[^"]*("/)""")
        if (dbNameRegex.containsMatchIn(content)) {
            val currentMatch = dbNameRegex.find(content)
            if (currentMatch != null && !currentMatch.value.contains("value=\"$dbNameValue\"")) {
                content = dbNameRegex.replace(content, "$1$dbNameValue$2")
                modified = true
            }
        }

        // Set StartDBServer=false
        val startDbRegex = Regex("""(<configuration\s+name="StartDBServer"\s+value=")[^"]*("/)""")
        if (startDbRegex.containsMatchIn(content)) {
            val currentMatch = startDbRegex.find(content)
            if (currentMatch != null && !currentMatch.value.contains("value=\"false\"")) {
                content = startDbRegex.replace(content, "${"\$1"}false$2")
                modified = true
            }
        }

        if (modified) {
            persistenceXml.writeText(content)
        }
        return modified
    }

    /**
     * Patches security-properties.xml to set IAM server, service name, and logout page.
     * Eclipse reads these from zide_properties.xml and injects them.
     */
    fun patchSecurityProperties(ctx: PatchContext): Boolean {
        val webappDir = Path.of(ctx.deploymentFolder, "AdventNet", "Sas", "tomcat", "webapps", ctx.parentService)
        val securityXml = webappDir.resolve("WEB-INF").resolve("security-properties.xml")
        if (!securityXml.exists()) return false

        var content = securityXml.readText()
        var modified = false

        // Set IAM server
        if (ctx.iamServer != null) {
            val iamRegex = Regex("""(<property\s+name="com\.adventnet\.iam\.internal\.server"\s+value=")[^"]*("/)""")
            if (iamRegex.containsMatchIn(content)) {
                content = iamRegex.replace(content, "$1${Regex.escapeReplacement(ctx.iamServer)}$2")
                modified = true
            }
        }

        // Set service.name
        if (ctx.iamServiceName != null) {
            val serviceNameRegex = Regex("""(<property\s+name="service\.name"\s+value=")[^"]*("/)""")
            if (serviceNameRegex.containsMatchIn(content)) {
                content = serviceNameRegex.replace(content, "$1${ctx.iamServiceName}$2")
                modified = true
            }

            // Also set service name in root <security> element
            val securityNameRegex = Regex("""(<security\s[^>]*?name=")[^"]*("[^>]*>)""")
            if (securityNameRegex.containsMatchIn(content)) {
                content = securityNameRegex.replace(content, "$1${ctx.iamServiceName}$2")
                modified = true
            }
        }

        // Set logout page with hostname
        if (ctx.hostName != null && ctx.httpsPort != null && ctx.iamServiceName != null) {
            val logoutUrl = "https://${ctx.hostName}:${ctx.httpsPort}/logout?servicename=${ctx.iamServiceName}"
            val logoutRegex = Regex("""(<property\s+name="logout\.page"\s+value=")[^"]*("/)""")
            if (logoutRegex.containsMatchIn(content)) {
                content = logoutRegex.replace(content, "$1${Regex.escapeReplacement(logoutUrl)}$2")
                modified = true
            }
        }

        if (modified) {
            securityXml.writeText(content)
        }
        return modified
    }

    /**
     * Builds a PatchContext from ZIDE service.xml and zide_properties.xml data.
     */
    fun buildPatchContext(
        serviceProps: Map<String, String>,
        zideProps: Map<String, String>
    ): PatchContext? {
        val deploymentFolder = serviceProps["ZIDE.DEPLOYMENT_FOLDER"] ?: return null
        val parentService = serviceProps["ZIDE.PARENT_SERVICE"] ?: return null
        return PatchContext(
            deploymentFolder = deploymentFolder,
            parentService = parentService,
            iamServer = zideProps["ZIDE.IAM_SERVER"],
            iamServiceName = zideProps["ZIDE.IAM_SERVICENAME"],
            hostName = zideProps["ZIDE.HOST_NAME"],
            httpsPort = zideProps["ZIDE.HTTPS_PORT"],
            dbName = zideProps["ZIDE_DB_NAME"],
            schemaName = zideProps["ZIDE.SCHEMA_NAME"]
        )
    }
}
