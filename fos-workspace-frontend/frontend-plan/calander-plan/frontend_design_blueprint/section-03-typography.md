---
section: 03
title: Typography System
depends_on: [01, 02]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Typography System

Use `Inter` for display and body. Load weights 400, 500, 600, 700 with `font-display: swap`.

| Token | Size | Line | Weight | Use |
|---|---:|---:|---:|---|
| `--type-page-title` | 28px | 1.2 | 600 | Month/page title |
| `--type-section-title` | 18px | 1.35 | 600 | Drawer title |
| `--type-card-title` | 14px | 1.35 | 600 | Event cards |
| `--type-body-md` | 14px | 1.5 | 400 | Default body |
| `--type-body-sm` | 13px | 1.45 | 400 | Helper text |
| `--type-label-md` | 11px | 1.2 | 600 uppercase | Form labels |

Rules: event titles clamp to 2 lines in week view and 1 line in month view. Never use text below 10px.
