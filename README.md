# AURA - Adaptive Universal Reasoning & Automation

A modular, AI-powered Android automation agent built with Kotlin, Jetpack Compose and AccessibilityService.
Developed entirely from a phone with Termux, Git, GitHub and GitHub Actions.

**Status:** Phase 1 (foundation). See PROJECT_STATE.md.

## Build
Push to GitHub. The "Build debug APK" workflow runs tests and uploads `aura-debug-apk` (Actions tab > latest run > Artifacts).

## First run on device (Android 13+ sideloaded apps)
1. Install the APK.
2. Settings > Apps > AURA > menu > **Allow restricted settings**.
3. Settings > Accessibility > **AURA Agent** > enable.
4. Open AURA > **Run Settings demo**. A red STOP button appears while it runs.

## Termux cheat sheet
```
pkg install git
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
git clone https://github.com/<you>/AURA.git && cd AURA
git add -A && git commit -m "message" && git push
git pull
```
Push needs a GitHub personal access token as the password.

## Safety
Never commit keystores, tokens or API keys. Signing uses CI secrets via environment variables.
