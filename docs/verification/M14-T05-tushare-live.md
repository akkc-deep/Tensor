# M14-T05 真实 Tushare 页面验收证据

本轮由用户于 2026-09-06 要求按权威看板执行 M14-T05。已完整读取[任务设计](../task-designs/M14-T05-design.md)、[入口交接](../task-handoffs/M14-T05-handoff.md)和任务卡。会话基线 `dcc7c9aaf4efc01c45681945094f6bb31aa6d7ca`，启动状态提交 `2dd3bd0`。本文如实区分本地验证与尚未执行的真实验收。

## 当前2000积分档阶段（2026-09-06，真实验收待启动）

用户明确要求“先把这两个排除，验证可以满足2000档积分的即可”。当前设计和看板已按独立提交 `d573bed`、`96f9604` 恢复至IN_PROGRESS：本轮固定40接口/48原样例/28ok/12empty/80次records查询，fixture另2POST/3查询。`top_inst`、`broker_recommend` 为higher_points；`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`hsgt_top10`、`namechange` 为permission_unverified。本轮9项范围排除不计skip或通过，原49接口目标仍未完成。

本节取代下文历史阶段要求先确认全49权限的恢复条件。已知公开频率决定本轮固定输入 `M14_T05_CALL_INTERVAL_MS=2000`；账户现时权限、额度和上游差异由正式页面结果如实验证，不先发送额外探测、不自动重试或更换原样例。下文49项未运行矩阵及旧启动器记录保留为历史事实；不把它们当作当前运行结果。

修订提交 `83ce0f4efb4772c205f5d03ddfb256f8d83750e7` 仅修改既有spec（72增/9删），SHA-256 `6e31e4d9e567feebdb3f22ee421b43d00202cc56832d0accfeab1dbe87f61ac3`。完整manifest校验仍为49/58/37ok/12empty，随后纯函数固定选择本轮集合；注册、成功门禁和安全证据均使用40/48/80，显式记录9项原因。

| 当前修订的实际本地检查 | 结果 |
|---|---|
| 同函数范围RED/GREEN：`node /tmp/m14-t05-points2000-red-probe.mjs`，使用Node24 | 实施前exit1、缺少选择函数；实施后exit0，精确集合/顺序/排除原因及缺项、重复、错序、样例数、状态反例通过 |
| Node24语法、既有`/tmp/m14-t05-pure-probe.mjs`、用例发现、diff检查 | 均exit0；恰40项Chromium标题且没有排除名 |
| `/usr/bin/python3 /tmp/m14-t05-points2000-missing-env.py` | 外层exit0，实际npx1/最终1保留；1 failed/39 did not run，安全摘要registered40/unexecuted40/manifest58/selected48/排除9；业务POST0、扫描/清理通过 |
| 独立任务审查 `96f9604..83ce0f4` | Spec通过、Quality Approved，无Critical/Important/Minor；仅批准本地实现，不证明真实验收 |
| `python3 /private/tmp/m14-t05-direct-launcher-probe.py` | exit0，10个合成场景：成功、原exit37、损坏JSON（exit0/37）、Popen失败、秘密命中、成功轮意外上下文、工作进程未退出、终检异常、缺Token先于私有输入拒绝；清理/终检顺序与原失败码保留通过 |

新一次性直接启动器位于 `/private/tmp/tensor-m14-t05-control.j9045eey/launch.py`，使用同文档已测终检函数；不再等待聊天确认文件。所有准备完成后才交用户在已有Token的终端执行，立即开始CLI并打印15秒耗时心跳；异常结果解析不能阻止独立DB清理，创建产物后启动失败仍终检，CLI与自有工作进程退出后才扫描。合成探针只验证控制流程，没有真实Token、JVM、数据库或业务调用。

本轮已实际准备独占回环MySQL8.4.6空schema，独立CLI验证初始0表、utf8mb4/utf8mb4_0900_as_cs、恰CREATE/INSERT/SELECT/UPDATE权限、实际来源host且非%。私有连接材料仅0600保存于0700控制目录，不含Token、不提交。最新只读复核确认容器所有权、MySQL8.4.6/0表、Java21、8080空闲、脚本权限/语法及冻结spec/JAR/manifest哈希；首次受沙箱Docker访问限制，窄范围提权核对通过。迁移后6/50/全空、真实页面和终态行数仍未测量。

整体独立审查覆盖修订spec、当前证据、一次性启动器及合成探针，结论为Approved for local launch readiness，无Critical/Important启动问题；真实40/48验收仍待执行。当前启动器SHA-256为 `1423f0ebfa6cd48a6e818b6eccf13f593b23e39ce68b0dd9722663c5ea5fb263`；文档shell/Python语法和终检函数与已提交版本逐字节一致检查通过。运行配置将固定当前spec/证据/JAR/manifest哈希和精确40/9集合，阻止审查后内容变化时启动。

当前工具不继承用户终端环境，未读取其他进程Token；因此用户仍需在保有Token的终端执行一次新命令。启动器会在CLI/工作进程结束后终检、独立核对表计数、精确清理自有数据库/卷/连接材料，再在该Token环境下扫描整个候选证据并追加安全报告。只有真实40全部通过及所有门禁通过才能报告当前阶段完成，原任务随后PAUSED；真实失败进入BLOCKED。当前尚未开始实际验收，也未声明真实凭证终检通过。

## 运行条件与输入（原49阶段历史）

- 首次本地实施时仅检查规定环境变量是否非空：`TENSOR_TUSHARE_TOKEN`、`M14_T05_CALL_INTERVAL_MS` 和三个 `TENSOR_DB_*` 均未配置，没有读取或输出凭证值。
- 已请求运行者确认账户覆盖全部49接口、分钟/小时限制、至少58次剩余额度及合规的最小毫秒间隔；尚未收到这些事实，不能猜测速率或发送真实请求探测权限。
- Node.js 24.15.0（从本机已有 nvm 安装使用）、Java 21.0.11、Python 3.11.5、Playwright 1.62.1；没有安装或修改依赖。
- manifest SHA-256 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`；49唯一接口、58组参数、37个ok/12个empty，与设计一致。只读取manifest，没有读取模板data。
- 原验收JAR存在且SHA-256为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。本轮没有构建、替换或运行该JAR。

