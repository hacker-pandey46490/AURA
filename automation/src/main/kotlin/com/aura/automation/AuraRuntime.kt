package com.aura.automation

import com.aura.core.model.AgentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide observable state shared between the service and the UI. */
object AuraRuntime {
    private val _serviceConnected = MutableStateFlow(false)
    private val _status = MutableStateFlow(AgentStatus.IDLE)
    private val _message = MutableStateFlow("Idle")
    private val _dump = MutableStateFlow("")

    val serviceConnected: StateFlow<Boolean> = _serviceConnected
    val status: StateFlow<AgentStatus> = _status
    val message: StateFlow<String> = _message
    val dump: StateFlow<String> = _dump

    internal fun setServiceConnected(value: Boolean) { _serviceConnected.value = value }

    internal fun begin(label: String) {
        _status.value = AgentStatus.RUNNING
        _message.value = "Running: $label"
    }

    internal fun publishDump(text: String) { _dump.value = text }

    internal fun finish(success: Boolean, message: String) {
        _status.value = if (success) AgentStatus.COMPLETED else AgentStatus.FAILED
        _message.value = message
    }

    internal fun markStopped() {
        if (_status.value == AgentStatus.RUNNING) {
            _status.value = AgentStatus.STOPPED
            _message.value = "Stopped by user"
        }
    }
}
