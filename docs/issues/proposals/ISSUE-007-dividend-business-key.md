# ISSUE-007：按分红进度区分记录的修复设计

## 状态与目标

**2026-09-06已关闭：** 用户明确批准D-01后，修复963ea17完成。76适配/元数据回归、73真实MySQL集成、7唯一打包合同、49总门禁及独立复审通过。nuy4jdhx真实页面dividend SUCCESS，source38/insert38/update0，末查38与独立DB38，requestId `c05dfddc-0ff5-4335-92d1-9b97c4d88722`，434ms。新证据471dfb0，全文SHA `9d5c283b31f586ee7a3b4fdbda84321fbf3c7e4f55274f9d69955d86353af170`。本问题已修复并真实验证，不再重复诊断；M14-T09本轮32/1/7的新阻塞是top10_holders历史EMPTY预期漂移，见其设计D-02，与分红冲突分别记录。以下为此前诊断/设计历史，不作为当前未修复或待批准事实。


**2026-09-06确认：** 用户对保留各阶段、四字段指纹、同阶段更新、V7保留数据及全40复验的具体方案回复“同意”。本方案已批准，以下“待确认”描述为确认前历史。当前执行、状态与新证据以M14-T09设计为准；本地测试须从净化环境运行，正式验收可由已继承用户Token的工具自行启动。


2026-09-06用户要求将剩余工作转交新增M14-T09。后续执行归属、任务状态门禁和新证据路径以 [M14-T09设计](../../task-designs/M14-T09-design.md) 为准；下文涉及M14-T05的技术合同继续作为输入，其旧恢复/阶段状态叙述不再作为新任务启动指令。此转交不确认候选业务方案，业务/迁移设计内容仍待确认。

待项目所有者确认的跨层设计；不是已实施修复。来源为用户持续修复授权下的单次诊断，以及 M14-T05 的真实失败。该方案会修订既有记录身份和数据库唯一约束，因此先形成可审阅方案，确认后再实施。当前 M14-T05 保持 BLOCKED。

目标：同一股票、报告期、公告日下，不同实施进度的分红记录分别保存；同一进度跨次下载仍更新原记录。保留可空实施进度和原始业务字段，不丢弃冲突行、不按金额或响应顺序猜测“最新”版本。

## 已建立的证据

