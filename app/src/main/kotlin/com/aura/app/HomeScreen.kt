package com.aura.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.automation.AuraRuntime

@Composable
fun HomeScreen(
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRunDemo: () -> Unit,
    onStop: () -> Unit,
) {
    val connected by AuraRuntime.serviceConnected.collectAsState()
    val status by AuraRuntime.status.collectAsState()
    val message by AuraRuntime.message.collectAsState()
    val dump by AuraRuntime.dump.collectAsState()

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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRunDemo) { Text("Run Settings demo") }
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        ) { Text("STOP") }
                    }
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
        }
    }
}
