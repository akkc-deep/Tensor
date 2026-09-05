# M14-T04 49 数据集自动契约与页面回归驱动——任务设计

任务编号：`M14-T04`。权威来源：[任务看板](../task-handoffs/tensor-v1-task-board.md) Order 74 与 [任务卡](../superpowers/plans/tensor-modules/M14-integration-release.md#task-m14-t04-49-数据集自动契约与页面驱动40h)。直接依赖只有 M03-T09、M04-T06、M14-T01。本设计在 M14-T03 完成记录 `139c2c0` 之后编制；不启动本任务实施。

## Goal

把既有 M03 元数据、M04 MySQL schema 和生产 JAR 契约串成可复跑门禁，再从原样验收 JAR 页面逐一选择 manifest 的全部 49 个下载接口及数据集，证明名称无缺失/多余、分类与中文说明、参数控件/必填状态、查询筛选定义一致。覆盖 AC-002/003、PRD-F-004/007/024；49 个真实上游下载属于 M14-T05，不以本任务元数据检查代替。

## Scope

只新增 `scripts/verify-49-contracts.sh`、`control-plane/e2e/tushare-metadata.spec.js` 和 `docs/verification/M14-T04-49-contracts.md`。shell 运行已有测试、核对新报告和归档资源；Playwright 运行 49 个接口/数据集配对用例，同文件包含独立期望、生命周期与安全证据辅助代码。不得新增依赖包、公共 helper 或配置文件。

不读取 M00～M13 后端生产实现，不改 Java/Vue/YAML/SQL、POM、package/lock、manifest/模板、runbook、既有测试或原验收 JAR。允许运行公开测试及原 Maven 生产构建；在独立临时源码快照构建，避免清理/覆盖工作区 target、用户静态图片或现有验收包。只读文件名/资源哈希核对不解释生产代码或 YAML 内容。页面不提交合法下载、不发 records 查询；不使用真实 Token、真实上游、SQL/API 种数、route/mock 响应或产品内部状态。排除适配数据、分页/宽表、性能、安全全矩阵与发布准入。

## Approach

### 直接输入与兼容决定

- M03-T09：[设计](M03-T09-design.md)、`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java`、`docs/data-template/manifest.json`、PRD 附录 A。既有测试通过公开 loader 对 49 YAML、851 个模板字段、独立参数/业务键/filters 校验；模板 data 由既有测试流式跳过。本任务只解析 manifest，不解析或复制任何模板样例行。
- M04-T06：[设计](M04-T06-design.md)、`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`。固定 Testcontainers `mysql:8.4.6`，显式 `-Dtest` 执行 49 动态表契约+3固定契约；首迁移6/validate成功/二迁移0；49生产表/1000列/49主键/40二级索引，另加fixture后50表/1007列/50主键。不把50误报为50生产表。
- M14-T01：[设计](M14-T01-design.md)、`control-plane/e2e/fixture-flow.spec.js`、[实际证据](../verification/M14-T01-fixture-flow.md)、[验收说明](../runbook/acceptance.md)、[配置说明](../runbook/configuration.md)。复用分发 JAR、真实页面/role/label、空 schema、进程所有权和正常停机模式，不 import 已注册用例的 spec，不重复其 fixture 下载/禁用矩阵。
- 构建与页面公开补充合同：`data-plane/pom.xml`、`data-plane/tensor-app/pom.xml`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`、`docs/contracts/openapi-v1.yaml`、M11-T01/T02 与 M12-T01 设计及其既有组件测试。POM/测试仅用于确认已有命令、报告和界面合同，不进入后端生产源码。

PRD 附录八类与现行分类的差异已由 M11-T01 项目所有者于2026-09-04批准：当前保持七组，基础组字面为 `basic_organization`，A.4与A.5合为 `互联互通与转融通`；其余为下表中文分类。按七组11/7/6/6/9/3/7检查，不制造八组失败，也不改产品映射。manifest 的 `query_mode=range` 映射公开 DTO 的 `date_range`。API“说明”为 displayName/apiName/category/queryMode 的公开“接口说明”区域，ApiDescriptor没有顶层description；当前参数description/defaultValue/pattern均不提供，不要求虚构说明。全部已有参数必填；六个无参数接口不生成必填字段。

### 独立 49 项基线

冻结 manifest SHA-256：`37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。脚本和spec都验证版本/hash、恰49项、唯一合法API名、filename精确为 `<api>.json`；只取 `api_name/filename/query_mode` 及params数组中各样例对象的参数key并集用于对照，不读取或输出 params 样例值、row_count/status，不把它们当本任务运行证据。以下表是来自 PRD 附录 A 与已批准分类的独立期望；spec显式编码，不从实际HTTP/YAML/DOM生成期望。manifest顺序用于49测试注册，集合相等不依赖接口响应排序。

| API | 中文说明 | 当前 category | DTO queryMode | 按序下载参数 | 业务列数 |
|---|---|---|---|---|---:|
| `stock_basic` | 股票基础信息 | `basic_organization` | `snapshot` | `list_status` | 10 |
| `stock_company` | 上市公司基本信息 | `basic_organization` | `snapshot` | `exchange` | 18 |
| `hs_const` | 沪深港通标的范围 | `basic_organization` | `snapshot` | `hs_type` | 5 |
| `trade_cal` | 交易日历 | `basic_organization` | `date_range` | `exchange`、`start_date`、`end_date` | 4 |
| `new_share` | IPO 新股发行信息 | `basic_organization` | `date_range` | `start_date`、`end_date` | 12 |
| `namechange` | 证券名称变更记录 | `basic_organization` | `date_range` | `start_date`、`end_date` | 6 |
| `stk_managers` | 上市公司管理层信息 | `basic_organization` | `snapshot` | 无 | 11 |
| `broker_recommend` | 券商月度推荐 | `basic_organization` | `snapshot` | `month` | 4 |
| `index_classify` | 行业指数分类 | `basic_organization` | `snapshot` | 无 | 7 |
| `index_member` | 行业指数成分 | `basic_organization` | `snapshot` | 无 | 5 |
| `index_member_all` | 行业分级与完整成分 | `basic_organization` | `snapshot` | 无 | 11 |
| `daily` | 日线行情 | `行情与估值` | `trade_date` | `trade_date` | 11 |
| `weekly` | 周线行情 | `行情与估值` | `trade_date` | `trade_date` | 11 |
| `monthly` | 月线行情 | `行情与估值` | `trade_date` | `trade_date` | 11 |
| `adj_factor` | 复权因子 | `行情与估值` | `trade_date` | `trade_date` | 3 |
| `suspend_d` | 每日停复牌信息 | `行情与估值` | `trade_date` | `trade_date` | 4 |
| `daily_basic` | 每日估值与市场指标 | `行情与估值` | `trade_date` | `trade_date` | 18 |
| `stk_limit` | 每日涨跌停价格 | `行情与估值` | `trade_date` | `trade_date` | 4 |
| `moneyflow` | 个股资金流向 | `交易与资金` | `trade_date` | `trade_date` | 20 |
| `margin` | 融资融券汇总 | `交易与资金` | `trade_date` | `exchange_id`、`trade_date` | 9 |
| `margin_detail` | 融资融券交易明细 | `交易与资金` | `trade_date` | `trade_date` | 10 |
| `top_list` | 龙虎榜每日明细 | `交易与资金` | `trade_date` | `trade_date` | 15 |
| `top_inst` | 龙虎榜机构明细 | `交易与资金` | `trade_date` | `trade_date` | 10 |
| `block_trade` | 大宗交易 | `交易与资金` | `trade_date` | `trade_date` | 7 |
| `moneyflow_hsgt` | 沪深港通资金流向 | `互联互通与转融通` | `trade_date` | `trade_date` | 7 |
| `hsgt_top10` | 沪深港通十大成交股 | `互联互通与转融通` | `trade_date` | `trade_date` | 11 |
| `hk_hold` | 沪深港股通持股明细 | `互联互通与转融通` | `trade_date` | `trade_date` | 7 |
| `slb_len` | 转融通期限与规模 | `互联互通与转融通` | `trade_date` | `trade_date` | 6 |
| `slb_sec` | 转融通证券汇总 | `互联互通与转融通` | `trade_date` | `trade_date` | 7 |
| `slb_sec_detail` | 转融通证券明细 | `互联互通与转融通` | `trade_date` | `trade_date` | 6 |
| `income` | 利润表 | `财务与披露` | `ann_date` | `ts_code`、`ann_date` | 85 |
| `balancesheet` | 资产负债表 | `财务与披露` | `ann_date` | `ts_code`、`ann_date` | 152 |
| `cashflow` | 现金流量表 | `财务与披露` | `ann_date` | `ts_code`、`ann_date` | 97 |
| `fina_indicator` | 财务指标 | `财务与披露` | `ann_date` | `ts_code`、`ann_date` | 108 |
| `fina_audit` | 财务审计意见 | `财务与披露` | `ann_date` | `ts_code`、`ann_date` | 7 |
| `fina_mainbz` | 主营业务构成 | `财务与披露` | `ann_date` | `ts_code`、`ann_date` | 8 |
| `express` | 业绩快报 | `财务与披露` | `ann_date` | `ann_date` | 15 |
| `forecast` | 业绩预告 | `财务与披露` | `ann_date` | `ann_date` | 13 |
| `disclosure_date` | 财报披露计划 | `财务与披露` | `ann_date` | `ann_date` | 5 |
| `dividend` | 分红送股 | `公司行动` | `ann_date` | `ann_date` | 14 |
| `repurchase` | 股票回购 | `公司行动` | `ann_date` | `ann_date` | 9 |
| `share_float` | 限售股解禁 | `公司行动` | `ann_date` | `ann_date` | 7 |
| `stk_rewards` | 管理层薪酬与持股 | `股东与治理` | `snapshot` | `ts_code` | 7 |
| `stk_holdernumber` | 股东户数 | `股东与治理` | `snapshot` | `ts_code` | 4 |
| `stk_holdertrade` | 股东增减持 | `股东与治理` | `ann_date` | `ann_date` | 11 |
| `top10_holders` | 前十大股东 | `股东与治理` | `ann_date` | `ann_date` | 9 |
| `top10_floatholders` | 前十大流通股东 | `股东与治理` | `ann_date` | `ann_date` | 9 |
| `pledge_stat` | 股权质押统计 | `股东与治理` | `snapshot` | 无 | 7 |
| `pledge_detail` | 股权质押明细 | `股东与治理` | `snapshot` | 无 | 14 |

参数名按表原序映射下列独立描述符；精确 key/value 比较 JSON 对象，数组维持声明顺序。所有参数 `required=true`，无description/defaultValue/pattern；非枚举不带allowedValues，非范围不带relatedParameter。用 OpenAPI 公开响应的省略规则，不把Java内部null当HTTP额外字段。

| 参数 | label | type | 附加合同 |
|---|---|---|---|
| list_status | 上市状态 | ENUM | allowedValues=[L,P,D] |
| exchange / exchange_id | 交易所 | ENUM | allowedValues=[SSE,SZSE,BSE] |
| hs_type | 沪深港通类型 | ENUM | allowedValues=[SH,SZ] |
| start_date | 开始日期 | DATE_RANGE_MEMBER | relatedParameter=end_date |
| end_date | 结束日期 | DATE_RANGE_MEMBER | relatedParameter=start_date |
| month | 月份 | MONTH | 无 |
| trade_date | 交易日期 | DATE | 无 |
| ann_date | 公告日期 | DATE | 无 |
| ts_code | 股票代码 | TS_CODE | 无 |

filters独立采用M03-T09的五组；构造map时拒绝重叠，最终key集合与manifest精确一致。不要从columns是否包含日期推导，`fina_mainbz`的下载ann_date与查询filters不同是既有批准事实。

| 按序 filters.field | API |
|---|---|
| [] | trade_cal,index_classify,index_member |
| [ts_code] | stock_basic,stock_company,hs_const,new_share,broker_recommend,index_member_all,fina_mainbz,pledge_stat |
| [trade_date] | margin,moneyflow_hsgt,slb_len |
| [ts_code,trade_date] | daily,weekly,monthly,adj_factor,suspend_d,daily_basic,stk_limit,moneyflow,margin_detail,top_list,top_inst,block_trade,hsgt_top10,hk_hold,slb_sec,slb_sec_detail |
| [ts_code,ann_date] | namechange,stk_managers,income,balancesheet,cashflow,fina_indicator,fina_audit,express,forecast,disclosure_date,dividend,repurchase,share_float,stk_rewards,stk_holdernumber,stk_holdertrade,top10_holders,top10_floatholders,pledge_detail |

ts_code对象恰为 `{field:'ts_code',operator:'EQ',controlType:'TEXT'}`；两类日期恰为field对应、operator=BETWEEN、controlType=DATE_RANGE。summary与definition的filters必须分别等于该独立期望。

### Shell：已有契约、新报告和生产归档

脚本可从任意cwd执行，通过脚本自身路径确定仓库根；`#!/bin/sh`、`set -eu`、`set +x`、`umask 077`。只提供正常执行入口，不增加skip、dry-run或任意Maven参数通道。使用已存在的Git、tar、Maven、Java21、Python3标准库及Docker；Python内嵌仅用于JSON/XML/ZIP/哈希和安全子进程辅助，不加pip/npm/Maven依赖。非秘密可选 `M14_MAVEN_REPO` 是绝对目录，默认 `/private/tmp/tensor-m2`；由运行者准备可用Docker连接环境，固定镜像不可用则失败，不禁用Ryuk或跳过测试。

1. 记录HEAD和三个任务文件实际hash，验证manifest/hash/49集合。在开始前与结束后检查构建消费的已跟踪输入相对HEAD无变动：所有 `data-plane` POM与src、`control-plane/src`、index.html、package/lock、vite/vitest配置、`docs/data-template`；不检查/改动用户无关暂存文档、E2E、生成target。差异只安全列出路径并非零退出，不偷偷验证旧版本。
2. 用mkdtemp创建本任务0700目录。先 `git archive --format=tar --output=<owned>/source.tar HEAD` 成功，再tar解压到该目录的snapshot；不管道掩盖git失败，不调用工作区 `clean`，不删除/取消暂存用户文件。先记录快照manifest hash一致，快照天然没有旧target报告。
3. 在快照中运行下列**唯一Maven主门禁**；前端原生命周期会执行npm ci、120项unit和build，不能skip。正常生产profile，不加acceptance或skipTests：

```sh
mvn -B -ntp -Dmaven.repo.local="$M14_MAVEN_REPO" \
  -f "$tensor_m14_snapshot/data-plane/pom.xml" -pl tensor-app -am \
  -Dtest=TushareMetadataContractTest,FlywaySchemaContractIT \
  -Dsurefire.failIfNoSpecifiedTests=false verify
```

`failIfNoSpecifiedTests=false`仅允许reactor上游无匹配类；后置报告门禁必须拒绝目标缺失/零次，不能吞掉任务测试。Maven子进程只继承PATH、HOME、JAVA_HOME、TMPDIR、LANG/LC_ALL和已提供的Docker/Testcontainers连接变量（DOCKER_HOST/DOCKER_TLS_VERIFY/DOCKER_CERT_PATH/TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE/TESTCONTAINERS_HOST_OVERRIDE）；清除TENSOR_*/SPRING_*/SERVER_*/MYSQL_*及JVM/Maven注入变量，不把真实应用凭证传给构建。命令参数不携秘密，stdout/stderr写新0600私有日志；不tee原日志、不输出XML的properties/system-out/失败stack。无论成功失败都记录真实退出码、起止时间和已生成报告的安全计数；失败保留原始退出码，不以报告解析成功覆盖失败。

4. Maven结束后用Python ElementTree解析本轮精确三个XML报告，验证 `testsuite@name` 为精确类名、每个 `testcase@classname` 为同一精确类名，并验证testcase数和tests/failures/errors/skipped一致，0失败/错误/跳过，且不存在失败/错误/skipped节点或重复用例。解析器忽略但不输出properties、system-out等内容。报告必须来自此新快照，缺失/损坏/多余目标suite/计数不符非零退出；不能grep `BUILD SUCCESS` 或打印硬编码通过数。

| 报告（相对snapshot/data-plane） | 精确类 | 通过调用数 |
|---|---|---:|
| tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.TushareMetadataContractTest.xml | com.akkc.tensor.plugin.tushare.metadata.TushareMetadataContractTest | 50（49参数化+1全局） |
| tensor-app/target/surefire-reports/TEST-com.akkc.tensor.db.FlywaySchemaContractIT.xml | com.akkc.tensor.db.FlywaySchemaContractIT | 52（49动态+3固定） |
| tensor-app/target/failsafe-reports/TEST-com.akkc.tensor.build.PackagedJarContractTest.xml | com.akkc.tensor.build.PackagedJarContractTest | 4 |

Surefire测试名可能是方法加调用序号；计数检查同时确认固定方法名、49个唯一参数化/动态调用属于各自方法，不假定XML保存JUnit displayName/API名。49具体身份由原测试内部独立集合断言保证。禁止宣称新增49个Java test：本轮为102项Surefire加4项Failsafe。`failsafe-summary.xml`也必须为4 completed、0 errors/failures/skipped；允许failureMessage元素缺失、文本为空/纯空白或 `xsi:nil="true"` 且无非空文本，只拒绝非空失败消息。不得要求成功XML省略该元素。

5. 资源检查使用 `zipfile` 枚举新生产可执行 `snapshot/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` 及其嵌套库，拒绝ZIP重复条目。源码资源目录只允许manifest49个直系 `<api>.yaml`，不接受额外YAML/YML/嵌套路径；生产包中49个同名资源必须只位于 `BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar` 的 `datasets/tushare_pro/`，集合精确等于manifest，逐文件字节SHA与快照资源一致。扫描所有嵌套库及外层，其他位置不得有该资源前缀的副本。保持现有PackagedJarContractTest对五生产迁移、fixture/V6/测试资源排除、入口/静态资源的原断言，不自行放宽。
6. 最后安全JSON记录版本、HEAD、manifest及新生产JAR hash、实际报告路径/hash/计数、49源YAML/49生产表/49打包YAML及M04附加fixture计数的证据来源。表计数是成功FlywaySchemaContractIT的已验证合同结果，不伪装为脚本额外SQL采样。所有门禁通过才打印简洁成功行和本地安全JSON路径；否则打印固定阶段/非秘密错误名并非零退出。保留新临时目录中的私有日志/XML与安全JSON；不提交任何生成物，不用总量删除Docker资源。Testcontainers负责自己的容器停止；验证本轮拥有的容器已退出，异常遗留只报告本轮ID并按所有权清理，不能停止他人容器。

### Playwright：原验收 JAR 与无调用上游

使用原 `ACCEPTANCE_JAR` 绝对普通文件，入口SHA为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`，记录实际值且运行前后不变；不是上节新生产JAR。每次完整运行/修订重跑按runbook创建新空 `^tensor_m14_t04_[a-f0-9]+$` MySQL8.4 schema与仅CREATE/SELECT/INSERT/UPDATE应用账号；空库、6成功迁移/50业务表、49生产表均无记录的只读准备/结束证据由运行者在私有管理员或同账号CLI会话采集，不写入浏览器测试密码文件，不用SQL种数。测试输入仅三个 `TENSOR_DB_*` 与ACCEPTANCE_JAR；凭证隐藏注入，不加产品入口。schema/host不得出现在公共报告。

spec使用Node标准库，本地127.0.0.1动态端口监听一个**零调用哨兵**：任一请求只增安全计数，固定HTTP500并记录失败，不读取/保存上游body，不返回成功数据。生成随机假Token只给自有JVM，使下载接口元数据可用；不读取调用者真实Tushare Token。净化继承TENSOR_*/SPRING_*/SERVER_*/MYSQL_*/M14_*及JVM注入变量，只回填三个DB值、`TENSOR_TUSHARE_TOKEN`（生成假值）和 `TENSOR_TUSHARE_BASE_URL`（本机哨兵URL）；不更改超时、日志级别、业务配置。

用spawn(shell:false)、新私有运行目录/0600日志启动：

```sh
java -jar "$ACCEPTANCE_JAR" \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

启动前核对Java21、三个DB变量非空、JDBC无嵌入凭证/密码query、schema名称、8080空闲、PLAYWRIGHT_BASE_URL未设置或恰 `http://127.0.0.1:8080`。缺项/端口被占用安全失败，不复用或停止未知应用。health每次2秒、总90秒等待200/UP；beforeAll180秒，每case120秒，afterAll180秒。串行describe、retries=0、Chromium1440×1000，trace/video/自动截图关闭；恰49项稳定标题 `metadataContract:<apiName>`，每项同时覆盖下载API和同名dataset，最终49/49配对通过。使用test内page fixture、每项新页面；不共享上一项输入结果。

### 每个API的真实页面配对流程

1. 动作前监听页面发出的同源metadata响应。goto `/downloads` HTTP200，level1“数据下载”，role combobox exact“数据源”选择 `Tushare Pro`（可用真实Enter展开，option选择）。接口列表GET精确 `/api/v1/data-sources/tushare_pro/apis`，HTTP200；全部49唯一apiName集合与独立manifest相等，ApiDescriptor恰五键、每项名称/分类/mode/参数全值精确等于期望。data-sources含Fixture和Tushare，Tushare的enabled/credentialConfigured/downloadAvailable全true、unavailableReason=null，不允许显示Token。
2. 展开combobox“数据接口”，以可见option的 `displayName + apiName` 和精确API边界选择当前项；全量可见option集合恰49，无重复/多余。第一次用例额外核对七组可见分类标签；不依赖 `.el-*`、私有ID、Vue/composable或CSS序号。当前 `region` 名“接口说明”（原生section由heading关联）可见，精确包含当前中文说明、API、category和queryMode的中文映射：交易日/公告日/快照/日期范围。关闭的其他popper不纳入活跃选项断言。
3. 参数按期望声明顺序具有可见label/必填星号、对应控件和 `aria-required=true`。非枚举为关联label的输入；枚举为combobox，其展开option列表与allowedValues原序相同且无自由输入选项。无参数六API（stk_managers/index_classify/index_member/index_member_all/pledge_stat/pledge_detail）不出现参数label/输入，“开始下载”可用但不点击。
4. 对有参数的43API，保持全空，点击“开始下载”，每个必填字段出现固定 `此项为必填项`，控件aria-invalid与aria-describedby关联错误，首个参数有真实焦点；在动作后的两次requestAnimationFrame和全用例监听中确认0下载POST。然后用真实控件填写固定安全值：trade_date/ann_date/end_date=2026-08-07、start_date=2026-08-01、month=2026-08、ts_code=000001.SZ、ENUM取首个允许值。日期/月输入Tab提交后Escape关闭弹层，显示ISO值；日期/月控件可打开真实选择浮层再Escape关闭，结合响应type与既有M11-T02控件合同验证，不读取组件props。字段自身错误消失、其他字段与顺序不变。不再次点击合法提交；不把“未提交”记录为下载成功。
5. 通过导航link“数据查看”进入 `/datasets` 并验证heading，选择Tushare和当前同名dataset。页面发出的datasets GET返回恰49唯一摘要，其displayName/category/queryMode/filters按独立期望全量核对；当前definition GET路径为 `/api/v1/data-sources/tushare_pro/datasets/<api>`，HTTP200、身份一致、filters与对应摘要和独立基线分别一致，columns数等于上表且不含内部business_key、displayOrder从0连续。完整字段顺序的851列合同由M03测试承担，不从模板data提取。
6. 核对页面筛选控件精确顺序、可选、初始为空：ts_code→label exact `证券代码 (ts_code)`；trade_date→`交易日期开始 (trade_date)`、`交易日期结束 (trade_date)`；ann_date同名公告日期两端。无对应filter的label/控件必须缺席，日期均为独立可选输入，所有控件无aria-required=true、无错误。显示“设置筛选条件后查询”，无表格/分页；“查询”“重置”可用。等待两次requestAnimationFrame后仍无records请求，整个用例持续计数为0。不要点击查询，当前任务不需要数据行。

每次响应在JSON解析/断言前做秘密扫描；metadata请求由页面产生，禁止request.get/fetch绕过页面取期望（health除外）。监听全部页面request/response/requestfailed/pageerror；任何HTTP>=400、请求失败、非同源请求、写请求或records GET都失败，无豁免。不得route.abort/fulfill或拦截业务成功来制造零写入；零调用哨兵是失败时阻止真实上游的最后防线，计数非0绝不通过。异步response检查在用例结束前全部settle；关闭页面/浏览器前等待正在观察的metadata真实完成，不能忽略关闭引起的失败。

### 安全、清理与实际证据

49个安全case结果逐项记录API、独立category/mode、参数名/type/required/allowedValues、实际filters、API/数据集通过标记、metadata请求状态及耗时；不保存完整HTTP包络、模板数据行或任意环境。扫描假Token、DB密码/用户名、JDBC配置值及凭证键；固定安全错误名避免matcher/异常回显秘密。私有JVM/Flyway正常日志允许无凭证连接信息，公开JSON/页面/响应/截图不得含配置。只存白名单投影；发生泄漏直接失败并隔离对应产物，不改写成通过。

至少保存八个下载页面代表截图（stock_basic、trade_cal、broker_recommend、daily、income、stk_managers、moneyflow_hsgt、pledge_detail），其中trade_cal保留必填文字/焦点，broker_recommend保留月份浮层；再保存五种筛选代表数据集（index_classify、stock_company、margin、daily、balancesheet）截图，共13张。均先扫描页面/目标截图区域可见文本再用testInfo.outputPath显式写PNG到现有忽略目录；记录真实路径/SHA，结束逐张人工查看。自动JSON不能自称manuallyReviewed=true；人工核对独立记录在文档。

afterAll不管用例失败与否都对拥有的JVM SIGTERM，最多150秒等待close/端口关闭；随后关闭本机哨兵及socket，收集最终完整日志并扫描/输出安全汇总。各清理独立执行，主错误与清理双失败用AggregateError保留；不常规SIGKILL。beforeAll失败同样清理已创建资源；哨兵启动失败不能继续启动JVM。最终必须49API/49dataset已实际通过、下载POST=0、recordsGET=0、上游调用=0、pageerror/HTTP/网络失败=0、JVM/哨兵清理true、JAR hash不变。外部运行者只读确认六迁移、50业务表及49生产表全空后，清理本轮自有schema/账号或专用容器与临时凭证，保留安全证据；不删除他人资源。

## Files

- Create `scripts/verify-49-contracts.sh`，100755：上述已有Maven契约/精确新报告/manifest和JAR资源门禁，内嵌标准库辅助，不新增永久辅助文件。
- Create `control-plane/e2e/tushare-metadata.spec.js`，100644：49配对真实页面用例、独立期望和最小同文件生命周期/安全代码。
- Create `docs/verification/M14-T04-49-contracts.md`，100644：实际版本、命令/退出码、manifest/生产新JAR/原验收JAR hash、Maven50+52+4、49/49页面逐项结果、分类兼容决定、零调用/只读DB/安全/13截图/清理证据及失败归因。

三个文件加入Git，实施提交消息 `test(release): verify all 49 dataset contracts`。设计、交接、看板独立提交。只暂存精确路径，保留其他会话暂存及生成文件；不提交快照、JAR、报告XML、JSON、PNG、日志、数据库或秘密。

## Tests

先写完整shell与49项spec，不制造产品RED；脚本同一内嵌报告/资源函数用临时合成输入证明会拒绝目标报告缺失、tests=0、skipped、重复case、失败节点、损坏XML、计数不匹配，以及49总数但错误API名/额外嵌套YAML/重复ZIP条目。使用合成XML/ZIP，不改已发布JAR或生产源码；探针不新增永久文件或Playwright用例。失败诊断只含固定检查名。

```sh
# 仓库根；脚本使用自己的全新快照，不触碰原target
sh -n scripts/verify-49-contracts.sh
scripts/verify-49-contracts.sh
# 另按runbook准备新空schema、私有DB输入和原ACCEPTANCE_JAR，再执行
cd control-plane
node --check e2e/tushare-metadata.spec.js
npx playwright test e2e/tushare-metadata.spec.js --list
npx playwright test e2e/tushare-metadata.spec.js
```

预期语法exit0；shell真实Maven退出0、新报告50/52/4全通过，源YAML/生产表/打包YAML均精确49，前端20文件/120通过；发现恰49个Chromium配对用例；真实运行49 passed/0 failed/0 skipped/0 retry，API与dataset各49/49、零业务/上游请求、13截图逐张人工审阅与所有清理通过。本任务只有设计时不得声称这些新增门禁已运行。

Maven门禁已运行完整前端回归；之后无前端源码变动时不重复120项。代码/测试修订后对受影响门禁重新运行；E2E每次全矩阵用新空schema。Docker/镜像、依赖下载、JDK、端口、DB/JAR缺失是前置失败；报告/ZIP解析与选择器问题是测试问题；产品合同失败保留原断言与安全证据，按看板准备独立单语言修复，不改本任务生产文件、不自行发明ID/Order、不skip/retry/降低覆盖。

提交前运行 `git diff --check`，核对三个实施文件之外的受保护路径相对实施base无差异；Git新增模式/路径精确符合Files。只读报告验证失败不能被成功的静态检查覆盖，不以历史M03/M04/M14-T01结果充当本轮结果。

## Acceptance

- 唯一shell入口在隔离源码快照运行已有M03/M04与生产JAR测试，并从本轮XML证明50+52+4、0失败/错误/跳过；源YAML/生产表/打包YAML三者均为manifest49全集，无额外/缺失/漂移，M04额外fixture明确单列。
- 版本manifest/hash和49个独立中文说明/category/queryMode/参数期望完全相符，七组兼容决定可追溯；不自举期望。
- 49项Playwright配对用例全部从原JAR页面完成接口及dataset选择、说明/必填/控件/枚举/精确filters检查；43必填拦截有效，六无参数表单为空，所有dataset均不自动查询。
- 零下载POST、零records GET、零上游、零异常，无真实凭证、mock、API/SQL种数或生产改动；完整安全证据、13截图人工核验与自有资源清理通过。
- 三个精确文件提交，既有前端120回归、语法、报告/资源合成失败探针、范围和格式通过；无未解决产品缺陷，不宣称真实49下载/发布已验收。

## Risks

- 七组分类是已批准兼容决定，PRD八类未同步不构成本任务擅改分类的授权；未来授权变更需同步契约与期望。
- Surefire对动态测试XML名的表示可能含序号；按所属方法/唯一调用/计数校验而不要求displayName进入XML，49身份由既有契约内部证明。
- 首次快照构建需要网络、磁盘和Docker镜像；失败须保留真实阶段，不能复用陈旧报告或工作区JAR充当新结果。
- 当前原验收包与新构建生产包职责不同，分别记录hash；不得替换/重装配原验收包，或把已执行V6的schema给生产JAR。
- 当前没有TEXT/默认值/可选参数或双日期filters组合；本任务只覆盖真实49定义，不构造不存在的元数据。页面浮层/可访问名称差异可按公开DOM证据最小修正定位，不改产品或删合同断言。
- 此设计未运行新增脚本或49项E2E；最终产品/环境结论只能由实施时实际结果确定。
