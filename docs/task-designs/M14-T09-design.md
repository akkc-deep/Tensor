# M14-T09 分红修复与2000档剩余验收——任务设计

权威看板：`docs/task-handoffs/tensor-v1-task-board.md`，Order76。用户2026-09-06明确要求新增一个任务并把剩余工作移交。直接输入为M14-T04的既有契约结果和M14-T05已经形成的实现、真实证据及诊断；不要求未完成的原49目标先完成。

**D-01已确认（2026-09-06）：** 用户对明确提出的四字段指纹/V7迁移/全40复验方案回复“同意”。采用 `FINGERPRINT: [ts_code, end_date, ann_date, div_proc]`，保留各实施阶段，允许元数据规定的空进度，同阶段跨批更新，迁移保留现有业务与来源时间；仅dividend当前验收预期改为ok。以下候选分支均已由此裁决选定，设计达到实施就绪。状态按看板依次执行NOT_STARTED→READY和READY→IN_PROGRESS，用户当前任务执行请求及本次确认构成启动授权。

**当前阶段（D-02/yx5keenc已执行）：** D-01分红修复仍通过，D-02的top10_holders已完成SUCCESS320/320/0、页面末查320和独立DB320闭环。最新完整范围实跑150秒，33通过/1失败/6未运行；top10_floatholders实际SUCCESS280而当前仍要求EMPTY，按合同停止，末次页面查询未执行。证据6ae877f已先独立提交，扫描与自有资源清理通过。用户随后明确同意D-03单接口当前预期修订及完整40项复验；本轮执行限定离线检查和新环境接入，历史失败保留。

## Goal

接手三项剩余工作：确定并修复dividend的记录区分规则；完成适配、数据库迁移、幂等、打包和启动的本地验证；以新冻结包完成2000档40接口的完整页面复验及安全证据归档。此前的28项通过是旧包/旧轮结果，不能与新轮拼接成40项通过。

本任务只承接当前2000档阶段，原49接口中9项排除的未覆盖事实仍保留。M14-T05的历史结果不改写、不因移交标为COMPLETED；未来的性能、安全及发布任务也不在本任务范围。

## Scope

- 先消费既有诊断和已批准的 [ISSUE-007修复设计](../issues/proposals/ISSUE-007-dividend-business-key.md)，按D-01裁决后的明确文件边界实施。
- 已提出的方案是保留不同实施进度，使用ts_code/end_date/ann_date/div_proc的四字段指纹身份，并保留同阶段跨批更新。本次明确确认是采用方案的证据；此前新增任务请求仍仅作移交证据。
- 修复阶段与页面验收阶段分别实施、验证、提交。修复仅服务本次已确认问题及必要兼容接入；不在验收失败后静默删接口、换参数或扩大生产修改范围。
- 完整复验仍为40接口/48原参数样例/80个页面records查询，fixture另计2POST/3查询；所有旧排除项、Token边界、失败停止和清理门禁继承M14-T05设计。
- 不修改原manifest/历史模板/已扫描M14-T05证据，不复用已使用控制目录，不读取用户其他进程环境或终端秘密，不保存真实响应、业务行或截图。

## Approach

### 输入与当前事实

1. M14-T05正式轮1gpnb4ru：139秒，28 passed / 1 failed / 11 did not run，真实POST37/records57，fixture2/3通过，99请求各恰一个完成事件；安全扫描、独立DB核对及自有环境清理通过。完整证据提交e3013b1，SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。
2. stock_company6294行与stk_holdernumber150行已真实通过，ISSUE-005/006关闭，不重新定位这两个已修复问题。
3. shhiyk_p仅dividend原参数诊断：1.56秒、客户端执行1次、sourceRowCount38；ADAPTER_TYPE_INVALID/conflicting_key，rowIndex21，差异字段div_proc/cash_div/cash_div_tax，适配计数未建立。扫描及Java退出通过，无DB、重试或真实行保存。该请求与先前页面失败分开记录。
4. 尚未运行11项：disclosure_date、repurchase、stk_holdertrade、top10_holders、top10_floatholders、new_share、stk_managers、pledge_stat、pledge_detail、index_classify、index_member_all。最终复验包含它们、失败的dividend及此前28项，仍共40项。
5. 当前基线验收包：`/private/tmp/tensor-issue-006-build.2rctzavi/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`。spec SHA `a81df4da7f92c6164062fa29a19902505dd5643c7c948a9aaa021f987220eee5`；manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。

### A. 已完成的设计裁决

D-01已由本次“同意”确认：执行现有四字段指纹方案。既有单次诊断与原参数保持不变，不重复诊断。

