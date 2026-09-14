# ISSUE-018-T14 最终实施与证据审查

## 审查范围

2026-09-13 独立只读代理 `t14_final_review` 对照T14全文设计与T13共享验收，检查当前未提交代码、实际证据、索引与说明。HEAD仍 `34f3e283c0ee7f58c19bf72e8a470c1c858ac26d`；原22项暂存成果保留。审查不执行账户操作、不修改文件/索引/HEAD、不启动应用。

## 代码与证据结论

没有Critical或Important代码/证据不一致。可交付已核验的部分成果，不能据此标记T14全部完成：11UNKNOWN、三项含糊限量、fina_mainbz和其余代表场景/30项RANGE缺口仍在。

审查独立核对validateEvidence、40接口/8轮/475case/4-30-6；原三轮329case逐对象等于原始基线；初始22个暂存补丁区段完整保留。三个新SOURCE run逐对象等于各私有source-run.json，SINGLE74及RANGE25个case与原始safe-results.json逐对象相等。所有新轮exit0/cleanupPASS；SINGLE74/148和5265/5265/0、RANGE25/50和68/52/16符合实际SQL安全产物。实际两包SHA符合运行登记；G1/G3/G4的XML独立汇总为1318/1060/1063、零失败/错误/跳过，G2=468/G5=3实际日志成立。

Minor：RANGE TASK的expectedCoverage继承SOURCE取证阶段“完整性未确认”预期标签；运行时规则/完整叶子/SQL实际另验。主流程核对harness第521行与第1053行后，在当前验收及运行登记明确解释，保留实际JSON不回填；独立复审确认此说明解决Minor，无需修改代码或重跑账户。

## G6 实证发现与修正审查

初次静态审查没有发现三个普通浏览器套件仍假设默认SINGLE。真实G6第一轮97PASS/7FAILED/22NOT_RUN、exit1，自有容器和秘密清理PASS；失败完整保留。实际快照、useDownloadFlow.js第185行和原单测共同确认既有合同为AVAILABLE默认RANGE。

主流程只修正普通测试：metadata和UI矩阵先断言四项默认RANGE、其他36项默认SINGLE，再显式选择SINGLE验证原表单；股票表单用例显式选择SINGLE。保留精确请求体mode=SINGLE/股票归属/必填和其余接口拒绝断言。另一个键盘用例曾因daily模糊搜索与ArrowDown选中daily_basic；改为唯一显示名“日线行情”、候选数1及精确daily选择标签，仍全部经键盘操作。

独立限定复审核对上述修正符合原产品合同；没有应用、策略、JAR、证券键或真实账户证据变化。失败结束后才复制三个测试文件到隔离目录，完整新G6以新四schema容器执行；其最终结果尚待主流程实际记录，不在此预填PASS。

## 交付边界

原SOURCE、TASK与失败历史均保留；仅四项具备足够的当前开放依据。完整T14/T13和母issue仍不能关闭，不创建T15、不提交/合并/发布。最终普通浏览器结果、环境清理及Git纳管由主流程在 [运行登记](ISSUE-018-T14-runs.md) 和 [T14交接](../task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md) 收尾。

## 模式动作的实际浏览器复验

随后实际复验发现原生radio被Element Plus可见inner span拦截，及宽范围文字定位与近期任务同名内容冲突；失败/中断的两次全量及第一聚焦均按 [运行登记](ISSUE-018-T14-runs.md) 保留。最终改为下载模式radiogroup内可见标签点击，并断言所选radio；默认模式/精确SINGLE请求体覆盖保留。

真实harness同类动作同步修正以支持后续AVAILABLE下的SINGLE，旧运行仍绑定原specSHA、不重跑账户。命令式离线用例先RED1，再77/77 GREEN；4项实际聚焦全部PASS、exit0/cleanup PASS（36.5s）。最后完整126项仍待实际结果，不将聚焦结果替代全量门禁。

## 主流程最终验证与状态

最终限定复审确认模式组内可见标签动作、精确键盘选择、默认模式断言、选中态及旧JSON/spec身份解释成立，无新增Critical/Important/Minor。随后主流程实际取得完整G6：7文件/126 PASS、0失败/跳过、exit0（15.9m），日志 `g6-complete.log`。这是聚焦4/4之后的完整新轮，前两次失败/中断及第一聚焦失败仍保存。

自有测试容器/秘密和preview已清理，4173/8080空闲；原账户容器和15/74/25任务全部保留。新增项目文件Git纳管，不提交。可交付本次已核验部分；T14全部Acceptance仍未成立，记录BLOCKED和恢复条件，T13和母issue不完成。
