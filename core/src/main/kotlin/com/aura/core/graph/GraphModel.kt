package com.aura.core.graph

import com.aura.core.model.Expectation
import com.aura.core.model.StepState
import com.aura.core.model.TaskStep
import com.aura.core.model.describe

/** Static definition of a task. Runtime state lives separately in [NodeStatus], keyed by node id. */
sealed interface GraphNode {
    val id: String
    val title: String
}

/** One verified step. An [optional] node that fails is skipped instead of stopping the task. */
data class ActionNode(
    override val id: String,
    val step: TaskStep,
    val optional: Boolean = false,
    val label: String? = null,
) : GraphNode {
    override val title: String
        get() = (if (optional) "optional: " else "") + (label ?: step.label)
}

/** Reusable named sequence (sub-graph). */
data class GroupNode(
    override val id: String,
    override val title: String,
    val children: List<GraphNode>,
) : GraphNode

data class LoopNode(
    override val id: String,
    val times: Int,
    val children: List<GraphNode>,
) : GraphNode {
    override val title: String get() = "repeat $times times"
}

data class ConditionNode(
    override val id: String,
    val condition: Expectation,
    val thenBranch: GroupNode,
    val elseBranch: GroupNode?,
) : GraphNode {
    override val title: String get() = "if ${condition.describe()}"
}

data class TaskGraph(val title: String, val roots: List<GraphNode>)

data class FlatNode(val node: GraphNode, val depth: Int)

data class HistoryEntry(val atMs: Long, val text: String)

data class NodeStatus(
    val state: StepState = StepState.PENDING,
    val detail: String = "",
    val attempts: Int = 0,
    val iteration: Int = 0,
    val iterations: Int = 0,
    val history: List<HistoryEntry> = emptyList(),
)

fun GraphNode.kind(): String = when (this) {
    is ActionNode -> "action"
    is GroupNode -> "group"
    is LoopNode -> "loop"
    is ConditionNode -> "condition"
}

fun GraphNode.childNodes(): List<GraphNode> = when (this) {
    is ActionNode -> emptyList()
    is GroupNode -> children
    is LoopNode -> children
    is ConditionNode -> listOfNotNull(thenBranch, elseBranch)
}

/** Depth-first list of every node with its nesting depth, in display order. */
fun TaskGraph.flattened(): List<FlatNode> {
    val out = mutableListOf<FlatNode>()
    fun visit(node: GraphNode, depth: Int) {
        out += FlatNode(node, depth)
        node.childNodes().forEach { visit(it, depth + 1) }
    }
    roots.forEach { visit(it, 0) }
    return out
}
