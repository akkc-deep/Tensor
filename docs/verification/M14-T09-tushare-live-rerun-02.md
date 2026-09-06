# M14-T09 D-03完整复验（第2次复跑）

本轮归属M14-T09，执行[当前设计](../task-designs/M14-T09-design.md)已获用户明确“同意”的D-03：仅增加top10_floatholders当前ok覆盖，保留dividend/top10_holders与其他37项期望、历史分类和全部原参数。历史28ok/12empty，当前31ok/9empty；真实来源数量不是未来固定值。

## 冻结输入与历史证据

- 原manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`，全部模板与原参数不变。
- 继续使用冻结JAR `/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`，不改生产/schema/依赖或重建二进制。
- [D-02真实证据](M14-T09-tushare-live-rerun-01.md)：6ae877f，SHA `6f91d9cbe0f4469a1278ede8de65b56f5841b292141202b88e97f2238948f695`；33通过/1失败/6未运行。top10_floatholders下载/入库280但EMPTY断言失败、末次页面查询未执行；历史失败保持。
- [D-01真实证据及后端门禁](M14-T09-tushare-live.md)：471dfb0，SHA `9d5c283b31f586ee7a3b4fdbda84321fbf3c7e4f55274f9d69955d86353af170`。[M14-T05历史证据](M14-T05-tushare-live.md) SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。三份已扫描历史文档均保持，不拼接任何旧轮通过数。

## 执行合同与本地准备

范围固定40接口/48组原样例/80页面records查询，fixture另计2POST/3查询；9项排除top_inst、broker_recommend、share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange不计skip或通过。原49目标尚未完成。单worker、零重试，上次真实下载响应完成后至少2秒再点击下一次；全部业务下载/查询来自页面，失败停止。

D-03同函数selector/outcome/finalDataset探针先RED（原spec缺第三项覆盖，exit1），两处最小修改后GREEN（exit0）；准确三项覆盖，其他状态/原参数不变，覆盖项拒绝全EMPTY与空末态，剩余empty拒绝SUCCESS，分类31/9与28/12、范围40/48/9正确。`node --check control-plane/e2e/tushare-live.spec.js` exit0；control-plane下 `npx playwright test e2e/tushare-live.spec.js --list` exit0，恰40个Chromium用例。当前spec SHA `05a6601f656dcfb5528c6ed832ccb17ef2416cfa84397fb93f11da1bfbd5901e`。

新私有控制目录 `/private/tmp/tensor-m14-t09-control.9mkzsd_0`；MySQL8.4.6、初始0表、回环绑定、真实来源host、utf8mb4/utf8mb4_0900_as_cs与CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX精确六权限已实测通过。启动器仅更换证据路径为本文件，仍提取固定SHA的M14-T05扫描算法；Python语法、5前置拒绝/3范围拒绝/10生命周期终检合成探针全部通过。本地子进程净化环境，无真实业务调用。

独立D-03审查：spec符合性PASS，无Critical/Important；唯一Minor为交接将旧EMPTY合同写成当前阻塞，已改为历史D-02失败和D-03剩余门禁。运行6hash、证据路径隔离和既有生命周期安全逻辑均核对一致。

运行前状态：真实新轮尚未执行，须先完成独立接入审查及单独BLOCKED→READY→IN_PROGRESS。启动器检查当前任务状态与设计/spec/JAR/manifest/证据/自身六项hash；真实Token仅从已有TENSOR_TUSHARE_TOKEN输入受控后端。正式结果在CLI/worker退出、终检扫描、独立DB核对和自有容器/卷/私密状态清理后追加，并在真实秘密集合下扫描本文件。
