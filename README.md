# Tensor

Tensor 支持 Tushare Pro 的 40 个证券数据接口，提供下载、入库与只读查询。完整清单见 [manifest.json](docs/data-template/manifest.json)，启动方式见[首次运行说明](docs/runbook/first-run.md)。

2026-09-11 起永久移除以下 9 个接口，不再提供下载、数据查看或后续支持：

- `top_inst`、`broker_recommend`、`share_float`
- `hs_const`、`moneyflow_hsgt`、`hk_hold`
- `index_member`、`hsgt_top10`、`namechange`

`index_member_all` 继续支持。旧 Flyway 迁移保持不变，以兼容已部署数据库；数据库仍保留 49 张来源表，其中 9 张旧表不再被应用使用。本次移除不会删除已有数据。

契约校验入口：`sh scripts/verify-contracts.sh`。历史设计和验收记录中的 49 项描述保留为当时的范围，不代表当前支持范围。
