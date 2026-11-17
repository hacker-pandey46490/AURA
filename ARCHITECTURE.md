# Architecture

Gradle modules:

- `core` (pure Kotlin/JVM, unit-tested)
  - `model`: `UiNode`, `ScreenSnapshot`, `Action`, `Selector`, `TaskStep`, `Expectation`, `TaskRunner`, `TaskScriptParser`, `SensitivityGuard`
  - `api`: `ScreenObserver`, `ActionExecutor`
  - `graph`: `GraphNode`, `TaskGraph`, `NodeStatus`, `GraphRunner`, `RunControl`, `GraphScriptParser`
  - `brain`: `ModelProvider` (provider-independent), `PlannerSession`, `PlannerPrompt`
- `automation` (Android library): accessibility service, executor, overlay panel, `AuraRuntime`, `AgentFactory`,
  model adapters (`AnthropicProvider`, `OpenAiCompatibleProvider`)
- `app` (Android app): Compose UI (Ask / Script / Demo tabs), encrypted AI settings

## Planner
`PlannerSession` sends the user's request to a `ModelProvider` and expects either `ASK: <question>` or a script in AURA's own task language.
The script is parsed by the same `GraphScriptParser` the Script tab uses. Parse errors are sent back to the model for correction (max 2 times).
The resulting `TaskGraph` is shown to the user for approval and then run by `GraphRunner` with verification, retries and recovery.
The model never gets direct control of the phone: it can only produce a plan, and every plan passes the parser and the approval dialog.

Phase 4 limitation: the planner cannot see the screen, so it plans from the request alone and relies on verification and retries.
Observing the screen between steps and re-planning (AI decision nodes) is Phase 5.

## Providers
Adding a provider = implementing `ModelProvider` (one suspend function). Nothing else in the app depends on a specific vendor.

## Retry, verification and recovery
- A failed action is executed again (up to `maxRetries`). A succeeded action whose check is not met yet is only re-verified, never re-executed.
- A failed non-optional node stops the task or waits for Retry / Skip / Stop. Optional nodes that fail are skipped.

## Security
HTTPS only (plain HTTP allowed for localhost). API key stored with EncryptedSharedPreferences. Keys are never logged or placed in error messages.
Typed text is never logged. Sensitive steps need approval.
