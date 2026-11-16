# Changelog
## 0.3.0 (unreleased)
- Task graph model: action, group, loop, condition nodes with per-node status, attempts, iteration and history
- GraphRunner with loops, conditions, optional steps, verify nodes
- Pause / resume / skip next / retry / skip / stop, from the app and a floating control panel
- Live animated task tree (grey/blue/green/yellow/red) with node inspection
- Script syntax: repeat, if/else, group, optional, verify, tap alias
- AgentFactory composition root
- Fix: stale results are cleared when a new script fails to parse

## 0.2.0
- Actions: click/long-click/double-tap by selector, type text, swipe, scroll backward, launch app by name, wait
- TaskRunner with settle delay, expectation checks and retry limits
- Task script parser, sensitivity guard, Task runner tab

## 0.1.0
- Project skeleton, accessibility service, UI tree capture, STOP overlay, GitHub Actions
