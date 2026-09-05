# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M14-T04`，完成记录 `80a9491`。
- **Next task:** `M14-T05`，按预定义Order 75选中。
- **Design document:** `docs/task-designs/M14-T05-design.md`，已由 `9b3d263` 提交并回填同一设计链接，之后完整读取。
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M14-T05`
- **Title:** 真实 Tushare 49 接口受控页面验收
- **Goal:** 从原验收JAR页面执行49接口的合法样例，证明真实非空下载的适配、入库和查看，以及合法EMPTY无占位行，补齐M14-T04未执行的真实业务调用。
- **Scope:** 只新增 `control-plane/e2e/tushare-live.spec.js` 和 `docs/verification/M14-T05-tushare-live.md`。同文件注册49个串行用例，按manifest顺序执行全部58组参数；独立fixture SUCCESS/EMPTY准备阶段另计2次POST、3次查询。所有业务来自页面，不读后端生产实现、不改生产/配置/依赖/旧测试/manifest/模板或原JAR，不使用API/SQL种数或替身响应，不进入性能/发布任务。
- **Acceptance criteria:** 完整命令及CLI退出后终检均exit0，49 passed、0 failed/skipped/retry；37个ok接口各至少一次非空SUCCESS，12个empty接口均0/0/0且末查0行。58次真实下载POST、98次真实dataset查询；页面末查total与空库前置下累计insertedRows一致，来源字段/本轮时间/可见记录与响应匹配；fixture补测单独通过。Token仅经 `TENSOR_TUSHARE_TOKEN` 隐藏注入，账户权限/频率/额度先确认，无自动重试，无秘密或真实行产物残留；独立表计数、请求关联、扫描、正常停机和自有资源清理通过。两实施文件提交为 `test(release): verify live Tushare interfaces`。

详细设计已通过独立就绪审查及定点复核：响应精确八键、启动/清理预算、CLI退出后的失败产物终检、页面资源允许列表四项均Addressed，无新增Critical/Important/Minor，`Ready for implementation: Yes`。设计结构、引用、manifest49接口/58样例/37ok/12empty及两文件边界已核对。新spec和实际验收证据尚未创建；本地安全失败探针、账户权限/额度和真实矩阵均未执行，设计预期不是实跑结果。

## Dependencies

### `M14-T04`

- **Artifact:** `docs/task-designs/M14-T04-design.md`；`docs/verification/M14-T04-49-contracts.md`；`scripts/verify-49-contracts.sh`；`control-plane/e2e/tushare-metadata.spec.js`；其冻结输入 `docs/data-template/manifest.json` 与原验收JAR `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`。
- **Decision:** 49 API/dataset身份与描述符/表/资源合同已验证；保留七组分类、43必填/6无参数、五组filters及range→date_range。manifest提供合法样例与接口级ok/empty，row_count是历史信息。原验收JAR与shell新建生产JAR用途不同。复用公开role/label、pending请求与page关闭后再排空、独立schema及正常停机方法，不import已注册测试的spec。
- **Rationale:** 直接消费已验证的定义、分发与页面入口，使本轮集中验证真实业务反馈和落库查看；不以假Token元数据结果替代真实下载，也不从服务端当前输出自举预期。
- **Constraint:** manifest SHA-256 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`；原验收JAR SHA-256 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。不读取模板data或重装配原包；全新 `tensor_m14_t05_<随机十六进制>` schema，应用只有CREATE/SELECT/INSERT/UPDATE。T04的假Token和零调用哨兵不能带入T05；后继用真实Token和固定 `https://api.tushare.pro`，浏览器不继承凭证。
- **Usage:** 从manifest注册49项并在每项内遍历params，共58次真实页面提交；以T04已验证的公开元数据定位表单/同名dataset。完成全部样例后按接口级37ok/12empty判定，结合初始空表、页面查询与插入数累计验证真实落库。新运行保留独立版本/哈希与安全证据，不能复用前序运行环境。
- **Readiness evidence:** 看板已在 `80a9491` 记录COMPLETED，实施 `f96c7b1`、修订 `1a23624`/`616d54d` 精确三文件。最终shell BRRlZI0U证据SHA `06c9698df9bb457691c786a19aa4e9096c29d771ff291eb59b0749740c91c4a2`，50/52/4全部通过、前端20文件/120、资源49/49/49、11合成拒绝及双Docker盘点失败探针通过；fixture附加1表另计。Run7真实49配对用例通过，43/6、零下载/records/上游、13截图人工接受；独立6迁移/50业务表/49生产表逐表0行、JVM/哨兵/容器/卷/凭证清理通过。脚本SHA `a3a53f0695fbcd661d615c04343ac8d90493859c5604493c480952c8979a5ce3`，spec SHA `113e63235b34d97d9e98a012ff479d7971085bd6de838b9c06d62a42982bc722`；最终审查全部Addressed、无未解决问题。环境已清理，代码/分发物/提交证据可继续消费。

