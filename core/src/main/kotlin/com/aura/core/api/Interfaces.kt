package com.aura.core.api

import com.aura.core.model.Action
import com.aura.core.model.ActionResult
import com.aura.core.model.ScreenSnapshot

/** Observes the current screen. Implementations: accessibility tree now, OCR/vision later. */
interface ScreenObserver {
    suspend fun capture(): ScreenSnapshot?
}

/** Executes primitive actions on the device. */
interface ActionExecutor {
    suspend fun execute(action: Action): ActionResult
}
