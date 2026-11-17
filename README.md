# AURA - Adaptive Universal Reasoning & Automation

A modular, AI-powered Android automation agent built with Kotlin, Jetpack Compose and AccessibilityService.
Developed entirely from a phone with Termux, Git, GitHub and GitHub Actions.

**Status:** Phase 4 (AI planner). See PROJECT_STATE.md.

## Build
Push to GitHub. The "Build debug APK" workflow runs tests and uploads `aura-debug-apk`.

## First run on device (Android 13+ sideloaded apps)
1. Install the APK.
2. Settings > Apps > AURA > menu > **Allow restricted settings**.
3. Settings > Accessibility > **AURA Agent** > enable.

## Ask tab (AI planner)
1. Tap **AI settings**, choose a provider, enter the model name and API key, tap **Test connection**, then **Save**.
   - Anthropic: default model `claude-sonnet-5-5`.
   - OpenAI-compatible: any cloud or local server that offers `/chat/completions` (OpenAI, OpenRouter, Groq, Ollama, llama.cpp ...).
     For a server on this phone use `http://127.0.0.1:<port>/v1` (plain HTTP is allowed only for localhost).
2. Type what you want ("Open Settings and scroll down") and tap **Make a plan**.
3. If the AI is unsure it asks one question; answer it.
4. Review the plan in the dialog and tap **Run**. Plans never run without your approval.

Privacy: only your request (and your installed app names, if you switch that on) is sent to the AI service.
Screen contents are not sent in this phase. The API key is stored encrypted with the Android Keystore.

## Script tab (advanced)
Same language the AI writes. One node per line, `#` starts a comment.

| Line | Meaning |
|---|---|
| `launch Settings` | open an app by its name |
| `wait 1500` | pause (milliseconds) |
| `click <selector>` (alias `tap`) | tap an element |
| `longclick <selector>` / `doubletap <selector>` | long press / double tap |
| `type <selector> = hello` / `type = hello` | type into a field / the focused field |
| `enter` | press the keyboard's Enter / Search key (Android 11+) |
| `scroll forward` / `scroll backward` | scroll the first scrollable area |
| `swipe up` / `down` / `left` / `right` | swipe (direction of the finger) |
| `back` / `home` | system navigation |
| `verify text:Foo` | check something is on screen (also `pkg:...`, `changed`) |
| `optional <step>` | if this step fails it is skipped |
| `repeat 3` ... `end` | loop 1-100 times |
| `if text:Foo` ... `else` ... `end` | condition |
| `group Name` ... `end` | named sub-sequence |

Selectors: `text:Wi-Fi`, `desc:Search settings`, `id:vigour_search_edit`. Add a check after `=>`: `changed`, `text:Foo`, `pkg:com.example`.

## Safety
Never commit keystores, tokens or API keys. Typed text is never logged; password fields are redacted from screen dumps.
Risky steps (send / pay / delete ...) are flagged for approval. A floating panel (PAUSE / STOP) is shown while automation runs.
