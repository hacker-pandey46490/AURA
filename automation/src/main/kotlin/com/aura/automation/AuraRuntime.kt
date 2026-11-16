package com.aura.automation

import com.aura.core.graph.NodeStatus
import com.aura.core.graph.RunControl
import com.aura.core.graph.TaskGraph
import com.aura.core.graph.flattened
import com.aura.core.model.AgentStatus
import com.aura.core.model.StepProgress
import com.aura.core.model.StepState
import com.aura.core.model.StepUpdate
import com.aura.core.model.TaskStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide observable state shared between the service and the UI. */
object AuraRuntime {
    private val _serviceConnected = MutableStateFlow(false)
    private val _status = MutableStateFlow(AgentStatus.IDLE)
    private val _message = MutableStateFlow("Idle")
    private val _dump = MutableStateFlow("")
    private val _steps = MutableStateFlow<List<StepProgress>>(emptyList())
    private val _graph = MutableStateFlow<TaskGraph?>(null)
    private val _nodeStatuses = MutableStateFlow<Map<String, NodeStatus>>(emptyMap())

    val serviceConnected: StateFlow<Boolean> = _serviceConnected
    val status: StateFlow<AgentStatus> = _status
    val message: StateFlow<String> = _message
    val dump: StateFlow<String> = _dump
    val steps: StateFlow<List<StepProgress>> = _steps
    val graph: StateFlow<TaskGraph?> = _graph
    val nodeStatuses: StateFlow<Map<String, NodeStatus>> = _nodeStatuses

    /** Pause / skip / retry controls shared by the UI and the on-screen overlay. */
    val control = RunControl()

    internal fun setServiceConnected(value: Boolean) { _serviceConnected.value = value }

    internal fun begin(label: String) {
        _status.value = AgentStatus.RUNNING
        _message.value = "Running: $label"
    }

    internal fun publishDump(text: String) { _dump.value = text }

    internal fun initProgress(steps: List<TaskStep>) {
        _steps.value = steps.map { StepProgress(it.label, StepState.PENDING, "", 0) }
    }

    internal fun updateStep(update: StepUpdate) {
        val list = _steps.value.toMutableList()
        if (update.index in list.indices) {
            list[update.index] = list[update.index].copy(
                state = update.state,
                detail = update.detail,
                attempts = update.attempts,
            )
            _steps.value = list
        }
    }

    internal fun initGraph(graph: TaskGraph) {
        _graph.value = graph
        _nodeStatuses.value = graph.flattened().associate { it.node.id to NodeStatus() }
    }

    internal fun updateNode(id: String, status: NodeStatus) {
        _nodeStatuses.value = _nodeStatuses.value + (id to status)
    }

    /** Clears the displayed tree (ignored while a task is running). */
    fun clearGraph() {
        if (_status.value != AgentStatus.RUNNING) {
            _graph.value = null
            _nodeStatuses.value = emptyMap()
        }
    }

    internal fun finish(success: Boolean, message: String) {
        _status.value = if (success) AgentStatus.COMPLETED else AgentStatus.FAILED
        _message.value = message
    }

    internal fun markStopped() {
        if (_status.value == AgentStatus.RUNNING) {
            _status.value = AgentStatus.STOPPED
            _message.value = "Stopped by user"
            _steps.value = _steps.value.map {
                if (it.state == StepState.RUNNING) it.copy(state = StepState.FAILED, detail = "Stopped") else it
            }
            _nodeStatuses.value = _nodeStatuses.value.mapValues { (_, st) ->
                if (st.state == StepState.RUNNING || st.state == StepState.WAITING) {
                    st.copy(state = StepState.FAILED, detail = "Stopped")
                } else {
                    st
                }
            }
        }
    }
}
