---
section: 13
title: Implementation Checklist
depends_on: [01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Implementation Checklist

- [ ] Add Angular route `/workspace/calendar`.
- [ ] Load design tokens globally.
- [ ] Configure API base URL `http://localhost:8080`.
- [ ] Never call `http://localhost:8082`.
- [ ] Implement icon rail, left rail, toolbar, day/week/month/year views.
- [ ] Implement create event popover/dialog.
- [ ] Implement people picker, required docs, tasks.
- [ ] Implement event drawer and file context menu.
- [ ] Head coach can create/delete; staff cannot.
- [ ] Staff can view all events.
- [ ] Keyboard navigation, focus trap, aria labels, missing status text.
- [ ] Visual QA: 60px sidebar, light minimal theme, blue `#0061FF`, red `#DE0000`, muted `#8B8783`.
