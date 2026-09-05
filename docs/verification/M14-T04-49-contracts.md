# M14-T04 49 数据集契约与页面回归证据

本文件记录 M14-T04 的本轮实际验证。实现基线为 `707215c9495b2652617092c5bd21e337033799bb`；冻结 manifest SHA-256 为 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。七组分类采用 2026-09-04 已批准兼容决定：`basic_organization` 11 项、行情与估值 7 项、交易与资金 6 项、互联互通与转融通 6 项、财务与披露 9 项、公司行动 3 项、股东与治理 7 项。

## 实施范围

- `scripts/verify-49-contracts.sh`：从任意工作目录定位仓库，在权限受限临时目录归档并解压当前 HEAD，净化构建环境后运行唯一 Maven 主门禁；解析本轮精确 XML 和 Failsafe summary，并逐字节核对源 YAML 与生产 JAR 中的 49 份元数据。
- `control-plane/e2e/tushare-metadata.spec.js`：从原样验收 JAR 页面串行执行 49 个 `metadataContract:<api>` 配对用例；逐项验证下载接口、数据集、参数与筛选，禁止下载 POST、records GET 和上游调用。
- 本任务没有修改生产 Java、Vue、YAML、SQL、POM、依赖、配置、manifest、模板、既有测试或验收 JAR。

脚本在每次真实构建前，以同一 XML/ZIP 校验函数执行 10 个合成拒绝探针：缺报告、零 tests、skipped、重复 testcase、failure 节点、损坏 XML、计数不匹配、49 总数但错误 API 名、嵌套额外 YAML、重复 ZIP 条目。探针只使用本轮私有临时目录，不产生提交文件。

## 自动验证结果

| 门禁 | 命令 | 实际结果 |
|---|---|---|
| shell 语法 | `sh -n scripts/verify-49-contracts.sh` | exit 0 |
| Node 语法 | `cd control-plane && node --check e2e/tushare-metadata.spec.js` | exit 0 |
| Playwright 发现 | `cd control-plane && npx playwright test e2e/tushare-metadata.spec.js --list` | exit 0；1 文件、49 tests |
| Maven/报告/归档总门禁 | `scripts/verify-49-contracts.sh` | exit 0；2026-09-05T17:40:16.075144Z～17:40:47.371912Z |
| 页面 49 配对矩阵 | `cd control-plane && npx playwright test e2e/tushare-metadata.spec.js` | Run6 exit 0；49 passed、0 failed/skipped/retry，4.4m |
| 格式与范围 | `git diff --check` 及精确三文件检查 | 提交前新鲜复核 exit 0；实施文件精确为三项 |

