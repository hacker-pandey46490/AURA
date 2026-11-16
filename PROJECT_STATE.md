# Project State

## Completed phases
- Phase 1 - Foundation (verified on device)
- Phase 2 - Action engine and manual task runner (verified on device)

## Current phase
Phase 3 - Task graph and live task tree. Code written; awaiting CI build and on-device test.

## Implemented and verified
- Multi-module skeleton, CI, accessibility service, STOP overlay
- Actions (click/long-click/double-tap/type/swipe/scroll/launch/back/home/wait), TaskRunner with verification and retries
- Flat task scripts, sensitivity guard with pre-run approval

## Implemented, NOT yet verified (Phase 3)
- Task graph model and GraphRunner (loops, conditions, groups, optional, verify)
- RunControl: pause, resume, skip next, retry/skip/stop on failure
- Floating control panel, live animated task tree, node inspection
- Script syntax: repeat, if/else, group, optional, verify, tap alias
- AgentFactory composition root; stale-results fix

## Pending features
- Edit future instructions while running (Phase 8 editor)
- AI decision nodes, clarification questions (Phase 4)
- Popup handling (Phase 5), recording (Phase 6), memory/Room (Phase 7), plugins (Phase 9)

## File structure
core/ (model/*, api/*, graph/*, tests), automation/ (service, executor, mapper, overlay, runtime, factory), app/ (MainActivity, HomeScreen),
.github/workflows/ (build.yml, release.yml)

## Known bugs / limitations
- Pause takes effect between nodes (the running node finishes first).
- "Skip" cannot interrupt a node that is mid-execution.
- View-id selectors are ROM-specific; prefer text/desc.
- CI shows Node 20 / setup-java v4 deprecation warnings (harmless).

## Build status
Phase 2 passing (v0.2.0). Phase 3 not built yet.

## Next development steps
1. Build Phase 3, fix compile errors, run the device checklist, record results.
2. Phase 4: model adapters and the natural-language planner.
