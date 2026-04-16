plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        zipSigner()
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.zoho.dzide"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
        vendor {
            name = "Zoho"
        }
        description = """
            DZIDE — Tomcat server management and ZIDE auto-configuration for IntelliJ IDEA.
            <ul>
                <li>Add / Edit / Remove Tomcat servers from a dedicated tool window</li>
                <li>Start / Stop servers with real-time log output</li>
                <li>Run and Debug Java projects on Tomcat with JPDA support</li>
                <li>Auto-configure from Eclipse ZIDE projects (.zide_resources/)</li>
                <li>Deploy-sync on save: ANT hooks and compiled class file copy</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    wrapper {
        gradleVersion = "8.12"
    }
}
