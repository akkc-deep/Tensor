# ISSUE-015 后端依赖漏洞审计

日期：2026-09-07。问题见 [ISSUE-015](../issues/problems/ISSUE-015-backend-dependency-audit.md)，实际扫描门槛沿用 [M14-T07 设计](../task-designs/M14-T07-design.md)，问题关闭依据见下述用户决定。

## 当前结论

**ISSUE-015 已完成（用户接受剩余风险并关闭）。** 扫描更新与报告覆盖解析已修复并生成新报告：93 个顶层依赖、50/50 个冻结包第三方 JAR 和六个 reactor 项目均有身份覆盖。报告中的 **35 条高阈值发现（27 个不同的 CVE）**及原始日志中的四类分析缺口，均按用户于 2026-09-07 的明确决定标记为“不需要处理”。

用户已了解这些项的功能影响与潜在安全风险，并要求全部不再处理、将 ISSUE-015 标记为完成。本次文档状态更新不安排依赖升级、适用性评估、分析环境或凭证补全；未修改产品依赖或替换冻结包。风险接受仅覆盖本次报告中的已知项，原始命令退出 1 和不完整分析的证据保留，扫描器判定规则不变。该报告仍只能作为部分风险证据，不表示完整审计通过、无高危漏洞或新产物已获验证。

## 根因与修复

原命令在新的 Git HEAD 快照、空漏洞缓存、清理后的子进程环境中重现失败。Dependency-Check 13.0.0 退出 1，错误为 `nvd-invalid-api-key`、`vulnerability-data-missing`、`report-missing`；本轮 CISA 更新成功，历史 `cisa-http-403` 未重现。未提供 `M14_SECURITY_NVD_API_KEY`。

固定版本的 `dependency-check-core-13.0.0.jar` 内默认属性包含空的 `nvd.api.key=`；`NvdApiDataSource.processApi()` 只检查值是否为 null，空字符串仍传给 `NvdCveClientBuilder.withApiKey()`。配套 `open-vulnerability-clients:9.0.6` 在值非 null 时发送 `apiKey` 请求头。对同一公开 NVD URL 的实际小请求结果为：

| 请求 | HTTP | 结果 |
| --- | ---: | --- |
| 不发送 `apiKey` 头 | 200 | 有效 CVE JSON |
| 发送空的 `apiKey` 头 | 404 | `message: Invalid apiKey.` |

因此，本次“Invalid API Key”是扫描器无密钥分支的空请求头问题，不表示用户提供了错误密钥。移除环境变量参数也不会消除扫描器内置的空默认值。

`scripts/security/verify-release.sh` 的无密钥分支给原命令追加固定的官方数据源参数：

```text
-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz
```

13.0.0 的官方源码明确支持这个 URL 模板：`NvdApiDataSource.update()` 选择 `processDatafeed()`，读取官方 `.meta` 文件、年度 JSON 2.0 文件和最后的 modified 文件，并写入同一漏洞数据库。初次更新使用新的空缓存，由扫描器按默认年份范围下载；没有手工填充数据库或用空报告代替分析。

有非空 key 时继续使用原 API 路径，凭证只通过 `M14_SECURITY_NVD_API_KEY` 环境变量传给扫描进程。未增加其他凭证入口。

首次有效数据导入还暴露了两个覆盖问题，一并修复：

- Dependency-Check 将部分 JAR 合并到 `relatedDependencies`，其 Maven 标识字段为 `packageIds`。原判定器只读顶层 `packages`，误报 18 个库缺失。现在遍历关联节点，仍逐个核对坐标和 SHA-256，并检查子节点的漏洞及分析异常。
- Boot jarmode 工具没有内嵌 Maven 元数据，报告最初只有文件名和 SHA，没有 PURL/CPE。扫描器 `JarAnalyzer` 支持相邻 `.pom` 文件；现在仅对该工具从 Maven Central 下载官方同版本 JAR，确认 SHA-256 与冻结包字节相同后，再下载官方 POM 放在提取库旁，交由原分析器识别。没有修改 JAR、手写 POM 或给报告补造坐标。官方 JAR SHA 为 `d05beb46a7eac0f1a06733c75828b190a1cb250076ce03be2213aca4a5c0f03f`，POM SHA 为 `0702ee56962d57157bf7d12f20a97ae5bd0b21eaabe2c9c1ac2c3952dc067e82`。

复核还发现：JSON 的 `analysisExceptions` 未反映日志中的 Assembly 初始化失败和 Node 漏检警告，OSS Index 也会因无凭证自行停用。现在同时识别这四类固定日志类别；即使退出码为 0、JSON 无异常且没有高危，也明确返回 `dependency-scanner-incomplete`，不再允许这些情况误通过。

版本 13.0.0、全部 reactor 模块、test/provided/runtime 范围、冻结包 `scanDirectory`、`autoUpdate=true`、`failOnError=true`、`failBuildOnCVSS=7` 和报告判定门槛保持不变；扫描时未升级产品依赖、主动关闭分析器、添加 suppression 或接受风险。官方数据源与经过字节校验的 POM 元数据是本问题对原扫描输入的补充，原 M14-T07 历史证据保持不变。后续用户风险接受决定记录于本报告，不回写原始扫描结果。

