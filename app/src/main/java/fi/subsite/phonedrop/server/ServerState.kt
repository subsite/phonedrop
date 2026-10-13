package fi.subsite.phonedrop.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState
    /** [password] is null while password login is off (key login only). */
    data class Running(val ip: String?, val port: Int, val password: String?, val root: String) : ServerState
    data class Error(val message: String) : ServerState
}

/** Process-wide server status shared between the service and the UI. */
object ServerStatus {
    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    internal fun set(state: ServerState) {
        _state.value = state
    }
}
