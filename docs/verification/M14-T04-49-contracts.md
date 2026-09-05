# M14-T04 49 数据集契约与页面回归证据

本文件记录 M14-T04 的本轮实际验证。实现基线为 `707215c9495b2652617092c5bd21e337033799bb`；冻结 manifest SHA-256 为 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。七组分类采用 2026-09-04 已批准兼容决定：`basic_organization` 11 项、行情与估值 7 项、交易与资金 6 项、互联互通与转融通 6 项、财务与披露 9 项、公司行动 3 项、股东与治理 7 项。

## 实施范围

- `scripts/verify-49-contracts.sh`：从任意工作目录定位仓库，在权限受限临时目录归档并解压当前 HEAD，净化构建环境后运行唯一 Maven 主门禁；解析本轮精确 XML 和 Failsafe summary，并逐字节核对源 YAML 与生产 JAR 中的 49 份元数据。
- `control-plane/e2e/tushare-metadata.spec.js`：从原样验收 JAR 页面串行执行 49 个 `metadataContract:<api>` 配对用例；逐项验证下载接口、数据集、参数与筛选，禁止下载 POST、records GET 和上游调用。
- 本任务没有修改生产 Java、Vue、YAML、SQL、POM、依赖、配置、manifest、模板、既有测试或验收 JAR。

脚本在每次真实构建前，以同一 XML/ZIP 校验函数执行 11 个合成拒绝探针：缺报告、额外目标 suite、零 tests、skipped、重复 testcase、failure 节点、损坏 XML、计数不匹配、49 总数但错误 API 名、嵌套额外 YAML、重复 ZIP 条目。探针只使用本轮私有临时目录，不产生提交文件。

## 自动验证结果

| 门禁 | 命令 | 实际结果 |
|---|---|---|
| shell 语法 | `sh -n scripts/verify-49-contracts.sh` | exit 0 |
| Node 语法 | `cd control-plane && node --check e2e/tushare-metadata.spec.js` | exit 0 |
| Playwright 发现 | `cd control-plane && npx playwright test e2e/tushare-metadata.spec.js --list` | exit 0；1 文件、49 tests |
| Maven/报告/归档总门禁 | `scripts/verify-49-contracts.sh` | exit 0；2026-09-05T18:32:22.639323Z～18:32:54.525438Z |
| 页面 49 配对矩阵 | `cd control-plane && npx playwright test e2e/tushare-metadata.spec.js` | Run7 exit 0；49 passed、0 failed/skipped/retry，4.5m |
| 格式与范围 | `git diff --check` 及精确三文件检查 | 提交前新鲜复核 exit 0；实施文件精确为三项 |

最终 shell 私有白名单 JSON 位于 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04.BRRlZI0U/verification.json`，SHA-256 为 `06c9698df9bb457691c786a19aa4e9096c29d771ff291eb59b0749740c91c4a2`。本轮基线为 `1a2362434c1243dec418cc6e338361831b5d6f9b`，Maven exit 0；内嵌合成门禁完成 11 个规定反例，容器身份另完成正常双 ID、无关 ID、缺少预期对、预先存在 ID、Maven 非零时部分 ID 和零 ID 检查，并证明非零原始退出码及已生成报告计数会保留。独立同函数探针还证明 Maven 37 在固定安全诊断后仍返回 37。Docker inventory 的 before/after 正式路径均先独立检查 `docker ps` 写入 0600 原始文件成功，再排序到 0600 inventory；两个调用点各以同一函数模拟 Docker exit 37，分别只输出固定安全诊断并返回 37。Testcontainers 私有日志中提取的本轮 Ryuk/MySQL 两个完整 ID 均不在前置 inventory，结束 inventory 与其交集为 0，结束 Docker inventory 为空。

| 本轮报告 | tests | failures/errors/skipped | SHA-256 |
|---|---:|---:|---|
| `TushareMetadataContractTest` | 50 | 0/0/0 | `907712bf192d5516eefd317669a976f8ef8f7496daddd94503876b129fce4d2a` |
| `FlywaySchemaContractIT` | 52 | 0/0/0 | `f84a3b05a3678fe3c37ad6b0c5b28e9593e97c05e75e3c45c12528ec0ee92314` |
| `PackagedJarContractTest` | 4 | 0/0/0 | `28ff7ba7a878cf38a71565aeb2024fdf4b093b5edae39c98ff88577dfb7268d3` |

Failsafe summary 为 completed 4、errors/failures/skipped 0，SHA-256 `b147141cd8c7e33d2920f5e57398d17dc840856cf8e18be8066e8362d20e4383`。新生产 JAR SHA-256 为 `2487993be08fb17462c7ddfec528d0a6aa91b6a31c9fe41d86c79560bc9f90b7`；源目录 49 份 YAML 与嵌套 `tensor-plugin-tushare` JAR 中 49 份同名资源逐文件字节哈希一致，没有其他位置副本。成功的 M04 结果级合同表示 49 张生产表；测试 fixture 另增 1 张，合计 50 张业务表、1007 列和 50 个主键。前端回归由同一 Maven 生命周期执行，实际为 20 files / 120 tests passed，5.12s。

最终 Run7 页面证据 JSON 位于 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-ds9oSf/metadata-evidence.json`，SHA-256 `c76855246a74445d8b097b232ad3bbdece9ee446c0521d0b7350f3806150c783`；2026-09-05T18:01:21.664Z～18:05:49.534Z 实际 49 passed，0 failed/skipped/retry，49/49 API、49/49 dataset、43 必填拦截、6 无参数、下载 POST 0、records GET 0、上游调用 0。每项由页面产生 5 个 metadata GET，共 245 个 HTTP 200：data-sources 98、apis 49、dataset summaries 49、49 个逐项 definition。七组实际分布为 11/7/6/6/9/3/7；filters 实际五组为 `[]` 3、`[ts_code]` 8、`[trade_date]` 3、`[ts_code,trade_date]` 16、`[ts_code,ann_date]` 19。页面不提交合法下载，不执行数据查询，因此本任务不构成 49 个真实 Tushare 下载；真实上游验收属于 M14-T05。

