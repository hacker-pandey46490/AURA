# Architecture

Gradle modules:

- `core` (pure Kotlin/JVM, unit-tested)
  - `model`: `UiNode`, `ScreenSnapshot`, `Action`, `Selector`, `TaskStep`, `Expectation`, `TaskRunner`, `TaskScriptParser`, `SensitivityGuard`
  - `api`: `ScreenObserver`, `ActionExecutor`
  - `graph`: `GraphNode` (Action / Group / Loop / Condition), `TaskGraph`, `NodeStatus`, `GraphRunner`, `RunControl`, `GraphScriptParser`
- `automation` (Android library): `AuraAccessibilityService`, `AccessibilityActionExecutor`, `NodeMapper`, `StopOverlay` (control panel),
  `AuraRuntime` (observable state), `AgentFactory` (composition root)
- `app` (Android app): Compose Home screen, Demo tab and Task runner tab with the live task tree

## Task graph
The graph is a static definition; runtime state is a separate `NodeStatus` per node id (state, detail, attempts, loop iteration, history).
`GraphRunner` walks the tree and reports each change through a listener, which `AuraRuntime` publishes as a `StateFlow` for the UI.
"Verify" is an action node whose check is the point (`verify text:Foo`). AI decision nodes arrive with the planner (Phase 4).

## Retry, verification and recovery
- A failed action is executed again (up to `maxRetries`). A succeeded action whose check is not met yet is only re-verified, never re-executed.
- A failed non-optional node either stops the task or, with "ask me" on, waits for Retry / Skip / Stop (`RunControl`).
- Optional nodes that fail are skipped.
- Pause takes effect before the next node starts; the running node finishes first.

## Dependency injection
Engines receive collaborators through constructors, and `AgentFactory` is the single wiring point
(manual composition root). A DI framework (Hilt/Koin) can replace the factory later without touching the engines.

## Safety
Floating PAUSE/STOP panel while running; password text redacted; typed text never logged; sensitive steps need approval before a run;
no signing secrets in source.
