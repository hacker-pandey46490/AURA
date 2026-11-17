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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.automation.AuraRuntime
import com.aura.core.brain.ChatMessage
import com.aura.core.brain.ChatRole
import com.aura.core.brain.ModelResult
import com.aura.core.brain.PlanResult
import com.aura.core.brain.PlannerSession
import com.aura.core.brain.ProviderKind
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SAMPLE_SCRIPT = """# One step per line. Add '=> changed' to verify a step.
launch Settings => pkg:com.android.settings
wait 1500
repeat 2
  scroll forward => changed
  optional scroll forward
end
group Return to top
  scroll backward => changed
  optional scroll backward
end"""

@Composable
fun HomeScreen(
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRunDemo: () -> Unit,
    onStartGraph: (TaskGraph, Boolean) -> Unit,
    onStop: () -> Unit,
    settingsStore: AiSettingsStore,
    appNames: () -> List<String>,
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
                Text("Service: " + (if (connected) "connected" else "not enabled") + "   |   $status", fontSize = 14.sp)
                Text(message, fontSize = 13.sp, color = Color.Gray)

                if (!connected) {
                    Text("1. Tap App info > menu > Allow restricted settings.\n2. Enable \"AURA Agent\" under Accessibility.")
                    OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) { Text("Open app info") }
                    Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Open accessibility settings") }
                } else {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ask") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Script") })
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Demo") })
                    }
                    when (tab) {
                        0 -> AskTab(status, settingsStore, appNames, onStartGraph, onStop)
                        1 -> ScriptTab(status, onStartGraph, onStop)
                        else -> DemoTab(onRunDemo, onStop)
                    }
                }
            }
        }
    }
}

// ---- Ask tab ----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.AskTab(
    status: AgentStatus,
    settingsStore: AiSettingsStore,
    appNames: () -> List<String>,
    onStartGraph: (TaskGraph, Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var request by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var question by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<PlannerSession?>(null) }
    var pending by remember { mutableStateOf<TaskGraph?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val running = status == AgentStatus.RUNNING

    fun submit(text: String, startNew: Boolean) {
        val settings = settingsStore.load()
        if (!settings.isConfigured) {
            problem = "Set up the AI first: tap \"AI settings\"."
            showSettings = true
            return
        }
        val existing = session
        val current = if (startNew || existing == null) {
            PlannerSession(createProvider(settings), if (settings.shareAppNames) appNames() else emptyList())
        } else {
            existing
        }
        session = current
        thinking = true
        problem = null
        scope.launch {
            val result = current.send(text)
            thinking = false
            when (result) {
                is PlanResult.Plan -> {
                    question = null
                    session = null
                    pending = result.graph
                }
                is PlanResult.Question -> {
                    question = result.text
                    answer = ""
                }
                is PlanResult.Failure -> {
                    question = null
                    session = null
                    problem = result.message
                }
            }
        }
    }

    OutlinedTextField(
        value = request,
        onValueChange = { request = it },
        label = { Text("What should I do?") },
        minLines = 2,
        maxLines = 4,
        enabled = !thinking && !running,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) {
            RunButtons(onStop)
        } else {
            Button(
                onClick = { submit(request, true) },
                enabled = request.isNotBlank() && !thinking,
            ) { Text("Make a plan") }
            TextButton(onClick = { showSettings = true }) { Text("AI settings") }
        }
    }
    if (thinking) Text("Thinking...", color = Color.Gray)
    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    question?.let { q ->
        Text(q, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Your answer") },
            enabled = !thinking,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { submit(answer, false) }, enabled = answer.isNotBlank() && !thinking) { Text("Send answer") }
    }

    TaskTree()

    pending?.let { g ->
        PlanReviewDialog(
            graph = g,
            onConfirm = {
                pending = null
                onStartGraph(g, true)
            },
            onCancel = { pending = null },
        )
    }
    if (showSettings) {
        AiSettingsDialog(settingsStore) { showSettings = false }
    }
}

