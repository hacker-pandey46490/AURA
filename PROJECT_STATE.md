# Project State

## Completed phases
- Phase 1 - Foundation (CI-built, verified on device, Android 16)

## Current phase
Phase 2 - Action engine and manual task runner. Code written; awaiting CI build and on-device test.

## Implemented and verified
- Gradle multi-module skeleton (app, core, automation), CI debug build + tests
- Accessibility service, UI tree capture, STOP overlay, Settings demo

## Implemented, NOT yet verified (Phase 2)
- Actions: click/long-click/double-tap by selector, type, swipe, scroll backward, launch app by name, wait
- TaskRunner (verification + retries), script parser, sensitivity guard (JVM unit tests run in CI)
- Task runner tab with live step progress and pre-run approval dialog

## Pending features
Phase 3 onward (see ROADMAP.md).

## File structure
core/ (model/*, api/*, tests), automation/ (service, executor, mapper, overlay, runtime), app/ (MainActivity, HomeScreen),
.github/workflows/ (build.yml, release.yml)

## Known bugs / risks
- Launching apps from the accessibility service while AURA is in the background may be blocked by Android; the runner reports it as a failed step.
- Selectors are ROM-specific for view ids (use text/desc where possible).
- CI shows Node 20 / setup-java v4 deprecation warnings (harmless).

## Build status
Phase 1 passing. Phase 2 not built yet.

## Next development steps
1. Build Phase 2, fix any compile errors, run the device checklist, record results.
2. Phase 3: task graph data model and live task-tree UI.
