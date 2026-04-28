---
section: 06
title: Component Library
depends_on: [01, 02, 03, 04, 05]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Component Library

Focus ring: `box-shadow: var(--shadow-focus); border-color: var(--color-border-focus); outline:none`.

Buttons: primary 36px high desktop/44px mobile, blue bg, white text, 6px radius. Secondary white with gray border. Ghost transparent with gray hover. Destructive red text. Icon-only 36x36 desktop/44x44 mobile with `aria-label`.

Forms: input 38px high, 1px `#D9D9D9`, radius 4px, padding 0 12px. Labels uppercase 11px muted semibold. People picker uses search + role filters + checklist. Required document row has document name + responsible staff + delete icon.

Sidebar: 60px icon rail, active Calendar blue. View switcher: Day, Week, Month, Year; active is blue pill with white text.

Calendar Event Card: Month min height 24px, week min 48px. Category background, 3px left accent border, 8px padding, 13px semibold title. Overflow: first 2 events then `+N more events`.

Create Event Dialog: 460px, max 90vh, sections Name, Who can access, Required Documents, Assign Tasks.

Event Detail Drawer: 520px desktop / 100vw mobile, slides from right, scrim `rgba(0,0,0,.32)`.

Context Menu: width 180px. Items: Open, Download, Share, Rename, Delete. Delete is red.
