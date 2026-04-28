---
section: 09
title: Interaction & Motion Design
depends_on: [02, 06, 07, 08]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Interaction & Motion Design

Motion is sparse and functional.

| Animation | From | To | Duration |
|---|---|---|---:|
| Page enter | opacity 0, y 4px | opacity 1, y 0 | 160ms |
| Drawer open | x 100% | x 0 | 250ms |
| Modal open | opacity 0, scale .98 | opacity 1, scale 1 | 160ms |
| Dropdown open | opacity 0, y -4px | opacity 1, y 0 | 120ms |
| Button press | scale 1 | scale .98 | 80ms |

Only animate transform and opacity. Reduced motion sets durations to 0ms.