采用已确认的四字段指纹方案，完整执行ISSUE-007修复设计的元数据、可空指纹、V7回填、停写和原子主键切换、索引/权限、回退边界及验证要求。该选择已实施并通过本地与真实dividend验证。

### B. 确认后实施修复与本地验证

遵循合成RED→最小修改→GREEN及独立复审。候选四字段方案的生产变更为dividend.yaml、GenericDatasetAdapter.java和新增V7迁移；详细路径、编码、精确数据量合同及关联测试/运行说明范围以ISSUE-007修复设计为准。任何已发布迁移和旧包均保留。

已批准的精确合同：生产V1～V5/V7共6次迁移，acceptance含V6共7次；49/50业务表，1001/1008物理列；851业务字段、46 COMPOSITE/3 FINGERPRINT、41二级索引。必须验证现存业务行及来源时间不变、Java与SQL指纹等价、不同进度并存、同阶段更新、完全重复去重及真正同身份冲突继续失败。旧包不兼容新schema，具体停写/迁移/恢复步骤不得省略。

本轮新包路径为 `/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，实际SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`；基线包保持不变。全部嵌套JAR递归展开共18018文件，较旧18017仅GenericDatasetAdapter.class/dividend.yaml改变及新增V7，前端与依赖逐字节不变。独立复审、合成health及真实结果仍按后续实测门禁判定。

新包在独立源码快照构建并冻结实际SHA，复用原静态前端，完成生产/acceptance归档合同、独立复审与仅health合成Token检查。只使用自有合成库做旧库升级/新建验证，不自动迁移任何外部现有数据库。

### C. 新任务接管既有页面验收

完整读取并继承 [M14-T05设计](M14-T05-design.md) 的40接口集合、所有原参数、页面流程、计数/来源/时间核对、一次性启动、限速、日志扫描、CLI后终检及独立DB清理算法。既有 `control-plane/e2e/tushare-live.spec.js` 继续复用，不复制第二套验收。技术环境名和临时schema前缀M14_T05可保留，以减少无关改动。

执行归属改为M14-T09：新控制器只检查M14-T09的当前IN_PROGRESS与批准后设计/包/manifest/spec固定hash，不能因M14-T05仍BLOCKED而等待旧任务启动；结果明确记录当前执行任务M14-T09和历史来源M14-T05。新真实证据写入 `docs/verification/M14-T09-tushare-live.md`，先以真实秘密集合扫描该精确新文件再提交，不追加或覆盖旧已扫描证据。

D-01轮精确限定dividend的当前预期为ok，历史manifestStatus=empty仍保留；该轮当前分类29ok/11empty，与历史28ok/12empty分列记录。该轮其他接口期望不变，38不是未来固定行数；后续D-02批准的单接口修订见下节。新增包及迁移/最小权限由本任务明确接入，沿用2秒间隔、单worker、零重试。除此之外原技术验收合同不变。

所有本地修复/检查/材料先完成，再由工具通过当前已实际继承的TENSOR_TUSHARE_TOKEN环境执行一次新命令。2026-09-06用户明确授权自行运行命令，工具仅检查变量非空，已确认可用；本地测试子进程净化环境且不继承真实Token。不重复设置Token，不进入长等待确认循环，不复用1gpnb4ru或shhiyk_p。真实错误按实际完成/失败/未运行计数记录，失败停止并使本任务BLOCKED。

### D-02. top10_holders当前验收预期（已确认）

**批准证据（2026-09-06）：** 用户在获知top10_holders实际下载/入库320条、EMPTY断言失败和32/1/7结果后，对“仅调整该接口当前预期为成功且有数据、保留原参数和历史结果、再完整跑40项”的具体方案回复“同意”。本次确认覆盖D-02；既有命令/Token授权持续有效。

nuy4jdhx真实证据已独立提交471dfb0，全文SHA `9d5c283b31f586ee7a3b4fdbda84321fbf3c7e4f55274f9d69955d86353af170`：top10_holders原样例返回SUCCESS，source/insert/update为320/320/0，requestId `01e1ba67-d26a-4e6d-918f-b42c70cb6e91`，164ms；独立DB320。因manifestStatus与当前acceptanceStatus均empty，同函数检查 `empty interface stays empty` 失败，末次页面查询尚未执行，不能将该用例改判通过。其前32项（含dividend38、disclosure_date10、repurchase27、stk_holdertrade44）完整通过；余7项未执行。

已确认的具体修订：只将top10_holders的当前acceptanceStatus改为ok，保留manifestStatus=empty及原manifest/模板/参数。本轮已批准dividend覆盖保持；其余38个接口期望不变。历史分类仍28ok/12empty，修订后的当前分类为30ok/10empty。320仅是本轮测量值，后续要求SUCCESS且sourceRowCount>0，页面、来源时间和独立DB全部按原合同核对，不要求固定320行。未运行的top10_floatholders不能据名称相似提前覆盖。

