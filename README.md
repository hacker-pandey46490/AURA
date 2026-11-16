# AURA - Adaptive Universal Reasoning & Automation

A modular, AI-powered Android automation agent built with Kotlin, Jetpack Compose and AccessibilityService.
Developed entirely from a phone with Termux, Git, GitHub and GitHub Actions.

**Status:** Phase 2 (action engine + task runner). See PROJECT_STATE.md.

## Build
Push to GitHub. The "Build debug APK" workflow runs tests and uploads `aura-debug-apk`.
From Termux: `gh run watch`, then `gh run download -n aura-debug-apk -D ~/storage/downloads/`.

## First run on device (Android 13+ sideloaded apps)
1. Install the APK.
2. Settings > Apps > AURA > menu > **Allow restricted settings**.
3. Settings > Accessibility > **AURA Agent** > enable.
4. Open AURA. A red STOP button appears on screen whenever automation is running.

## Task script reference (Task runner tab)
One step per line. `#` starts a comment.

| Line | Meaning |
|---|---|
| `launch Settings` | open an app by its name |
| `wait 1500` | pause (milliseconds) |
| `click <selector>` | tap an element |
| `longclick <selector>` / `doubletap <selector>` | long press / double tap |
| `type <selector> = hello` | type into a field |
| `type = hello` | type into the focused field |
| `scroll forward` / `scroll backward` | scroll the first scrollable area |
| `swipe up` / `down` / `left` / `right` | swipe gesture (direction of the finger) |
| `back` / `home` | system navigation |

Selectors: `text:Wi-Fi`, `desc:Search settings`, `id:vigour_search_edit`. A bare word means `text:`.
Add a check after `=>`: `changed`, `text:Foo`, or `pkg:com.android.settings`.
A failed check retries verification (without repeating the action), then stops the task.
Steps that look like send / pay / delete / etc. are flagged for approval before the run.

## Termux cheat sheet
```
git add -A && git commit -m "message" && git push
gh run watch
gh run view $(gh run list -L1 --json databaseId -q '.[0].databaseId') --log-failed | grep -iE "error|e: |FAILED|wrong" | head -40
```

## Safety
Never commit keystores, tokens or API keys. Signing uses CI secrets via environment variables.
Typed text is never written to logs or progress messages; password fields are redacted from screen dumps.
