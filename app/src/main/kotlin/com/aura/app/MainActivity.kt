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

    private val settingsStore by lazy { AiSettingsStore(this) }

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
                settingsStore = settingsStore,
                appNames = ::launchableAppNames,
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

    /** Names of launcher apps, shared with the AI only if the user switched that on. */
    @Suppress("DEPRECATION")
    private fun launchableAppNames(): List<String> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, 0)
            .map { it.loadLabel(packageManager).toString() }
            .distinct()
            .sorted()
            .take(150)
    }

    private fun toastServiceOff() {
        Toast.makeText(this, "Enable the AURA accessibility service first", Toast.LENGTH_LONG).show()
    }
}
