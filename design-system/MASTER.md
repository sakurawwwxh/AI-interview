# Interview Studio — Design System Master

> Generated with ui-ux-pro-max + project Ink & Signal direction.  
> Hierarchical source of truth for UI/UX decisions.

## Product

| Field | Value |
|-------|--------|
| Name | Interview Studio / 面试训练 |
| Type | Education + Productivity SaaS (AI interview coaching) |
| Audience | Job seekers preparing for technical interviews (local / single-user friendly) |
| Stack | React 18 + Vite + Tailwind v4 + Framer Motion + Lucide |

## Style Direction

**Ink & Signal Studio** — editorial coaching desk, not generic purple SaaS.

- Warm paper light surfaces + deep ink dark mode
- Teal signal primary + amber accent for emphasis moments
- Serif display (Fraunces + Noto Serif SC) + Noto Sans SC body
- Micro-interactions 150–300ms; respect `prefers-reduced-motion`

### Anti-patterns

- Emoji as structural icons
- Placeholder-only form labels
- Icon-only controls without `aria-label`
- Touch targets &lt; 44×44px
- Color-only status (always pair icon/text)
- Removing focus rings without replacement
- Decorative motion that ignores reduced-motion

## Color Tokens

| Role | Light | Dark | CSS |
|------|-------|------|-----|
| Background | `#f4f1ea` | `#070b0a` | `--shell-bg` |
| Panel | `#fffcf7` | `#0f1513` | `--shell-panel` |
| Text | `#141a18` | `#e8f0ed` | `--shell-text` |
| Muted | `#5c6864` | `#8fa39b` | `--shell-muted` |
| Primary | `#0d9488` / scale `primary-*` | `#2dd4bf` accent | `--color-primary-*` / `--accent` |
| Signal / CTA accent | `#c4782a` | `#e8a54b` | `--signal` |
| Destructive | `#dc2626` | red-400/500 | semantic danger |
| Focus ring | teal-400/600 | teal-400 | `:focus-visible` |

## Typography

| Role | Font | Notes |
|------|------|--------|
| Display / H1–H3 | Fraunces + Noto Serif SC | `font-display`, tracking tight |
| Body / UI | Noto Sans SC | base 16px, line-height 1.5 |
| Labels / kicker | Noto Sans SC | uppercase tracking for kickers |

## Spacing & Layout

- 4/8dp rhythm
- Content max ~1280px on dashboard-like pages
- Sidebar 17.5rem; main has responsive horizontal gutters
- Mobile-first; hamburger drawer &lt; lg

## Components (shared classes)

| Class | Use |
|-------|-----|
| `.ui-panel` | Elevated card surface |
| `.ui-panel-signal` | Panel with left signal bar |
| `.ui-btn-primary` | Primary CTA (min-height 44px) |
| `.ui-btn-ghost` | Secondary / ghost |
| `.ui-icon-btn` | 44×44 icon button |
| `.ui-input` | Form field |
| `.skip-link` | Keyboard skip to `#main-content` |
| `.page-kicker` / `.page-title` | Page header hierarchy |
| `.auth-mesh` / `.glass-card` | Auth screens |

## Interaction Rules

1. Visible labels on all inputs (`htmlFor` + `id`)
2. Loading buttons: `disabled` + `aria-busy` + spinner text
3. Dialogs: `role="dialog"`, `aria-modal`, Esc close, focus trap/return
4. Nav current page: `aria-current="page"`
5. Errors: near field + `role="alert"`
6. Password fields: show/hide toggle with `aria-pressed`
7. Transitions: prefer `transform` / `opacity`, 150–300ms

## Accessibility Checklist (ship gate)

- [ ] Contrast ≥4.5:1 body text both themes
- [ ] Skip link works to `#main-content`
- [ ] All icon-only buttons labeled
- [ ] Focus order matches visual order
- [ ] Reduced motion: no long/looping motion
- [ ] Touch targets ≥44px on primary controls

## Page Overrides

See `design-system/pages/` when a screen diverges (auth, interview room, charts).