最终 shell 私有白名单 JSON 位于 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04.QgjcVrwu/verification.json`，SHA-256 为 `4f6a63cd42252ace6acf5b8055c3c7e05149ca04166fd3f563a872e86565fe77`。本轮基线为 `707215c9495b2652617092c5bd21e337033799bb`，Maven exit 0；内嵌合成门禁完成 10 个规定反例，容器身份另完成正常双 ID、无关 ID、缺少预期对、预先存在 ID、Maven 非零时部分 ID 和零 ID 检查，并证明非零原始退出码及已生成报告计数会保留。Testcontainers 私有日志中提取的本轮 Ryuk/MySQL 两个完整 ID 均不在前置 inventory，结束 inventory 与其交集为 0，未处理控制器或其他会话容器。

| 本轮报告 | tests | failures/errors/skipped | SHA-256 |
|---|---:|---:|---|
| `TushareMetadataContractTest` | 50 | 0/0/0 | `90ef4fca338600a4a0209690ca14e3f377324b5a75c03626765c656c68cedf07` |
| `FlywaySchemaContractIT` | 52 | 0/0/0 | `6716f327c725fc210df6eb300eefc31c29abb8a6e0b2ae4d399fc48de450213c` |
| `PackagedJarContractTest` | 4 | 0/0/0 | `c5c8e9aa7c1ab6682d0037a858b6b471c35d85a2379b65eddc54dc00ac1cc697` |

Failsafe summary 为 completed 4、errors/failures/skipped 0，SHA-256 `b147141cd8c7e33d2920f5e57398d17dc840856cf8e18be8066e8362d20e4383`。新生产 JAR SHA-256 为 `21a6f6b8e9262c763bc56d364661409e53bc6a56770d91a4a6602aebd0dccc57`；源目录 49 份 YAML 与嵌套 `tensor-plugin-tushare` JAR 中 49 份同名资源逐文件字节哈希一致，没有其他位置副本。成功的 M04 结果级合同表示 49 张生产表；测试 fixture 另增 1 张，合计 50 张业务表、1007 列和 50 个主键。前端回归由同一 Maven 生命周期执行，实际为 20 files / 120 tests passed。

最终 Run6 页面证据 JSON 位于 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-NBBtkE/metadata-evidence.json`，SHA-256 `6dccbf12802864b7c3b40774006601ccbc3b70f55f3b4682a71aa1cbcbfbe699`；2026-09-05T17:33:17.397Z～17:37:43.409Z 实际 49 passed，0 failed/skipped/retry，49/49 API、49/49 dataset、43 必填拦截、6 无参数、下载 POST 0、records GET 0、上游调用 0。每项由页面产生 5 个 metadata GET，共 245 个 HTTP 200：data-sources 98、apis 49、dataset summaries 49、49 个逐项 definition。七组实际分布为 11/7/6/6/9/3/7；filters 实际五组为 `[]` 3、`[ts_code]` 8、`[trade_date]` 3、`[ts_code,trade_date]` 16、`[ts_code,ann_date]` 19。页面不提交合法下载，不执行数据查询，因此本任务不构成 49 个真实 Tushare 下载；真实上游验收属于 M14-T05。

首次页面 Run1 在 JVM 启动前以未初始化哨兵环境失败；修正后 Run2 到达 `stock_basic`，以 Element Plus 枚举外层/输入重复 `aria-required` 暴露了测试定位问题；Run3 通过前三项后，公开 accessibility snapshot 证明非枚举标签需容纳必填星号。三次均保留原失败，分别用条件注入、按精确 role/label 逐控件校验、锚定完整标签正则最小修正，没有删减产品断言。Run4 功能全部通过；其 13 张 PNG 人工审阅发现关闭动画残影，因此功能结果保留、截图不作为最终视觉证据，测试增加公开 popover 收起/展开状态和动画完成等待后重跑。

## 运行安全与环境

原验收 JAR 入口 SHA-256 必须在运行前后保持 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。每次完整页面运行使用新的 `tensor_m14_t04_<随机值>` 空 schema 和只含 CREATE、SELECT、INSERT、UPDATE 的应用账号；浏览器测试只接收三个 `TENSOR_DB_*` 输入与 `ACCEPTANCE_JAR`，JVM 使用测试生成的假 Token 和回环零调用哨兵。schema、host、账号、密码、JDBC、假 Token、原始响应与日志均不进入本文件。

Run6 使用 Node.js v24.15.0、Java 21、Playwright 1.62.1、Chromium 151.0.7922.34。原验收 JAR 前后 SHA-256 均为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。测试正常 SIGTERM 自有 JVM、关闭哨兵及 socket、验证端口释放并完成私有日志扫描，三个 cleanup 标记均为 true。

控制器独立只读验证在 Run6 启动完成后和结束后各执行一次：`/private/tmp/tensor-m14-t04-controller-lutpgcyi/run-6-database-after-startup.json`（SHA-256 `72b27bd256c8e2824a3699461fac738e4628241e34cd7eb32196c8c7860ba501`）与 `/private/tmp/tensor-m14-t04-controller-lutpgcyi/run-6-database.json`（SHA-256 `96c04cba90298d410d5f3b102ab1072af0c9debb3ff9d6e4ab71c0c857242b2d`）均证明 6 次迁移成功、50 张业务表，其中 49 张生产表逐表均为 0 行。运行账号只有目标 schema 上 CREATE、SELECT、INSERT、UPDATE；schema、host、账号、密码、JDBC 和私有响应未写入本文件。控制器按精确名称与所有权标签删除自有 MySQL 容器及匿名卷，并删除私有状态与凭证文件；`cleanup.json` SHA-256 为 `de94bad514e7da3cda749b7796c71a7d5754c408b273ead584e102e11db80543`，三项结果均为 true，结束 Docker inventory 为空。