### `M14-T05任务卡指定的公开补充合同`

- **Artifact:** `docs/contracts/openapi-v1.yaml`；`docs/runbook/acceptance.md`；`docs/runbook/configuration.md`；`docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的5.6/5.7、12.2；`docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的7.1～7.4、10.4；fixture补充为 `docs/task-designs/M14-T02-design.md`、`control-plane/e2e/download-outcomes.spec.js` 和 `docs/verification/M14-T02-download-outcomes.md`。这些是任务卡直接消费的公开来源，不新增看板任务依赖。
- **Decision:** DownloadResponse恰八键、PageResponse恰九键，requestId头体一致；SUCCESS来源数大于0、EMPTY三计数0。写入数按不同业务键而非原始行数计算。真实Token仅经规定环境输入，130秒前端/120秒上游超时不改，正常SIGTERM观察最多150秒。fixture SUCCESS为1/1/0和公开七列行，EMPTY为0/0/0且原行/时间不变。
- **Rationale:** 公开接口和既有fixture闭环固定本轮结果判定、安全边界与通用适配补测，避免误将业务键去重、合法空数据或缺权限解释成产品成功。
- **Constraint:** 不运行旧15项全故障矩阵或故障DDL，不复制真实响应/行进入报告；fixture不冒充12个空接口的真实非空结果。`credentialConfigured=true`不证明权限。当前账户权限、频率/额度和 `M14_T05_CALL_INTERVAL_MS` 的实际值需要运行者在真实调用前确认，不能猜默认速率或以真实请求探测权限。
- **Usage:** 在新spec独立准备阶段重做fixture SUCCESS/EMPTY页面闭环；正常业务用例按OpenAPI核对请求/结果与页面；外部运行者创建 `M14_T05_ARTIFACT_DIR`、隔离CLI输出，并在npx完全退出后执行设计规定的终检/自动产物删除，原失败码不能被扫描成功覆盖。
- **Readiness evidence:** M14-T02既有证据记录原样同SHA验收JAR、15 passed、0 failed/skipped/retry；SUCCESS连续两次1/1/0与1/0/1、EMPTY0/0/0且完整行/时间不变，前端120、请求完成事件、秘密扫描与清理通过。本轮补测仍需实际执行；公开合同没有与M14-T04输入冲突。

两组输入的49身份、分发物、结果/计数语义、fixture隔离和页面操作一致。M14-T04零真实调用与M14-T05真实调用是明确任务边界；后继设计显式替换上游与凭证注入方式，不改变已完成前驱。没有未解决的输入冲突。

## Start Here

按顺序读取：

1. `docs/task-designs/M14-T05-design.md`，完整读取。
2. 本交接与 `docs/task-handoffs/tensor-v1-task-board.md` 的M14-T05行/详情。
3. `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的Global Constraints及Task M14-T05。
4. M14-T04设计、实际证据、公开spec；`docs/data-template/manifest.json`，只读manifest，不读模板data。
5. OpenAPI、验收/配置runbook、PRD/TRD指定章节与任务卡指定的T02 fixture公开合同。

首个实施动作：确认两实施目标不存在或没有重叠修改，按已完成设计创建 `control-plane/e2e/tushare-live.spec.js` 的49项注册、58样例顺序执行、接口级结果/计数与安全生命周期；先完成同函数纯本地拒绝探针、当前Playwright合成失败产物终检探针、语法和49项发现，不用真实Token制造失败。随后在权限/频率/额度确认与私有输入、新空schema准备完成后，从原JAR页面执行完整矩阵和CLI退出后终检，再写实际证据。首动作不补设计，不实现后续性能或发布任务。

## Risks

- 当前真实账户权限/积分、分钟/小时频率和剩余额度未实测；下一工作者必须取得运行者非秘密确认及规定私有Token输入，缺项保留环境阻塞，不skip或自动重试。
- 历史样例可能漂移；37ok/12empty按设计严格判定，不能替换日期或将异常改为成功。58是页面提交总数，没有出站捕获时不能宣称独立测量了58次上游调用。
- Playwright自动失败上下文可能晚于afterAll生成；必须使用本轮独占目录，并在CLI/worker完全退出后扫描/删除自动产物，不能只关闭截图就宣称安全。
- beforeAll600秒、afterAll330秒与各阶段上限已经固定；排空超时仍继续独立停机并保留双错误，不能被默认hook超时截断或停未知进程。
- 前序数据库与凭证已清理；新运行必须重建专用空环境。用户并行ISSUE-004文档/脚本和target产物不属于本任务，只提交精确两实施路径。
