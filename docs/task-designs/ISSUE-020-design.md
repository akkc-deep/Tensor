# ISSUE-020：主营业务分类与限量取证

## Goal

厘清 `fina_mainbz` 省略 `type` 的实际分类和官方100行与SINGLE实测150行的关系，补足两股票报告期整段、非空边界SOURCE，向ISSUE-026交付规则与匹配任务输入。

## Scope

用户于2026-09-13要求“完成issue20”，授权本项调查、最小取证工具修复和验证。遵守[T13](ISSUE-018-T13-design.md)、[T14](ISSUE-018-T14-design.md)及[本issue](../issues/problems/ISSUE-020-fina-mainbz-default-type.md)的既有合同。本设计先落实无需改变业务范围的取证；参数、业务键或完整性承诺的实质变更须在证据形成后记录用户决定，不能凭调查结果自行补全。

2026-09-13用户已明确“同意方案A（推荐）”，见[决策记录](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)。默认分类采用一次上游实际返回（本次样本含P/D/I），SINGLE只承诺单次快照；RANGE接受100为工程拆分阈值，<100按此口径完整，>=100拆分，最小单日满额失败。此为限定验收修订，不是上游保证，也不改变参数、业务键或生产准入。来源取证已独立完成，本次更新规则引用、独立验证及ISSUE-026输入，不重跑账户请求。

保留SINGLE仅`ts_code`、RANGE仅`ts_code/start_date/end_date`，8列及业务键`ts_code,end_date,bz_item,curr_type`。不添加type、period、自定义fields、VIP或分页（客户端原有YAML八列fields保持），不提交新TASK、不写数据库，不开放生产RANGE。

## Approach

1. 复核官网[81静态文档](https://tushare.pro/wctapi/documents/81.md)及HTML，保存获取时间、原字节摘要和准确引文。当前官方明确`bz_code`为P/D/I、`type`可选和“单次最大提取100行”，没有默认值或SINGLE例外。区分官方事实、账户观察和待决定的工程口径。
2. 在测试侧`TushareRangeSourceProbe.Projection`仅为fina_mainbz的SINGLE/RANGE增加安全摘要：P/D/I各行数、空/非法分类行数、报告期日期集合及无效报告期行数、有效/无效业务键行数、不同键数及跨合法分类共用键数。键只在内存保存；不输出主营项目、金额、原始分类值或原始行。缺失/非法值计入不可用，不当成P；不去重来源行、不改既有状态。尚无成功响应不输出指标，空响应输出0。该补充为T13/T14安全投影的本issue限定扩展，不更改SOURCE/任务合同。
3. 先用合成Envelope覆盖SINGLE和RANGE的三分类、非法值、重复/跨类键、空/失败/未执行，观察测试RED后实现。原请求参数、行数和SOURCE状态保留，证明异常文字不会进入摘要。
4. 固定16case的新`issue020-source-<UTC>`轮次：两股票`000001.SZ`、`600000.SH`各1个SINGLE及7个RANGE。RANGE窗口依次为20250101～20251231、20250630单日、20251231单日、20250630～20251231、20250629～20250630、20250630～20250701、20200101～20251231。旧SOURCE已观察000001.SZ的20250630/20251231，第二股使用相同法定半年度/年报期检验；不预言非空。最后宽窗口专门观察限量，不将达到阈值记为完整。空/失败保留，失败停轮，无自动换日或重试。
5. 新SOURCE在完整复制当前Git管理文件（含暂存成果）、逐文件核对的隔离clone执行。重新运行acceptance verify、绑定完整源码差异及生产/验收包摘要；运行前后稳定。复用受限wrapper：0700目录/0600清单、HTTPS、至少2000ms间隔、30分钟/5000请求许可、35分钟外层停止和秘密/身份清理核对。此固定清单正常为16次请求，无账户变量进入普通构建/测试，无数据库连接。
6. 真实结果只追加到既有唯一JSON索引和T14运行登记，逐对象确认原10轮/513case及其他39接口不变。比对单次快照与区间的各分类、报告期和键计数；有限样本不证明全历史默认集合或截断上限。发现矛盾仍保留未确认，形成具体范围决策方案，用户决定不替代上游事实。
7. 规则和来源成立后，在ISSUE-026记录准确SOURCE caseId、参数、候选策略要求及仍须完成的RANGE TASK/SQL；更新ISSUE-017当前事实。关闭本issue须满足下列全部验收，母issue不随之提前关闭。

## Files

- 本设计、ISSUE-020问题/必要的方案、后续issue看板：范围、决定、状态。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`、`TushareRangeSourceProbeTest.java`：安全投影和回归。
- `docs/verification/ISSUE-018-T14-runs.md`、`ISSUE-018-range-acceptance.md`、`.json`：计划、真实新观察和唯一索引。
- `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md`、ISSUE-017问题/验证记录：最终输入及事实同步。

## Tests

Java21、项目Node24，普通命令移除账户/DB变量：

```sh
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -Pacceptance verify
node --test control-plane/e2e/tushare-range-evidence.test.js
```

RED只因缺少新投影失败；GREEN及隔离构建退出0、无失败/错误/跳过。追加结果后运行既有索引校验和旧对象比较；`git diff --check`及暂存差异检查通过。新文件显式Git纳管，不自动提交/发布。

登记和身份核对后，唯一真实入口：

```sh
TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test
```

逐case核对实际退出码、cleanup、请求数和摘要；EVIDENCE_MISSING不当作完成，SOURCE无TASK/SQL字段。

## Acceptance

- 默认分类、SINGLE快照和RANGE100工程阈值按已确认方案A形成可复核结论；官方未承诺的默认集合/硬上限如实保留，不将用户决定当作上游事实。
- 两股票报告期整段、非空上下边界SOURCE有效；不拼接P/D/I、不修改键隐藏冲突、不调用VIP。
- 新旧证据保留且身份清洁，规则/来源/匹配任务输入交给ISSUE-026，ISSUE-017当前事实同步，母issue保留剩余验收。

## Risks

官方未给省略type的默认集合，实测150也不能升级为新上限。默认结果可能混类或跨类共享键，观察到这些事实可能需要用户选择固定分类或修改存储范围；本次决定只授权默认原样返回和工程阈值；未来若需固定分类或改键仍须另行明确范围。SOURCE成功仅为样本有效，不证明完整提取或持久化通过。

## 首轮后固定补证（2026-09-13）

首轮600000.SH全年110行触及候选100，不能用作完整性PASS。两股票20250630单日已分别观察37/55行，故先在运行登记固定独立两case：各股票20250629～20250701整段，预期包含同一报告期；不更改首轮、不增加type或阈值。复用稳定隔离包，各一次请求，仍执行同样身份/节流/安全清理合同。该来源取证独立于限量采用决定，不能替代用户决定；2025全年及宽窗口仍保留未确认。
