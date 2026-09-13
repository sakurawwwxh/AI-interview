# Design QA — business module redesign

## Comparison target

- Source visual direction: `.zcode/design-reference.png` — compact, low-noise, graphite workspace with divider-led hierarchy and one blue action accent.
- Implementation targets: authenticated local pages `/upload`, `/job-targets`, `/history`, and `/interview-statistics`.
- Intended state: desktop, empty upload and target state; logged-in user data for history and statistics where available.

## Implemented scope

- Rebuilt the shared upload workbench used by both resume upload and knowledge-base upload.
- Rebuilt the job-target workspace around current focus, saved directions, and an in-context JD form.
- Rebuilt the resume library into a dense training-material queue with analysis, score, interview, deletion, and empty states.
- Rebuilt capability statistics into a growth dashboard with a metric strip, radar/trend regions, weak-area routing, and ability detail.

## Browser evidence

- `/upload`: rendered the upload workbench, native file picker control, disabled submit state, and process side panel.
- `/job-targets`: rendered current-focus, target list, and create form.
- `/history`: rendered the training-material heading, upload action, and data-list surface.
- `/interview-statistics`: rendered either the populated growth dashboard or the intentional no-data growth state; no console errors were reported on this route.
- `pnpm run build`: passed.

## QA finding

- [P1, verification blocker] The Windows restricted-token image viewer could not open either the saved design reference or a browser screenshot for side-by-side pixel inspection, even after copying the reference into the workspace. DOM-level and browser route checks completed, but a visual fidelity claim cannot be made without the required paired image inspection.
  - Fix needed: restore local image-viewer access or provide the design reference directly as an attachment in a future run, then compare same-viewport captures and update this report.

## Required fidelity surfaces

- Typography: implemented compact hierarchy, restrained metadata, and high-contrast action labels; pixel comparison blocked.
- Layout rhythm: implemented divider-led sections, denser lists, and a single primary action per workspace; pixel comparison blocked.
- Color tokens: uses the shared light/dark shell tokens plus blue for primary action and semantic state colors.
- Image and asset fidelity: the target direction contains no required photographic or decorative asset; existing Lucide interaction icons were retained.
- Copy: rewritten around a continuous training workflow rather than generic page labels.

## Implementation checklist

- [x] Rebuild shared upload experience
- [x] Rebuild target-job workspace
- [x] Rebuild resume library workspace
- [x] Rebuild capability-statistics workspace
- [x] Production build
- [x] Authenticated browser route checks
- [ ] Restore paired visual-capture access and complete pixel-level QA

final result: blocked