## 恢复准备（未实跑）

2026-09-06，用户报告已设置Token，但当前工具子进程的规定Token/间隔存在性检查仍为false；原生终端工具以安全限制拒绝访问，未读取用户终端的Token或绕过限制。账户权限、频率与额度仍未确认。

已用本地MySQL8.4.6镜像实际准备新的独占回环容器/schema。独立CLI检查：版本8.4.6、初始0表、utf8mb4/utf8mb4_0900_as_cs、应用schema权限恰CREATE/INSERT/SELECT/UPDATE；通过当前连接的IPv4映射地址核对来源host，未授予`%`。两次准备失败的自有容器/卷均已正常停止并精确清理；最终环境暂存等待用户终端启动器。准备安全证据为 `/private/tmp/tensor-m14-t05-control.c0f2ywas/database-preflight.json`，连接材料仅存同一0700目录内0600临时文件，未输出/提交，未写入Token。

Java21、8080空闲、原验收JAR/manifest/spec冻结哈希重新检查通过。临时启动器语法、缺Token先于DB访问拒绝、0600/0700模式及看板状态门禁验证通过；所复用CLI终检函数与已有已测函数AST一致。启动器由用户在已设置Token的终端运行并先等待账户确认和控制器状态转换；其真实运行尚未发生，不能声称完整启动器/数据库迁移/真实凭证扫描已通过。

