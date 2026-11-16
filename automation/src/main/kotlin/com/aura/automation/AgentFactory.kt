package com.aura.automation

import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver
import com.aura.core.graph.GraphRunner
import com.aura.core.graph.NodeStatus
import com.aura.core.graph.RunControl
import com.aura.core.model.TaskRunner

/**
 * Composition root: the one place that wires interfaces to implementations.
 * Everything else receives its collaborators through constructors, so a different observer
 * (OCR, vision) or executor can be swapped in here without touching the engines.
 */
internal class AgentFactory(
    private val observer: ScreenObserver,
    private val executor: ActionExecutor,
    private val control: RunControl,
) {
    fun createTaskRunner(pause: suspend (Long) -> Unit): TaskRunner = TaskRunner(observer, executor, pause)

    fun createGraphRunner(
        holdOnFailure: Boolean,
        pause: suspend (Long) -> Unit,
        listener: (String, NodeStatus) -> Unit,
    ): GraphRunner = GraphRunner(
        taskRunner = createTaskRunner(pause),
        observer = observer,
        control = control,
        listener = listener,
        holdOnFailure = holdOnFailure,
    )
}