修订仅触及现有spec固定覆盖和分类断言、当前设计/看板/交接及新的独立证据文件；不改生产代码、新包、业务键、schema或数据库参数。本轮先用同一selector/outcome/finalDataset函数做合成RED/GREEN：恰dividend与top10_holders覆盖、其余empty仍拒绝SUCCESS、固定40/48/9及历史/当前分类分别正确，原manifest及参数不变；语法和发现仍为40。原新包81ad...保持冻结，不为验收预期改动重建二进制。

本篇已扫描真实证据禁止改写。下一轮新证据采用 `docs/verification/M14-T09-tushare-live-rerun-01.md`，写入此前证据及包的精确链接，再用下一轮真实秘密集合扫描该新文件。以新私有控制目录、新空库及更新后的设计/spec/证据/启动器hash接入，完成独立接入复审后，才按明确解阻证据BLOCKED→READY，再单独READY→IN_PROGRESS；已有当前任务执行授权继续有效，不重复请求Token或同一已确认方案。

下一轮仍完整40接口/48原样例/80查询与fixture2/3，不只跑剩余8项、不拼接本轮32通过；任一真实错误继续失败停止。只有完整新轮及全部计数、扫描清理门禁通过才能完成M14-T09，原9项不覆盖及M14-T05原49未完成继续保留。

### D-03. top10_floatholders当前验收预期（已确认）

**批准证据（2026-09-06）：** 用户了解最新33通过/1失败/6未运行、top10_floatholders实际280行与EMPTY要求不符后，对“仅将该接口当前预期改为非空成功、保留原参数与历史记录、再完整复验40项”的具体D-03方案回复“同意”。本次授权增加第三项覆盖；既有命令/Token授权继续有效。

yx5keenc真实证据为 `docs/verification/M14-T09-tushare-live-rerun-01.md`（6ae877f），整篇SHA `6f91d9cbe0f4469a1278ede8de65b56f5841b292141202b88e97f2238948f695`。top10_floatholders原样例返回SUCCESS，source/insert/update为280/280/0，requestId `7dda164d-82b7-435f-a866-55f83d0a90f4`，165ms；独立DB280。当前与历史均empty，触发 `Safe check failed: empty interface stays empty`，只执行页面初查0，末查未执行，因此保留失败。本轮33通过/1失败/6未运行，真实POST42/records67，fixture2/3，114个请求逐ID恰一个完成事件。

已确认修订仅增加top10_floatholders当前acceptanceStatus=ok；保留已批准的dividend与top10_holders覆盖，其他37项期望不变。原manifest、历史empty、模板和全部原参数保持；历史仍28ok/12empty，新的当前分类31ok/9empty。280只是本轮观测值，后续要求SUCCESS且sourceRowCount>0、页面末态/来源时间/独立DB一致，不要求固定280条。

本轮仅修改现有 `control-plane/e2e/tushare-live.spec.js` 的固定覆盖和分类断言及必要控制文档，新结果写独立 `docs/verification/M14-T09-tushare-live-rerun-02.md`；不改生产代码、二进制、schema、业务键或DB参数，不覆盖已扫描的三份历史证据。仍沿用冻结验收JAR81adba...，不重建。

先运行同一selector/outcome/finalDataset合成RED/GREEN：准确三项覆盖、其他状态不变、其余empty仍拒绝SUCCESS、覆盖项拒绝全EMPTY和空末态，历史/当前分类及固定40/48/9、原manifest/参数均正确。使用净化子进程执行 `node --check control-plane/e2e/tushare-live.spec.js` 与control-plane下 `npx playwright test e2e/tushare-live.spec.js --list`，必须exit0且恰40；沿用已验证安全流程，运行新证据路径受影响的启动检查并独立审查新接入材料。

只使用新私有控制目录、新空库和更新后的设计/spec/新证据/启动器hash，旧yx5keenc已使用且清理，不得复用。确认及离线/复审门禁齐备后才BLOCKED→READY，再单独READY→IN_PROGRESS；用户既有命令和环境Token授权持续有效，无需重复配置。完整复验仍40接口/48样例/80查询与fixture2/3、9排除、单worker/零重试/至少2秒间隔，失败停止；不只跑剩余7项、不拼接本轮33通过。

本轮尚未执行6项：new_share、stk_managers、pledge_stat、pledge_detail、index_classify、index_member_all；无当前结果，不提前修改其预期。只有全40新轮与所有计数、扫描清理门禁通过才完成M14-T09；原49目标未完成和9项不覆盖继续保留，不自动准备后继。

