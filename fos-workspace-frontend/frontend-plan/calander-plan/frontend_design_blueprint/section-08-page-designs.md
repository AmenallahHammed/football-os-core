---
section: 08
title: Page-by-Page Design Specifications
depends_on: [01, 02, 03, 04, 05, 06, 07]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Page-by-Page Design Specifications

## Workspace Calendar
Route: `/workspace/calendar`. Backend: call gateway only at `http://localhost:8080`; use `/api/v1/events`. Auth ignored for now. Use existing backend data, not mock data.

Toolbar: previous, Today, next, view switcher Day/Week/Month/Year, search field. Week and month are both supported.

Month view: 7 columns, row height 112px, today highlighted. Week view: 64px time gutter + 7 day columns, hour rows 72px. Day view: one day column + time gutter. Year view: 12 mini-month cards.

Interactions: head coach clicking empty date shows `Create event` popover then opens dialog. Staff empty-date click does nothing. Clicking event opens drawer for all roles. Head coach can delete events; staff cannot. All events are visible to staff.

## Create Event Dialog
Fields: Name, Who can access, Required Documents, Assign Tasks. Required documents allow multiple rows with document name and responsible staff. Create disabled until Name is filled. On success: POST `/api/v1/events`, close, refetch, toast `Event created`.

## Event Detail Drawer
Slides from right. Shows date, event count, missing document count, event title/type/time/location/coach, description, participant chips, Required Documents list, Upload button, and note composer. Status: `MISSING` red or `UPLOADED` muted.
