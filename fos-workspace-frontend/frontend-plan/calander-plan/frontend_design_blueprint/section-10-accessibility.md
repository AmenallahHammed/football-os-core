---
section: 10
title: Accessibility Specification
depends_on: [02, 03, 04, 06, 08, 09]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Accessibility Specification

Target WCAG 2.1 AA. Modals/drawers trap focus and return focus to trigger. Escape closes overlays.

Landmarks: icon rail `nav aria-label="Workspace modules"`; left rail `aside aria-label="Calendar navigation"`; main `main id="calendar-main" aria-label="Workspace calendar"`; grid `role="grid"`; event drawer and create dialog `role="dialog" aria-modal="true"`.

Keyboard: Tab/Shift+Tab moves focus; Enter/Space activates; arrows navigate calendar grid; Home/End jumps within rows/menus.

Screen reader event label: `{title}, {type}, {start time}, {missing document count} missing documents`. Missing status must be text, not color alone.