首次页面 Run1 在 JVM 启动前以未初始化哨兵环境失败；修正后 Run2 到达 `stock_basic`，以 Element Plus 枚举外层/输入重复 `aria-required` 暴露了测试定位问题；Run3 通过前三项后，公开 accessibility snapshot 证明非枚举标签需容纳必填星号。三次均保留原失败，分别用条件注入、按精确 role/label 逐控件校验、锚定完整标签正则最小修正，没有删减产品断言。Run4 功能全部通过；其 13 张 PNG 人工审阅发现关闭动画残影，因此功能结果保留、截图不作为最终视觉证据，测试增加公开 popover 收起/展开状态和动画完成等待后重跑。

Run6 后的独立审查用同一监视函数复现 pending request 可越过旧边界，并指出通用日期分支未验证真实浮层。修订以 request/requestfinished/requestfailed 完整跟踪并循环排空运行中新增的响应扫描，在自有 `page.close()` 后再做最终错误检查；日期和月份控件均验证 `aria-haspopup=dialog`、展开状态、`aria-controls` 关联浮层可见和 Escape 关闭。另用 `AggregateError` 保留启动与清理双失败。聚焦同函数探针分别证明 pending request 会阻塞、关闭边界晚到 HTTP 500 会失败、picker 开闭关联成立、主错误和清理错误均保留；最终在新空 schema 完整运行 Run7。

## 运行安全与环境

原验收 JAR 入口 SHA-256 必须在运行前后保持 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。每次完整页面运行使用新的 `tensor_m14_t04_<随机值>` 空 schema 和只含 CREATE、SELECT、INSERT、UPDATE 的应用账号；浏览器测试只接收三个 `TENSOR_DB_*` 输入与 `ACCEPTANCE_JAR`，JVM 使用测试生成的假 Token 和回环零调用哨兵。schema、host、账号、密码、JDBC、假 Token、原始响应与日志均不进入本文件。

Run7 使用 Node.js v24.15.0、Java 21、Playwright 1.62.1、Chromium 151.0.7922.34。原验收 JAR 前后 SHA-256 均为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。测试正常 SIGTERM 自有 JVM、关闭哨兵及 socket、验证端口释放并完成私有日志扫描，三个 cleanup 标记均为 true。

控制器独立只读验证在 Run7 启动完成后和结束后各执行一次：`/private/tmp/tensor-m14-t04-controller-lutpgcyi/run-7-database-after-startup.json`（SHA-256 `f63dbc361d68c4d6dd858034687b959439811253b9b2677e05f031d09287ce26`）与 `/private/tmp/tensor-m14-t04-controller-lutpgcyi/run-7-database.json`（SHA-256 `90b07bf9956bff6b93bc905310922ca2168baae4e168a0378d509bce48416696`）均证明 6 次迁移成功、50 张业务表，其中 49 张生产表逐表均为 0 行。运行账号只有目标 schema 上 CREATE、SELECT、INSERT、UPDATE；schema、host、账号、密码、JDBC 和私有响应未写入本文件。控制器按精确名称与所有权标签删除自有 MySQL 容器及匿名卷，并删除私有状态与凭证文件；`cleanup.json` SHA-256 为 `c99dff35ecff1d6c948571c92d705d8f375dd0712e3d8a1b9059b3b0a21dc07e`，三项结果均为 true，结束 Docker inventory 为空。

## 截图人工审阅

最终 Run7 生成以下 13 张 PNG，文件哈希已与 JSON 逐项复核；每张均与控制器已人工接受的 Run5 对应图片逐字节相同，因此人工结论按内容身份转移到 Run7。自动 JSON 如实保留 `manuallyReviewed=false`；13/13 页面无关闭动画残影、无秘密暴露，月份完整显示，`trade_cal` 三个必填错误及首控件焦点正确。

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

最终 shell 门禁运行时记录的脚本 SHA-256 为 `a3a53f0695fbcd661d615c04343ac8d90493859c5604493c480952c8979a5ce3`；最终 Run7 使用的 spec SHA-256 为 `113e63235b34d97d9e98a012ff479d7971085bd6de838b9c06d62a42982bc722`，运行前后相同。验证文档自身持续补录实际结果，因此不使用运行中自哈希作为最终内容身份；提交对象由 Git commit 固定。
