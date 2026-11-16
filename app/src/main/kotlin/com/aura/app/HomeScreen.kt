package com.aura.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.automation.AuraRuntime
import com.aura.core.graph.ActionNode
import com.aura.core.graph.FailureDecision
import com.aura.core.graph.FlatNode
import com.aura.core.graph.GraphScriptParser
import com.aura.core.graph.NodeStatus
import com.aura.core.graph.TaskGraph
import com.aura.core.graph.flattened
import com.aura.core.graph.kind
import com.aura.core.model.AgentStatus
import com.aura.core.model.SensitivityGuard
import com.aura.core.model.StepState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SAMPLE_SCRIPT = """# Phase 3 sample: loop, condition, verify, optional step, group
launch Settings => pkg:com.android.settings
wait 1500
repeat 2
  scroll forward => changed
  optional scroll forward
end
if text:Display
  verify text:Display
else
  scroll backward
end
group Return to top
  scroll backward => changed
  optional scroll backward
  optional scroll backward
end"""

@Composable
fun HomeScreen(
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRunDemo: () -> Unit,
    onStartGraph: (TaskGraph, Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val connected by AuraRuntime.serviceConnected.collectAsState()
    val status by AuraRuntime.status.collectAsState()
    val message by AuraRuntime.message.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("AURA", style = MaterialTheme.typography.headlineMedium)
                Text("Service: " + if (connected) "connected" else "not enabled")
                Text("Status: $status - $message", fontSize = 14.sp)

                if (!connected) {
                    Text("1. Tap App info > menu > Allow restricted settings.\n2. Enable \"AURA Agent\" under Accessibility.")
                    OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) { Text("Open app info") }
                    Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Open accessibility settings") }
                } else {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Demo") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Task runner") })
                    }
                    if (tab == 0) {
                        DemoTab(onRunDemo, onStop)
                    } else {
                        TaskRunnerTab(status, onStartGraph, onStop)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DemoTab(onRunDemo: () -> Unit, onStop: () -> Unit) {
    val dump by AuraRuntime.dump.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onRunDemo) { Text("Run Settings demo") }
        StopButton(onStop)
    }
    Text("Last screen dump", style = MaterialTheme.typography.titleSmall)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            text = dump.ifEmpty { "(nothing captured yet)" },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ColumnScope.TaskRunnerTab(
    status: AgentStatus,
    onStartGraph: (TaskGraph, Boolean) -> Unit,
    onStop: () -> Unit,
) {
    var script by rememberSaveable { mutableStateOf(SAMPLE_SCRIPT) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf<TaskGraph?>(null) }
    var holdOnFailure by rememberSaveable { mutableStateOf(true) }
    var inspectedId by remember { mutableStateOf<String?>(null) }

    val graph by AuraRuntime.graph.collectAsState()
    val statuses by AuraRuntime.nodeStatuses.collectAsState()
    val paused by AuraRuntime.control.paused.collectAsState()
    val deciding by AuraRuntime.control.awaitingDecision.collectAsState()

    OutlinedTextField(
        value = script,
        onValueChange = { script = it },
        label = { Text("Task script") },
        minLines = 4,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = holdOnFailure, onCheckedChange = { holdOnFailure = it })
        Text("Ask me (Retry / Skip / Stop) when a step fails", fontSize = 13.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status == AgentStatus.RUNNING) {
            when {
                deciding -> {
                    Button(onClick = { AuraRuntime.control.decide(FailureDecision.RETRY) }) { Text("Retry") }
                    Button(onClick = { AuraRuntime.control.decide(FailureDecision.SKIP) }) { Text("Skip") }
                }
                paused -> {
                    Button(onClick = { AuraRuntime.control.resume() }) { Text("Resume") }
                    Button(onClick = { AuraRuntime.control.requestSkipNext() }) { Text("Skip next") }
                }
                else -> Button(onClick = { AuraRuntime.control.pause() }) { Text("Pause") }
            }
        } else {
            Button(onClick = {
                val result = GraphScriptParser.parse(script)
                val g = result.graph
                when {
                    result.errors.isNotEmpty() -> {
                        errors = result.errors
                        AuraRuntime.clearGraph()
                    }
                    g.roots.isEmpty() -> {
                        errors = listOf("The script has no steps")
                        AuraRuntime.clearGraph()
                    }
                    else -> {
                        errors = emptyList()
                        pending = g
                    }
                }
            }) { Text("Review & run") }
        }
        StopButton(onStop)
    }
    errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        val g = graph
        if (g != null) {
            g.flattened().forEach { flat ->
                NodeRow(flat, statuses[flat.node.id] ?: NodeStatus()) { inspectedId = flat.node.id }
            }
        }
    }

    pending?.let { g ->
        val flat = g.flattened()
        val flagged = flat
            .filter { f -> (f.node as? ActionNode)?.let { SensitivityGuard.reason(it.step) } != null }
            .map { it.node.id }
            .toSet()
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Run ${flat.size} nodes?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (flagged.isNotEmpty()) {
                        Text("Lines with a warning sign may send, pay, delete or change data. Approve only if you intend that.\n")
                    }
                    flat.forEach { f ->
                        val warn = if (f.node.id in flagged) "\u26A0 " else ""
                        Text(
                            "  ".repeat(f.depth) + warn + f.node.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    onStartGraph(g, holdOnFailure)
                }) { Text(if (flagged.isEmpty()) "Run" else "Approve & run") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    inspectedId?.let { id ->
        val node = graph?.flattened()?.firstOrNull { it.node.id == id }?.node
        val st = statuses[id] ?: NodeStatus()
        if (node != null) {
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            AlertDialog(
                onDismissRequest = { inspectedId = null },
                title = { Text(node.title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("Type: ${node.kind()}")
                        Text("State: ${st.state}")
                        if (st.attempts > 0) Text("Attempts: ${st.attempts}")
                        if (st.iterations > 0) Text("Iteration: ${st.iteration} of ${st.iterations}")
                        if (st.detail.isNotEmpty()) Text("Detail: ${st.detail}")
                        Text("\nHistory", style = MaterialTheme.typography.titleSmall)
                        if (st.history.isEmpty()) Text("(nothing yet)", fontSize = 12.sp)
                        st.history.forEach { h ->
                            Text(
                                timeFormat.format(Date(h.atMs)) + "  " + h.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { inspectedId = null }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun NodeRow(flat: FlatNode, status: NodeStatus, onClick: () -> Unit) {
    val color by animateColorAsState(nodeColor(status.state), label = "nodeColor")
    val pulse: Float = if (status.state == StepState.RUNNING) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        animated
    } else {
        1f
    }
    val title = flat.node.title + if (status.iterations > 0) "   (${status.iteration}/${status.iterations})" else ""

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (flat.depth * 16).dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(12.dp)
                .alpha(pulse)
                .background(color, CircleShape),
        )
        Column {
            Text(title, fontSize = 14.sp)
            if (status.detail.isNotEmpty()) {
                Text(status.detail, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

private fun nodeColor(state: StepState): Color = when (state) {
    StepState.PENDING -> Color(0xFF9E9E9E)
    StepState.RUNNING -> Color(0xFF1E88E5)
    StepState.SUCCEEDED -> Color(0xFF2E7D32)
    StepState.WAITING -> Color(0xFFF9A825)
    StepState.FAILED -> Color(0xFFC62828)
    StepState.SKIPPED -> Color(0xFFCFD8DC)
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("STOP") }
}
