---
section: 11
title: Responsive Behavior
depends_on: [03, 05, 06, 07, 08]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Responsive Behavior

Desktop ≥1024px: 60px icon rail + 300px left rail + calendar. Tablet 768–1023px: 60px icon rail, left rail hidden. Mobile <768px: bottom nav replaces icon rail.

| Component | Mobile | Tablet | Desktop |
|---|---|---|---|
| Toolbar | 2 rows, search icon | 1 row, search 180px | 1 row, search 220px |
| Month grid | horizontal scroll | 7 cols | 7 cols |
| Week grid | horizontal scroll | scroll if needed | 7 cols fit |
| Drawer | 100vw | 520px | 520px |
| Dialog | calc(100vw - 32px) | 460px | 460px |
