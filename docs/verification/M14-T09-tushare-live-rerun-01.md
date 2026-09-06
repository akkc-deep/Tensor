# M14-T09 D-02完整复验（第1次复跑）

本轮归属M14-T09，权威设计为[当前设计](../task-designs/M14-T09-design.md)的D-02。用户明确同意仅将top10_holders当前验收预期调整为成功且有数据，保留原参数、历史结果和完整40项复验；D-01的dividend覆盖继续有效，其余38项期望不变。历史分类28ok/12empty，当前30ok/10empty。38和320仅是此前测量值，不是本轮固定行数。

## 冻结输入与历史证据

- 原manifest及全部模板/参数不变，SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。
- 沿用冻结验收JAR：`/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`。本轮不改生产代码、不重建二进制。
- [上一轮M14-T09实际证据](M14-T09-tushare-live.md)，提交471dfb0，整篇SHA `9d5c283b31f586ee7a3b4fdbda84321fbf3c7e4f55274f9d69955d86353af170`；32通过/1失败/7未运行，top10_holders的末次页面核对未执行，原失败保留。
- [M14-T05历史证据](M14-T05-tushare-live.md)，整篇SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。既有修复回归/迁移/打包/health/49总合同结果见上一轮证据，本轮只修改验收预期，不重复后端构建测试。

## 执行合同与本地准备

完整范围仍为40接口/48组原样例/80次真实页面records查询，fixture另计2POST/3查询。9项排除仍为top_inst、broker_recommend、share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange；不计skip或通过，原49目标仍未完成。使用单worker、零重试、上次真实响应结束后至少2秒再点击下一次下载；所有业务下载/查询均来自页面。全轮失败停止，不将此前32项与本轮拼接。

新私有控制目录 `/private/tmp/tensor-m14-t09-control.yx5keenc`，准备脚本实际通过MySQL8.4.6、初始0表、回环绑定、真实来源host、utf8mb4/utf8mb4_0900_as_cs及CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX六项权限检查。新启动器沿用已审查逻辑，仅将证据写入目标更换为本文件；扫描器仍从固定SHA的M14-T05历史证据提取，不修改扫描算法。

D-02同函数探针先RED（旧spec仅dividend覆盖，与预期双覆盖不符，exit1），仅修改两处spec后GREEN（exit0）。验证准确双覆盖、其余期望不变、非空/EMPTY与末次数据集断言、历史28/12与当前30/10及40/48/9，原manifest/参数不变。`node --check control-plane/e2e/tushare-live.spec.js` exit0；`npx playwright test e2e/tushare-live.spec.js --list` exit0，恰40个Chromium用例。

本地启动材料验证：Python语法通过；5项前置拒绝、3项精确范围拒绝及10项生命周期/终检合成探针全部通过。这些探针仅使用合成凭证，无真实业务调用；本地子进程使用净化环境。

独立D-02接入审查：spec符合性PASS；唯一Minor为交接仍把旧specSHA标作当前，已更正为 `20499ebe50010f07edcbfc6502ed13fe84aac06f557700f92f8cde7fd9757f94`。当前spec/config和6项运行hash、0700/0600权限及控制目录未使用均已核对。

运行前状态：真实复验尚未执行。正式命令由本轮私有启动器在M14-T09明确恢复IN_PROGRESS且设计/spec/JAR/manifest/证据/启动器hash均匹配后执行；真实Token仅从现有TENSOR_TUSHARE_TOKEN进入受控后端。实际结果由同一启动器在全部worker退出、终检扫描、独立DB核对与自有容器/卷/私密材料清理后追加，并对本文件执行真实秘密集合扫描。
