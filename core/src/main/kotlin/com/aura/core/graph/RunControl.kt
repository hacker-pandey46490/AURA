package com.aura.core.graph

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

enum class FailureDecision { RETRY, SKIP, STOP }

/**
 * User controls for a running graph. Pause takes effect before the next node starts
 * (the node already running is allowed to finish).
 */
class RunControl {
    private val _paused = MutableStateFlow(false)
    private val _awaitingDecision = MutableStateFlow(false)
    private var skipRequested = false
    private var decision: CompletableDeferred<FailureDecision>? = null

    val paused: StateFlow<Boolean> = _paused
    val awaitingDecision: StateFlow<Boolean> = _awaitingDecision
    val isPaused: Boolean get() = _paused.value

    fun reset() {
        _paused.value = false
        _awaitingDecision.value = false
        skipRequested = false
        decision?.cancel()
        decision = null
    }

    fun pause() { _paused.value = true }

    fun resume() { _paused.value = false }

    /** Skips the next node (and resumes if paused). */
    fun requestSkipNext() {
        skipRequested = true
        _paused.value = false
    }

    fun consumeSkip(): Boolean {
        val requested = skipRequested
        skipRequested = false
        return requested
    }

    suspend fun awaitResume() {
        _paused.first { !it }
    }

    suspend fun awaitDecision(): FailureDecision {
        val d = CompletableDeferred<FailureDecision>()
        decision = d
        _awaitingDecision.value = true
        try {
            return d.await()
        } finally {
            _awaitingDecision.value = false
            decision = null
        }
    }

    fun decide(value: FailureDecision) {
        decision?.complete(value)
    }
}