@Composable
private fun AiSettingsDialog(store: AiSettingsStore, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(store.load()) }
    var testResult by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("AI settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        draft = if (draft.kind == ProviderKind.ANTHROPIC) {
                            draft.copy(kind = ProviderKind.OPENAI_COMPATIBLE, model = "")
                        } else {
                            draft.copy(kind = ProviderKind.ANTHROPIC, model = "claude-sonnet-5-5")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(draft.kind.displayName + "  (tap to switch)", fontSize = 12.sp) }
                if (draft.kind == ProviderKind.OPENAI_COMPATIBLE) {
                    OutlinedTextField(
                        value = draft.baseUrl,
                        onValueChange = { draft = draft.copy(baseUrl = it) },
                        label = { Text("Server URL") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    label = { Text("Model name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it) },
                    label = { Text("API key (stored encrypted)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = draft.shareAppNames, onCheckedChange = { draft = draft.copy(shareAppNames = it) })
                    Text("Share my installed app names with the AI", fontSize = 13.sp)
                }
                Text(
                    "Your request (and app names, if shared) is sent to this AI service. Screen contents are not sent.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
                TextButton(
                    enabled = !testing && draft.isConfigured,
                    onClick = {
                        testing = true
                        testResult = "Testing..."
                        scope.launch {
                            val reply = createProvider(draft).complete(
                                listOf(ChatMessage(ChatRole.USER, "Reply with the single word OK.")),
                            )
                            testResult = when (reply) {
                                is ModelResult.Success -> "Works: " + reply.text.trim().take(40)
                                is ModelResult.Error -> "Failed: " + reply.message
                            }
                            testing = false
                        }
                    },
                ) { Text("Test connection") }
                if (testResult.isNotEmpty()) Text(testResult, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.save(draft)
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

// ---- Script tab (advanced) ---------------------------------------------------------------------

@Composable
private fun ColumnScope.ScriptTab(
    status: AgentStatus,
    onStartGraph: (TaskGraph, Boolean) -> Unit,
    onStop: () -> Unit,
) {
    var script by rememberSaveable { mutableStateOf(SAMPLE_SCRIPT) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf<TaskGraph?>(null) }
    var holdOnFailure by rememberSaveable { mutableStateOf(true) }
    val running = status == AgentStatus.RUNNING

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
        if (running) {
            RunButtons(onStop)
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
    }
    errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

    TaskTree()

    pending?.let { g ->
        PlanReviewDialog(
            graph = g,
            onConfirm = {
                pending = null
                onStartGraph(g, holdOnFailure)
            },
            onCancel = { pending = null },
        )
    }
}

// ---- Demo tab ---------------------------------------------------------------------------------

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

// ---- shared pieces ----------------------------------------------------------------------------

/** Pause / Resume / Skip / Retry (depending on state) plus STOP. */
@Composable
private fun RowScope.RunButtons(onStop: () -> Unit) {
    val paused by AuraRuntime.control.paused.collectAsState()
    val deciding by AuraRuntime.control.awaitingDecision.collectAsState()
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
    StopButton(onStop)
}

@Composable
private fun PlanReviewDialog(graph: TaskGraph, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val flat = graph.flattened()
    val flagged = flat
        .filter { f -> (f.node as? ActionNode)?.let { SensitivityGuard.reason(it.step) } != null }
        .map { it.node.id }
        .toSet()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Run \"${graph.title}\"?") },
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
            TextButton(onClick = onConfirm) { Text(if (flagged.isEmpty()) "Run" else "Approve & run") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Live task tree with tap-to-inspect. */
@Composable
private fun ColumnScope.TaskTree() {
    val graph by AuraRuntime.graph.collectAsState()
    val statuses by AuraRuntime.nodeStatuses.collectAsState()
    var inspectedId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        val g = graph
        if (g != null) {
            g.flattened().forEach { flat ->
                NodeRow(flat, statuses[flat.node.id] ?: NodeStatus()) { inspectedId = flat.node.id }
            }
        }
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
