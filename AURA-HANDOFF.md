# AURA HANDOFF - read this fully before replying

Use: start a NEW chat, attach (1) this file, (2) the original "PROJECT AURA" specification text/file, (3) AURA-source.zip (the current code), then paste the STARTER PROMPT below.

---

## STARTER PROMPT (paste this as the first message)

You are continuing an existing project called AURA (Android AI automation agent) that another Claude chat built with me over many turns. You have no memory of that chat, so before you answer:
1. Read AURA-HANDOFF.md completely, then the original specification, then unzip and read the source (core/, automation/, app/, .github/workflows/, the six docs).
2. Do NOT regenerate or redesign the project. The code in the zip is the truth. Never overwrite working code with placeholders.
3. Reply with a short summary (max 8 lines, easy English): current phase, what is verified, what is not, and the single next step. Then wait for my "go".
4. Follow the "Working agreement" and "Delivery method" sections exactly. I use only an Android phone (Termux + GitHub). Give short, copy-paste Termux commands in easy language.

---

## 1. Who I am and how to talk to me
- I work only on an Android phone (Android 16). No PC, no Android Studio. Tools: Termux, git, GitHub CLI (`gh`, already logged in), GitHub Actions for builds.
- GitHub repo: hacker-pandey46490/AURA (private). Local copy: ~/AURA in Termux. Branch: main.
- I have no money for paid APIs. I use Google AI Studio's free Gemini tier.
- I want SIMPLE language, short steps, and a simple UI. Advanced/dev things must be hidden from the main screen.
- I want voice commands and other extras added LAST, as plugins (Phase 9). Follow the roadmap in order.
- Never claim something works until I have tested it. Say clearly "written, not yet tested" when it is not verified. I cannot compile in chat, so compile errors may happen: I paste the CI error lines and you fix them.

## 2. Working agreement (from the original spec, section 15, plus what worked)
- Work in phases, never the whole project at once. Each phase: explain architecture, list files, give complete working code with exact paths, Termux commands, commit/push steps, how to build, test steps, and a self-contained prompt for the next phase.
- Maintain: README.md, ARCHITECTURE.md, ROADMAP.md, CHANGELOG.md, PROJECT_STATE.md, CONTRIBUTING.md (PROJECT_STATE: completed phases, current phase, implemented features, pending, file structure, known bugs, build status, next steps).
- Never silently remove functionality. Keep it modular: depend on interfaces in `core`, no hardcoded AI provider.
- Safety rules from the spec: user-authorised accessibility only, visible STOP control, confirm before sending/paying/deleting, never log sensitive text/clipboard, never put keys in source, no untrusted plugin code.

## 3. Delivery method that works (keep using it)
Deliver each phase as a ZIP containing ONLY new/changed files (paths relative to repo root, no top-level folder). I unzip over the repo:
```
cd ~/AURA
unzip -o ~/storage/downloads/AURA-phaseN.zip
git add -A && git commit -m "Phase N: ..." && git push
sleep 10 && gh run watch
```
If the build fails, I run this and paste the output:
```
gh run view $(gh run list -L1 --json databaseId -q '.[0].databaseId') --log-failed | grep -iE "error|e: |FAILED|wrong" | head -40
```
Clean install of the APK (a stale APK with the same name once caused a "nothing changed" confusion; ALWAYS bump versionCode/versionName and use a new file name):
```
RUN=$(gh run list -L1 --json databaseId -q '.[0].databaseId')
rm -rf ~/apk && mkdir ~/apk
gh run download $RUN -n aura-debug-apk -D ~/apk
cp ~/apk/app-debug.apk ~/storage/downloads/aura-X.Y.Z.apk
termux-open ~/storage/downloads/aura-X.Y.Z.apk
```
Uninstall the old AURA first, then: App info -> menu -> "Allow restricted settings", then enable "AURA Agent" in Accessibility.
The current version in app/build.gradle.kts is versionCode 4 / versionName "0.4.0". Bump it every phase.
Every delivered zip should also include updated PROJECT_STATE.md, CHANGELOG.md, ROADMAP.md (and README/ARCHITECTURE when relevant).

## 4. Current status
| Phase | What | Status |
|---|---|---|
| 1 | Skeleton, CI, accessibility service, UI-tree dump, STOP overlay, Settings demo | built + verified on my phone |
| 2 | Action engine, verified task runner, script parser, sensitivity guard | built + verified |
| 3 | Task graph, GraphRunner, live task tree, pause/skip/retry, floating control panel | built + verified |
| 4 | AI planner (Ask tab), Anthropic + OpenAI-compatible providers, AI settings, `enter` action | built by CI and installed as 0.4.0; the end-to-end planner is NOT yet verified |

Phase 4 test status: the app installs and the AI settings dialog works. "Test connection" with Google's Gemini returned HTTP 503 ("model is currently experiencing high demand") - a temporary Google-side error, not an AURA bug; my free tokens then ran out. Still to verify: Test connection says "Works", "Make a plan" produces a valid plan, the plan runs, a vague request makes the AI ask a question.
Gemini settings that should work: provider "OpenAI-compatible", Server URL `https://generativelanguage.googleapis.com/v1beta/openai` (must end in /openai; AURA appends /chat/completions), a Flash model name taken from the AI Studio model list (the name I tried, gemini-3.8-flash, may not exist), and my AI Studio key. 429 / "limit: 0" = that model's free quota is used up: try another model or another key.

