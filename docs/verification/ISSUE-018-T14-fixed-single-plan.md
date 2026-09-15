# ISSUE-018-T14：固定 SINGLE 私有清单入口验证

2026-09-13。依据 [T14 设计](../task-designs/ISSUE-018-T14-design.md)的固定新轮次和完整 SINGLE 合同，以及 [T13 设计](../task-designs/ISSUE-018-T13-design.md)的真实浏览器与持久化证据合同，完成测试侧最小修复。本记录仅证明离线入口和校验行为，不记录真实账户、任务或 SQL 通过。

## 缺陷与修复

原 `selectTaskCases('single', plan)` 拒绝 plan；浏览器入口只在 RANGE 读取 `ISSUE018_T13_CASES_FILE`，SINGLE 因而使用自动生成的 T13 runId，并统一给 caseId 加前缀，无法消费 T14 预先登记的身份。

- SINGLE 现在可选读取现有 `ISSUE018_T13_CASES_FILE`，沿用顶层 `runId,cases` 及八字段 case 合同。显式空路径或非法文件硬失败，不回退至默认清单。
- 固定清单必须与 canonical 74 个 SINGLE 样本按 API、mode 和精确 params 一一匹配；拒绝缺项、额外项、重复 caseId、重复样本、换股票/日期/交易所/状态、额外或缺失参数、未知 API/字段、错误日期轴与敏感字符串。
- 输入的对象键和清单排序不影响匹配；执行仍按原 canonical API 顺序，每个股票 API 先 `000001.SZ` 再 `600000.SH`，stock_company 第二股票为 SSE。返回匹配 case 的原始身份和引用，不修改输入。
- 固定 runId 与 caseId 原样进入初始 NOT_RUN 证据，不拼接重写。无 plan 时保持原 40 API / 74 样本及自动生成身份。跨历史 SOURCE/TASK 的全局身份去重仍须在 T14 轮次登记时与完整历史索引核对；SINGLE 不额外依赖 RANGE 候选索引。
- 读取和清理均复用绝对路径、当前用户普通非 symlink、文件 0600 / 直接父目录 0700 校验。清理按首次绑定路径重读并比较原始字节 SHA-256；内容、权限或 symlink 替换使 `immutableInputs=false`，afterAll 保留失败并拒绝清洁验收。safe-results 保留首次输入摘要。

修改仅涉及 `tushare-range-evidence.js`、对应 `.test.js` 和 `tushare-live.spec.js`；未修改来源、生产策略、SQL 或页面。

## TDD 与离线验证

使用项目 Node **v24.15.0**，全部命令通过 `env -i` 清空继承环境，只提供 PATH；Playwright 发现模式另显式提供专用套件和 SINGLE 阶段变量及合成 plan 路径。未带入 Token、DB、真实证据、JAR 或其他账户配置。

```sh
/usr/bin/env -i PATH=/usr/bin:/bin \
  /Users/qiangzhiwei/code/github/Tensor/data-plane/tensor-app/target/frontend/node/node \
  --test --test-reporter=spec control-plane/e2e/tushare-range-evidence.test.js
```

| 检查 | 实际结果 |
| --- | --- |
| 修改前离线基线 | 67/67，通过，退出 0 |
| 新测试 RED，限定 `fixed SINGLE\|SINGLE harness` | 5 项中 4 失败、1 通过，退出 1；真实复现拒绝 plan、接受空 plan、忽略固定身份及忽略非法路径 |
| 核心入口修复后、权限复核修复前 | 同 5 项中 4 通过、1 失败，退出 1；内容未变但权限改为 0644 时原清理误报不变 |
| 最终完整 Node 回归 | **74/74**，失败 0、跳过 0，退出 0 |
| 默认 SINGLE 的真实 Playwright `test --list` | 40 tests in 1 file，退出 0 |
| 合成 0700/0600 固定 SINGLE plan 的真实 Playwright `test --list` | 40 tests in 1 file，退出 0 |
| 同一合成 plan 改为 0644 后 `test --list` | 固定安全输入错误，退出 1 |
| 三个代码文件的 `git diff --check` | 退出 0 |

新增回归执行实际 harness 的注册代码及输入/清理函数，注册回调不运行浏览器、JVM、SQL 或来源请求。覆盖固定身份、canonical 顺序、初始 NOT_RUN、原始字节摘要、字节变化、权限变化、同字节 symlink 替换、非法输入及无 plan 兼容；SINGLE/RANGE 都验证原始摘要写出和 afterAll 的 immutableInputs 失败传播。

发现模式的合成清单存于临时 0700 目录，执行后已删除。未启动真实 live、SOURCE、迁移或完整构建；未提交，新增验证文档由主代理统一加入 Git。

## 追加：显式 utf8mb4 defaults 预检

同日预检发现 T14 要求 MySQL defaults 显式声明 utf8mb4，而现有 `validateSqlInputs` 固定只接受 `[client]` 加六键的七行文件，会拒绝增加 `default-character-set=utf8mb4` 的八行文件；该问题在真实 live 启动前发现。

本次仅调整该函数接受七行或八行：七行仍须精确六键；八行须精确多出 `default-character-set`，值只能为 `utf8mb4`。按行数确定精确键集合，因此重复键不能被 `Object.fromEntries` 静默覆盖后放行。主机、端口、schema、用户名、密码及 TCP 协议匹配要求保持，权限和路径合同不变；mysql CLI 已有的强制 utf8mb4 参数不改。

先新增三个实际函数测试，使用临时 0600 合成 defaults 和注入的离线 DB 身份，不读取真实账户配置、不连接数据库。限定测试 RED 为 3 项中 2 失败、1 通过，退出 1：有效八行文件被旧 `SQL defaults shape` 拒绝，八行身份核验也被该错误提前阻断。最小修复后通过前述 `env -i` / 项目 Node 24 命令完整回归，最终 **77/77**，失败 0、跳过 0，退出 0；代码 `git diff --check` 退出 0。

覆盖旧七行及 utf8mb4 八行、utf8/latin1/大写或空编码、重复键、未知键、缺失键，以及两种格式下主机/端口/schema/用户名/密码/协议不匹配。临时文件已删除，未运行真实请求或提交。新 diff 已通知 `t14_evidence_review` 限定审查。
