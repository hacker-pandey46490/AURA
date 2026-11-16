package com.aura.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

internal enum class OverlayMode { RUNNING, PAUSED, DECISION }

internal enum class OverlayAction { PAUSE, RESUME, SKIP_NEXT, RETRY, SKIP, STOP }

private data class OverlayButton(val label: String, val action: OverlayAction, val color: Int)

/**
 * Always-on-top control panel shown while automation runs.
 * Uses the accessibility overlay window type, so no extra permission is needed.
 */
internal class StopOverlay(
    private val service: AccessibilityService,
    private val onAction: (OverlayAction) -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panel: LinearLayout? = null
    private var mode = OverlayMode.RUNNING

    fun show() {
        if (panel != null) return
        val view = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 200
        }
        windowManager.addView(view, params)
        panel = view
        render()
    }

    fun setMode(newMode: OverlayMode) {
        mode = newMode
        render()
    }

    fun hide() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
    }

    private fun buttonsFor(mode: OverlayMode): List<OverlayButton> {
        val neutral = 0xFF37474F.toInt()
        val stop = OverlayButton("STOP", OverlayAction.STOP, Color.RED)
        return when (mode) {
            OverlayMode.RUNNING -> listOf(OverlayButton("PAUSE", OverlayAction.PAUSE, neutral), stop)
            OverlayMode.PAUSED -> listOf(
                OverlayButton("RESUME", OverlayAction.RESUME, neutral),
                OverlayButton("SKIP NEXT", OverlayAction.SKIP_NEXT, neutral),
                stop,
            )
            OverlayMode.DECISION -> listOf(
                OverlayButton("RETRY", OverlayAction.RETRY, neutral),
                OverlayButton("SKIP", OverlayAction.SKIP, neutral),
                stop,
            )
        }
    }

    private fun render() {
        val view = panel ?: return
        view.removeAllViews()
        for (spec in buttonsFor(mode)) {
            view.addView(
                Button(service).apply {
                    text = spec.label
                    setTextColor(Color.WHITE)
                    setBackgroundColor(spec.color)
                    setOnClickListener { onAction(spec.action) }
                },
            )
        }
    }
}