- 2026-09-06 单次原参数诊断用时 1.56 秒，clientExecuteCalls=1，sourceRowCount=38；原 GenericDatasetAdapter 返回 ADAPTER_TYPE_INVALID / conflicting_key，首次冲突行索引21（从0开始），与先前同键行不同的字段仅为 div_proc、cash_div、cash_div_tax。adaptedRowCount 未建立。
- 诊断使用原 manifest 和 ISSUE-006 冻结包，秘密扫描、Java退出与私有产物白名单通过，无数据库、重试或真实行落盘。该结果是独立新请求，不能补写为此前1gpnb4ru失败的具体字段或数量。
- [Tushare 官方分红文档](https://tushare.pro/document/2?doc_id=103)于同日只读核对：div_proc 为“实施进度”，ann_date 为“公告日(预案，决案)”，cash_div/cash_div_tax 为每股税后/税前分红。
- 当前 dividend YAML 和 V5 主键均为 ts_code/end_date/ann_date，div_proc 可空。相邻 repurchase 接口已把进度 proc 纳入身份，但其 proc 明确不可空，不能直接套用其物理复合主键。
- TRD 9.4 已定义指纹用于“合法记录的区分字段允许为空”的场景，并要求业务键变更同步版本化元数据、唯一约束和 Flyway 迁移。FingerprintKeyCodec 已支持显式 null 标记；GenericDatasetAdapter 当前对所有模式的身份字段均拒绝 null，与这项指纹语义不一致。
- 历史 dividend 模板为空；真实38行证明历史 EMPTY 预期已漂移，不能把适配错误或失败后的空表当作 EMPTY。

## 方案比较与裁决请求

| 方案 | 行为及代价 | 建议 |
|---|---|---|
| 四字段身份的 FINGERPRINT | 在旧三字段后增加 div_proc，允许合法 null，需一个迁移和通用适配器的键模式判断修正 | 采用 |
| 四字段 COMPOSITE | 物理键较直接，但必须把原可空 div_proc 改为不可空，或引入伪值，改变有效输入范围 | 不采用 |
| 旧三字段键下选一条记录 | 需发明进度优先级或依赖响应顺序，会丢弃上游不同进度记录 | 不采用 |

推荐身份固定为 `FINGERPRINT: [ts_code, end_date, ann_date, div_proc]`。金额是可更新内容，不加入身份；不改成整行快照指纹。当前诊断只证明首个冲突涉及不同进度，后续若出现同进度不同内容冲突仍显式失败，不能预先声称38行全部可适配。

纳入进度的业务前提是“保留不同进度的记录”。这不是仅由错误码就能决定的唯一修法：如果目标改为每次分红只保存最终方案，应另行定义哪些进度可进入系统、如何处理撤销/缺少最终阶段等情况；不能把当前推荐当成已获确认的需求。

## 元数据与适配行为

1. 仅改变 dividend 的 businessKey 模式及四字段有序列表；14列名称/顺序/类型/长度/可空性、参数、筛选、固定列和批大小均保留。
2. GenericDatasetAdapter 的缺失判断改为：非空业务列始终拒绝 null；COMPOSITE 身份字段也拒绝 null；FINGERPRINT 身份字段按其列 nullable 属性校验，允许的 null 交既有 FingerprintKeyCodec 编码。
3. 不改 ValueConverter 或 FingerprintKeyCodec 的规范转换、字段顺序、UTF-8/长度前缀/显式空标记和 SHA-256 格式。不新增源特例或前端逻辑。
4. 完全相同的重复行仍仅保留首次出现；同一四字段身份却有不同内容的批内冲突仍整批失败。不同进度分别入库；同一身份跨批次沿用现有 upsert 和插入/更新计数。
5. 必填 ts_code/end_date/ann_date 仍不可空；空白 STRING 按已有转换规则归为 null，不填充进度占位符。
6. 通用修正也覆盖已有 stk_managers、pledge_detail 的可空指纹字段，须补回归，不把本地回归当成这两个接口的真实验收。

## 版本化数据库迁移

新增 `data-plane/tensor-app/src/main/resources/db/migration/V7__version_dividend_business_key.sql`；不修改已发布 V1～V5 或 acceptance 专用 V6。

迁移前停止旧应用及其他所有写入者，并保持对外写入关闭，直到新包完成迁移、schema校验和健康检查；不允许旧二进制与新二进制并行写入。迁移在既有 tushare_pro__dividend 上依次执行：

1. 在 imp_ann_date 后新增 `business_key CHAR(64) NULL`，保持三个来源列位于最后。
2. 从每条现存行的 ts_code/end_date/ann_date/div_proc 计算既有 Java 编码等价的 SHA-256，填充 business_key，业务列及来源/入库时间不更新。
3. 最终以同一条 MySQL8.4.6/InnoDB `ALTER TABLE` 完成 business_key 的 NOT NULL、DROP PRIMARY KEY、ADD PRIMARY KEY(business_key) 及 ADD INDEX idx_dividend_ts_code(ts_code)。主键切换语句失败时旧主键保持，不存在删除旧主键后单独新增失败的窗口。
4. 保留 idx_dividend_ann_date；新的ts_code索引补偿旧主键不再覆盖该筛选。不得保留旧三列 UNIQUE，否则不同进度仍无法并存。其他表不改变。

回填编码固定：前三项均为非空。每个非空项为 `0x01 + 4字节大端UTF-8字节长度 + UTF-8值`，null 项仅 `0x00`。日期规范文本为 yyyy-MM-dd，固定10个ASCII字节。字符串使用现存已适配的精确内容，不在SQL中额外 trim、转大小写或按字符数计长；用 `CONVERT(value USING utf8mb4)` 和 `OCTET_LENGTH`。长度前缀可通过 `UNHEX(LPAD(HEX(byte_length), 8, '0'))` 构造。四段按固定顺序二进制拼接后 `SHA2(..., 256)`，须在真实MySQL测试中与 Java 编码逐行比较，不能仅测试 SQL 自洽。

现有每个旧键只有一行，保留旧键字段再追加进度不会主动合并现有行；散列碰撞或不合法迁移输入导致唯一约束失败时保留错误，不删行绕过。MySQL 多条DDL不构成整体事务：实施只在自有合成库验证新建和旧库升级；已有外部数据库的迁移需正常发布窗口/备份，不自动执行或声称可回滚。迁移失败不得自动 Flyway repair、清库或重试，保留阶段与状态供定位。

旧二进制不生成新的business_key且使用旧元数据，不能直接回退运行在新schema上。需要回退时保持停写，通过已验证的备份恢复到配套旧schema与旧包；不得只回退JAR或删新主键“修好”启动。上述新增可空列和回填可能在最终ALTER失败后保留，必须如实保留迁移阶段；不能把单条最终ALTER的原子性说成整个V7可回滚。

发布后精确合同：生产迁移为 V1～V5、V7，共6个；acceptance 加 V6，共7个。生产49表/851业务列/1001物理列，acceptance50表/1008物理列；COMPOSITE46个、FINGERPRINT3个（dividend、pledge_detail、stk_managers）。生产与acceptance二级索引均41个。公开页面/API仍为14业务列及原来源列，不暴露 business_key。

## 验收预期的单接口修订

- 原 manifest 文件及 SHA、49/58历史总量、历史 dividend.status=empty、模板0行和所有参数保持不变。
- 在 M14-T05 的设计/spec 中加入精确限定 dividend 的“当前验收预期=ok”覆盖，并记录诊断依据。其他39个选中接口继续使用既有期望；不开放任意接口或环境可配置覆盖。
- 保留历史分类28ok/12empty；当前执行分类明确为29ok/11empty。证据分别记录 manifestStatus、acceptanceStatus 和实际 outcome，不把历史状态改写为ok，也不把这次38行定为未来固定数量。
- dividend 下一轮要求 SUCCESS、sourceRowCount>0，正常插入/查看/来源时间/分页/独立DB计数全部核对；若再次EMPTY或失败则如实停止，不按现时结果动态放宽预期。
- 选中范围仍40接口/48样例/80查询；fixture仍2POST/3查询。9项排除、零重试、失败停止、原参数和全40通过后PAUSED规则均保留。

## 精确实施范围

生产改动仅三处：dividend.yaml、GenericDatasetAdapter.java，以及新增V7迁移。同步修订 TRD 9.4 的当前业务键说明并保留 v1.0 旧键历史；本文作为 M03-T07、M04-T05、M05-T05 对应旧决定的显式后续修订，不改写已完成任务的历史验证。

验证文件：GenericDatasetAdapterTest、TushareMetadataContractTest、FlywaySchemaContractIT、PackagedJarContractTest；新增 tensor-app 下 `adapter/TushareDividendAdaptationTest.java` 与 `db/DividendBusinessKeyMigrationIT.java`。FixtureFlowIT、ProductionApplicationContextIT、DownloadControllerIT 仅同步精确迁移计数6→7。AcceptancePackagedJarContractTest 沿用“保留生产内容、只增加fixture/V6”的差分合同；新增V7属于生产基线。

同步更新 `docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`docs/runbook/acceptance.md` 的迁移版本、schema范围最小权限、停写升级及旧包不兼容说明；生产是V1～V5/V7共6次，验收是V1～V7共7次，生产仍不包含V6。`scripts/verify-49-contracts.sh` 的当前包汇总改为1008物理列，并继续由真实schema合同测试支撑，不产生与新包不符的旧1007证据；已提交的M14-T04历史1007报告保留。

验收接入限于现有 M14-T05 设计、spec、任务卡、交接/看板及安全证据；spec只增加dividend预期覆盖和新包固定hash。独占空库准备需为新V7增加运行迁移所需最小权限 ALTER、INDEX，并验证不授予DROP/DELETE等无关权限。既有安全记录不重写；新运行的7迁移结论和材料hash按实际测量登记。旧三个验收包全部保留，新包构建完成后单独冻结并明确接入，不预填未知产物hash。

不增加接口、字段、参数、上游请求重试、全局去重宽容、任意历史状态接受规则、数据库外部操作或界面新功能。

## 验证顺序与完成条件

1. 先写合成RED：相同旧三字段/不同进度及金额的两行应分别适配；合法null进度应成功；旧实现分别暴露冲突/null键错误。真实38行不保存为fixture。
2. 最小适配/元数据修正后GREEN：两阶段并存；相同阶段同内容去重；同阶段不同内容仍拒绝；跨次同阶段更新；null可空指纹稳定且与非空进度不同；必填null及COMPOSITE nullable键仍拒绝。已有两种指纹数据集带可空身份的合成用例通过。
3. MySQL8.4.6新空库完整迁移+validate+再次migrate零执行。另建独占旧V5/V6库，放入合成中文、ASCII、null进度与边界日期行，执行V7，逐行比较Java指纹、业务值/来源列/时间及行数不变；null与“null”文本区分；随后插入同旧键不同进度并验证查询及upsert。无需真实Token。
4. 49元数据合同、全schema列/主键/索引合同与相关服务/fixture集成回归通过；生产/acceptance打包合同通过，归档比较只有获准生产类/元数据/V7及对应构建清单变化，原静态前端与依赖保持一致。
5. 独立代码与迁移复审通过。新包仅health启动检查采用合成Token和新空库，验证7迁移/50空表、扫描和全部自有资源清理；不调用真实数据接口。
6. 对M14-T05预期覆盖做离线反例：仅dividend覆盖，其他历史empty仍拒绝SUCCESS，原manifest/参数不变、历史及执行分类分别正确、40项发现与现有安全门禁通过。
7. 明确新冻结包和修订验收设计接入后，才BLOCKED→READY，再单独READY→IN_PROGRESS。以用户已有Token终端进行原范围一次完整验收；真实结果决定再次BLOCKED或2000档阶段完成PAUSED。

本次交付是已定位根因和可审阅修复设计，尚未进行上述生产修改或迁移/回归/真实复验。确认本设计即授权按该范围实施及本地验证，不再重复询问同一方案。

## 实施时的精确本地检查入口

以下是确认后执行的命令，当前未运行。工作目录为独立源码快照的 `data-plane`，使用Java21、既有Maven和隔离构建输出。沿用修复包构建方式，将原包静态前端完整放入该快照的control-plane/dist，使用 `-Dskip.installnodenpm -Dskip.npm` 复用它，保留生产资源复制与打包检查。不得覆盖任何旧包或从实际Token环境运行这些本地测试。

```sh
mvn -pl tensor-app -am -Dskip.installnodenpm -Dskip.npm -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GenericDatasetAdapterTest,TushareMetadataContractTest,TushareDividendAdaptationTest,TushareDecimalAdaptationTest,TushareAnnouncementDateAdaptationTest test
mvn -pl tensor-app -am -Dskip.installnodenpm -Dskip.npm -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DividendBusinessKeyMigrationIT,FlywaySchemaContractIT,FixtureFlowIT,ProductionApplicationContextIT,DownloadControllerIT test
mvn -pl tensor-app -am -Pacceptance -Dskip.installnodenpm -Dskip.npm -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GenericDatasetAdapterTest,TushareDividendAdaptationTest -Dit.test=PackagedJarContractTest,AcceptancePackagedJarContractTest verify
```

第一条先在未修复实现观察对应RED，再用于GREEN和相关回归；第二条通过Surefire显式选择IT类，不依赖默认测试名称发现，必须核对每个命名类有实际执行结果、零失败/错误/跳过。迁移IT另覆盖最终主键切换失败保持旧约束及停写升级后的后续写入。第三条确认生产/acceptance打包合同实际执行；不能把test阶段成功替代verify中的归档检查。上面的命令不删除“*IT”选择来绕开Docker失败。

契约门禁须在已包含并提交本次变更、受保护输入干净的 `main` Git 检出根执行 `sh scripts/verify-49-contracts.sh`，由脚本从已提交HEAD自行创建隔离构建快照；不能在没有.git的普通源码快照中运行。要求既有49接口元数据/schema/包覆盖和更新后的50表/1008列安全汇总均通过。该脚本不承担新增业务行为测试，仍须保留前三条结果。M14-T05的Node离线反例、40项发现及合成health检查按其已建立入口接入新包，不发真实请求。

## 设计复审记录

独立复审未发现Critical；2项Important（运行说明/计数接入遗漏、停写与主键切换边界）修订后定点复审关闭。1项Minor的明确测试命令已补，最后的契约脚本Git前置条件也按复审要求补齐并由控制器核对脚本53～75行。方案可供业务确认；复审不等于用户已批准，也不代表迁移或修复已经实施。
