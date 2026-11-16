package com.aura.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.automation.AuraRuntime
import com.aura.core.model.SensitivityGuard
import com.aura.core.model.StepProgress
import com.aura.core.model.StepState
import com.aura.core.model.TaskScriptParser
import com.aura.core.model.TaskStep

private const val SAMPLE_SCRIPT = """# One step per line. Add '=> changed' or '=> text:Foo' to verify a step.
launch Settings
wait 1500
scroll forward => changed
scroll backward => changed"""

@Composable
fun HomeScreen(
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRunDemo: () -> Unit,
    onStartTask: (List<TaskStep>) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("AURA", style = MaterialTheme.typography.headlineMedium)
                Text("Service: " + if (connected) "connected" else "not enabled")
                Text("Status: $status - $message")

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
                        TaskRunnerTab(onStartTask, onStop)
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
private fun ColumnScope.TaskRunnerTab(onStartTask: (List<TaskStep>) -> Unit, onStop: () -> Unit) {
    var script by rememberSaveable { mutableStateOf(SAMPLE_SCRIPT) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf<List<TaskStep>?>(null) }
    val progress by AuraRuntime.steps.collectAsState()

    OutlinedTextField(
        value = script,
        onValueChange = { script = it },
        label = { Text("Task script") },
        minLines = 5,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            val result = TaskScriptParser.parse(script)
            errors = when {
                result.errors.isNotEmpty() -> result.errors
                result.steps.isEmpty() -> listOf("The script has no steps")
                else -> emptyList()
            }
            if (errors.isEmpty()) pending = result.steps
        }) { Text("Review & run") }
        StopButton(onStop)
    }
    errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        progress.forEach { StepRow(it) }
    }

    pending?.let { steps ->
        val flagged = SensitivityGuard.sensitiveSteps(steps).map { it.first }.toSet()
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Run ${steps.size} steps?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (flagged.isNotEmpty()) {
                        Text("Steps marked with a warning sign may send, pay, delete or change data. Approve only if you intend that.")
                    }
                    steps.forEachIndexed { i, step ->
                        Text("${i + 1}. " + (if (i in flagged) "\u26A0 " else "") + step.label)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    onStartTask(steps)
                }) { Text(if (flagged.isEmpty()) "Run" else "Approve & run") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("STOP") }
}

@Composable
private fun StepRow(p: StepProgress) {
    val (symbol, color) = when (p.state) {
        StepState.PENDING -> "\u25CB" to Color.Gray
        StepState.RUNNING -> "\u25B6" to Color(0xFF1E88E5)
        StepState.SUCCEEDED -> "\u2713" to Color(0xFF2E7D32)
        StepState.FAILED -> "\u2717" to Color(0xFFC62828)
    }
    val detail = if (p.detail.isNotEmpty()) "\n    ${p.detail}" else ""
    Text("$symbol ${p.label}$detail", color = color, fontSize = 13.sp)
}
