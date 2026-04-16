package com.zoho.dzide.persistence

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.zoho.dzide.model.ZideConfigReadResult

@Service(Service.Level.PROJECT)
@State(
    name = "DzideZideCache",
    storages = [Storage("dzide/eclipse-zide.xml")]
)
class ZideCacheState : PersistentStateComponent<ZideCacheState.State> {

    data class State(
        var combinedHash: String? = null,
        var cachedResult: ZideConfigReadResult? = null
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getCachedConfig(expectedHash: String): ZideConfigReadResult? {
        if (myState.combinedHash == expectedHash) {
            return myState.cachedResult
        }
        return null
    }

    fun setCachedConfig(hash: String, result: ZideConfigReadResult) {
        myState.combinedHash = hash
        myState.cachedResult = result
    }

    companion object {
        fun getInstance(project: Project): ZideCacheState =
            project.getService(ZideCacheState::class.java)
    }
}
