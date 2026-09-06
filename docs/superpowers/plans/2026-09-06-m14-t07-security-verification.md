# M14-T07 Security Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement and execute the approved security gate against the frozen production JAR, recording actual outcomes without changing production behavior.

**Architecture:** One POSIX shell entry embeds private Node/Python helpers. It orchestrates same-function counterexample checks, isolated existing build tests, vulnerability scans, loopback MySQL/stub/JVM/browser probes, final secret scanning, and exact owned-resource cleanup. Independent gates retain separate failures.

**Tech Stack:** POSIX sh, Python 3, Node 24, existing Playwright 1.62.1, Java 21, Maven 3.9.x, npm 11.x, MySQL 8.4.6, Dependency-Check 13.0.0.

**Spec:** `docs/task-designs/M14-T07-design.md` (explicitly approved by the user).

## Global Constraints

- 实施只新增 `scripts/security/verify-release.sh` 和 `docs/verification/M14-T07-security.md`。
- 不阅读或修改 M00～M13 生产实现；源码扫描只按字节寻找本轮 canary，不解释 Java/Vue/YAML/SQL 内容。
- 不得修改生产代码、POM、package/lock、现有 E2E、manifest、历史证据或原 JAR。
- 固定生产包 SHA-256 为 `acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef`。
- 不调用真实 Tushare，不消费继承的真实 Token，不复跑历史真实验收控制器。
- 辅助 Python/Node 程序由脚本写入本轮私有临时目录，不新增永久 helper、Playwright spec、依赖或配置。
- 主命令只在全部必需门禁及清理通过时退出 0，检查失败退出 1，参数错误退出 2；不存在 skip-as-pass。
- AGENTS.md permits working directly on main; newly created deliverables must be tracked by Git. Preserve pre-existing untracked target directories.

---

### Task 1: Implement the single security gate and counterexamples

**Files:** Create `scripts/security/verify-release.sh` only. Read the approved Spec completely; its matrix, exact canaries, scope, versions, commands, cleanup and acceptance rules are binding. Use public existing E2E helpers as reference without importing their registered tests. Do not write results or update the board in this task.

**Interfaces:** `sh scripts/security/verify-release.sh --self-test` runs offline counterexamples; no arguments performs all gates using the Spec's environment inputs. Private helpers communicate over stdin/stdout or 0600 local files; secret values never appear in argv. The final JSON/Markdown projection contains only safe labels, counters, IDs, hashes, timestamps and dependency advisories. Persist a private, scanned JSON outcome so the controller can write the evidence without guessing. Print its safe location only after the final scan; never print raw exceptions or raw subprocess output.

- [ ] Implement the self-test cases first using the same callable scanner, HTTP/header verdict, report-verdict and finalization functions used by the actual run. Observe rejection for a deliberately unsafe initial implementation before enabling formal execution.

```python
def rejected(action):
    try:
        action()
    except GateError:
        return
    raise AssertionError('counterexample was accepted')

# Use actual implemented names consistently in both self-test and runtime.
rejected(lambda: scan_bytes(b'prefix ' + token.encode(), secret_patterns))
rejected(lambda: query_verdict(200, {'code': 'PARAM_INVALID'}))
rejected(lambda: final_verdict({'checks': {'S01': 'pass'}, 'cleanup': False}))
```

- [ ] Cover raw/encoded secret injection in source, nested ZIP, HTTP, logs and artifacts; unreadable/missing files; corrupt ZIP; malicious HTML DOM; permissive HTTP/header/Actuator responses; high/critical or failed/missing dependency reports; empty test reports; and failed cleanup. Clean controls pass, each injected defect fails through the real decision function.
- [ ] Implement small named helpers inside the one script: input identity/tool validation; sanitized subprocess execution; recursive JAR scanning; test/audit report validation; Docker/defaults/SQL lifecycle; loopback stub and production JVM lifecycle; S01～S08 browser/HTTP probes; final scan and cleanup. Avoid a generic framework or arbitrary runtime selection flags.
- [ ] Make preflight inspect hash/profile/resource identities, effective Node/Java versions, actual MySQL source-host permissions, and port ownership. Use the exact public-source snapshot and commands in the Spec for Maven/npm scans. Capture all scanner output privately and project only a reviewed summary.
- [ ] Retain the original child exit code for every gate, complete independent gates when safe, and always run finalization. Invalid security responses remain failures even if the product ignores parameters. Never fix production or change an assertion to accept an observed defect.
- [ ] Expose detailed safe per-probe outcomes in the final report (including each S05/S06/S07 request) so real failures are reproducible without raw response dumps. Report expected probes not reached as not-run.
- [ ] Verify:

```sh
sh -n scripts/security/verify-release.sh
sh scripts/security/verify-release.sh --self-test
```

Expected: exit 0, all positive and negative decision checks pass, no database/JVM/real upstream interaction. Parse embedded Node/Python programs too. Review secret/environment flow and resource ownership before any live execution. Make the script executable, then commit only it as `test(security): add release control verification gate`.

### Task 2: Execute the reviewed gate and record its outcome

**Files:** Create `docs/verification/M14-T07-security.md`. Read-only execute `scripts/security/verify-release.sh`. Control updates use the authoritative board and the applicable handoff template; actual defects use the existing issue workflow.

**Interfaces:** Consumes Task 1's reviewed executable and safe output. Produces an evidence file with command/time/version/input identity, each gate's real status and exit, scanner coverage/advisories, S01～S08 sub-probe counts, and cleanup results. It must be rescanned by the same run's live secret set before commit (the script may render the exact final evidence itself).

- [ ] Select installed Node 24 and Java 21, verify the frozen JAR, current Git identity, Docker/MySQL/browser availability, and clean protected inputs. Preserve prior untracked artifacts. No real credentials are needed.
- [ ] Execute from repository root with only the approved JAR input and safe tool paths:

```sh
M14_SECURITY_JAR=/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  sh scripts/security/verify-release.sh
```

- [ ] Record actual safe results. Scanner/network errors remain failures with exact stages; no suppressed errors, no fabricated report, no dependency upgrade. Correct harness defects with focused same-function counterexamples, re-review changes, then rerun only the affected independent gate where evidence integrity allows; full exit-0 acceptance still needs one coherent complete run.
- [ ] Verify all owned resources and secrets are cleaned and the final evidence scan succeeded. Commit implementation evidence separately from board/control records as `test(security): record release control verification results`.
- [ ] If all outcome-level criteria pass, mark M14-T07 COMPLETED before inspecting the predefined successor, then follow the required successor-design/handoff gates. Otherwise record the actual blocker and resolution in a pause handoff, then transition IN_PROGRESS→BLOCKED. Do not start M14-T08 or mark release ready while prerequisites remain unresolved.

## Plan self-review

| Relationship | Producer / consumer | Consistency |
|---|---|---|
| Task 1 internal | Self-test and runtime use identical decision functions; Spec defines expected outcomes | Negative fixtures must fail, actual product results cannot redefine expectations. |
| Task 1 → Task 2 | One executable plus scanned outcome and optional final evidence rendering | Task 2 runs the exact reviewed script and only consumes safe evidence. |
| Task 2 internal | Actual run → final secret scan → evidence commit → state transition | A failure cannot produce COMPLETED or a normal successor handoff. |
| All tasks / Spec | Frozen JAR, two implementation files, no production edits, loopback credentials and cleanup | No scope conflicts; board and plan documents are separate workflow artifacts. |

All Spec sections map to Task 1 implementation or Task 2 actual evidence. No new business requirement or risk acceptance is introduced.
