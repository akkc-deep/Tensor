# T13 design readiness review

Date: 2026-09-12. Reviewed `docs/task-designs/ISSUE-018-T13-design.md` in full using designing-task-contracts, its template, board T06/T12/T13 entries, and necessary direct-source sections: overall design §3.4–3.5/§5.3/§6–7, T06 contract, current policies/calendar/client/loader interfaces, request examples and existing live harness selection/counting/cleanup. Read-only except this report; no tests, real calls, implementation, product edits or subagents.

## Verdict

**PASS — design is ready for successor linking and handoff.** No concrete material requirement conflict or implementation-blocking design gap found. This verdict concerns design readiness, not T13 execution or successful external acceptance. At review time T06/T12 are COMPLETED and T13 remains NOT_STARTED with board Design/Handoff None.

## Confirmed

- Matrix has exactly40 unique APIs matching current request-example membership:31 native (26 stock/3 dates/1 exchange/1 exchange_id),1 trading-day,2 calendar-day and6 SINGLE-only. RANGE29/5 and overall34/6 stock rules agree with T06. The11 UNKNOWN APIs match the existing policy and overall-design lists; candidate limits/document IDs/date axes agree with current policy declarations.
- Evidence-first order avoids the closed-gate circularity: official/rule evidence, explicit test-side client SOURCE probes while production stays closed, per-item local candidate/version change, rebuilt task/SQL evidence, then final disposition. Existing public context-aware client and package-local policy/calendar helpers support the proposed probe. Unknown algorithms require a design revision before opening; UNKNOWN, empty samples or equal wide/narrow samples cannot become completeness evidence.
- A real task failure withdraws that item's candidate flag and advances its version without deleting successful history. top_list depends on verified complete trade_cal; BJ/BSE evidence remains distinct. Suspended-interface historical availability and fina_mainbz default type are expressly unresolved external facts with required outcomes and no invented answers.
- The old live harness migration explicitly replaces synchronous receipt/outcome assumptions with202/Location/task/batch/SQL/event assertions. SINGLE remains40 registered tests with74 actual source-task samples and148 sample queries, with fixture2/3 counted separately. RANGE uses validated per-API cases, rejects empty selection as success, preserves strict traffic/security/cleanup, uses fresh isolated MySQL8.4.6 schemas and keeps real-source spacing at least the supplied2000ms minimum.
- First action is concrete and executable without an account: add the independent40-item evidence validator tests, observe missing-item/UNKNOWN-open rejection RED, then implement the minimal helper/index. Files, interfaces, private-input boundaries, exact commands, discovery-versus-execution distinction and observable acceptance criteria are specified. Default tests exclude the explicitly selected real SOURCE probe; ordinary browser execution excludes real-account inputs and retains its seven-file boundary.

## External prerequisites and completion boundary

Credentials, current permissions, authoritative completeness rules and meaningful historical/event samples are prerequisites to particular real runs and are the work T13 must obtain. Their absence is not filled by guesses or reported as passing. The design specifies fixed initial samples, documented additional rounds, no automatic retries/date searching, and EVIDENCE_MISSING/pause behavior. Remaining unresolved target interfaces prevent T13/mother-issue completion unless a separate explicit scope decision exists. Publication retains main/clean/HEAD prerequisites.

No revision requested before linking this design and preparing its next-task handoff. This review does not start T13, authorize calls, open RANGE, alter the board or publish anything.
