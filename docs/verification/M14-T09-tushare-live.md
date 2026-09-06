# M14-T09 分红修复与2000档页面验收证据

本任务依据权威看板 Order76、`docs/task-designs/M14-T09-design.md` 和已确认的 ISSUE-007 四字段指纹方案执行。用户2026-09-06回复“同意”，批准保留各实施阶段、同阶段跨批更新、V7保留现有数据及完整40项复验。批准记录c72a823，启动记录fbd594a。

## 输入与范围

历史M14-T05证据只读，全文SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`；其28通过/1失败/11未运行不拼入本轮。基线ISSUE-006包SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`，原manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`；两者均在本轮准备时核对一致。

原40接口/48参数样例/80页面查询，fixture另2POST/3查询不变。仅dividend当前验收预期为ok，原manifestStatus=empty保持；历史分类28ok/12empty，当前29ok/11empty；每接口manifestStatus/acceptanceStatus与本轮实际outcome分别记录。38是旧诊断来源计数，不是本轮固定行数。

9项仍不覆盖：top_inst、broker_recommend为higher_points；share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange为permission_unverified。不计测试skip或通过；原49目标及M14-T05 BLOCKED事实保留，不自动准备M14-T06。

## 已执行的离线验收接入检查

所有本地验证子进程采用环境白名单，不继承真实Token。

- 新dividend预期同函数合成反例先RED：原spec的acceptanceStatus缺失导致断言失败、exit1；修改后同探针GREEN、exit0，验证仅dividend覆盖、非空/空结果与页面末态断言、其他empty仍拒绝SUCCESS、manifest及参数不变、40/48/9与两组分类。
- 原40项范围选择探针、原纯函数反例（末态参数名随实际接口改为acceptanceStatus）均exit0。
- `node --check control-plane/e2e/tushare-live.spec.js` exit0；`cd control-plane && npx playwright test e2e/tushare-live.spec.js --list` exit0、恰40个Chromium用例，归属M14-T09。
- 新私有启动器的10项合成回归exit0：成功/原非零码保留、损坏安全JSON、创建失败、秘密命中、自动上下文、worker未退出、终检失败及缺Token；5项新前置拒绝exit0：仅旧任务启动、设计hash错误、启动器hash错误、历史扫描源变化、目录已用。尚未凭这些离线检查宣称真实40通过。

独立接入审查发现3项Important（优化模式移除assert、准备中断不清理、排除列表未验证）和1项Minor（旧任务提示）。合成反例均先RED，改为无条件检查、受保护的中断清理和冻结manifest的精确40/9集合后，新增2项准备反例与3项范围反例GREEN；原10项生命周期和5项前置检查再次通过。定点复审及后端/新包/真实验证结果按实际完成补录。

## 新包内容与合成启动实测

新包位于 `/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，实际SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`。所有嵌套JAR递归展开旧18017/新18018文件：仅GenericDatasetAdapter.class、dividend.yaml改变，新增V7__version_dividend_business_key.sql，无删除；原静态前端、依赖及其他所有文件逐字节一致。

独立接入定点复审已关闭3Important/1Minor，无剩余发现。随后以合成Token运行私有health探针，控制目录 `/private/tmp/tensor-m14-t09-control.kcznkmbm`：专用MySQL8.4.6空schema，实际来源host、回环绑定、字符集/排序规则与六项最小权限（CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX）验证通过。Node exit0，health就绪；独立SQL测得7次成功迁移、50业务表全部0行；JVM正常退出，终检扫描2文件、无自动产物、scanPassed/cleanupPassed均true，自有容器/匿名卷及私密DB状态已清理。没有业务请求；这不计真实接口通过。

## 后端修复与契约验证

修复提交 `963ea17`。合成RED先分别复现可空指纹被拒绝及旧三字段下不同分红阶段冲突；随后按设计限定三处生产修改。12个后端改动文件与测试快照逐字节一致，已发布V1～V6未修改。

| 检查 | 实际结果 |
|---|---|
| 设计第一条Maven适配/元数据选择器 | 76 tests，0 failures/errors/skipped；core13、元数据50、dividend4、decimal1、ann_date8 |
| 设计第二条Maven显式IT选择器 | MySQL8.4.6，73 tests，0 failures/errors/skipped；迁移5、schema52、fixture5、production1、download10 |
| 设计第三条acceptance verify选择器 | BUILD SUCCESS；生产包4、验收包3个唯一归档合同全部通过；随后仅补测试，生产包内容/hash未变 |
| 独立修复复审 | PASS；追加必填FINGERPRINT空值拒绝与V6回填后实际upsert覆盖，两项缺口已复审关闭，无剩余发现 |
| `sh scripts/verify-49-contracts.sh` | 在提交963ea17的main根执行，隔离HEAD构建exit0；metadata50/schema52/package4、49/49源与包资源、50业务表/1008物理列/50主键，11合成拒绝门禁全部通过 |

升级IT逐列核对现存业务值、来源与入库时间不变，并比较Java/SQL的UTF-8长度前缀、中文、null与文本null、1000-01-01边界日期指纹；最终ALTER被强制制造失败时旧主键仍在。V6旧实施行经V7回填后，实际PersistenceService同阶段更新计数0/1、不同预案插入1/0并共存；完全重复去重、同身份异内容冲突继续失败。

契约脚本首次因本次启动命令的 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 漏写 `.sock`，Ryuk启动失败（metadata50通过，schema启动错误，exit1）；该环境错误未计通过。改为已通过IT使用的 `/var/run/docker.sock` 后，原脚本在新隔离目录完整重跑成功，无产品或测试断言改动。

成功总门禁安全JSON：`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04.OGkWAfc6/verification.json`，SHA `01f895555b5c1865bb4995e03bd6fc7a8c0686d07887078990ff4d800f5704c9`，2026-09-06T10:41:53.463277Z～10:42:24.254344Z。该沿用脚本内部task=M14-T04表示合同来源，本轮结果归属M14-T09，不改写旧M14-T04历史报告；新真实执行仍使用前节冻结acceptance包。

## 正式运行约束

专用一次性控制目录 `/private/tmp/tensor-m14-t09-control.nuy4jdhx`；命令 `python3 /private/tmp/tensor-m14-t09-control.nuy4jdhx/launch.py`。新环境已确认MySQL8.4.6、初始0表、回环端口、实际来源host、字符集及精确六项schema权限。启动器固定M14-T09当前IN_PROGRESS、批准设计/脚本/包/manifest/本文件hash；选中40与排除9须与冻结manifest精确吻合。2秒间隔、1worker、零重试；只从当前TENSOR_TUSHARE_TOKEN环境读取真实Token，不经文件/argv，不交本地构建或浏览器环境。

运行前全部本地修复、回归、复审、打包、health与总门禁已通过。下文真实报告只有在CLI及worker退出、终检、独立DB核对和自有环境清理后生成，并使用运行时真实秘密集合扫描本文件全文；以实际结果判定，不能将本节准备工作计为40项通过。
