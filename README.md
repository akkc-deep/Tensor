# Tensor

当前支持范围为 [manifest](docs/data-template/manifest.json) 中的 **40 个 Tushare Pro 数据集**。

2026-09-10 永久移除 `top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`hsgt_top10`、`namechange`，不再提供下载、数据查看或后续接入。`index_member_all` 继续保留。

对应 YAML、JSON 模板及入口注册已删除。V8 迁移会删除九张退役表及其中数据；升级操作见 [首次运行](docs/runbook/first-run.md#v8-永久移除九个数据集)。历史迁移及验收报告用于旧版本追溯。
