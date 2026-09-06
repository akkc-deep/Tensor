# ISSUE-004-T02 Implementation Report

## Result

Implemented the approved application shell, settings route and theme form, and in-memory business-view caching. Download and dataset views retain their local form, result, pagination, error, and in-flight request state while settings is open. Settings imports no API client and does not start metadata or business requests.

## Implementation

- Replaced the top header with the approved responsive 210px / 180px / 154px sidebar and 680px top-navigation layout.
- Added the named `/settings` route, semantic three-item navigation, current-page breadcrumb, SVG line icons, and a focusable `#workspace` skip target.
- Added `SettingsView` using the T01 `useTheme()` state: immediate color-picker apply, HEX submit and validation, applied-color display, brightness-correction explanation, saved/preview-only status, and glacier-white reset.
- Kept `DownloadView` and `DatasetView` alive by their explicit component names with `max=2` and route-name keys. Settings and 404 are outside the include list.
- Added and reused `PageHeading` in download, dataset, and settings views. Reused the existing `FieldError` for accessible HEX validation. Field/input refactoring remains outside T02 as assigned to T03, and category-selector reuse remains assigned to T05.
- Removed the 1280px body floor, set the minimum width to 360px, and added `min-width: 0`, responsive workspace spacing, themed focus/selection states, and 44px settings controls.

## Files

- `.superpowers/sdd/2026-09-07-issue-004-ui-redesign/task-ISSUE-004-T02-report.md`
- `control-plane/src/App.spec.js`
- `control-plane/src/components/common/PageHeading.vue`
- `control-plane/src/layouts/AppLayout.spec.js`
- `control-plane/src/layouts/AppLayout.vue`
- `control-plane/src/router/index.js`
- `control-plane/src/router/index.spec.js`
- `control-plane/src/style.css`
- `control-plane/src/views/DatasetView.spec.js`
- `control-plane/src/views/DatasetView.vue`
- `control-plane/src/views/DownloadView.spec.js`
- `control-plane/src/views/DownloadView.vue`
- `control-plane/src/views/SettingsView.spec.js`
- `control-plane/src/views/SettingsView.vue`

## TDD Evidence

RED was observed before the corresponding implementation:

- `npm test -- src/router/index.spec.js`: 1 failed / 2 passed because the named settings route did not exist.
- `npm test -- src/views/SettingsView.spec.js`: 3 failed because the settings heading and theme controls did not exist.
- `npm test -- src/layouts/AppLayout.spec.js`: 3 failed / 2 passed because the three-item navigation and cached download instance did not exist.
- `npm test -- src/views/DownloadView.spec.js src/views/DatasetView.spec.js`: 2 failed / 14 passed because the shared page descriptions were absent.
- `npm test -- src/App.spec.js src/router/index.spec.js src/layouts/AppLayout.spec.js src/views/SettingsView.spec.js`: the legacy single-header shell assertion failed against the new semantic shell and was updated to assert one sidebar navigation and one workspace.

GREEN verification during implementation:

- `npm test -- src/router/index.spec.js`: 3 passed.
- `npm test -- src/views/SettingsView.spec.js src/router/index.spec.js`: 6 passed.
- `npm test -- src/layouts/AppLayout.spec.js`: 9 passed, including form retention, in-flight download/query completion, second page at 100 rows, and failure-snapshot retry.
- `npm test -- src/views/DownloadView.spec.js src/views/DatasetView.spec.js`: 16 passed.
- `npm test -- src/router/index.spec.js src/layouts/AppLayout.spec.js src/views/SettingsView.spec.js src/App.spec.js`: 18 passed.

## Final Verification

All commands used Node 24.15.0 through `PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH`.

- `npm test`: 23 files passed, 157 tests passed, exit 0.
- `npm run build`: 1693 modules transformed, build completed, exit 0.
- `git diff --check`: exit 0.

## Concerns

Vite continues to report the existing warning that the main minified chunk exceeds 500 kB. T02 adds `SettingsView` as a separate lazy chunk; resolving the existing main-bundle size is outside this task. Final real-viewport visual acceptance remains assigned to T06.
