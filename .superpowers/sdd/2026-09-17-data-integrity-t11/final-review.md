# T11 independent review

Spec verdict: changes required. Quality verdict: changes required (one asynchronous selection mutation, one explicit ordering-contract mismatch).

Reviewed the complete T11 design and review brief, T11 production files and tests, and router/layout/style deltas against `/tmp/tensor-t11-baseline`. T10 was read only to trace submission semantics. No T12/backend requirements were added. Known formatting and verification work in progress is excluded from findings.

## Findings

1. **P2 — A delayed category response silently reverses an explicit empty API selection.** `control-plane/src/views/IntegrityView.vue:86-93` waits for `listApis` after capabilities have already populated the form, then defaults every API whenever the current array is empty. With multiple sources, select a source, resolve capabilities while holding its category request, click 全选 then 清空, and release the category response. The empty choice becomes all APIs. Confirmation can already be checked, so this also silently re-enables submission for a scope the user cleared. Category metadata must not control/default the selected scope. Apply initial defaults when the current capability request completes, independently of category completion, and distinguish initialization from an intentional empty selection. Guard the continuation against superseding selection generations and unmount as well. An in-memory harness executing the current setup functions with Vue refs reproduced `[]` before category completion and `['first', 'second']` afterward. Add a deferred-category regression test that preserves Clear.

2. **P2 — Rechecking an API changes the submitted API order.** `control-plane/src/components/integrity/IntegrityCheckForm.vue:32` appends a checked API to the current selection. For capability order `[first, second]`, uncheck and recheck `first`: the selection becomes `[second, first]`; the view passes it directly to `prepareSubmission`, and T10 freezes that same order. This violates the design's explicit original-API-order requirement. Derive the selected known API array in capability order while preserving removed selections until explicitly removed. Add an off/on checkbox regression asserting exact submission order. Executing the current `toggleApi` function reproduced `[second, first]`.

## Evidence and scope limits

- Re-read the relevant functions immediately before recording the findings; both remained present.
- Reviewed frozen prepared retry, uncertain recovery, original pending scope, hash-change behavior, strict dates/limits, history BigInt arithmetic and request generations, route validation, and lack of N+1/fabricated conclusions. No additional actionable production issue was found in those paths.
- No full suites were duplicated; the parent owns final Node 24 unit/build/browser runs and Git tracking. Reviewer wrote only this report. A temporary local dev-server attempt could not bind inside the sandbox, so findings rely on source tracing and a direct execution harness, not a claimed browser run.

## Scoped fix re-review — approved

Final Spec verdict: PASS. Final Quality verdict: PASS. Both findings above are resolved; no remaining actionable finding within this review scope.

Re-read only the two fixes, their new regression tests, and directly affected lifecycle/order paths. `chooseSource` now defaults immediately after capability completion without waiting for categories; `choiceGeneration` rejects superseded or unmounted continuations. `toggleApi` emits known APIs in capability order and retains removed selections until explicitly removed.

Executed the current setup functions with Vue refs and assertions, without modifying production/test files. All passed:

- Defaults are applied before the delayed category response; an explicit Clear remains empty when categories settle.
- A capability completion after unmount cannot assign defaults.
- Off/on restores the original known API order, retains an existing removed API, and removes it only after an explicit uncheck.

Read the added deferred-category view regression and off/on form regression; both exercise the reported failure conditions. Final full unit/build/browser results and Git tracking remain the parent coordinator's verification responsibility. This scoped re-review introduces no additional implementation request.
