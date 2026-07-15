# Page override: Auth (Login / Register)

Extends `design-system/MASTER.md`.

## Layout

- Full-bleed `auth-mesh` (no app sidebar)
- Desktop: split hero copy (left) + glass form card (right)
- Mobile: form-first; hero collapsible/hidden

## Form UX

- Visible labels (never placeholder-only)
- Password show/hide toggle (`aria-label` + `aria-pressed`)
- Submit min-height 48px; disable + spinner while loading
- Errors: `role="alert"` below fields
- Register: helper text under password (length rules)

## Motion

- Staggered card/hero enter ≤500ms total
- Respect reduced-motion (global CSS kills long animations)
