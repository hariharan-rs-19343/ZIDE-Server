package com.zoho.dzide.persistence

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.zoho.dzide.model.ProjectServerMapping
import com.zoho.dzide.model.TomcatServer

@Service(Service.Level.PROJECT)
@State(
    name = "DzideTomcatServers",
    storages = [Storage("dzide/tomcat-servers.xml")]
)
class TomcatServerState : PersistentStateComponent<TomcatServerState.State> {

    data class State(
        var version: Int = 1,
        var servers: MutableList<TomcatServer> = mutableListOf(),
        var mappings: MutableList<ProjectServerMapping> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getServers(): List<TomcatServer> = myState.servers.toList()

    fun getMappings(): List<ProjectServerMapping> = myState.mappings.toList()

    fun addServer(server: TomcatServer) {
        myState.servers.add(server)
    }

    fun removeServer(serverId: String) {
        myState.servers.removeAll { it.id == serverId }
        myState.mappings.removeAll { it.serverId == serverId }
    }

    fun updateServer(serverId: String, updater: (TomcatServer) -> Unit) {
        myState.servers.find { it.id == serverId }?.let(updater)
    }

    fun getServer(serverId: String): TomcatServer? =
        myState.servers.find { it.id == serverId }

    fun getProjectMapping(projectPath: String): ProjectServerMapping? {
        val normalized = java.nio.file.Paths.get(projectPath).toAbsolutePath().normalize().toString()
        return myState.mappings.find {
            java.nio.file.Paths.get(it.projectPath).toAbsolutePath().normalize().toString() == normalized
        }
    }

    fun setProjectMapping(mapping: ProjectServerMapping) {
        val normalized = java.nio.file.Paths.get(mapping.projectPath).toAbsolutePath().normalize().toString()
        val normalizedMapping = mapping.copy(
            projectPath = normalized,
            contextPath = mapping.contextPath.trim().ifEmpty { "ROOT" }
        )
        val index = myState.mappings.indexOfFirst {
            java.nio.file.Paths.get(it.projectPath).toAbsolutePath().normalize().toString() == normalized
        }
        if (index == -1) {
            myState.mappings.add(normalizedMapping)
        } else {
            myState.mappings[index] = normalizedMapping
        }
    }

    companion object {
        fun getInstance(project: Project): TomcatServerState =
            project.getService(TomcatServerState::class.java)
    }
}
