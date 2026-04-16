# ZIDE — IntelliJ IDEA Plugin

Tomcat server management and ZIDE integration for IntelliJ IDEA.

## Features

- **Server Management** — Add, edit, remove Tomcat servers from the SAS-ZIDE tool window
- **Run / Debug** — Start Tomcat in foreground mode (`catalina.sh run`) or debug mode with JPDA auto-attach
- **Stop** — Gracefully stop the attached Tomcat process
- **Build** — Run ANT build scripts from the project directory
- **Update Deployment** — Deploy a zip file to the ZIDE deployment folder
- **App Logs** — View application logs in a dedicated tool window tab
- **Deploy Sync on Save** — Automatically copy compiled classes, run ANT hooks, and sync resources on file save
- **ZIDE Auto-Configuration** — Detect and import Eclipse ZIDE project settings (`.zide_resources/`)

## Requirements

- IntelliJ IDEA 2024.1.7+ (Community or Ultimate)
- Java 17+
- Gradle 8.12+

## Building

```bash
./gradlew clean build
```

The plugin zip will be in `build/distributions/`.

## Running in Sandbox

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ IDEA instance with the plugin installed.

## Tool Window Tabs

| Tab | Description |
|-----|-------------|
| **Servers** | Tree view of configured Tomcat servers with status indicators |
| **Output** | Console output from server start/stop and build commands |
| **App Logs** | Application log viewer (`*application0.txt`) |

## ZIDE Menu

Available under **ZIDE** in the main menu bar:

| Action | Description |
|--------|-------------|
| Add Tomcat Server | Add a new server (manual or ZIDE auto-config) |
| Edit Server | Modify server settings |
| Remove Server | Delete a server configuration |
| Run | Start Tomcat (`catalina.sh run`) |
| Debug | Start with JPDA and auto-attach debugger |
| Stop | Stop the running Tomcat process |
| Refresh | Refresh all server statuses |
| Build | Run ANT build |
| Update Deployment | Deploy zip to server |
| App Logs | View application logs |