## 截图人工审阅

最终 Run6 生成以下 13 张 PNG，文件哈希已与 JSON 逐项复核；每张均与控制器已人工接受的 Run5 对应图片逐字节相同，因此人工结论按内容身份转移到 Run6。自动 JSON 如实保留 `manuallyReviewed=false`；13/13 页面无关闭动画残影、无秘密暴露，月份完整显示，`trade_cal` 三个必填错误及首控件焦点正确。

| 截图 | SHA-256 | 本地相对路径（位于 `control-plane/`） |
|---|---|---|
| `download-stock_basic.png` | `fa43e35f30bbe46f279997c4aa3185b4846f2baa02033740bfaaea70c3bed438` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-2ca06-etadataContract-stock-basic-chromium/download-stock_basic.png` |
| `dataset-stock_company.png` | `9c821d68a1a54f99d960dc53b35e99f878ae6e26178117b13eccf5f48aaae683` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-63c2b-adataContract-stock-company-chromium/dataset-stock_company.png` |
| `download-income.png` | `b801abc36588bed7a9981ea530eab723ef8915ef72a0633c0d221700c886bb68` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-e500c-cts-metadataContract-income-chromium/download-income.png` |
| `dataset-balancesheet.png` | `a2125099221a75e75dc3d055a2f50eae6f66bef8c77a3ce3db40310f2aa94b65` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-9847a-tadataContract-balancesheet-chromium/dataset-balancesheet.png` |
| `download-broker_recommend.png` | `c9b1a8b1e38601479a9505b2f13fb40a8da919c375b2aa2ce3cde59f5e6953b8` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-21ba3-taContract-broker-recommend-chromium/download-broker_recommend.png` |
| `download-trade_cal.png` | `a424a9dc5a82143afed0304d778e9f9986d618d05b1c879e622150a44e92e757` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-bd9ce--metadataContract-trade-cal-chromium/download-trade_cal.png` |
| `dataset-margin.png` | `0bceb918754fda95848f38151352dde80744a72e5a509057cdbb954802ba7ed8` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-6c73f-cts-metadataContract-margin-chromium/dataset-margin.png` |
| `download-daily.png` | `a8b5dd68eb10c7dd63ceb8aaf352399e8c1467af20739d608fdd426fc84c008e` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-3b9db-acts-metadataContract-daily-chromium/download-daily.png` |
| `dataset-daily.png` | `daae0f74bddc5825d58c09236926592c3d2c990b3d7500630e16c10bf4a4fa55` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-3b9db-acts-metadataContract-daily-chromium/dataset-daily.png` |
| `download-moneyflow_hsgt.png` | `fdd7432d4b1b264c42983730674b55c01ddb6ac675e880ab4d9533089a17318c` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-8a724-dataContract-moneyflow-hsgt-chromium/download-moneyflow_hsgt.png` |
| `download-stk_managers.png` | `4258d0735a5cc50f846ec75edb95da7cecb9332a73d6dcc79e70b2d88622ae55` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-a45c3-tadataContract-stk-managers-chromium/download-stk_managers.png` |
| `download-pledge_detail.png` | `b501a0def9e25a50ebbb9bca719a51e8c0c7dbb05f28b7576bd668a22ad5195f` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-06b90-adataContract-pledge-detail-chromium/download-pledge_detail.png` |
| `dataset-index_classify.png` | `b7ec79be9b034cbe2b3e59ce2e240b62b9887d27489a2c76dc4ea3318f1e867b` | `node_modules/.cache/tensor-playwright/tushare-metadata-Tushare-4-59984-dataContract-index-classify-chromium/dataset-index_classify.png` |

## 实施文件哈希时点

最终 shell 门禁运行时记录的脚本 SHA-256 为 `4c6cac4f511fc6e9ff277bfa6ba195195904e6b11fed28676706f2f4c366e552`；最终 Run6 使用的 spec SHA-256 为 `8e85b848a2b8000ce4ec35aa89e3dfa9e98f17a892c982547c772d55d3dae39f`，运行前后相同。验证文档自身持续补录实际结果，因此不使用运行中自哈希作为最终内容身份；提交对象由 Git commit 固定。
