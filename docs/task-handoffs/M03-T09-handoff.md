# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M03-T08`
- **Next task:** `M03-T09`
- **Design document:** `docs/task-designs/M03-T09-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M03-T09`
- **Title:** 49/49 名称、字段、参数、键和筛选总契约
- **Goal:** 创建永久 `TushareMetadataContractTest` 构建门禁，通过公开 loader 加载全部 49 份 Tushare Pro YAML，并以 manifest、49 个模板的流式字段投影、PRD/已批准设计参数、TRD 9.4 业务键和已批准 filters 为独立基线，拒绝接口全集、851 列顺序、参数、键、筛选、表名或引用漂移。
- **Scope:** 只创建设计 Files 节指定的一个测试文件；不得修改生产 Java、POM、schema、YAML、模板、manifest、既有测试或其他模块。模板解析必须跳过 `data`，参数/键/filters 期望不得从被测 YAML 或实际定义自举。
- **Acceptance criteria:** 可归因缺测试类 RED 通过；49 次参数化契约调用和一个全局覆盖测试通过；manifest、loader 和三个独立期望 map 均精确覆盖 49 API；字段总数 851 且逐模板同序；定向 50 项、reactor 137/137、`verify`、Enforcer、生产 JAR 排除、范围和格式门禁通过；实现提交精确包含一个测试文件。

## Dependencies

### `M03-T02`

- **Artifact:** `docs/task-designs/M03-T02-design.md`，以及提交 `5fe20a2` 中 `stock_basic`、`stock_company`、`hs_const`、`trade_cal`、`new_share`、`namechange`、`stk_managers`、`broker_recommend`、`index_classify`、`index_member`、`index_member_all` 共 11 份运行时 YAML。
- **Decision:** 冻结 11 API、93 列模板顺序、参数枚举/日期关联/月参数、10 个 COMPOSITE 键、`stk_managers` FINGERPRINT 和精确 filters。
- **Rationale:** 基础与组织模板未完整规定类型及展示契约，已由项目所有者批准的任务设计消除实施时猜测，并把永久 49/49 门禁留给 M03-T09。
- **Constraint:** M03-T09 从 classpath loader 读取实际 YAML，但参数与 filters 期望只能转录该设计；字段期望来自相应模板，键期望来自 TRD 9.4，不得从实际定义反向生成。
- **Usage:** 为参数/filters 显式 map 提供 11 API 基线，并为总集合、字段顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`5fe20a2` 精确包含 11 份 YAML，公开-loader harness、reactor 87/87、`verify`、Enforcer、JAR/范围门禁均已记录通过。

### `M03-T03`

- **Artifact:** `docs/task-designs/M03-T03-design.md`，以及提交 `3c2e977` 中 `daily`、`weekly`、`monthly`、`adj_factor`、`suspend_d`、`daily_basic`、`stk_limit` 共 7 份运行时 YAML。
- **Decision:** 冻结 7 API、62 列模板顺序、必填 `trade_date: DATE`、七个 COMPOSITE 键和统一 `[ts_code, trade_date]` filters。
- **Rationale:** 行情与估值元数据必须保持日期、十进制精度和模板顺序一致，集中总门禁负责持续发现跨文件漂移。
- **Constraint:** `stk_limit` 业务键顺序固定为 `[trade_date, ts_code]`，不得因 filters 顺序而重排；参数期望不得读取实际 YAML。
- **Usage:** 为参数/filters 显式 map 提供 7 API 基线，并为总集合、字段顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`3c2e977` 精确包含 7 份 YAML，公开-loader harness、reactor 87/87、`verify`、Enforcer、JAR/范围门禁和审查均已记录通过。

### `M03-T04`

- **Artifact:** `docs/task-designs/M03-T04-design.md`，以及提交 `c00ea0d` 中 `moneyflow`、`margin`、`margin_detail`、`top_list`、`top_inst`、`block_trade` 共 6 份运行时 YAML。
- **Decision:** 冻结 6 API、71 列模板顺序、`margin` 的 `[exchange_id, trade_date]` 参数与枚举、其余必填交易日参数、六个 COMPOSITE 键和精确 filters。
- **Rationale:** 交易与资金复合身份字段及 `margin` 特例必须由独立字面基线保护，避免宽松引用检查掩盖顺序或枚举漂移。
- **Constraint:** `margin` filters 仅为 `[trade_date]`；其他五项为 `[ts_code, trade_date]`。`top_inst`、`block_trade` 多字段键必须保留 TRD 顺序。
- **Usage:** 为参数/filters 显式 map 提供 6 API 基线，并为总集合、字段顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`c00ea0d` 精确包含 6 份 YAML，公开-loader 检查、reactor 87/87、`verify`、Enforcer、模板/manifest/JAR/范围门禁和审查均已记录通过。

### `M03-T05`

- **Artifact:** `docs/task-designs/M03-T05-design.md`，以及提交 `09967d4` 中 `moneyflow_hsgt`、`hsgt_top10`、`hk_hold`、`slb_len`、`slb_sec`、`slb_sec_detail` 共 6 份运行时 YAML。
- **Decision:** 冻结 6 API、44 列模板顺序、统一必填交易日参数、六个 COMPOSITE 键，以及按是否存在 `ts_code` 划分的 filters。
- **Rationale:** 三个 SLB 空模板仍需完整契约；`hk_hold.code` 与 `hk_hold.ts_code` 的键/筛选职责必须持续区分。
- **Constraint:** `hk_hold` 业务键使用 `code`，filters 使用 `[ts_code, trade_date]`；`moneyflow_hsgt` 与 `slb_len` 仅使用 `[trade_date]`。
- **Usage:** 为参数/filters 显式 map 提供 6 API 基线，并为总集合、字段顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`09967d4` 精确包含 6 份 YAML，空模板断言、公开-loader harness、reactor 87/87、`verify`、Enforcer、JAR/范围门禁和审查均已记录通过。

