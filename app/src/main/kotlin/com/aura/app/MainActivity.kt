package com.aura.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aura.automation.AuraAccessibilityService
import com.aura.core.graph.TaskGraph

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeScreen(
                onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOpenAppInfo = {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                    )
                },
                onRunDemo = ::runDemo,
                onStartGraph = ::startGraph,
                onStop = { AuraAccessibilityService.instance?.stopAutomation() },
            )
        }
    }

    private fun runDemo() {
        val service = AuraAccessibilityService.instance
        if (service == null) {
            toastServiceOff()
            return
        }
        startActivity(Intent(Settings.ACTION_SETTINGS))
        service.startDemo()
    }

    private fun startGraph(graph: TaskGraph, holdOnFailure: Boolean) {
        val service = AuraAccessibilityService.instance
        if (service == null) {
            toastServiceOff()
            return
        }
        service.startGraph(graph, holdOnFailure)
    }

    private fun toastServiceOff() {
        Toast.makeText(this, "Enable the AURA accessibility service first", Toast.LENGTH_LONG).show()
    }
}
