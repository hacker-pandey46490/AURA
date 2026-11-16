# AURA - Adaptive Universal Reasoning & Automation

A modular, AI-powered Android automation agent built with Kotlin, Jetpack Compose and AccessibilityService.
Developed entirely from a phone with Termux, Git, GitHub and GitHub Actions.

**Status:** Phase 3 (task graph + live task tree). See PROJECT_STATE.md.

## Build
Push to GitHub. The "Build debug APK" workflow runs tests and uploads `aura-debug-apk`.
From Termux: `gh run watch`, then download into a clean folder (see CONTRIBUTING.md).

## First run on device (Android 13+ sideloaded apps)
1. Install the APK.
2. Settings > Apps > AURA > menu > **Allow restricted settings**.
3. Settings > Accessibility > **AURA Agent** > enable.
4. Open AURA. A control panel (PAUSE / STOP) floats on screen whenever automation is running.

## Task script reference (Task runner tab)
One node per line, `#` starts a comment, indentation is ignored.

| Line | Meaning |
|---|---|
| `launch Settings` | open an app by its name |
| `wait 1500` | pause (milliseconds) |
| `click <selector>` (alias `tap`) | tap an element |
| `longclick <selector>` / `doubletap <selector>` | long press / double tap |
| `type <selector> = hello` / `type = hello` | type into a field / the focused field |
| `scroll forward` / `scroll backward` | scroll the first scrollable area |
| `swipe up` / `down` / `left` / `right` | swipe (direction of the finger) |
| `back` / `home` | system navigation |
| `verify text:Foo` | check something is on screen (also `pkg:...`, `changed`); retries a few times |
| `optional <step>` | if this step fails it is skipped instead of stopping the task |
| `repeat 3` ... `end` | loop 1-100 times |
| `if text:Foo` ... `else` ... `end` | condition (`text:` or `pkg:`); `else` is optional |
| `group Name` ... `end` | named sub-sequence |

Selectors: `text:Wi-Fi`, `desc:Search settings`, `id:vigour_search_edit`. A bare word means `text:`.
Add a check after `=>` on any step: `changed`, `text:Foo`, `pkg:com.android.settings`.

## Task tree
Every run shows a live tree: grey = pending, blue (pulsing) = running, green = done, yellow = paused/waiting for you,
red = failed, light grey = skipped. Tap any node to inspect its state, attempts, loop iteration and history.

Controls (in the app and in the floating panel): Pause / Resume, Skip next (while paused), STOP.
With "Ask me when a step fails" on, a failed step waits for Retry / Skip / Stop instead of ending the task.

## Safety
Never commit keystores, tokens or API keys. Typed text is never written to logs or progress messages;
password fields are redacted from screen dumps. Steps that look like send / pay / delete are flagged for approval before a run.
