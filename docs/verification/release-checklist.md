# Tensor v1 发布证据清单

归档日期：2026-09-07。任务：[M14-T08](../task-handoffs/tensor-v1/tensor-v1-task-board.md#m14-t08)；依据：[收窄设计](../task-designs/M14-T08-designs.md)和[进入交接](../task-handoffs/tensor-v1/M14-T08-handoff.md)。另见 [AC 与需求映射](ac-001-018.md)及[发布摘要](release-summary.md)。

## 范围与结论

本清单归档现有记录，文档交付与产品验收分别判断。**已有证据完成归档，当前候选版本尚未完成本轮整体验收。** 本任务不指定一个已验证的发布候选包。

以下产品命令、日期、计数和版本均摘自原记录，本次没有执行这些命令。只新增文档核对；没有构建、产品回归、安全复扫、真实接口请求或新环境首跑。不同提交、生产包、验收包及纯前端结果分别保留，不能相加形成同一候选包通过结论。未记录的字段统一写为“原记录未提供”；未测量不等于零。

## 证据索引

E01～E16 是本清单内的证据索引号，不是新增任务。身份表中的散列来自原记录；文档当前 SHA-256 单独列在本节末。时间沿用来源时区，带 Z 的时间为 UTC。

### E01：M14-T01 fixture 页面闭环

来源：[M14-T01-fixture-flow.md](M14-T01-fixture-flow.md)，2026-09-05；完整运行起止时间原记录未提供。原源码提交原记录未提供。

| 原命令／场景 | 原执行结果 | 当前处置／局限 |
|---|---|---|
| `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -Pacceptance clean verify`（原 PATH 选择 Node 24.15.0） | exit 0；前端 120、Surefire 368、生产包合同 4、验收归档合同 3 均通过。 | 历史同次构建的两类包，身份见下表；不是本轮构建。 |
| `cd control-plane && npx playwright test e2e/fixture-flow.spec.js`，生产包负向对照 | exit 1；第一项找不到 Fixture，后两项未执行。 | 预期负向对照，不改写为正向通过。 |
| 同命令，验收包最终正向 | exit 0；3 passed，15.7s，0 failed/skipped/retry。 | 页面 SUCCESS、EMPTY、同库禁用重启通过；不覆盖失败矩阵、真实 Tushare 或完整 AC。 |
| `cd control-plane && npm run test:unit -- --run` | exit 0；20 files / 120 tests。 | 历史前端回归。 |

正向页面两次下载 POST，首次 1/1/0、EMPTY 0/0/0；来源、精度、空值和时间展示有断言。最终六次迁移、50 张业务表、fixture 一行，禁用后 Tushare 摘要不变；自有 JVM 清理与原凭证扫描通过。本次不复制记录行或完整响应。

### E02：M14-T02 下载结果矩阵

来源：[M14-T02-download-outcomes.md](M14-T02-download-outcomes.md)。最终 Run9：2026-09-05 10:25:44Z～10:28:23Z；源码提交原记录未提供；验收包与 E01 相同。

| 原命令／场景 | 原执行结果 | 当前处置／局限 |
|---|---|---|
| `cd control-plane && npx playwright test e2e/download-outcomes.spec.js` | exit 0；15 passed，2.7m，0 failed/skipped/retry。 | 合成回环上游、fixture 故障注入；不是实际无效 Token 的外部鉴权验证。 |
| `cd control-plane && node --check e2e/download-outcomes.spec.js`；同 spec 的 Playwright `--list` | 均 exit 0；发现 15 项。 | 语法／发现结果不能代替页面矩阵。 |
| `cd control-plane && npm run test:unit -- --run` | exit 0；20 files / 120 tests。 | 历史回归。 |

两项客户端非法参数均 0 POST／0 stub。完整矩阵为 14 次页面下载、17 次页面查询、8 次 stub 调用，31 个请求各一条完成事件。涵盖 SUCCESS、EMPTY、鉴权／权限／限流／服务／网络／超时／解析／类型失败及固定 AFTER UPDATE 触发器回滚。重复 fixture 下载第二次 1/0/1、仍一行且入库时间更新；失败后原完整行不变。超时样例实际 120089ms 是单次下载记录，不是查询 P95。Run8 的 2 passed / 1 failed / 12 did not run 等历史失败保留在原文；仅引用最终 Run9 为本索引正向结果。

### E03：M14-T03 查询、分页与宽表

来源：[M14-T03-dataset-query.md](M14-T03-dataset-query.md)。最终 Run16：2026-09-05T15:35:38.728Z～15:36:27.987Z；源码提交原记录未提供；验收包同 E01。

| 原命令／场景 | 原执行结果 | 当前处置／局限 |
|---|---|---|
| `cd control-plane && npx playwright test e2e/dataset-query.spec.js` | Run16 exit 0；11 passed，49.6s。 | Run15 的 50.7s 及更早成功／失败轮均为历史，不混作最终轮。 |
| `cd control-plane && node --check e2e/dataset-query.spec.js`；同 spec 的 Playwright `--list` | exit 0；11 项。 | 原命令表记录，不冒充新检查。 |
| `cd control-plane && npm run test:unit -- --run` | exit 0；20 files / 120 tests，5.05s。 | 原文明确运行于 Run11 后，不是 Run16 同轮单测。 |

最终 7 次页面下载、7 次替身调用、40 次 records 请求（39 响应＋1 次预登记浏览器中断），46 个服务端响应请求各一完成事件。验证 AND／单边日期、无条件查询、默认 50 与 20/50/100、越界归一化、重置与竞态、网络重试、键盘。独立数据库为 daily 126、company 1、index 1、balancesheet 1、disclosure 123。宽表 152 业务列＋3 来源列、固定列、横滚、精度、纯文本 tooltip、空／null／零有页面证据。全部业务数据来自合成上游；真实 balancesheet 非空和查询性能未由此证明。

### E04／E05：M14-T04 的两种 49 覆盖

来源：[M14-T04-49-contracts.md](M14-T04-49-contracts.md)。实施基线 `707215c9495b2652617092c5bd21e337033799bb` 与最终自动门禁输入 `1a2362434c1243dec418cc6e338361831b5d6f9b` 分开保留。

| 索引／原命令 | 原时间与结果 | 当前处置／局限 |
|---|---|---|
| E04：`scripts/verify-49-contracts.sh` | 2026-09-05T18:32:22.639323Z～18:32:54.525438Z；exit 0，metadata 50／schema 52／package 4，均 0 failures/errors/skipped；同生命周期前端 120 项。 | 49/49 自动契约、同名 YAML 与生产包逐字节一致。49 生产表＋fixture 共 50 表、1007 列、50 主键是该旧版结果，不能改为 V7 后的 1008 列。 |
| E05：`cd control-plane && npx playwright test e2e/tushare-metadata.spec.js` | Run7，2026-09-05T18:01:21.664Z～18:05:49.534Z；exit 0，49 passed、0 failed/skipped/retry。 | 使用旧验收包；49 API／49 dataset、43 必填拦截、6 无参数，245 metadata GET。下载 POST、records GET、上游调用均 0；不是 49 真实下载。 |

原 `sh -n`、Node 语法和 Playwright 49 项发现均 exit 0。七组 11/7/6/6/9/3/7 沿用原报告已批准兼容决定；PRD 八分类的原文不改写。页面轮六次迁移、50 张空业务表；13 张截图的原人工审阅与清理结果属于该原版本。

### E06：M14-T09 固定 40 接口同轮真实验收

来源：[M14-T09-tushare-live-rerun-02.md](M14-T09-tushare-live-rerun-02.md)。D-03 最终轮，2026-09-06T12:30:17.396Z～12:33:07.370Z；控制器结果追加时刻 12:33:08.990446+00:00。原命令 `cd control-plane && npx playwright test e2e/tushare-live.spec.js --workers=1`，npx／最终 exit 均 0，控制器耗时 173 秒。

**40 通过 / 0 失败 / 0 未运行**，48 次真实下载 POST、80 次真实 records 查询，fixture 另计 2 次下载／3 次查询；133 个唯一请求各一完成事件的核对见看板 M14-T09 完成证据。原分类 28 ok / 12 empty，批准后当轮分类 31 ok / 9 empty；不把分类变化外推为未来稳定数据量。

daily 当轮 SUCCESS 5535/5535/0，末查与独立数据库总数 5535；dividend 38、top10_holders 320、top10_floatholders 280。初始七次迁移、50 表全空，最终全部选定页面计数与独立数据库相符，九张排除表仍空、fixture 一行。原文件记录扫描 4 文件、删除 1 自动产物、worker/JVM/自有容器卷与私密输入清理通过。原 JSON 中 `sourceTask=M14-T05` 是工具来源，`task=M14-T09` 是实际任务归属。

当前适用性：只支持当轮固定 40 接口；九项未覆盖见延期表，未计为成功、失败、EMPTY 或 skip。真实 balancesheet 等 empty 项没有因此取得非空写入证据。D-01／D-02 失败轮仅留在来源中，未拼接通过数。

### E07～E11：原安全门禁与后续生产包专项

下列 `sh scripts/security/verify-release.sh` 均为原命令摘录。各 ISSUE 使用其记录的独立验证副本、新 JAR 身份和回环 18080；不等同于现在执行主仓库脚本。环境前缀和副本差异见各来源，本清单不复制私有配置。

| 索引／来源 | 原时间（UTC） | 原命令与执行结果 | 当前处置／局限 |
|---|---|---|---|
| E07：[M14-T07](M14-T07-security.md) | 2026-09-06 16:07:48.278549～16:15:20.947727 | `sh scripts/security/verify-release.sh`；exit 1；18 pass / 6 fail / 0 not-run。 | 六项失败为 Maven、backend_audit、S01、S05、S06、log_scan。M14-T07 的 COMPLETED 是用户决定的管理收尾，技术原失败不变。 |
| E08：[ISSUE-010](ISSUE-010-health.md) | 2026-09-07 02:27:57.348448～02:29:07.076917 | 副本同入口；exit 1；20 pass / 4 fail / 0 not-run；S01 11/11。 | health 问题已解决；S05、S06、log_scan、backend_audit 仍失败。不是全安全通过。 |
| E09：[ISSUE-011](ISSUE-011-dataset-method-status.md) | 2026-09-07 02:51:21.402333～02:51:46.248111 | 副本同入口；exit 0；限定 19 项通过、0 失败；未运行计数原记录未提供；S05 12/12。 | 三路径×四方法均 405、Allow GET、空体；每次业务指纹／stub 不变。未运行 Maven、两类依赖审计、S06/S07。 |
| E10：[ISSUE-012](ISSUE-012-query-extra-parameters.md) | 2026-09-07 03:16:19.557055～03:16:44.476725 | 副本同入口；exit 0；限定 20 pass / 0 fail / 0 not-run；S06 6/6。 | 六个任意字段参数拒绝、合法末查通过；未运行 Maven、依赖审计、S05/S07；六次拒绝事件当轮不纳入通过。 |
| E11：[ISSUE-013](ISSUE-013-query-completion-events.md) | 2026-09-07 04:09:10.599702～04:09:37.446217 | 副本同入口；exit 0；限定 21 pass / 0 fail / 0 not-run。 | 同一专项包 S01～S08 与日志通过，15 请求各 1 事件；10 个参数拒绝为 failure/parameter/PARAM_INVALID。未纳入安全 Maven 与前后端漏洞审计。 |

E07 前端依赖审计 exit 0，196 项覆盖、零告警；S02/03/04/07/08、页面合成下载与鉴权失败、安全头、凭证扫描和清理按原报告通过。它不证明后端审计通过；四个查询虽返回正确 400，当轮仍缺完成事件。E08～E11 的扫描、指纹与清理仅归属于各自原包。所有回环实验均未独立抓取 JVM 全部外部网络流量，外部部署访问控制/TLS 不在已实测范围。

专项自动回归也分轮保留，日期均为 2026-09-07，具体时刻原记录未提供：

| 来源 | 原命令／范围 | 原结果与局限 |
|---|---|---|
| E08 | `mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 verify`；定向 test 参数见来源 | 构建成功：后端 385、生产合同 4、前端 170；定向 47 通过；自检 309。原文未单列各命令数值退出码。 |
| E09 | 同构建命令；定向 test 参数见来源 | 构建 exit 0：后端 397、生产合同 4、前端 170；定向 77 通过；自检 309。定向／自检数值退出码原记录未提供。 |
| E10 | 同构建命令；定向 test 参数见来源 | 构建 exit 0：397/4/170；定向 111 通过；自检 311。定向／自检数值退出码原记录未提供。 |
| E11 | 同构建命令；定向 test 参数见来源 | 构建 exit 0：397/4/170；定向 156 通过（Controller 45）；18 组绑定探针一致；自检 327。定向／自检数值退出码原记录未提供。 |

上述完整 `verify` 默认不包含全部 `*IT`，不能把单测总数写成全数据库集成回归；相关真实 MySQL 验证依来源的定向和黑盒记录。原 RED、环境预检和验收器失败均保留在来源，不从成功计数中删除历史事实。

### E12／E13：ISSUE-014 Maven 专项两轮

来源：[ISSUE-014-security-maven-verification.md](ISSUE-014-security-maven-verification.md)。原命令如下，只作引用：

```sh
mvn -B -ntp -f data-plane/pom.xml -pl tensor-app -am \
  -Dmaven.repo.local="$M14_MAVEN_REPO" \
  -Dtest=ModuleDependencyTest,ForbiddenGitCapabilityTest,ObservabilityTest,ProductionWebConfigurationTest,QuerySqlFactoryTest,UpsertSqlFactoryTest,DatasetControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

| 索引／输入 | 原时间（UTC） | 原结果 | 当前处置／局限 |
|---|---|---|---|
| E12：HEAD `221bf6189096f6e66a4abaa0cb4d7d063ed0e716`，617 文件 | 2026-09-07 04:42:21.906565～04:42:53.551249 | exit 0，七类 81 项，六模块 Enforcer；前端 170 及构建通过。 | 原 HEAD 单独通过；不使用另一轮补数。 |
| E13：同基线的工作区快照，623 文件，含已有修复 | 2026-09-07 04:38:18.884501～04:39:06.934516 | exit 0，七类 118 项，六模块 Enforcer；前端 170 及构建通过。 | 工作区内容清单识别版本；独立快照提交／JAR 身份原记录未提供。 |

两轮 failures/errors/skipped 均 0，七份 XML 分别核对且 Enforcer 实际执行。ISSUE-014 已解决；只关闭 Maven 缺口，不代表一次整体安全或新发布包验收。

### E14：ISSUE-015 后端依赖风险

来源：[专项报告](ISSUE-015-backend-dependency-audit.md)及[用户关闭决定](../issues/problems/ISSUE-015-backend-dependency-audit.md)。最终扫描 2026-09-07 06:40:46～06:40:59 UTC，报告生成于 06:40:58.649709Z；基线 `221bf6189096f6e66a4abaa0cb4d7d063ed0e716`，扫描 E07 冻结生产包。

原记录给出的执行入口为仓库脚本的 `scan_jar`、`backend_audit`、`dependency_verdict`；**完整 Maven 扫描命令原记录未提供**，只指向原私有目录。记录的扫描器为 Dependency-Check Maven 13.0.0，全部 reactor/test/provided/runtime 与冻结包 scanDirectory，autoUpdate=true、failOnError=true、failBuildOnCVSS=7；无 key 路径使用官方 NVD JSON 2.0 feed。原 Maven **exit 1**，判定 `dependency-audit-failed`。

报告覆盖 93 个顶层依赖、六 reactor、冻结包第三方 JAR 50/50；90 条发现／61 个不同 CVE。高阈值为 **35 条／27 个不同 CVE**，包含 HIGH 14、CRITICAL 18 及三条 MEDIUM 标签但 CVSS≥7。JSON analysisExceptions=0，日志仍有四类分析缺口；身份覆盖不等于分析完整。原 `sh -n scripts/security/verify-release.sh` 通过；`sh scripts/security/verify-release.sh --self-test` 318 项通过，数值退出码原记录未提供。

用户于 2026-09-07 接受上述已知风险并关闭 ISSUE-015，处置为“不需要处理”；没有升级依赖、添加 suppression 或把扫描改判通过，接受范围不自动延伸至新产物。四类缺口为：

- `assembly-analyzer-unavailable`：缺 dotnet；原后续核对五 DLL 为测试依赖内非 CLR 文件且不在生产包，不能推断生产需要 .NET。
- `node-lockfile-missing`：WireMock 内 Swagger UI 缺 lockfile，可能漏检。
- `node-modules-missing`：同一包缺 node_modules，RetireJS 不能替代完整 Node 分析。
- `oss-index-credentials-missing`：无 Sonatype 凭证，扫描器默认停用该补充数据源。

### E15：ISSUE-004 前端验收

来源：[ISSUE-004-ui-redesign.md](ISSUE-004-ui-redesign.md)，2026-09-07；完整起止时间原记录未提供。T06 实现 `daa7b64`、补充断言 `83530b0`；无 JAR 身份（纯前端，原记录未提供）。

在 `control-plane`：`npm test` exit 0，24 files / 170 tests；`npm run build` exit 0；`npx playwright test --config=playwright.ui.config.js --project=chromium --workers=1 --retries=0` 修正轮 exit 0，60/60，2.0 分钟，无重试/跳过；`--list` 发现 60 项，exit 0。49 元数据场景＋11 组合场景、五视口、四主题、键盘、宽表、日期与状态恢复有证据。最终审查 approved / ship；八张仓库截图链接见来源。

API 为符合 DTO 的 route stub，既有客户端／业务 composable／后端保护源未改；不证明与本轮打包后端和真实上游集成。主 chunk 超过 500kB 的原提示保留；测试 YAML 解析器 Minor 由 ISSUE-016 单独跟踪。

### E16：M13-T04 首跑历史记录

来源：[权威看板 M13-T04 的 Completion evidence](../task-handoffs/tensor-v1/tensor-v1-task-board.md#m13-t04)；[原进入交接](../task-handoffs/tensor-v1/M13-T04-handoff.md)只说明进入时条件，不是该任务完成证据。runbook 是运行说明，不能代替执行结果。

2026-09-05，实施提交 `59acec3`。看板记录 14:00:04（Asia/Shanghai）执行 `mvn -f data-plane/pom.xml clean verify`，exit 0，前端 120／后端 368／JAR 合同 4；107/107 临时 HTTP 黑盒矩阵通过（完整临时命令原记录未提供）。分发目录以 `java -jar tensor-app-1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080` 启动，`sh scripts/smoke-test.sh http://127.0.0.1:8080` 四 GET 通过；**smoke 数值退出码、启动完整起止时间、原 JAR SHA-256 原记录未提供**。

该轮 Java 21.0.11、MySQL 8.4.6，V1～V5 五迁移、49 业务表；无 Token 首跑、SIGTERM 后同库重启、两页 Chrome 直接打开／刷新与实际 Vue 渲染通过。Token 哨兵仅验证配置状态，未下载／请求上游。只支持该旧版首跑场景；当前 runbook 已记 V7/六权限和 health 修复，不能将现行说明倒写为当时已验证。AC-018 仍只部分支持。

### 原源码、产物与报告身份

下表完整散列均为**原记录中的身份**；不是本次重新测量 JAR、报告或代码得到的值。未提供源码提交的 E01～E03、未提供 JAR 的 E12/E13/E15/E16 已在相应条目披露。共同 manifest SHA-256 为 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。

| 索引 | 原源码／脚本身份 | 原 JAR 类型与 SHA-256 | 原报告／清单 SHA-256 |
|---|---|---|---|
| E01 | 源码提交：原记录未提供 | 生产 `a9dc150a2e411d6479429091ab928f45f4b5159e938a9cec97b72288b062bc08`；验收 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac` | fixture flow JSON `5d9082000ff0f8b3569a7682f2c60c1e73a827776b2036771bd00fa43e3278be` |
| E02 | 源码提交：原记录未提供 | 验收，同 E01 | Run9 JSON `4c3d6e574b562aa0441d7752e34aeed891a6b8de04a6a2c69e8eab1f4b3ab22f` |
| E03 | Run16 spec `dbec488cba6a69bf852061855a9fa87182d4097883362c1940b228ba240fdfbd` | 验收，同 E01 | Run16 JSON `8e3f75669136d26e13b64862fa435f6364344bdb90d558d13dbc30119da11924` |
| E04 | 源码 `1a2362434c1243dec418cc6e338361831b5d6f9b`；脚本 `a3a53f0695fbcd661d615c04343ac8d90493859c5604493c480952c8979a5ce3` | 生产 `2487993be08fb17462c7ddfec528d0a6aa91b6a31c9fe41d86c79560bc9f90b7` | verification JSON `06c9698df9bb457691c786a19aa4e9096c29d771ff291eb59b0749740c91c4a2` |
| E05 | spec `113e63235b34d97d9e98a012ff479d7971085bd6de838b9c06d62a42982bc722` | 验收，同 E01 | Run7 JSON `c76855246a74445d8b097b232ad3bbdece9ee446c0521d0b7350f3806150c783` |
| E06 | 运行源码 `07ec79724e63a08a1867e06d2fe75485719e0f7a`；spec `05a6601f656dcfb5528c6ed832ccb17ef2416cfa84397fb93f11da1bfbd5901e` | 验收 `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002` | 最终证据提交 `14e038e`、原整篇文档 `5dd888c38608b058b15c89d9f3a29ce82ab1aeddd4e197eaa7ee66cafd6bf770`（看板／ISSUE-008） |
| E07 | 源码 `741376b605dc37ca9075b17abe98cf254230d6ad`；脚本 `41f0d54f071bf545e2e59a28b532d8c335ef6a53a86639be10a6728357d9afec` | 生产 `acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef` | 原报告文档 `5ff4a5d5005f1ee3ab558867e1cb3e8adb38f3a6bc7d06ae4fc60a6d927cd83b`，证据提交 `67b1be6`（看板） |
| E08 | 构建 `8adb96ea481f9fd90d4438198ee9478b95efd312`；验证副本 `d393a32f19e5304e6a6fc6f476a861f859cffce5`；脚本 `49bb3beacbd8f5298ca8c251d96d305dd4feb1f133a61bce8cd525753a39113a` | 生产 `6ed11c7dad6900cbad70f6af5244e34dbe5cd5527434bb35f447e2dd7c023f7f` | outcome JSON `4936f247f7948e358e6a5d8092209e9f54a024f125201e5d0dc26e41b4d214d7` |
| E09 | 构建 `c9332416fd7ff1fa68a4d28fa26d3340691739ab`；副本 `b037ea27b35a5f9147c5399f9b733ad9fa885dcd`；脚本 `798d6b74e18381692e4dc9342234570ddf505d3ed4c5095ffbcb74aaae63f4c4` | 生产 `4f5797ae5b3b4cb7fc3ba4ea9cee413083d356b848a9b689d3b691743b34d120` | outcome JSON `1e072ab551bbe198b833b7edc76aa2fe6d830035b5a8b89f58acfe83efdad22b` |
| E10 | 构建 `e1982b836c7c5ab5066d1f83cff0f31439870655`；副本 `ff2366eded79426f78840ae34bd81cf1417121a8`；脚本 `a6638545021a2ea6ae147f14616c94af0d1af614aacef9f0d92c5596ec89f33a` | 生产 `61131bab34adf06501c1480841b971f7cafb619186014d04aff1022289b9bb7d` | outcome JSON `c2b3f93dff0d59f149509d7fc9c36f7eb4a4a42161cbdb9c5810fdf3c70a3a5e` |
| E11 | 构建 `38d242c27b6437150f7eda76ff15be323f6883b2`；副本 `1a36abdda99f35ba039a76666e59100877a62084`；脚本 `817e0141613d18554efc86637499e98dfb243cf076135a6f8eb67362131291ef` | 生产 `9b4e393997488b431b53250fe49af9f84bd2eb057e9f8a5455554a22c2111ed8` | outcome JSON `851f48055353a6893b3639ee11c95063dbd6eda562aa0d04ae340149356bf51d` |
| E12 | HEAD `221bf6189096f6e66a4abaa0cb4d7d063ed0e716` | 原记录未提供 | 源码清单 `0af707a5c522ab1b193291f61ee21f9a94c111c1333983e12e70d32fdff36e8c`；日志 `43293554a7e7e631a9477da12a5ea353a42bf681c27b668d898fe0046c7c01a1` |
| E13 | 同基线工作区；独立提交原记录未提供 | 原记录未提供 | 源码清单 `da8362c895ca387e7173ea459310d7965f0d340ca7c854b5125482d8d3e289f0`；日志 `125216f8aa5ec9154ee1b80c59d1f01dcbf5ee20e3b2c4f4a63bfdea865602f5` |
| E14 | HEAD `221bf6189096f6e66a4abaa0cb4d7d063ed0e716`；最终脚本 `87b30570dbe70768f649c77c999025126666cf2805ef9152f58308c72e898ee8` | 生产，同 E07 | 依赖报告 `227e42e4bfa5bde94ac2443fc3b26192a9ec387a91fa64befec9cbd9597230d4`；outcome `cb72c4cb6b65fc57dd39bbd511ed0795463f2cd2f447cff802074c4e55c11ceb` |
| E15 | T06 `daa7b64`／`83530b0` | 原记录未提供（纯前端） | 独立机器报告散列原记录未提供；仓库八图见来源 |
| E16 | 实施 `59acec3`（不是已声明的 JAR 构建提交） | 原记录未提供 | 独立报告散列原记录未提供；结果来源为看板完成记录 |

### 引用文档当前 SHA-256

下表是 2026-09-07 对工作区文档字节的本次只读测量，包含原有已暂存／未暂存记录；不使用 `git archive HEAD` 代替工作区内容。看板散列在本任务状态收尾后更新。它只标识此次阅读的文字，不把文字引用变成产品复验。

归档时部分设计、交接、专项证据及问题处置仍是工作区已有未提交内容。本任务的证据提交只包含三份新增交付文档，未混入这些既有修改；单独检出该提交不能取得全部引用版本。完整追踪须保留本表路径对应的工作区文档及其散列，不能用旧 HEAD 内容替代。

| 文档（当前读取版本） | 当前文档 SHA-256 |
|---|---|
| [docs/task-designs/M14-T08-designs.md](../task-designs/M14-T08-designs.md) | `c25b637f0d9cbdb1deaf0aaa1d40321523d550b16ca73c1e3eb4c4f3fbb6dcd5` |
| [docs/task-handoffs/tensor-v1/M14-T08-handoff.md](../task-handoffs/tensor-v1/M14-T08-handoff.md) | `66e16030a8d20ea9da840d9b1d89c43c0d0db43f8ec3aaec006181d2fb8a4212` |
| [docs/task-handoffs/tensor-v1/tensor-v1-task-board.md](../task-handoffs/tensor-v1/tensor-v1-task-board.md) | `f3e979088acfa5fdbff49098c9c011bc532c55d2be32c932713dc843ba860857` |
| [docs/superpowers/plans/tensor-modules/M14-integration-release.md](../superpowers/plans/tensor-modules/M14-integration-release.md) | `71d58c7ab33076678b2ed9d75621fe113d1f06185cd67176a081e1d46d603c63` |
| [docs/design/Tensor_多源证券数据平台_PRD_v1.0.md](../design/Tensor_多源证券数据平台_PRD_v1.0.md) | `2597510a0ac815d487e0645fbf6519851021a77803c36d51f321a17b76416a2e` |
| [docs/traceability/tensor-v1-requirements.md](../traceability/tensor-v1-requirements.md) | `6b824eebf5c9f7e117e728f071b369456b592916e6eaab817d6f5690b15ef188` |
| [docs/verification/M14-T01-fixture-flow.md](M14-T01-fixture-flow.md) | `35f35ed2076135d518aed8f7f40adc31562214b1bb0a69eab618a63638a71ad6` |
| [docs/verification/M14-T02-download-outcomes.md](M14-T02-download-outcomes.md) | `d5786b21eaf0a92d3747ca32a8e4b8b9cff6fe4a3af0a96b769f7d1260e1c862` |
| [docs/verification/M14-T03-dataset-query.md](M14-T03-dataset-query.md) | `811cb9cef376f48263460ca322a4e50fe0c10bf343f519546690d34708b9050f` |
| [docs/verification/M14-T04-49-contracts.md](M14-T04-49-contracts.md) | `ba8861e4fa534c98e307c7e527a840130c9f88fe6befe6b36d0505499e224f45` |
| [docs/verification/M14-T09-tushare-live-rerun-02.md](M14-T09-tushare-live-rerun-02.md) | `5dd888c38608b058b15c89d9f3a29ce82ab1aeddd4e197eaa7ee66cafd6bf770` |
| [docs/verification/M14-T07-security.md](M14-T07-security.md) | `5ff4a5d5005f1ee3ab558867e1cb3e8adb38f3a6bc7d06ae4fc60a6d927cd83b` |
| [docs/verification/ISSUE-010-health.md](ISSUE-010-health.md) | `de9d6649bc90920ffe0d4e6ed90a1e62e10969980dd4f5ee3f85f4d4fe77631c` |
| [docs/verification/ISSUE-011-dataset-method-status.md](ISSUE-011-dataset-method-status.md) | `23193d09a158c2af5e4626a8cdce56cb2bf38434d9f4bef8c801798cb646cf1c` |
| [docs/verification/ISSUE-012-query-extra-parameters.md](ISSUE-012-query-extra-parameters.md) | `43419d2359b51af035403df22d16444f03df60620d8a77b56d7e4b4e9bb04a82` |
| [docs/verification/ISSUE-013-query-completion-events.md](ISSUE-013-query-completion-events.md) | `5556add7386e9fa1ce7523b811dd704081cf229da6e42fe3fb57b679a7d6aabc` |
| [docs/verification/ISSUE-014-security-maven-verification.md](ISSUE-014-security-maven-verification.md) | `95f51f43f6579e4dd8ead86d5d42ae3dfb319f4d4123f893e7a97e83b97d934b` |
| [docs/verification/ISSUE-015-backend-dependency-audit.md](ISSUE-015-backend-dependency-audit.md) | `288c819389e71eedf6532401d99dfbc3d2f7b92f3dde777c4dca53a8dcc3d049` |
| [docs/verification/ISSUE-004-ui-redesign.md](ISSUE-004-ui-redesign.md) | `db751afd17f8a2dff192e1aec6a580247384cae957b252b886960ab93af965a7` |
| [docs/task-handoffs/tensor-v1/M13-T04-handoff.md](../task-handoffs/tensor-v1/M13-T04-handoff.md) | `f9b42dc66b5af54d4002b7432f6e030e85b6c1971381b3f1e962aa6f717b39c5` |
| [docs/runbook/first-run.md](../runbook/first-run.md) | `fd16613f36742a102afbde7fdb819e62e9e5437d083bf9b51332c0d2f4dd7e1e` |
| [docs/runbook/configuration.md](../runbook/configuration.md) | `3d5bca9ca09e1d353020c0503ac45ff2890c3547a33d8323c1dea850b8e92595` |
| [docs/runbook/acceptance.md](../runbook/acceptance.md) | `0877defd90fe32d400724d220f944403627ee0789fbf688be84d4ca5079ce2ff` |
| [docs/issues/README.md](../issues/README.md) | `02f0b48c225cc77a159a8a3e870d034ea354b67d3609e1ebe52cd705ce9b9526` |
| [docs/issues/problems/ISSUE-008-tushare-live-coverage-gap.md](../issues/problems/ISSUE-008-tushare-live-coverage-gap.md) | `53cd55c39e63d8525e51ba171c63602b3c32e123b6a359f9aab15a15a99e92ce` |
| [docs/issues/problems/ISSUE-009-query-performance-verification.md](../issues/problems/ISSUE-009-query-performance-verification.md) | `89bbb14106e5fe35652a706ab4997793180dbbb8189ff20cbd32aa8bdcb31811` |
| [docs/issues/problems/ISSUE-015-backend-dependency-audit.md](../issues/problems/ISSUE-015-backend-dependency-audit.md) | `6613fa17e0fc1e4829dab31bb4e63077e72e9b3b2cb165d8332e95bf43ed9c44` |
| [docs/issues/problems/ISSUE-016-ui-fixture-yaml-validation.md](../issues/problems/ISSUE-016-ui-fixture-yaml-validation.md) | `aedb5483f0f696a097eb7bd3d6217d936b7b96cf7e1bbe9c5306e7783a6e9bf1` |
| [docs/issues/problems/ISSUE-001-method-input-aggregation.md](../issues/problems/ISSUE-001-method-input-aggregation.md) | `6f6888e3465f792f2d9b3bfc9f3ef45704ce52d96bfb653bf3d9dcfb64516c74` |
| [docs/issues/problems/ISSUE-002-controller-business-logic-layering.md](../issues/problems/ISSUE-002-controller-business-logic-layering.md) | `43a753f03c0cf05d969fb0288458ced2eb377228f5235e5062280bb94cb9cfad` |
| [docs/issues/problems/ISSUE-003-database-access-complexity.md](../issues/problems/ISSUE-003-database-access-complexity.md) | `0ca8363892dd1a9ee9ca0f839b95a9b20ee22d6f173b5e8f98f202d06e109ef4` |

私有报告、截图和临时分发物不随本清单提交。本次只用文件存在性检查，确认 E01 两份最终日志、E02 Run9 JSON、E03 Run16 JSON、E04/E05 JSON、E06 原验收 JAR、E08～E11 outcome、E12/E13 verified-summary 及 E14 依赖报告在记录路径可定位；**没有打开私有日志、报告、凭证或重算其散列**。E03 原文仅给出 Run16 临时目录后缀，本次在原系统临时根目录下定位到对应 evidence.json；只核对存在性。E07/E08 的完整机器投影已内嵌于仓库文档；其他条目消费其脱敏摘要，不据此声称已经重新审计私有原件。

## 门禁汇总

| 目标 | 原执行结果 | 当前处置／适用性 |
|---|---|---|
| 49 数据集自动契约 | E04 metadata 50／schema 52／package 4，exit 0 | 原生产包自动覆盖；本次未复验。 |
| 49 元数据页面 | E05 49/49；E15 49 项 route 场景 | 两版本与测试层次分列，不能作为真实下载。 |
| 固定 40 真实接口 | E06 40/0/0，48/80，fixture 2/3 | 已归档原同轮结果；本次未复验。 |
| 原 49 真实目标 | 尚缺九接口 | ISSUE-008 不依赖，用户后续单独处理；M14-T05 保留 BLOCKED。 |
| 功能 AC／P0/P1 | 见 18/31/6 映射中的逐项覆盖 | 未提供本轮同版整体验收；内联要求不由 AC 自动推定满足。 |
| 性能 | 无规定场景 P95、300ms 实测 | ISSUE-009 不依赖，用户后续单独处理；M14-T06 管理完成不代表实测。 |
| 安全原门禁 | E07 exit 1，18/6/0；E08 exit 1，20/4/0 | 保留失败；E09～E13 各专项独立通过，不能合为新一次整体通过。 |
| 依赖漏洞 | E14 exit 1，35 高阈值／27 CVE＋四分析缺口 | ISSUE-015 原已知风险接受，非扫描通过，非新包豁免。 |
| 单 JAR／新环境 | E01/E04/E08～E11 原包合同；E16 原首跑 | 原记录各自有效；当前候选包未冻结／重建／新环境整体验收。 |
| 本任务文档 | 三份文档及下面的文档核对 | 文档完整准确即可完成 M14-T08，不改变技术发布结论。 |

## 延期与风险

ISSUE-008／009 均保持问题索引的“新增／未解决”，按用户 2026-09-07 决定不依赖，不作为 M14-T08 或本轮发布前置条件；不表示原目标通过。

| ISSUE-008 九接口 | 原排除依据（本次未核验权限） | 当前处置 |
|---|---|---|
| `top_inst` | 原接口页 5000 积分与总表有差异，用户此前排除 | 不依赖，用户后续单独处理 |
| `broker_recommend` | 原接口页 6000 积分与总表有差异，用户此前排除 | 不依赖，用户后续单独处理 |
| `share_float` | 原接口页 120 与总表 3000 矛盾 | 不依赖，用户后续单独处理 |
| `hs_const` | 原官方文档不存在，权限待确认 | 不依赖，用户后续单独处理 |
| `moneyflow_hsgt` | 同上 | 不依赖，用户后续单独处理 |
| `hk_hold` | 同上 | 不依赖，用户后续单独处理 |
| `index_member` | 同上 | 不依赖，用户后续单独处理 |
| `hsgt_top10` | 原已读说明未明确最低积分 | 不依赖，用户后续单独处理 |
| `namechange` | 原已读说明未明确最低积分 | 不依赖，用户后续单独处理 |

ISSUE-009 保留 daily／152 列 balancesheet 的冷／热查询、重复 Upsert、50 条 P95≤2 秒、300ms 加载反馈、索引／EXPLAIN 和资源指标缺口。曾建议的数据量没有获批，不作为测试合同或实测值。本任务不新建性能报告。

ISSUE-015 的风险接受只覆盖 E14 原报告已知项，不能覆盖未分析的任意漏洞或新包；四类缺口和原 exit 1 保留。其他问题沿用[问题索引](../issues/README.md) 2026-09-07 状态：

| 问题 | 原当前状态 | 已记录影响／处置 |
|---|---|---|
| [ISSUE-001](../issues/problems/ISSUE-001-method-input-aggregation.md) | 新增；Download 方案已确认，待正式设计 | Controller 参数关系分散、裸动态 Map 缺少类型语义；未形成重构验收。 |
| [ISSUE-002](../issues/problems/ISSUE-002-controller-business-logic-layering.md) | 新增；分层方案已确认，待实施计划 | Controller 业务编排与 Web DTO 耦合；方案不是已实现状态，不能将拟调整日志／指标行为视为当前证据。 |
| [ISSUE-003](../issues/problems/ISSUE-003-database-access-complexity.md) | 新增；架构已确认，待文档复核 | 动态 JDBC/SQL/绑定重复的维护复杂度，未完成重构及回归。 |
| ISSUE-004／005／006／007 | 已解决／已解决／已解决／已关闭 | 沿用索引；本任务未重新修复或扩充其验收范围。 |
| ISSUE-010～014 | 已解决 | 专项证据见 E08～E13；不等于整体安全通过。 |
| [ISSUE-016](../issues/problems/ISSUE-016-ui-fixture-yaml-validation.md) | 新增；待解决方案 | 仅 UI 测试解析器，未知／重复 YAML 键可能使未来 fixture 失真；现有 49 输入已核对，原评审为非阻塞 Minor。 |

## 文档核对

2026-09-07 本次只读文档检查的实际结果如下。临时 Python 检查器只读取三份交付文档、PRD／追踪索引、上述来源和会话开始时的文件／Git 索引基线；不会执行文档中的产品命令。检查器位于本机 `/tmp/m14-t08-check-docs.py`，未作为产品测试工具加入仓库。

| 实际命令／核对 | 实际结果 |
|---|---|
| `python3 /tmp/m14-t08-check-docs.py`：PRD 原文和追踪表逐单元格比较 | exit 0；18 项 AC、31 项 PRD-F、六类 NFR 的完整编号、原描述／验收／优先级／直接部分内联语义匹配；全部标记本次未复验；AC-018／PRD-F-031 均为部分支持。 |
| 同命令：仓库路径及标题锚点、交付物互链 | 224 个本地链接／锚点通过，三份文档两两相互链接。 |
| 同命令：来源身份 | 31 份来源文档的当前 SHA-256 匹配；引用的原提交之外的 64 位身份散列均可在原来源文本定位，且与当前文档散列分栏。 |
| 同命令：原报告及范围断言 | 原 JSON 的 40/0/0、48/80、fixture 2/3 和安全 18/6/0、exit 1 核对通过；九项排除顺序精确一致；35／27 与四类分析缺口、未复验／发布边界保留。 |
| 同命令：安全摘要和变更范围 | 未新增 JSON 完整响应／SQL 代码块、凭证赋值或 JDBC 连接字符串；人工逐项核对只含原有脱敏摘要、要求和引用。会话入口 627 个跟踪文件中，除本任务看板外其余 626 个内容散列不变；看板差异限于 M14-T08 行与详情。 |
| `git diff --check`；`git diff --cached --check` | 均 exit 0；没有空白错误。 |
| `git add -- docs/verification/release-checklist.md docs/verification/ac-001-018.md docs/verification/release-summary.md`；临时 Python 比较 `git ls-files --stage -z` 与入口索引 | exit 0；精确新增三份跟踪文档，原有全部索引条目保持不变。首次沙箱写 index.lock 被拒，获准同一限定命令后成功；未使用 reset/clean。 |

本次检查不读取秘密值，因此结构与人工内容检查不能冒充在真实秘密集合下的新安全扫描；产品安全扫描结论仅来自 E01～E15 的相应原记录。文档所披露的验证缺口不阻止 M14-T08 文档任务完成，也没有被这些文档检查改判为产品通过。

独立文档审查：规格符合、质量通过，无 Critical／Important／Minor 问题；范围为本任务三份文档及启动记录，未将当时尚未补记的检查和完成转换当作已完成。随后实际文档检查通过并按看板单独记录 IN_PROGRESS -> COMPLETED；无更大预定义 Order，不创建后继任务或交接。