## 5. Tech facts (do not change without a reason)
- Gradle modules: `:app` (Android app, Compose), `:automation` (Android library), `:core` (pure Kotlin/JVM, unit-tested).
- AGP 8.10.1, Kotlin 2.1.0 (+ compose compiler plugin), Gradle 8.11.1 installed by gradle/actions/setup-gradle (NO gradle wrapper in repo), JDK 17, compileSdk/targetSdk 36, minSdk 29, Compose BOM 2024.12.01, kotlinx-coroutines 1.9.0, androidx.security:security-crypto 1.0.0.
- CI (`.github/workflows/build.yml`): checkout, setup-java 17, install SDK via the runner's preinstalled sdkmanager called by FULL PATH (`ls -d "$ANDROID_HOME"/cmdline-tools/*/bin/sdkmanager`), setup-gradle, `gradle :core:test assembleDebug`, upload artifact `aura-debug-apk`. Do NOT use android-actions/setup-android (it failed: "Failed to find package 'tools'"). `release.yml` = optional signed release from secrets (AURA_KEYSTORE_BASE64, AURA_KEYSTORE_PASSWORD, AURA_KEY_ALIAS, AURA_KEY_PASSWORD).
- Unit tests only run in CI (`:core:test`) for the pure-Kotlin core.
- Node 20 / setup-java v4 deprecation warnings in CI are harmless.

## 6. Code map
core/src/main/kotlin/com/aura/core/
- model/: UiNode.kt, Action.kt (Action sealed, ActionResult, AgentStatus, describe()), Selector.kt, UiNodeSearch.kt, Task.kt (Expectation, TaskStep, StepState, StepUpdate, StepProgress, TaskOutcome), ExpectationCheck.kt, TaskRunner.kt (runStep + run), TaskScriptParser.kt (single-step lines), SensitivityGuard.kt, TreeFormatter.kt
- api/Interfaces.kt: ScreenObserver, ActionExecutor
- graph/: GraphModel.kt (ActionNode/GroupNode/LoopNode/ConditionNode, TaskGraph, NodeStatus, flattened()), RunControl.kt (pause/resume/skip/decision), GraphRunner.kt, GraphScriptParser.kt (repeat/if/else/group/optional/verify)
- brain/: ModelProvider.kt (ChatMessage, ModelResult, ProviderKind), PlannerPrompt.kt, Planner.kt (PlannerSession: ASK or script, auto-fix loop x2)
automation/src/main/kotlin/com/aura/automation/: AuraAccessibilityService.kt (startDemo/startTask/startGraph/stopAutomation), AccessibilityActionExecutor.kt, NodeMapper.kt (password text redacted), StopOverlay.kt (floating PAUSE/RESUME/SKIP/RETRY/STOP panel), AuraRuntime.kt (StateFlows shared with UI), AgentFactory.kt (manual composition root = DI), ModelProviders.kt (AnthropicProvider, OpenAiCompatibleProvider via HttpURLConnection + org.json). res/xml/aura_accessibility_service.xml; manifest has launcher `<queries>`.
app/src/main/kotlin/com/aura/app/: MainActivity.kt, HomeScreen.kt (tabs Ask / Script / Demo, plan review dialog, task tree, AI settings dialog), AiSettings.kt (EncryptedSharedPreferences). Manifest: INTERNET permission + network_security_config (HTTPS only, cleartext allowed for localhost/127.0.0.1).

## 7. Task script language (what the AI planner writes and the Script tab accepts)
One node per line, `#` comment. `launch <app>`, `wait <ms>`, `click|tap <selector>`, `longclick <selector>`, `doubletap <selector>`, `type <selector> = text` / `type = text`, `enter` (Android 11+), `scroll forward|backward`, `swipe up|down|left|right`, `back`, `home`, `verify <check>`, `optional <step>`, `repeat N ... end` (1-100), `if <check> ... else ... end`, `group Name ... end`.
Selectors: `text:..`, `desc:..`, `id:..` (bare word = text:). Checks after `=>`: `changed`, `text:..`, `pkg:..`.
Retry policy (important): a failed ACTION is re-executed (maxRetries, default 2); an action that SUCCEEDED but whose check is not yet true is only re-VERIFIED, never re-executed (so a click never happens twice). A failed non-optional node either stops the task or (switch "Ask me") waits for Retry/Skip/Stop. Optional failures are skipped. Pause takes effect before the next node.
Sensitivity guard flags steps whose target matches send/pay/delete/etc.; the plan review dialog shows a warning and needs approval. Plans from the AI never auto-run.

## 8. Lessons learned on my phone
- My Settings app: `desc:Search settings` works; view ids look like `vigour_search_edit` (ROM-specific, prefer text/desc).
- A scrollable list already at its end rejects another scroll: use `optional scroll ...` or accept a retry.
- Launching an app from the service while another app is in front worked.
- The AI cannot see the screen in Phase 4; it guesses labels and relies on verify/retry.

## 9. Next steps (in this order)
1. Finish Phase 4 verification with a working free model (see section 4). Suggested small fix first: automatic retry with a short wait for HTTP 503/429 inside ModelProviders.kt, and a clearer message for quota errors.
2. Phase 5: screen-aware planning - observe the screen between steps, decide, re-plan (AI decision nodes); popup / unexpected-screen handling; ask me when unsure. Must keep privacy rule: tell me before screen text is sent to a cloud model, redact passwords, make it opt-in.
3. Phase 6 recording + adaptive replay; Phase 7 multi-app workflows + Room memory; Phase 8 dashboard/studios (edit future instructions, workflow editor); Phase 9 plugin SDK (out-of-process, signed, permissioned) - voice commands, more providers and integrations ship as plugins.
4. Keep the main UI simple (Ask tab); advanced tools stay in other tabs.

## 10. Do not
- Do not paste secrets (API keys) in chat or commit them. My key lives only in the app's encrypted settings.
- Do not start Phase 5 before I confirm Phase 4 works.
- Do not give long explanations; give the exact commands and what I should see.