参考：

- [Dependency-Check 13.0.0 官方源码包](https://repo.maven.apache.org/maven2/org/owasp/dependency-check-core/13.0.0/dependency-check-core-13.0.0-sources.jar)：`NvdApiDataSource.java` 的 `processApi`、`processDatafeed` 和 `FeedUrl`。
- [aggregate 参数文档](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/aggregate-mojo.html#nvdDatafeedUrl)。
- [NVD 官方 modified 元信息](https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-modified.meta)：本轮公开请求 HTTP 200。

## 验证与输入身份

- `sh -n scripts/security/verify-release.sh` 通过。
- `sh scripts/security/verify-release.sh --self-test`：318 项通过，failures 为零。原 309 项自检通过后，新增的合并依赖回归先出现三个预期失败（漏覆盖、接受子依赖高危、接受子依赖分析异常），修复后全部通过。随后新增有效 JSON/退出码 0 的分析缺口反例，四个反例均先误通过、修复后均被拒绝；新增干净对照通过。首次沙箱运行的回环 socket/Chromium 权限失败不计为通过；通过结果来自获准权限的完整重跑。
- 对真实冻结包注入错误的“官方 JAR”响应，确认 `packaged-metadata-jar-mismatch` 拒绝该输入且不写入 POM。
- 独立代码复核发现并纠正了仅依赖 JSON 异常字段的漏检风险；未完成的分析及依赖风险保留为失败。
- 基线提交：`221bf6189096f6e66a4abaa0cb4d7d063ed0e716`；快照来自 `git archive HEAD`。
- 冻结生产包 SHA-256：`acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef`。
- 最终脚本 SHA-256：`87b30570dbe70768f649c77c999025126666cf2805ef9152f58308c72e898ee8`。
- Maven 3.9.15、Java 21.0.11；Maven 缓存为 `/private/tmp/tensor-m2`，用户配置和漏洞缓存使用本轮私有目录。未继承代理、TENSOR/SPRING 凭证或 Java 注入参数。

原失败复现目录：`/private/tmp/tensor-issue-015-pexmxbbl`，扫描区间为 `2026-09-07T05:35:07.902603Z` 至 `2026-09-07T05:35:13.360901Z`。

首次全量导入目录：`/private/tmp/tensor-issue-015-qrn_y78h`。从空缓存下载 2002—2026 年共 25 个年度文件及 modified 文件；官方年度元信息的压缩大小合计 227,349,256 字节。扫描从 `2026-09-07T05:38:34Z` 至 `06:03:55Z`，产生报告，但 CISA 再次 HTTP 403，故该报告不计为有效通过证据。随后在 `/private/tmp/tensor-issue-015-8rgwlsyb` 重试，CISA 更新成功且 JSON `analysisExceptions` 为空；该轮用于定位关联依赖和 jarmode 身份缺口，不表示日志中的分析器缺口已消除。

最终运行目录：`/private/tmp/tensor-issue-015-5l3wvucc`。直接使用最终仓库脚本中的 `scan_jar`、`backend_audit` 与 `dependency_verdict`，创建新 Git HEAD 快照和报告目录，复制同轮 Dependency-Check 数据缓存。保留 `autoUpdate=true`，没有离线或跳过更新参数；NVD、RetireJS 和 Hosted Suppressions 分别按默认四小时、24 小时和两小时有效期复用同轮数据，CISA 本轮成功在线更新。最后核对冻结包与 617 个快照输入文件未变。

| 最终核对 | 结果 |
| --- | --- |
| Maven 扫描时间（UTC） | 2026-09-07 06:40:46—06:40:59 |
| 报告生成时刻 | 2026-09-07T06:40:58.649709Z |
| NVD API / Cache Last Checked | 2026-09-07T05:38:44Z |
| NVD API / Cache Last Modified | 2026-09-07T05:00:06Z |
| JSON `analysisExceptions` | 0（不代表日志无分析错误） |
| 日志确认的分析未完成类别 | 4，均记录为失败 |
| 顶层依赖 | 93 |
| 冻结包第三方 JAR 的坐标与 SHA 覆盖 | 50/50；missingJars 为空 |
| 项目引用 | data-plane 及全部五个子模块；missingModules 为空 |
| 全部漏洞发现 / 不同 CVE | 90 / 61 |
| HIGH / CRITICAL 标签发现 | 14 / 18 |
| 原综合门槛的高阈值发现 | 35（还包含 3 条 MEDIUM 标签但 CVSS ≥ 7 的发现） |
| 严重性未评估 | 0 |
| Maven / 安全判定（扫描时） | 退出 1 / `dependency-audit-failed`，同时保留当时未接受的高阈值告警及四类分析未完成信息 |

四类分析缺口均来自原始日志，当前处理状态均为“不需要处理（用户接受分析覆盖不足）”：

- `assembly-analyzer-unavailable` — **不需要处理**：PATH 中没有 dotnet，.NET Assembly Analyzer 初始化失败。后续只读核对确认，报告中的五个 DLL 来自 Byte Buddy/JNA，PE 的 CLR 目录均为空，且不在冻结生产包中；该缺口不证明生产程序需要 .NET。
- `node-lockfile-missing` — **不需要处理**：WireMock 内 Swagger UI 包没有 lockfile，扫描器明确警告可能漏检。
- `node-modules-missing` — **不需要处理**：同一包缺少 node_modules；RetireJS 检出的打包 JS 漏洞不能替代完整 Node Package 分析。
- `oss-index-credentials-missing` — **不需要处理**：未提供 Sonatype 凭证，13.0.0 自身按默认行为停用了 OSS Index；缺少该补充数据源，NVD 分析仍已执行。

没有主动禁用分析器、把错误降级为警告，或将上述未完成项当作通过。

原始 JSON、日志、完整命令及逐库映射留在最终私有目录。关键 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `dependency-report/dependency-check-report.json` | `227e42e4bfa5bde94ac2443fc3b26192a9ec387a91fa64befec9cbd9597230d4` |
| `command-0001.log` | `aaf56c878ef5cbc0d07c5331a9ec8254d93bc2d809be793c3b16d5f0fb9bde3d` |
| `outcome.json` | `cb72c4cb6b65fc57dd39bbd511ed0795463f2cd2f447cff802074c4e55c11ceb` |
| `source-manifest.json` | `0c5bf9cdc5e2d8be703d3a48801e045b0e6b22730499216d3800f2aa04b54624` |
| `jar-inventory.json` | `fdcc4a856120070a64ca3b55da2011d81703631d369701c36c52e058e69a3062` |

## 已接受、不需要处理的依赖风险

下表为扫描告警，不等于每项漏洞都已证明能在 Tensor 的实际配置中被利用。全部按用户决定标记为不需要处理；保留 CVE、版本和此前调查依据，不添加扫描 suppression。

| 依赖 | 高阈值发现 | 处理状态 | 已有调查依据（留档） |
| --- | ---: | --- | --- |
| docker-java-transport-zerodep 3.4.2 内的 httpcore5 5.0.2（测试） | 2 | 不需要处理 | CVE-2026-54399、CVE-2026-54428；公告影响至 5.4.2，修复须覆盖内嵌实现，仅覆盖外部同名 JAR 无法替换 shaded 字节。 |
| log4j-api 2.24.3（生产） | 1 | 不需要处理 | CVE-2026-34479；描述针对 Log4j 1→2 bridge 的 XML layout，组件适用性尚未确认；修复版 2.25.4，公开仓库另有 2.26.1。MEDIUM 标签但 CVSS v3=7.5，仍触发扫描门槛。 |
| mysql-connector-j 9.7.0（生产） | 2 | 不需要处理 | CVE-2026-60586、CVE-2026-60623；Oracle 2026-07 CPU 指出 9.7.0—9.7.1 受影响；公开仓库已有 26.7.0，未验证升级。 |
| spring-core / spring-test 6.2.19（生产及测试） | 7×2 | 不需要处理 | CVE-2026-47890、CVE-2026-47891、CVE-2026-47892、CVE-2026-47893、CVE-2026-59282、CVE-2026-59283、CVE-2026-59313；涉及 SSE、WebFlux、SpEL、数据绑定等不同前置条件，未完成逐项适用性评估。公开 Maven Central 的 6.2 分支截至本轮仅到 6.2.19，所需 6.2.20 不在其中。 |
| tomcat-embed-core 10.1.55（生产） | 14 | 不需要处理 | CVE-2026-53404、CVE-2026-53434、CVE-2026-55276、CVE-2026-59083、CVE-2026-59084、CVE-2026-65182、CVE-2026-65183、CVE-2026-65637、CVE-2026-65905、CVE-2026-65927、CVE-2026-66422、CVE-2026-68525、CVE-2026-68569、CVE-2026-68763；修复边界最高为 10.1.58，公开仓库已有 10.1.59。 |
| WireMock standalone 3.13.2 内两份 Swagger UI 的 DOMPurify 3.2.6（测试） | 1×2 | 不需要处理 | CVE-2026-65898；修复要求 DOMPurify ≥3.4.11。WireMock 3.13.2 仍为本轮查询时公开仓库最新稳定版，未确定替换方案。MEDIUM 标签但 CVSS v3=7.2。 |

上述修复版本来源为本轮报告中的 NVD/厂商公告和 Maven Central 实际元信息，仅保留为调查记录，未构建或复扫验证，也不再列为本问题的待办。

本问题按用户明确决定关闭，原有依赖修复及分析条件补全不再作为关闭前置条件。本次没有替换冻结包，原始扫描证据及 M14-T07 整体安全验收结果保持原样。
