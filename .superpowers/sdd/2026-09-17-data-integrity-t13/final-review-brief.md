# Final T13 review brief

Worktree: `/Users/qiangzhiwei/code/github/Tensor/.worktrees/data-integrity`.
Review only T13 delta against preserved inherited staged tree `1728e704532dca83032c35aaf4cc576307e37ca5`, never against HEAD (which would include inherited Studio/T01–T12).

Read T13 design, this plan’s implementation reports and `docs/verification/DATA-INTEGRITY-T13.md`. Review final diff package once, then only focused related checks for named risks. Treat claims as claims and verify them against code. Do not run already-passing broad tests. Do not mutate code, Git or board; write only final-review.md. No subagents.

Check outcome acceptance and code quality for: acceptance-only fixture versioning/windows/extension; independent full-key SQL and exact-string JSON; actual no-token flow/zero owned-upstream calls; securities immutability; same-DB2→3/history; real browser and legacy regression; launcher schema/credentials/resource ownership/cleanup; no core/HTTP/UI source branches; docs correctly separate synthetic vs production, stub vs real, default Maven vs selected MySQL IT.

Known final contract gate cannot pass in the preserved uncommitted feature worktree. Design section6 explicitly requires genuine clean committed main; no integration was authorized. This is an acknowledged task blocker, not a request to bypass guard or commit inherited baseline. Ensure evidence/board never claim COMPLETED, and pause handoff names exact resolution.

Evidence: integrity-tests.json(441 selected tests including125 realIT),fixture-http-final.json(final amended1 realIT),acceptance-tests/build JSON,stub-summary.json,subsequent production and real-browser evidence. Broad tests must not be rerun just to regenerate evidence. Review any scoped task fixes for cross-file risks, not a second full per-task review.

Return severity-ranked concrete findings with file:line and spec/quality verdicts. No speculative refactors.
