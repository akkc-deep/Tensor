# 九个数据集永久移除验证

2026-09-10，分支 `feature/remove-download-entry`。

按用户要求永久移除 `top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`hsgt_top10`、`namechange`。

## 变更

- 删除九份生产 YAML、九份 JSON 模板及 manifest 条目，当前清单精确等于原清单减去九项，共 40 项；`index_member_all` 保留。
- 下载和数据查看由注册元数据驱动，移除项不再展示；直接提交退役接口在 HTTP 入口返回 `DATASET_MISCONFIGURED`，不会调用来源。
- 删除退役接口专用的 `HsTypeParameters`、`MonthParameters` 及编解码注册，同步前后端合同和验证脚本。
- 新增 V8 删除九张退役表。V1～V5、V7 与原版本逐字节一致，保持已有库的 Flyway 校验兼容。
- 当前需求文档和运行说明改为 40 项；ISSUE-008 按永久退出支持范围关闭。历史验收报告保留原结果。

## 验证结果

| 检查 | 结果 |
|---|---|
| 插件删除回归 | 修改前两项失败，修改后通过；确认退役项不再出现在描述符且下载被拒绝 |
| 后端及 MySQL 集成测试 | 最终 Surefire 报告合计 622 项，失败、错误、跳过均为 0 |
| V7 → V8 升级 | 九表删除；daily、dividend、index_member_all 的全部原行保持一致；重复迁移为 0 |
| 表结构合同 | 43 项通过；40 张生产表、789 业务列、912 物理列、34 个次级索引 |
| 前端单元测试 | 24 个文件、170 项通过 |
| 浏览器元数据矩阵 | 40 项通过；测试发现共 160 项，未执行真实 Tushare 套件 |
| 安装包合同 | 生产包 4 项、验收包 3 项通过，构建成功；两包均恰有 40 份生产 YAML 和 V8 |
| 发布脚本离线自检 | 328 项通过；隔离生命周期探针确认迁移 JVM 退出、撤权、新 JVM 启动及 health 检查的执行顺序 |
| 目录与历史迁移 | 40 项 manifest/YAML/模板精确一致，保留条目及历史 SQL 未改变 |
| 代码审查和差异检查 | 独立复审通过；已修正撤权后连接池可能保留旧权限的问题，`git diff --check` 通过 |

后端结果来自完整测试及定向复跑：首次全量发现两处旧数量断言，修正后全量仅有无凭据场景受终端已有 `TENSOR_TUSHARE_TOKEN` 影响；隔离业务环境变量后，`ProductionApplicationContextIT` 和安装包验证通过，最终报告全部为零失败。没有通过修改产品行为绕过测试。

完整后端测试使用 Maven `-Pacceptance '-Dtest=*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' verify`；初次使用 `clean verify` 清除旧构建资源。定向复跑使用 `-Dtest=ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false verify`，并通过 `env -u` 隔离业务配置。MySQL 使用临时 Testcontainers 8.4.6，前端使用 Node 24.15.0。

本次未连接真实 Tushare 或用户数据库，也未执行完整发布门禁。V8 会在用户部署并升级数据库时删除退役表及其中数据，详见 [升级说明](../runbook/first-run.md#v8-永久移除九个数据集)。