本次准备新增的可观察事实仅为独占空库与终端恢复路径；权威状态仍BLOCKED，49接口/58真实POST/98查询与fixture均未运行。后续操作和临时资源所有权入口见[当前交接](../task-handoffs/M14-T05-handoff.md#resume-preparation-2026-09-06)。

用户执行终端启动器后的只读复核：安全就绪文件`tokenPresent=true`、初始0表，登记PID对应的启动器仍存活；本轮容器所有权、MySQL8.4.6、当前0表、8080空闲和三个冻结哈希均通过。没有读取进程环境或Token值。账户输入/完整就绪/启动门禁/运行开始文件仍均不存在；当前仅Token传递已解决，账户权限/额度/频率确认仍缺失，未启动JVM或任何业务用例。

## 2000积分档位的公开权限核对（未调用数据接口）

用户后续说明账户为“2000+积分”。本次只访问Tushare官网公开文档，不携带Token、不调用任何数据API，也不读取账户页面。读取[积分权限总览](https://tushare.pro/document/1?doc_id=108)、[积分频次表](https://tushare.pro/document/1?doc_id=290)及manifest49接口的文档地址：45页实际包含对应API说明，4个原文档地址返回“404, 文档不存在！”正文；这不是对API可用性或账户授权的实际测量。

| 当前任务中的具体问题 | 官方页面的实际说明 | 对恢复的影响 |
|---|---|---|
| `top_inst` | [接口页](https://tushare.pro/document/2?doc_id=107)明确至少5000积分；权限总览仍写2000 | 存在官方页面不一致，不能以2000档推断有权限，也不能将权限缺失记为已实测 |
| `broker_recommend` | [接口页](https://tushare.pro/document/2?doc_id=267)明确达到6000积分；频次总表把券商月度金股列在10000档特色数据中 | 2000档不能证明覆盖，且不能承诺6000就满足全部当前规则；需确认实际授权 |
| `share_float` | [接口页](https://tushare.pro/document/2?doc_id=160)写120积分；权限总览写3000 | 记录冲突，不能把任一页面当成账户授权证明 |
| `hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member` | 原文档地址[104](https://tushare.pro/document/2?doc_id=104)、[47](https://tushare.pro/document/2?doc_id=47)、[188](https://tushare.pro/document/2?doc_id=188)、[182](https://tushare.pro/document/2?doc_id=182)均返回文档不存在正文 | 当前公开页面不能确认这些旧接口的权限或可用性；不替换为新API、不改manifest |
| `hsgt_top10`、`namechange` | [48](https://tushare.pro/document/2?doc_id=48)与[100](https://tushare.pro/document/2?doc_id=100)页面有对应API说明，正文未明示最低积分 | 不把缺少限制文字解释为无限制授权 |

官网明确积分是分级门槛，并不随调用消耗。频次表对2000以上档写每分钟200次、每天100000次/个API；本轮58次是49API参数样例的页面提交数，不是需要扣除58积分。账户本日实际使用量未读取；总表没有给出普通积分接口的独立每小时上限，不能自行补造。具体接口限制优先核对，例如[stock_basic](https://tushare.pro/document/2?doc_id=25)仅每分钟50次。因此即便多数普通接口可按公开限制安排较低频率，也不能据此生成“全部49权限已确认”的运行输入。

当前明确待确认的是这些具体权限/文档差异及账户实际限制，而不是再次设置Token。已向用户具体询问top_inst、broker_recommend是否已有访问权限；没有把回答“2000+积分”扩大为全49权限、剩余额度和间隔均已确认。未写`confirmed-inputs.json`或启动门禁，看板保持BLOCKED、真实矩阵仍未开始。公开页面快照和逐API摘录仅暂存在本机临时文档目录，未作为任务新增分发文件提交。

等待进程结束后的安全结果记录为`KeyboardInterrupt`、`ownedContainerRemoved=true`，私有DB连接文件已删除，运行开始/结束标志均不存在；这是启动器报告的正常中断清理结果，没有JVM、业务调用或真实验收结果。旧Token-ready文件已成为历史证据，不可用于未来解阻。追加文档复核确认49条公开API审计、45有效API说明及内嵌已测终检函数AST一致；临时DB连接材料已随清理删除，未在此次追加后重新进行凭证字面扫描，不声称真实Token扫描通过。

## 实施与静态检查

`739e128`（`test(release): verify live Tushare interfaces`）只新增 `control-plane/e2e/tushare-live.spec.js`，模式100644。该时点SHA-256为 `d3aafc7b3aa14311bc691fdb37ac105473598cd06c4d1d7a58949d3bd13238a5`。实现包含49项无条件注册、58样例串行页面流程、独立fixture准备、页面/请求/计数/来源时间核对、环境及日志隔离和正常停机；后续审查修订已闭环（见下文）；真实验收仍未执行。

| 检查 | 实际命令 | 结果 |
|---|---|---|
| Node24语法 | `cd control-plane && node --check e2e/tushare-live.spec.js` | exit0 |
| 用例发现 | `cd control-plane && npx playwright test e2e/tushare-live.spec.js --list` | exit0；49 Chromium tests / 1 file |
| 同函数纯本地反例 | `node /tmp/m14-t05-pure-probe.mjs`，使用Node24 | exit0；`M14-T05 pure counterexample probes: PASS` |
| CLI终检函数 | `python3 .superpowers/sdd/M14-integration-release/probe-terminal-scan.py` | exit0；10项通过 |
| Chromium合成失败产物 | `python3 .superpowers/sdd/M14-integration-release/probe-playwright-terminal.py` | 外层exit0；故意失败的npx1与最终1均保留 |
| 正式spec缺环境拒绝 | `python3 .superpowers/sdd/M14-integration-release/probe-missing-environment.py` | 外层exit0；npx1、最终1，1 failed / 48 did not run；未执行任何业务用例 |
| 文档内可复跑代码 | 提取2个shell片段执行`sh -n`，2个Python片段执行`ast.parse` | 均通过；内嵌终检与已测试代码逐字节一致，49个未运行API行完整 |

纯本地反例直接使用正式函数的VM前缀，覆盖manifest错误hash/缺项/多项/重复API/未知status/非法params、多样例ok允许部分EMPTY但全空拒绝、empty拒绝SUCCESS、历史row_count不参与当次计数、不同键写入少于上游行数合法、重复requestId/额外POST/缺登记查询/pending请求拒绝、插入累计不符/来源错误、跨chunk秘密与items包络键拒绝。没有在正式spec引入环境开关或skip入口。

缺环境拒绝轮使用全新自有0700目录 `/private/tmp/tensor-m14-t05._v16zhkp`，隐藏捕获CLI输出后运行同一终检。首个固定前置失败为 `live token supplied`；没有向Java注入Token、启动JVM或建立业务连接。CLI也记录了尚未初始化的输入/日志及证据清理检查失败；原失败全部保留。终检scanPassed/cleanupPassed均true、全部自动产物删除、8080空闲，spec哈希前后相同。该轮的1失败/48未运行是前置拒绝结果，不是执行49接口后的产品结论。

## 本地验证

终检代码先用临时探针检查缺失实现，实际exit1（`terminal_scanner_not_implemented`）；实现后，同一 `finalize` 函数的10项探针exit0：安全成功、保留原exit37、秘密原文、JSON转义秘密、晚生成秘密、成功轮异常上下文、目录符号链接不跟随、意外文件、FIFO不读取、泄漏时仍保留exit37。探针均使用临时合成输入，清理自有目录。

当前Playwright的真实合成页面失败探针实际为：npx exit1、终检exit1、外层探针exit0；page/context均已关闭、afterAll已完成，仍枚举到1个自动 `error-context.md` 和1个路径附件。在CLI/全部worker退出后另写合成秘密文件，终检命中并删除；全部Playwright自动产物被删除，原失败码未被扫描成功覆盖。页面仅含合成秘密和合成行，没有启动应用/数据库或调用Tushare。

探针首次被macOS沙箱的Chromium bootstrap限制阻止，窄范围工具提权后浏览器可正常运行。随后定位到 `testInfo.attach({body})` 在此版本保留内存附件、不创建路径附件，探针改为真实path附件后完成上述验证；这是临时探针修正，不是产品缺陷。两次失败探针遗留的自有目录也已扫描清理。

本地探针源码与安全结果保留于忽略目录 `.superpowers/sdd/M14-integration-release/`，不作为新增产品helper或额外live用例提交。终检的可复跑代码完整嵌入下文。

## 独立审查与修订

对 `739e128` 的独立审查提出4项Important和1项Minor：缺少先于外层Playwright超时的内部工作/共享清理期限；日志扫描在换行处分段丢失秘密重叠；失败轮用已通过/已投影数代替实际执行/POST计数；ApiError.code未验证公开枚举；前置失败重复终结引起额外清理报错。控制器使用冻结版本、合成秘密和内存sink独立复现换行缺口，结果为拒绝false/完整合成秘密持久化到内存true，没有真实凭证或磁盘泄漏。修订 `d378ad2` 增加内部绝对期限和共享清理预算、跨换行/UTF-8字节重叠、独立实际请求与用例计数、16项公开错误码/fieldErrors校验，以及可重复终结的阶段检查。扩展同函数反例、语法、49项发现和diff检查均exit0。复审发现新引入的半行日志提前关联问题，随后由 `a9bf981` 定点修正为仅暴露完整换行行。第二次定点复审确认Addressed、无新问题，结论为 `Approved for local readiness`；原4项Important、1项Minor及新1项Important全部闭环，没有未解决的本地审查问题。终检代码与证据真实性未发现其他问题。

控制器在该修订上重新执行缺环境CLI，私有目录 `/private/tmp/tensor-m14-t05.xij2s9th`，spec SHA-256 `99780152e656ec3e1e5464c9eb8da97a2daf1c8e850bd7a12e37b73c754d2380` 前后一致。原始/最终退出码均1，仍为1 failed / 48 did not run，但只剩固定 `live token supplied` 前置失败，没有额外清理或证据写入错误。安全计数准确为attempted/failed/completed均0、unexecuted49；观察到的四类业务请求计数均0，应用日志0字节，cleanup三项true、8080空闲、自动产物无残留。这里的0仅为该前置拒绝轮观察到的请求数，不代填真实数据行、耗时或上游结果。


最终spec提交为 `a9bf981`，SHA-256 `f7f3c315913bc19b8e2d59ab7ca07e82e4d3bdcd58d7ed86ea0545fbbb47fb90`。该版语法、扩展同函数反例、49项Chromium发现和 `git diff --check` 均exit0。新增日志分块反例证明首块0匹配、第二块恰1条完整事件，已完整但暂缓落盘的事件仍能关联。`d378ad2` 的缺环境CLI证明前置拒绝和幂等清理；最后一次窄修改只改变待写日志的完整行暴露，未重复运行不受影响的缺环境/Chromium产物门禁，也没有真实矩阵结果。

最终只读核对：spec工作区内容与提交对象一致；manifest/原JAR哈希仍为上述固定值；实施提交区间只增加指定spec，第二指定文件为本证据文档。2个内嵌shell片段、2个Python片段语法通过，终检与已测试函数字节相同，49个未运行API行完整。首次阻塞时实际环境仍未配置Token、调用间隔和三个DB变量，账户确认仍未提供；记录为M14-T05外部环境阻塞，不宣称任务验收完成。

## 真实矩阵实际状态

本轮真实49接口均未执行；58次真实页面POST、98次真实dataset查询及fixture的2POST/3查询均未启动。恢复准备已创建专用schema并独立测得初始0表，尚未启动JVM、迁移或上游替身；没有真实业务行、请求ID、耗时、迁移后表计数或页面匹配结果可供验收，不能用0或前序任务的历史结果代填未测量值。以下仅列manifest身份和本轮未执行状态。

| API | 设计接口级预期 | 样例数 | 本轮实际 |
|---|---|---:|---|
| `stock_basic` | ok | 3 | 未运行 |
| `stock_company` | ok | 3 | 未运行 |
| `hs_const` | ok | 2 | 未运行 |
| `income` | empty | 1 | 未运行 |
| `balancesheet` | empty | 1 | 未运行 |
| `cashflow` | empty | 1 | 未运行 |
| `fina_indicator` | empty | 1 | 未运行 |
| `fina_audit` | empty | 1 | 未运行 |
| `fina_mainbz` | ok | 1 | 未运行 |
| `stk_rewards` | ok | 1 | 未运行 |
| `stk_holdernumber` | ok | 1 | 未运行 |
| `broker_recommend` | ok | 1 | 未运行 |
| `trade_cal` | ok | 3 | 未运行 |
| `margin` | ok | 3 | 未运行 |
| `daily` | ok | 1 | 未运行 |
| `weekly` | ok | 1 | 未运行 |
| `monthly` | empty | 1 | 未运行 |
| `adj_factor` | ok | 1 | 未运行 |
| `suspend_d` | ok | 1 | 未运行 |
| `daily_basic` | ok | 1 | 未运行 |
| `moneyflow` | ok | 1 | 未运行 |
| `stk_limit` | ok | 1 | 未运行 |
| `moneyflow_hsgt` | ok | 1 | 未运行 |
| `hsgt_top10` | ok | 1 | 未运行 |
| `hk_hold` | ok | 1 | 未运行 |
| `top_list` | ok | 1 | 未运行 |
| `top_inst` | ok | 1 | 未运行 |
| `margin_detail` | ok | 1 | 未运行 |
| `block_trade` | ok | 1 | 未运行 |
| `slb_len` | empty | 1 | 未运行 |
| `slb_sec` | empty | 1 | 未运行 |
| `slb_sec_detail` | empty | 1 | 未运行 |
| `forecast` | ok | 1 | 未运行 |
| `express` | ok | 1 | 未运行 |
| `dividend` | empty | 1 | 未运行 |
| `disclosure_date` | ok | 1 | 未运行 |
| `repurchase` | ok | 1 | 未运行 |
| `share_float` | ok | 1 | 未运行 |
| `stk_holdertrade` | ok | 1 | 未运行 |
| `top10_holders` | empty | 1 | 未运行 |
| `top10_floatholders` | empty | 1 | 未运行 |
| `new_share` | ok | 1 | 未运行 |
| `namechange` | ok | 1 | 未运行 |
| `stk_managers` | ok | 1 | 未运行 |
| `pledge_stat` | ok | 1 | 未运行 |
| `pledge_detail` | ok | 1 | 未运行 |
| `index_classify` | ok | 1 | 未运行 |
| `index_member` | ok | 1 | 未运行 |
| `index_member_all` | ok | 1 | 未运行 |

## 运行与CLI退出后终检命令

下列为基础执行及固定终检代码，**尚未用于真实矩阵**。当前阶段由上述直接启动器编排，运行者先完成修订设计规定的新空MySQL8.4.6 schema/最小权限账号、独立只读初始和结束表计数，以及私密环境注入；Token只能通过 `TENSOR_TUSHARE_TOKEN`，不写文件或聊天。采用原验收JAR绝对路径、明确的 `M14_T05_CALL_INTERVAL_MS` 和Node24。首次失败停止，不自动重试、不换日期或参数。

spec仅写 `run/application.log` 和 `run/safe-results.json`。终检在npx和所有worker完全退出后运行；目录不跟随符号链接，异常文件/对象直接失败，泄漏文件删除，全部Playwright产物删除。成功轮存在上下文/附件视为失败；`.last-run.json`只作为会删除的正常运行索引。保留的白名单日志/JSON均在本机私有目录，不能发布原日志。

```sh
cd control-plane
set +x
umask 077
M14_T05_ARTIFACT_DIR=$(mktemp -d "${TMPDIR:-/tmp}/tensor-m14-t05.XXXXXXXX") || exit 1
export M14_T05_ARTIFACT_DIR
tensor_m14_t05_exit=0
npx playwright test e2e/tushare-live.spec.js --workers=1 \
  --output "$M14_T05_ARTIFACT_DIR/playwright" \
  >"$M14_T05_ARTIFACT_DIR/runner.log" 2>&1 || tensor_m14_t05_exit=$?
python3 - "$tensor_m14_t05_exit" <<'PY'
import json
import os
from pathlib import Path
import re
import stat
import sys


def finalize(root, original_exit, secrets):
    root = Path(root)
    info = root.lstat()
    if (not root.is_absolute() or not re.fullmatch(r'tensor-m14-t05\.[A-Za-z0-9_-]+', root.name)
            or not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid()
            or stat.S_IMODE(info.st_mode) != 0o700):
        raise ValueError('artifact_root_rejected')
    needles = set()
    for secret in filter(None, secrets):
        needles.add(secret.encode())
        for ascii_only in [True, False]:
            needles.add(json.dumps(secret, ensure_ascii=ascii_only)[1:-1].encode())
    result = {'npxExitCode': original_exit, 'filesScanned': 0, 'deletedArtifacts': 0,
              'scanPassed': True, 'cleanupPassed': True}
    allowed_files = {'runner.log', 'post-cli-summary.json',
                     'run/application.log', 'run/safe-results.json'}

    def remove(path):
        try:
            path.unlink()
            result['deletedArtifacts'] += 1
        except OSError:
            result['cleanupPassed'] = False

    def walk(directory):
        try:
            entries = list(directory.iterdir())
        except OSError:
            result['scanPassed'] = result['cleanupPassed'] = False
            return
        for path in entries:
            relative = path.relative_to(root).as_posix()
            automatic = relative.startswith('playwright/')
            try:
                entry = path.lstat()
                if stat.S_ISDIR(entry.st_mode):
                    unexpected_dir = relative not in {'run', 'playwright'} and not automatic
                    if unexpected_dir:
                        result['scanPassed'] = False
                    walk(path)
                    if relative == 'playwright' or automatic or unexpected_dir:
                        try:
                            path.rmdir()
                        except OSError:
                            result['cleanupPassed'] = False
                    continue
                if not stat.S_ISREG(entry.st_mode) or entry.st_nlink != 1:
                    result['scanPassed'] = False
                    remove(path)  # unlink an unexpected link/object itself; never follow it
                    continue
                result['filesScanned'] += 1
                flags = os.O_RDONLY | os.O_NOFOLLOW
                descriptor = os.open(path, flags)
                with os.fdopen(descriptor, 'rb') as source:
                    opened = os.fstat(source.fileno())
                    if (opened.st_dev, opened.st_ino) != (entry.st_dev, entry.st_ino):
                        raise ValueError('artifact_identity_changed')
                    overlap = max(map(len, needles), default=1) - 1
                    tail = b''
                    leaked = False
                    while True:
                        chunk = source.read(1024 * 1024)
                        if not chunk:
                            break
                        data = tail + chunk
                        if any(value in data for value in needles):
                            leaked = True
                        tail = data[-overlap:] if overlap else b''
                unexpected = relative not in allowed_files and not automatic
                # Playwright's run bookkeeping is deleted, but is not a context/attachment.
                success_context = automatic and relative != 'playwright/.last-run.json' and original_exit == 0
                if leaked or unexpected or success_context:
                    result['scanPassed'] = False
                if leaked or unexpected or automatic:
                    remove(path)
            except (OSError, ValueError):
                result['scanPassed'] = False
                remove(path)

    walk(root)
    summary = root/'post-cli-summary.json'
    try:
        if summary.exists() or summary.is_symlink():
            summary.unlink()
        descriptor = os.open(summary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            json.dump(result, output, sort_keys=True)
            output.write('\n')
    except OSError:
        result['cleanupPassed'] = False
    code = original_exit or (0 if result['scanPassed'] and result['cleanupPassed'] else 1)
    return code, result


if __name__ == '__main__':
    original = 1
    try:
        original = int(sys.argv[1])
        if not 0 <= original <= 255:
            raise ValueError('exit_code_rejected')
        secrets = [os.environ.get(name, '') for name in
                   ['TENSOR_TUSHARE_TOKEN', 'TENSOR_DB_PASSWORD', 'TENSOR_DB_USERNAME', 'TENSOR_DB_URL']]
        code, summary = finalize(os.environ['M14_T05_ARTIFACT_DIR'], original, secrets)
        print(json.dumps(summary, sort_keys=True))
    except Exception:
        code = original or 1
        print('M14_T05_TERMINAL_SCAN_FAILED')
    sys.exit(code)
PY
```

待真实证据补录后，在仓库根用同一环境秘密集合检查这个精确文档；任何命中只输出固定标志，不输出值，保留失败并清除泄漏内容：

```sh
python3 - <<'PY'
import json, os
from pathlib import Path
body = Path('docs/verification/M14-T05-tushare-live.md').read_bytes()
values = [os.environ.get(k, '') for k in
          ['TENSOR_TUSHARE_TOKEN', 'TENSOR_DB_PASSWORD', 'TENSOR_DB_USERNAME', 'TENSOR_DB_URL']]
needles = {v.encode() for v in values if v}
for v in filter(None, values):
    needles.update(json.dumps(v, ensure_ascii=a)[1:-1].encode() for a in [True, False])
if any(v in body for v in needles):
    raise SystemExit('M14_T05_EVIDENCE_SECRET_SCAN_FAILED')
print('M14_T05_EVIDENCE_SECRET_SCAN_PASSED')
PY
```

当前阶段运行后须独立验证新空库、6迁移/50业务表、40生产表末行数与页面总数一致、9个排除表仍空、fixture1行，核对全部请求完成事件，正常停机并清理本轮精确自有资源。全40通过、零失败/未执行/重试和全部扫描/清理门禁通过后，仅记录2000档阶段完成并将原任务PAUSED；原49目标未完成，不准备后继。真实失败则记录证据并BLOCKED。

## 2000档实际运行 2026-09-06T04:43:22.473702+00:00

以下为本次启动器在CLI退出、终检和清理后记录的实际结果；空值表示未测量，原全49目标仍不完整。

```json
{
  "controllerFailure": null,
  "databaseAfterMigration": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 0,
      "fixture_daily": 0,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 0,
      "stock_basic": 0,
      "stock_company": 0,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 6
  },
  "databaseFinal": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 0,
      "fixture_daily": 0,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 0,
      "stock_basic": 0,
      "stock_company": 0,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 6
  },
  "elapsedSeconds": 8,
  "finalExitCode": 1,
  "npxExitCode": 1,
  "ownedContainerRemoved": true,
  "ownedWorkersExited": true,
  "postCliScan": {
    "cleanupPassed": true,
    "deletedArtifacts": 2,
    "filesScanned": 5,
    "npxExitCode": 1,
    "scanPassed": true
  },
  "scopeId": "points-2000",
  "selectedPageCountsAndExcludedEmptyMatched": false,
  "specResults": {
    "cleanup": {
      "immutableInputs": true,
      "jvmStopped": true,
      "logScanned": false,
      "networkDrained": true
    },
    "command": "npx playwright test e2e/tushare-live.spec.js --workers=1",
    "downloads": [],
    "finishedAt": "2026-09-06T04:43:20.146Z",
    "fixture": [],
    "inputs": {
      "gitCommit": "c10c67bf2a0d6dc4f90b1d40442ef68d355f0c92",
      "jarSha256": "a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac",
      "manifestSha256": "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2",
      "specSha256": "6e31e4d9e567feebdb3f22ee421b43d00202cc56832d0accfeab1dbe87f61ac3"
    },
    "queries": [],
    "scope": {
      "excludedInterfaces": [
        {
          "apiName": "top_inst",
          "reason": "higher_points"
        },
        {
          "apiName": "broker_recommend",
          "reason": "higher_points"
        },
        {
          "apiName": "share_float",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hs_const",
          "reason": "permission_unverified"
        },
        {
          "apiName": "moneyflow_hsgt",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hk_hold",
          "reason": "permission_unverified"
        },
        {
          "apiName": "index_member",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hsgt_top10",
          "reason": "permission_unverified"
        },
        {
          "apiName": "namechange",
          "reason": "permission_unverified"
        }
      ],
      "id": "points-2000",
      "manifestCases": 49,
      "manifestSamples": 58,
      "selectedCases": 40,
      "selectedSamples": 48
    },
    "startedAt": "2026-09-06T04:43:15.276Z",
    "task": "M14-T05",
    "totals": {
      "attemptedCases": 0,
      "callIntervalMs": 2000,
      "completedCases": 0,
      "failedCases": 0,
      "fixtureDownloadPostsObserved": 0,
      "fixtureRecordsGetsObserved": 0,
      "liveDownloadPostsObserved": 0,
      "liveDownloadResultsRecorded": 0,
      "liveQueryResultsRecorded": 0,
      "liveRecordsGetsObserved": 0,
      "manifestSamples": 58,
      "registeredCases": 40,
      "selectedSamples": 48,
      "unexecutedCases": 40
    },
    "version": 1
  },
  "task": "M14-T05"
}
```

## 启动日志阻塞的本地修复（2026-09-06）

用户要求由执行者处理后，按修订设计完成本地诊断和修复。上面的真实失败报告保持原文，其已扫描版本在da56d38中，整篇旧SHA为814bae86e823c0f639e5c376056401c08fb778479f4ad33ee17e4e15a0f629cf；以下追加内容尚未在用户真实Token环境扫描，不复用旧文件哈希宣称当前整篇扫描通过。

从当前spec生成本机临时探针，保留同一前置检查、原JAR、JVM环境、health和正常停机函数；不注册或执行live用例，不调用下载/records、不运行上游替身。诊断仅使用控制器新生成的合成Token，通过专门子进程TENSOR_TUSHARE_TOKEN环境传入；没有读取用户Token。日志命中只投影固定类别和已知框架标识布尔值，原文由现有保护逻辑丢弃。每次都使用新的独占MySQL8.4.6空schema。

| 同一原JAR启动探针 | 实际结果 |
|---|---|
| RED：`python3 /private/tmp/m14-t05-startup-diagnostic.py /private/tmp/tensor-m14-t05-control.gsu6eluc` | 外层0、Node1；health未就绪，secretScan/FlywayMention/JDBCMarker=true；包络/长度/其他扫描标记false。6成功迁移、50业务表全空，JVM停止、终检及精确DB/卷清理通过 |
| GREEN：同命令，控制目录换为`/private/tmp/tensor-m14-t05-control.dwbbqgr3` | 外层0、Node0；health就绪，无任何扫描触发；6成功迁移、50业务表全空，JVM停止、终检及精确DB/卷清理通过 |

根因是第三方Flyway的INFO启动日志包含JDBC连接位置，与本任务禁止保存连接位置的日志合同冲突；本地RED复现了该路径。修复提交b8cc305d33590b07c3cd945849b6e0f6384a9702只在spec的applicationEnvironment新增一行固定 `LOGGING_LEVEL_ORG_FLYWAYDB=WARN`。它仅作用于JVM中的Flyway包，保留WARN/ERROR、全部业务完成日志、原argv和所有扫描规则；不修改生产文件、原JAR、模板、接口范围、样例或请求计数。

同函数环境探针 `/tmp/m14-t05-flyway-env-probe.mjs` 在修复前因值undefined而RED；修复后GREEN，核对固定WARN覆盖外部同名TRACE、全局/业务日志覆盖不继承、DB/Token仅进入JVM、浏览器/辅助publicEnvironment不含这些输入。Node24语法、该环境探针、既有 `/tmp/m14-t05-pure-probe.mjs` 和diff检查均exit0；既有合成秘密扫描反例仍拒绝命中。此一行不改变注册或浏览器行为，未重复无关全套测试。独立定点审查Spec通过、Quality Approved、Ready，无Critical/Important/Minor。

修复后spec SHA-256为72b9763941e7ed82fbf1b207a79ee515d3d7343c9831ce2604f8ec9abd7ee973；原JAR/manifest冻结哈希不变。新正式运行环境为 `/private/tmp/tensor-m14-t05-control.kybrot1f`，再次独立核对MySQL8.4.6/0表、最小权限/实际来源host、Java21、8080空闲及文件哈希/权限通过。launch.py与先前已审查的直接启动器逐字节一致，SHA仍为1423f0ebfa6cd48a6e818b6eccf13f593b23e39ce68b0dd9722663c5ea5fb263；新run-config将固定本次最终证据和输入哈希。旧j9045eey及两次诊断环境均已使用并清理，不可复用。

本次GREEN只证明启动阻塞已修复，不证明任何真实Tushare接口、账户权限或fixture页面结果。固定40/48/80与fixture2/3仍待用户已有Token的终端执行正式新命令，结束后由同一Token环境完成整篇证据扫描和实际报告追加；真实错误仍保留，不自动重试。

## 2000档实际运行 2026-09-06T05:45:06.940813+00:00

以下为本次启动器在CLI退出、终检和清理后记录的实际结果；空值表示未测量，原全49目标仍不完整。

```json
{
  "controllerFailure": null,
  "databaseAfterMigration": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 0,
      "fixture_daily": 0,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 0,
      "stock_basic": 0,
      "stock_company": 0,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 6
  },
  "databaseFinal": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 0,
      "fixture_daily": 1,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 0,
      "stock_basic": 5895,
      "stock_company": 0,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 6
  },
  "elapsedSeconds": 26,
  "finalExitCode": 1,
  "npxExitCode": 1,
  "ownedContainerRemoved": true,
  "ownedWorkersExited": true,
  "postCliScan": {
    "cleanupPassed": true,
    "deletedArtifacts": 2,
    "filesScanned": 5,
    "npxExitCode": 1,
    "scanPassed": true
  },
  "scopeId": "points-2000",
  "selectedPageCountsAndExcludedEmptyMatched": false,
  "specResults": {
    "cleanup": {
      "immutableInputs": true,
      "jvmStopped": true,
      "logScanned": true,
      "networkDrained": true
    },
    "command": "npx playwright test e2e/tushare-live.spec.js --workers=1",
    "downloads": [
      {
        "apiName": "stock_basic",
        "durationMs": 2835,
        "insertedRows": 5556,
        "outcome": "SUCCESS",
        "requestId": "899b3fa0-5857-45c1-96cd-fb2b57ace3ea",
        "sourceRowCount": 5556,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 30,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "c119af65-4893-4603-b544-5d572f3f87ab",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 197,
        "insertedRows": 339,
        "outcome": "SUCCESS",
        "requestId": "d3fc1bd2-2e8e-4c3c-9289-abfa7d861fed",
        "sourceRowCount": 339,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 304,
        "outcome": "ADAPTER_TYPE_INVALID",
        "requestId": "e82ccf95-6182-4d73-9b98-10ae2bd7e13b"
      }
    ],
    "finishedAt": "2026-09-06T05:45:05.843Z",
    "fixture": [
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "532608e5-08c9-4815-9b8d-f1090e07726b",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 21,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "e121d3d9-7fdb-4e20-ba67-002ec40b494d",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "c83beddb-c9b6-46fe-9e36-48c165a8083d",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 0,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "c5ee58d2-a318-47b9-9eb6-b520db289ac6",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "0ac2b503-5e59-4d88-a66f-6b2a57f54d76",
        "resultCount": 1,
        "totalElements": 1
      }
    ],
    "inputs": {
      "gitCommit": "4b46ec174b041f67715007aafdfdf39d6ff158bd",
      "jarSha256": "a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac",
      "manifestSha256": "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2",
      "specSha256": "72b9763941e7ed82fbf1b207a79ee515d3d7343c9831ce2604f8ec9abd7ee973"
    },
    "queries": [
      {
        "apiName": "stock_basic",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "871b2648-e1b5-463a-b1d8-09e54af67ac8",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "d302fcfc-8445-45f7-bde9-cea454563c34",
        "resultCount": 50,
        "totalElements": 5895
      },
      {
        "apiName": "stock_company",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "eaa7fdc8-2a33-481c-8f07-37b47eed3f21",
        "resultCount": 0,
        "totalElements": 0
      }
    ],
    "scope": {
      "excludedInterfaces": [
        {
          "apiName": "top_inst",
          "reason": "higher_points"
        },
        {
          "apiName": "broker_recommend",
          "reason": "higher_points"
        },
        {
          "apiName": "share_float",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hs_const",
          "reason": "permission_unverified"
        },
        {
          "apiName": "moneyflow_hsgt",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hk_hold",
          "reason": "permission_unverified"
        },
        {
          "apiName": "index_member",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hsgt_top10",
          "reason": "permission_unverified"
        },
        {
          "apiName": "namechange",
          "reason": "permission_unverified"
        }
      ],
      "id": "points-2000",
      "manifestCases": 49,
      "manifestSamples": 58,
      "selectedCases": 40,
      "selectedSamples": 48
    },
    "startedAt": "2026-09-06T05:44:41.708Z",
    "task": "M14-T05",
    "totals": {
      "attemptedCases": 2,
      "callIntervalMs": 2000,
      "completedCases": 1,
      "failedCases": 1,
      "fixtureDownloadPostsObserved": 2,
      "fixtureRecordsGetsObserved": 3,
      "liveDownloadPostsObserved": 4,
      "liveDownloadResultsRecorded": 4,
      "liveQueryResultsRecorded": 3,
      "liveRecordsGetsObserved": 3,
      "manifestSamples": 58,
      "registeredCases": 40,
      "selectedSamples": 48,
      "unexecutedCases": 38
    },
    "version": 1
  },
  "task": "M14-T05"
}
```

## 2000档实际运行 2026-09-06T06:18:48.024311+00:00

以下为本次启动器在CLI退出、终检和清理后记录的实际结果；空值表示未测量，原全49目标仍不完整。

```json
{
  "controllerFailure": null,
  "databaseAfterMigration": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 0,
      "fixture_daily": 0,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 0,
      "stock_basic": 0,
      "stock_company": 0,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 6
  },
  "databaseFinal": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 150,
      "fixture_daily": 1,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 1428,
      "stock_basic": 5895,
      "stock_company": 6294,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 6
  },
  "elapsedSeconds": 58,
  "finalExitCode": 1,
  "npxExitCode": 1,
  "ownedContainerRemoved": true,
  "ownedWorkersExited": true,
  "postCliScan": {
    "cleanupPassed": true,
    "deletedArtifacts": 2,
    "filesScanned": 5,
    "npxExitCode": 1,
    "scanPassed": true
  },
  "scopeId": "points-2000",
  "selectedPageCountsAndExcludedEmptyMatched": false,
  "specResults": {
    "cleanup": {
      "immutableInputs": true,
      "jvmStopped": true,
      "logScanned": true,
      "networkDrained": true
    },
    "command": "npx playwright test e2e/tushare-live.spec.js --workers=1",
    "downloads": [
      {
        "apiName": "stock_basic",
        "durationMs": 2694,
        "insertedRows": 5556,
        "outcome": "SUCCESS",
        "requestId": "ecdb2c3c-4c28-4e11-a557-19df2ce0b1ff",
        "sourceRowCount": 5556,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 31,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "49daa717-3954-4480-ae62-e7353c3d509d",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 198,
        "insertedRows": 339,
        "outcome": "SUCCESS",
        "requestId": "69b00ad9-e0d7-41b0-bdd4-5a6ec8e5be85",
        "sourceRowCount": 339,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1488,
        "insertedRows": 2457,
        "outcome": "SUCCESS",
        "requestId": "b3d4124a-5233-4d16-b4ae-bd09e1128180",
        "sourceRowCount": 2457,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1863,
        "insertedRows": 3083,
        "outcome": "SUCCESS",
        "requestId": "c4d0403b-864a-4b2a-859b-88ce7a536fd8",
        "sourceRowCount": 3083,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 474,
        "insertedRows": 754,
        "outcome": "SUCCESS",
        "requestId": "6f81e5b4-b47e-46f3-8f65-96e3e95c728f",
        "sourceRowCount": 754,
        "updatedRows": 0
      },
      {
        "apiName": "income",
        "durationMs": 32,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "c5af136e-d728-4e6c-86e3-74ea8084b874",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 36,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "c846b465-087e-4b39-9255-655b7c34187b",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 29,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "631b6b36-9d9a-4aa5-8537-99755b820078",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 438,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "df39e379-797b-4311-8954-5435ad6a882d",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 24,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "ebf6c924-6235-41ee-9ab7-dc59b42f8283",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 109,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "902d0955-db63-47a0-a6e6-2e0b3c7f09b4",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 628,
        "insertedRows": 1428,
        "outcome": "SUCCESS",
        "requestId": "6bc2dccc-9c86-4c20-bc2d-9e0e841daef2",
        "sourceRowCount": 1428,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 37,
        "outcome": "ADAPTER_TYPE_INVALID",
        "requestId": "841ad417-262b-4f40-a6ae-4154c536aac2"
      }
    ],
    "finishedAt": "2026-09-06T06:18:46.240Z",
    "fixture": [
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "c208176b-6720-4537-b52f-1e89d24ba7e3",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 21,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "1ec50480-ccc1-4deb-b8d0-f74d3ebef109",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "00b5dbc7-4198-4dec-a687-5b4dadb3e8dd",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 0,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "9ef1452b-b513-42fb-b8ea-b300474faca1",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "605fdd81-4d41-4ad8-8c0c-7c932c876c49",
        "resultCount": 1,
        "totalElements": 1
      }
    ],
    "inputs": {
      "gitCommit": "ecbf035cf81fe56e1660f12959fb227f2398f5fc",
      "jarSha256": "7f794f3494109c27f134c04846e486bda3fe18beec3a88246b58fbcea719cef9",
      "manifestSha256": "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2",
      "specSha256": "0ab8f12d96fe622a257bdb08fc0f0882c4fc0d94758900af2dc6e2ab45b457a2"
    },
    "queries": [
      {
        "apiName": "stock_basic",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "841a58e4-3489-4a87-91a9-9529a8a34416",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "1eaafd75-f031-4b69-9fc4-7fd9bb3ea4b0",
        "resultCount": 50,
        "totalElements": 5895
      },
      {
        "apiName": "stock_company",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "a4c09307-ed2f-472e-a406-80dddef6b01c",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 10,
        "outcome": "SUCCESS",
        "requestId": "ff473a62-6387-48b4-9a37-f2182fa03c6c",
        "resultCount": 50,
        "totalElements": 6294
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "38d4e76b-35a5-4b34-baa6-f3542b7b2b09",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "a312dd12-b700-4d7d-89cd-65db4655838f",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "4eebc837-f89c-44c6-9850-1a56afedf538",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "35f0109c-17f8-4db6-af93-35d2d5c918fb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "1fa424a1-a6f6-47bf-91e8-cb7c1ddb3592",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "65b93b98-2532-4250-a4fb-96ad8a63a569",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "45840648-9a2b-450a-8050-cdce21a73095",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "3f2c9ce3-dd80-46b5-a786-b98b9fe9cd6d",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "ddc397f2-cdb2-40af-b729-924a27126a08",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "cbe80f57-df7f-434b-9733-f77b71f1f6f9",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "b91c469a-3869-4aef-9bd4-f431dcca0f06",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "5d9112b9-0591-4a30-a5ac-0dd5b2171dce",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "2f752fc9-0155-42e2-9041-51d2f0935d70",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "080aed4b-5a52-4626-aca1-ea24448eb8f5",
        "resultCount": 50,
        "totalElements": 1428
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "4e7a118a-b623-4d01-9542-1e2ad32e2c82",
        "resultCount": 0,
        "totalElements": 0
      }
    ],
    "scope": {
      "excludedInterfaces": [
        {
          "apiName": "top_inst",
          "reason": "higher_points"
        },
        {
          "apiName": "broker_recommend",
          "reason": "higher_points"
        },
        {
          "apiName": "share_float",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hs_const",
          "reason": "permission_unverified"
        },
        {
          "apiName": "moneyflow_hsgt",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hk_hold",
          "reason": "permission_unverified"
        },
        {
          "apiName": "index_member",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hsgt_top10",
          "reason": "permission_unverified"
        },
        {
          "apiName": "namechange",
          "reason": "permission_unverified"
        }
      ],
      "id": "points-2000",
      "manifestCases": 49,
      "manifestSamples": 58,
      "selectedCases": 40,
      "selectedSamples": 48
    },
    "startedAt": "2026-09-06T06:17:51.401Z",
    "task": "M14-T05",
    "totals": {
      "attemptedCases": 10,
      "callIntervalMs": 2000,
      "completedCases": 9,
      "failedCases": 1,
      "fixtureDownloadPostsObserved": 2,
      "fixtureRecordsGetsObserved": 3,
      "liveDownloadPostsObserved": 14,
      "liveDownloadResultsRecorded": 14,
      "liveQueryResultsRecorded": 19,
      "liveRecordsGetsObserved": 19,
      "manifestSamples": 58,
      "registeredCases": 40,
      "selectedSamples": 48,
      "unexecutedCases": 30
    },
    "version": 1
  },
  "task": "M14-T05"
}
```
