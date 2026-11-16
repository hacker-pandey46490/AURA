# Architecture

Gradle modules (more are added per phase, following the target layout in the spec):

- `core` (pure Kotlin/JVM): data models (`UiNode`, `ScreenSnapshot`, `Action`, `AgentStatus`) and interfaces (`ScreenObserver`, `ActionExecutor`). No Android dependencies, so it is unit-testable on the JVM.
- `automation` (Android library): `AuraAccessibilityService`, `NodeMapper`, `AccessibilityActionExecutor`, `StopOverlay`, `AuraRuntime` (observable state).
- `app` (Android app): Compose Home screen.

Rules: engines depend on `core` interfaces, never on each other's implementations. AI providers will be added behind a `ModelProvider` interface (Phase 4). Dependency injection is introduced when the number of collaborators justifies it (Phase 3).

Safety built in from the start: STOP overlay while running, password-field text redacted from dumps, no clipboard logging, no signing secrets in source.
