package com.aura.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

/** Always-on-top STOP button. Uses the accessibility overlay window type, so no extra permission is needed. */
internal class StopOverlay(
    private val service: AccessibilityService,
    private val onStop: () -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var button: Button? = null

    fun show() {
        if (button != null) return
        val view = Button(service).apply {
            text = "STOP"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.RED)
            setOnClickListener { onStop() }
        }
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
        button = view
    }

    fun hide() {
        button?.let { runCatching { windowManager.removeView(it) } }
        button = null
    }
}