### `M03-T06`

- **Artifact:** `docs/task-designs/M03-T06-design.md`，以及提交 `73f9278` 中 `income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit`、`fina_mainbz`、`express`、`forecast`、`disclosure_date` 共 9 份运行时 YAML。
- **Decision:** 冻结 9 API、490 列模板顺序、前六项 `[ts_code, ann_date]` 参数、后三项 `[ann_date]` 参数、九个 COMPOSITE 键和精确 filters；`fina_mainbz` filters 仅为 `[ts_code]`。
- **Rationale:** 财务宽表和五个空模板需要低内存、全字段顺序门禁，不能依赖样例数据存在性或只检查字段数。
- **Constraint:** M03-T09 必须流式跳过模板 `data`；不得把 `fina_mainbz` 的参数 `ann_date` 误当成同名列或 filter。
- **Usage:** 为参数/filters 显式 map 提供 9 API 基线，并为总集合、490 列顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`73f9278` 精确包含 9 份 YAML，公开-loader harness、reactor 87/87、`verify`、Enforcer、JAR/范围门禁和审查均已记录通过。

### `M03-T07`

- **Artifact:** `docs/task-designs/M03-T07-design.md`，以及提交 `7cc724e` 中 `dividend`、`repurchase`、`share_float` 共 3 份运行时 YAML。
- **Decision:** 冻结 3 API、30 列模板顺序、统一必填公告日参数、三个 COMPOSITE 键和统一 `[ts_code, ann_date]` filters。
- **Rationale:** 公司行动模板和空 `dividend` 样例仍须在总门禁中按独立模板字段顺序持续验证。
- **Constraint:** 三个业务键字段及顺序分别保持 TRD 9.4 的既定值，不能由统一 filters 顺序派生。
- **Usage:** 为参数/filters 显式 map 提供 3 API 基线，并为总集合、字段顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`7cc724e` 精确包含 3 份 YAML，空模板断言、公开-loader harness、reactor 87/87、`verify`、Enforcer、JAR/范围门禁和两层审查均已记录通过。

### `M03-T08`

- **Artifact:** `docs/task-designs/M03-T08-design.md`，以及提交 `cedb21b` 中 `stk_rewards`、`stk_holdernumber`、`stk_holdertrade`、`top10_holders`、`top10_floatholders`、`pledge_stat`、`pledge_detail` 共 7 份运行时 YAML。
- **Decision:** 冻结 7 API、61 列模板顺序、两项股票代码参数、三项公告日参数、两项空参数、六个 COMPOSITE 键、`pledge_detail` 全 14 字段原序 FINGERPRINT 和精确 filters。
- **Rationale:** 两个空 top-10 模板和全字段可空的 FINGERPRINT 需要永久门禁，防止以空样例为由删字段或改变身份顺序。
- **Constraint:** `pledge_detail` 的 14 个期望键字段必须在测试源码中显式列出，不能由模板或实际 YAML 动态生成；`pledge_stat` filters 仅为 `[ts_code]`。
- **Usage:** 为参数/filters 显式 map 提供 7 API 基线，并为总集合、字段顺序和业务键校验提供实际定义。
- **Readiness evidence:** 权威看板为 `COMPLETED`；`cedb21b` 精确包含 7 份 YAML，公开-loader RED/GREEN、reactor 87/87、`verify`、Enforcer、JAR 7/源目录 49、范围门禁和无发现独立审查均已记录通过。

- **Dependency comparison:** 七个直接依赖的 API 集互不重叠，按 11+7+6+6+9+3+7 合计 49，字段数按 93+62+71+44+490+30+61 合计 851；全部使用 `tushare_pro`、同一公开 loader、`tushare_pro__<api>` 表名公式和默认 batchSize 500。参数与 filters 由各已批准设计分区提供，业务键统一以 TRD 9.4 为权威，字段顺序统一以对应 JSON 模板为权威，不存在未解决冲突。

## Start Here

1. 完整读取 `docs/task-designs/M03-T09-design.md`，以其中测试结构、参数分组、TRD 业务键转录规则、filters 五组、路径/流式解析和失败边界作为唯一实施契约。
2. 核对 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 Global Constraints、Task M03-T09 和 Module Gate。
3. 核对 M03-T01 的公开 loader/test 与模块 POM，不得修改它们；再按设计顺序核对 manifest、PRD A.1～A.8、TRD 9.4 和 M03-T02～T08 设计。
4. 首个实施动作：运行设计 Tests 节的隔离本地仓库安装命令，然后在目标测试类不存在时运行模块级 `-Dtest=TushareMetadataContractTest test`，确认只因 `No tests matching pattern` 非零，取得可归因 RED；随后才创建设计唯一文件。

## Risks

- 测试依赖仓库内模板，只用于构建期；从仓库外单独复制模块执行会因找不到两个根哨兵文件而明确失败。
- 49 个模板可能很大，必须流式读取 `api_name`/`fields` 并跳过 `data`，不得改用 `readTree` 物化完整样例数组。
- 参数、业务键或 filters 的授权变更必须同步修改来源文档、对应任务设计和显式期望；不得改成从被测 YAML 自举期望以消除门禁失败。
- 参数化测试显示计数若因测试框架版本变化，应以 49 次 API 调用全部执行且 0 failure/error/skipped 为结果证据，不得删减覆盖。
