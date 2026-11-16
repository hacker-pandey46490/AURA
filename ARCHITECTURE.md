# Architecture

Gradle modules (more are added per phase, following the target layout in the spec):

- `core` (pure Kotlin/JVM, unit-tested): models (`UiNode`, `ScreenSnapshot`, `Action`, `Selector`, `TaskStep`, `Expectation`),
  interfaces (`ScreenObserver`, `ActionExecutor`), `TaskRunner` (execute -> settle -> verify -> retry),
  `TaskScriptParser`, `SensitivityGuard`.
- `automation` (Android library): `AuraAccessibilityService`, `NodeMapper`, `AccessibilityActionExecutor`
  (node actions + `dispatchGesture` + app launch), `StopOverlay`, `AuraRuntime` (observable state).
- `app` (Android app): Compose Home screen with Demo and Task runner tabs.

Rules: engines depend on `core` interfaces, never on each other's implementations. AI providers will be added behind a
`ModelProvider` interface (Phase 4). Dependency injection arrives in Phase 3.

## Retry and verification policy
- If an action fails, it is executed again (up to `maxRetries`).
- If an action succeeded but its expectation is not met, only verification is repeated. Actions are never re-executed
  after succeeding, so a click cannot happen twice by accident.

## Safety
STOP overlay while running; password text redacted; typed text never logged; sensitive steps need approval before a run;
no signing secrets in source.
