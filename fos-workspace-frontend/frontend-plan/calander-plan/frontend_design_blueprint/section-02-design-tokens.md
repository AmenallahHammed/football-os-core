---
section: 02
title: Design Tokens
depends_on: [01]
status: ready-for-implementation
last_updated: 2026-04-28
---

# Design Tokens

Use CSS custom properties.

```css
:root {
  --color-brand-primary:#0061FF; --color-brand-primary-hover:#0052D9; --color-brand-primary-active:#0046B8;
  --color-neutral-0:#FFFFFF; --color-neutral-100:#F5F5F5; --color-neutral-200:#EDEDED; --color-neutral-300:#D9D9D9; --color-neutral-500:#8B8783; --color-neutral-1000:#000000;
  --color-error:#DE0000; --color-error-bg:#FFF0F0; --color-info-bg:#EAF2FF;
  --color-text-primary:#000000; --color-text-muted:#8B8783; --color-text-on-brand:#FFFFFF;
  --color-border-default:#D9D9D9; --color-border-subtle:#EDEDED; --color-border-focus:#0061FF;
  --space-0:0px; --space-1:4px; --space-2:8px; --space-3:12px; --space-4:16px; --space-5:20px; --space-6:24px; --space-8:32px; --space-10:40px; --space-12:48px;
  --radius-sm:4px; --radius-md:6px; --radius-lg:8px; --radius-full:9999px;
  --shadow-focus:0 0 0 3px rgba(0,97,255,.22); --shadow-md:0 8px 24px rgba(0,0,0,.10); --shadow-lg:0 16px 40px rgba(0,0,0,.14);
  --z-dropdown:100; --z-overlay:300; --z-modal:400; --z-toast:500; --z-tooltip:600;
  --duration-fast:100ms; --duration-normal:200ms; --duration-slow:350ms;
  --ease-out:cubic-bezier(0,0,.2,1); --ease-in:cubic-bezier(.4,0,1,1); --ease-in-out:cubic-bezier(.4,0,.2,1);
  --shell-sidebar-width:60px; --calendar-left-rail-width:300px; --calendar-header-height:56px; --drawer-width:520px;
}
```
