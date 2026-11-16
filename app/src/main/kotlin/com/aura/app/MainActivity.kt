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
import com.aura.core.model.TaskStep

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
                onStartTask = ::startTask,
                onStop = { AuraAccessibilityService.instance?.stopAutomation() },
            )
        }
    }

    private fun runDemo() {
        val service = AuraAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Enable the AURA accessibility service first", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(Settings.ACTION_SETTINGS))
        service.startDemo()
    }

    private fun startTask(steps: List<TaskStep>) {
        val service = AuraAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Enable the AURA accessibility service first", Toast.LENGTH_LONG).show()
            return
        }
        service.startTask(steps)
    }
}
