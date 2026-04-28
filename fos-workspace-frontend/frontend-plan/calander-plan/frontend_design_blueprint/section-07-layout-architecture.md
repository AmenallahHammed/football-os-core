---
section: 07
title: Layout Architecture
depends_on: [01, 02, 05, 06]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Layout Architecture

No top nav/header for this module. The calendar fills the viewport.

```text
Desktop
┌──────┬────────────────────────────┬────────────────────────────────────┐
│60px  │300px Calendar Left Rail    │Main Calendar                       │
│Icons │Mini month + file sections  │Toolbar + day/week/month/year grid  │
└──────┴────────────────────────────┴────────────────────────────────────┘
```

Under 1024px, hide the 300px left rail behind a toggle. Under 768px, replace icon rail with 64px bottom navigation.
