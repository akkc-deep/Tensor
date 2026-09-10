# ISSUE-008：九个 Tushare 接口永久移除

## 当前状态

2026-09-10，按用户决定关闭：以下九个接口永久退出系统支持范围，物理删除下载与数据查看注册、数据集 YAML、JSON 模板及专用参数定义，不再安排权限核验、恢复接入或真实验收。

`top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`hsgt_top10`、`namechange`。

当前支持清单缩减为 [manifest](../../data-template/manifest.json) 中的 40 项。`index_member_all` 继续保留。

V8 负责删除九张退役表及其中数据；保留历史迁移以兼容现有 Flyway 校验。升级说明见 [首次运行](../../runbook/first-run.md#v8-永久移除九个数据集)。

## 历史依据

原 M14-T05 的 49 接口目标有九项未覆盖：`top_inst`、`broker_recommend` 此前因积分要求排除；其余项因权限要求冲突、文档缺失或最低积分未明确而待核验。2026-09-07 曾将九项延期为不依赖，本次永久移除决定取代该安排。

[M14-T09 完整复验](../../verification/M14-T09-tushare-live-rerun-02.md) 记录了所选 40 接口当轮通过、48 次下载及 80 次查询。历史报告及 M14-T05 原失败状态保留；关闭本问题表示产品范围调整，不表示九项已通过真实下载验证。
