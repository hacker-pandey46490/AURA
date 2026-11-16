package com.aura.core.graph

import com.aura.core.api.ScreenObserver
import com.aura.core.model.StepState
import com.aura.core.model.TaskRunner
import com.aura.core.model.checkExpectation
import com.aura.core.model.describe

sealed interface GraphOutcome {
    data object Completed : GraphOutcome
    data class Failed(val nodeId: String, val reason: String) : GraphOutcome
    data object Stopped : GraphOutcome
}

/**
 * Executes a [TaskGraph] node by node and reports every state change through [listener].
 * Action nodes run through [TaskRunner.runStep] (verification + retries). On a failed non-optional
 * step the runner either stops (holdOnFailure = false) or asks the user to Retry / Skip / Stop.
 */
class GraphRunner(
    private val taskRunner: TaskRunner,
    private val observer: ScreenObserver,
    private val control: RunControl,
    private val listener: (nodeId: String, status: NodeStatus) -> Unit,
    private val holdOnFailure: Boolean = true,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private sealed interface NodeResult {
        data object Ok : NodeResult
        data class Failed(val nodeId: String, val reason: String) : NodeResult
        data object Stopped : NodeResult
    }

    private val statuses = mutableMapOf<String, NodeStatus>()

    suspend fun run(graph: TaskGraph): GraphOutcome {
        statuses.clear()
        graph.flattened().forEach { flat -> update(flat.node.id) { NodeStatus() } }
        return when (val result = runNodes(graph.roots)) {
            NodeResult.Ok -> GraphOutcome.Completed
            is NodeResult.Failed -> GraphOutcome.Failed(result.nodeId, result.reason)
            NodeResult.Stopped -> GraphOutcome.Stopped
        }
    }

    // ---- traversal ----------------------------------------------------------------------------

    private suspend fun runNodes(nodes: List<GraphNode>): NodeResult {
        for (node in nodes) {
            val result = runNode(node)
            if (result != NodeResult.Ok) return result
        }
        return NodeResult.Ok
    }

    private suspend fun runNode(node: GraphNode): NodeResult {
        gate(node)
        if (control.consumeSkip()) {
            skipSubtree(node, "Skipped by user")
            return NodeResult.Ok
        }
        return when (node) {
            is ActionNode -> runAction(node)
            is GroupNode -> runGroup(node)
            is LoopNode -> runLoop(node)
            is ConditionNode -> runCondition(node)
        }
    }

    /** Blocks while the user has paused the run; the node shows as WAITING (yellow). */
    private suspend fun gate(node: GraphNode) {
        if (!control.isPaused) return
        val previous = statuses[node.id] ?: NodeStatus()
        update(node.id) { it.copy(state = StepState.WAITING, detail = "Paused - waiting for you") }
        control.awaitResume()
        update(node.id) { previous }
    }

    private suspend fun runAction(node: ActionNode): NodeResult {
        set(node.id, StepState.RUNNING, "Running", "started", attempts = 0)
        while (true) {
            val outcome = taskRunner.runStep(node.step) { attempt, detail ->
                update(node.id) { it.copy(attempts = attempt, detail = detail) }
            }
            if (outcome.success) {
                set(node.id, StepState.SUCCEEDED, outcome.detail, "succeeded", attempts = outcome.attempts)
                return NodeResult.Ok
            }
            if (node.optional) {
                set(node.id, StepState.SKIPPED, "Optional step failed: ${outcome.detail}", "optional step failed: ${outcome.detail}", attempts = outcome.attempts)
                return NodeResult.Ok
            }
            set(node.id, StepState.FAILED, outcome.detail, "failed: ${outcome.detail}", attempts = outcome.attempts)
            if (!holdOnFailure) return NodeResult.Failed(node.id, outcome.detail)
            when (control.awaitDecision()) {
                FailureDecision.RETRY -> {
                    set(node.id, StepState.RUNNING, "Retrying", "retry requested by user", attempts = 0)
                }
                FailureDecision.SKIP -> {
                    set(node.id, StepState.SKIPPED, "Skipped by user", "skipped by user")
                    return NodeResult.Ok
                }
                FailureDecision.STOP -> return NodeResult.Stopped
            }
        }
    }

    private suspend fun runGroup(node: GroupNode): NodeResult {
        set(node.id, StepState.RUNNING, "Running", "started")
        val result = runNodes(node.children)
        return finishContainer(node.id, result, "Completed")
    }

    private suspend fun runLoop(node: LoopNode): NodeResult {
        set(node.id, StepState.RUNNING, "Starting", "started")
        for (i in 1..node.times) {
            update(node.id) { it.copy(iteration = i, iterations = node.times, detail = "Iteration $i of ${node.times}") }
            log(node.id, "iteration $i of ${node.times}")
            if (i > 1) resetSubtree(node.children)
            val result = runNodes(node.children)
            if (result != NodeResult.Ok) return finishContainer(node.id, result, "")
        }
        set(node.id, StepState.SUCCEEDED, "Completed ${node.times} iterations", "completed")
        return NodeResult.Ok
    }

    private suspend fun runCondition(node: ConditionNode): NodeResult {
        set(node.id, StepState.RUNNING, "Checking ${node.condition.describe()}", "checking condition")
        val snapshot = observer.capture()
        val isTrue = snapshot != null && checkExpectation(node.condition, null, snapshot) == null
        val taken = if (isTrue) node.thenBranch else node.elseBranch
        val other = if (isTrue) node.elseBranch else node.thenBranch
        other?.let { skipSubtree(it, "Branch not taken") }
        set(node.id, StepState.RUNNING, if (isTrue) "Condition true" else "Condition false", "condition was $isTrue")
        if (taken == null) {
            set(node.id, StepState.SUCCEEDED, "Condition false, nothing to do", "done")
            return NodeResult.Ok
        }
        val result = runGroup(taken)
        return finishContainer(node.id, result, if (isTrue) "Condition true, branch done" else "Condition false, branch done")
    }

    private fun finishContainer(id: String, result: NodeResult, okDetail: String): NodeResult {
        when (result) {
            NodeResult.Ok -> set(id, StepState.SUCCEEDED, okDetail, "done")
            is NodeResult.Failed -> set(id, StepState.FAILED, "A step inside failed", "inner step failed")
            NodeResult.Stopped -> Unit
        }
        return result
    }

    // ---- state helpers ------------------------------------------------------------------------

    private fun resetSubtree(nodes: List<GraphNode>) {
        for (node in nodes) {
            update(node.id) { it.copy(state = StepState.PENDING, detail = "", attempts = 0) }
            resetSubtree(node.childNodes())
        }
    }

    private fun skipSubtree(node: GraphNode, detail: String) {
        set(node.id, StepState.SKIPPED, detail, detail)
        node.childNodes().forEach { skipSubtree(it, detail) }
    }

    private fun set(id: String, state: StepState, detail: String, log: String? = null, attempts: Int? = null) {
        update(id) { s ->
            s.copy(
                state = state,
                detail = detail,
                attempts = attempts ?: s.attempts,
                history = if (log != null) (s.history + HistoryEntry(clock(), log)).takeLast(60) else s.history,
            )
        }
    }

    private fun log(id: String, text: String) {
        update(id) { s -> s.copy(history = (s.history + HistoryEntry(clock(), text)).takeLast(60)) }
    }

    private fun update(id: String, change: (NodeStatus) -> NodeStatus) {
        val next = change(statuses[id] ?: NodeStatus())
        statuses[id] = next
        listener(id, next)
    }
}