### D. 收尾与原任务关系

本任务只有全40和fixture、独立DB、请求事件、安全扫描/清理全部通过，且修复回归/迁移/包门禁齐备，才可IN_PROGRESS→COMPLETED。同步把本轮2000档结果及新证据链接回M14-T05交接，继续保留其原49目标未完成事实；原任务状态如需同步，仅按当时明确证据和看板允许转换处理，不直接BLOCKED→PAUSED/COMPLETED。

不将新任务完成视作原49完成，不自动准备或启动M14-T06。9项排除需用户另行要求覆盖；该未覆盖范围不会因本次任务转移消失。

## Files

- 本任务控制文档：`docs/task-designs/M14-T09-design.md`、`docs/task-handoffs/M14-T09-handoff.md`、权威看板及M14模块任务卡。
- 沿用生产/测试修改范围：`docs/issues/proposals/ISSUE-007-dividend-business-key.md` 的“精确实施范围”；D-01已确认，本轮启用。
- 复用并修改 `control-plane/e2e/tushare-live.spec.js`：批准后的dividend预期、新包冻结hash及执行任务归属；保留既有生命周期和凭证保护。
- 新建 `docs/verification/M14-T09-tushare-live.md`：本任务修复/验证索引和新真实轮安全证据，扫描通过后提交。
- 更新ISSUE-007问题/方案记录和M14-T05交接中的归属与结果链接。原M14-T05证据只读；不提交临时脚本、真实数据、日志、数据库或五个已有target目录。

## Tests

此前任务移交未运行生产测试或真实接口。D-01已确认，下列是本轮必须执行的验证；具体命令和实际报告不能相互替代。

在独立源码快照 `data-plane`、Java21下执行ISSUE-007方案“实施时的精确本地检查入口”的三条Maven命令：合成适配及元数据回归、显式-Dtest选择的迁移/schema/fixture/服务IT、acceptance verify打包合同。核对每个命名类实际执行且零失败/错误/跳过。真实Token不进入这些进程。

包含本次修复且已提交、受保护输入干净的main Git检出根执行：

```sh
sh scripts/verify-49-contracts.sh
node --check control-plane/e2e/tushare-live.spec.js
```

契约脚本自行从HEAD隔离构建；不能在没有.git的普通源码快照执行。它须输出与批准后的新schema一致的数量；旧历史报告不改写。Node语法exit0，新增预期/归属覆盖的同函数反例通过，原安全反例只在受影响时重跑。

在control-plane验证发现：

```sh
npx playwright test e2e/tushare-live.spec.js --list
```

须恰40个Chromium用例。正式命令仍为 `npx playwright test e2e/tushare-live.spec.js --workers=1 --output <本轮独占目录>/playwright`，由新私有启动器安全捕获输出、执行CLI后终检及文档扫描，不能直接在未准备好的环境手动运行。完整成功为40 passed、无失败/skip/retry、48真实POST/80真实查询以及独立fixture2/3，所有计数和门禁均来自该新轮。

## Acceptance

- D-01明确确认并记录；采用的实现与所确认规则相符，未把候选方案默认为批准。
- 适配/旧库迁移/幂等/schema/打包/独立复审与合成health验证均有真实通过证据，新包独立冻结且旧包保留。
- 新轮完整40项、fixture、页面计数与可见行、独立DB、逐requestId完成事件及安全/清理全部通过；此前28项与本轮不拼接计数。
- 新证据在真实Token所在进程完成精确扫描后提交；无真实行/完整响应/秘密/截图入库，所有自有运行资源已清理。
- 已记录9项不覆盖及原49未完成，并完成当前任务交接/状态更新；不自动推进其他预定义任务。

## Risks

- **D-01已解决：** 四字段指纹/V7修复及本地门禁、真实dividend38行页面闭环已通过。
- **D-02已验证：** top10_holders下载/入库/页面末查与独立DB320一致，完整通过；该结论不覆盖下一接口。
- **D-03已确认：** 当前允许dividend/top10_holders/top10_floatholders三项覆盖，其他37项期望不变；离线检查不代表全40真实验收通过。
- 实际诊断只定位首个进度冲突，不能据此证明全部38行或尚未执行11接口已通过。
- 旧schema/旧包兼容、MySQL多DDL非整体事务及nullable指纹语义必须由批准后的方案和真实本地IT覆盖。
- 临时包和安全结果可能失效或被清理；先验证存在性/固定hash。材料缺失应按已提交源码与合同重建新产物，不绕过hash、不要求重复旧诊断。
- 真实权限、频率、网络及上游数据仍可能变化，按原参数失败停止并保留证据；不把有限积分阶段当作完整49验收。
