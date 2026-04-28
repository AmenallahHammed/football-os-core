---
section: 01
title: Design Direction & Philosophy
depends_on: []
status: ready-for-implementation
last_updated: 2026-04-28
---

# Design Direction & Philosophy

Football OS — Workspace Calendar is an Angular frontend module for professional football clubs. It lets staff view calendar events, required documents, assigned tasks, and event details, while only the Head Coach can create/delete events.

Chosen direction: `refined-minimal`. The calendar must feel like a professional planning surface. Visual hierarchy comes from spacing, hairline borders, exact typography, and restrained color. Blue is for primary scheduling emphasis; red is only for missing/deleting/error states.

Must evoke: precise, calm, professional. Must never feel: playful, cluttered, heavy.

References: Google Calendar for event-grid clarity, Notion Calendar for calm event surfaces, Linear for restrained controls, Apple Calendar for today highlighting, Atlassian for accessible forms and menus.

Banned: gradients, decorative football imagery, desktop hamburger, top marketing header, direct calls to workspace service `:8082`, arbitrary colors outside tokens.
