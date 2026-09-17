# Task 1 implementation report

## Changed paths

- Added `control-plane/src/utils/integrityForm.js` and its unit tests.
- Added `control-plane/src/components/integrity/IntegrityCheckForm.vue`, `IntegrityScopePreview.vue`, `IntegrityCheckHistory.vue` and their unit tests.
- Added `control-plane/src/views/IntegrityView.vue`, `IntegrityCheckView.vue` and their unit tests.
- Updated `control-plane/src/router/index.js`, `router/index.spec.js`, `layouts/AppLayout.vue`, `layouts/AppLayout.spec.js`, and the narrow-screen navigation rules in `style.css`.

## RED / GREEN evidence

- Initial required RED: the empty utility skeleton produced 2 assertion failures: normalized Tushare symbols were `[]`, and validation returned zero units/invalid. The implemented utility then passed 14 tests.
- Empty component skeletons produced 9 expected behavior assertion failures covering source/API choices, symbol entry, scope explanations, BigInt history paging, and report IDs. Their implementations passed those tests.
- Draft normalization after a source change failed with `600000.sh` instead of `600000.SH`; the watcher fix passed.
- Prepared-storage retry, pending recovery with empty metadata, and new-source API defaults each failed focused view assertions before their state fixes.
- A deferred category response reselected cleared APIs in the focused race test; source-selection generation and immediate defaulting fixed it.
- Rechecking the first API produced `['mystery', 'daily']`; capability-order reconstruction fixed it to `['daily', 'mystery']`.

## Verification

- Task-focused suite: 8 files, 46 tests passed before the two independent review fixes.
- Review-fix suite: `IntegrityCheckForm.spec.js` and `IntegrityView.spec.js`, 2 files and 12 tests passed after both fixes.
- Full frontend suite before the review fixes: 46 files, 694 tests passed.
- Production build before the review fixes: Vite transformed 1,738 modules and completed successfully; only the existing large-chunk warning remained.
- Browser validation and final full-suite/build reruns are owned and recorded by the parent task so they are not duplicated here.

## Important design details

- Form parsing never consults a local stock catalogue. Tushare input is normalized and strictly validated; other plugins require nonblank ordered unique values.
- Validation uses UTC closed-range day counts, the supplied Shanghai date, exact inclusive limits, and counts `NON_STOCK` as one unit while treating a missing descriptor as stock-scoped.
- Capability APIs remain the option authority. Metadata only supplies categories; missing or late category responses cannot remove or reselect APIs.
- Confirmation follows the current capability hash. Same-source hash changes retain removed APIs visibly until the user removes them, and source changes default the new source in capability order.
- The real T10 composable owns IDs, frozen requests, recovery, resend, storage errors, and definition changes. A prepared request is frozen in the UI and storage retry calls `submit()` without preparing a new ID.
- Restored pending requests recover by GET before any capability load. Their original submission ID and scope, errors, and recovery controls remain visible even when current source metadata fails or is empty.
- History keeps snapshots per criteria, aborts stale GETs, preserves same-criteria data on refresh failure, uses BigInt for total/page math, and never derives a result conclusion from task status.
- The report route validates its UUID locally and performs no report request in T11.

## Limitations

- T11 intentionally supplies only the validated report entry. Report loading, result/issue filters, and rerun behavior belong to T12.
- Browser tests use strict API stubs; a real backend round trip remains T13 scope.
