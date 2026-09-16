# Tensor 控制面 UI Demo

新增[后端接入版 Demo](live/README.md)：启动开发服务后打开 `/studio-live.html`，使用真实目录、任务与数据查询。下文描述原版 `/ui-demos.html`。

已选定「工作台 Studio」设计：可搜索接口目录、下载配置、任务列表三栏联动。后续仅适配和验收 PC 桌面浏览器。其他四套设计和方案切换入口已移除。

在 `control-plane` 使用 Node 24.15+（24.x）运行 `npm run dev`，打开 `/ui-demos.html`。旧链接附带的 `?v=` 参数不再切换设计，均展示工作台。

提供单次下载、按日期范围批量下载、数据查看、外观设置；支持模拟任务创建、排队、逐批完成、重试、恢复、详情、条件查询与分页。状态保存在当前页面内存中，刷新后重置。主题颜色仅影响本次 Demo。

这是独立的多页面入口，不使用原应用路由、样式、请求层或后端服务。现有控制面仍由 `index.html` 加载；仅在 Vite 构建配置中增加 Demo 入口，`npm run build` 会同时输出两者。

`catalog.json` 是 2026-09-13 的本地快照：40 个接口的名称、参数、筛选字段来自 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/*.yaml`；记录来自 `docs/data-template/*.json`，每个接口最多 24 条、8 个字段，不代表数据库全量数据。样例为空的接口显示空状态。

批量能力由 `demoBatch.js` 保存 2026-09-16 的 `TushareBatchPolicies.java` 本地快照：30 个接口可用，`balancesheet`、`cashflow`、`repurchase`、`fina_indicator` 四个接口待验证，另外六个仅支持单次下载。报告期、公告日等日期轴与生产策略一致；北交所交易日历的批量入口仍不可用。`catalog.json` 中旧的 `rangePending` 字段不再用于能力判断。

切换到“批量下载”后填写起止日期即可提交，表单不展示批次预览。演示按自然月拆分（最多 120 批），提交后在任务详情中查看批次区间、进度、行数和尝试次数。该演示规则不复现生产的交易日历枚举或动态拆分。默认任务包含部分失败和中断示例，重试/恢复只处理未完成批次，保留成功批次及其行数。仅采集返回记录的接口会显示完整性说明。

模拟进度和重试/恢复结果不代表真实执行，数据查看内容不会随模拟下载改变。

Logo 复用现有 `AppLayout.vue` 的 Tensor 几何标识。所有资源本地提供，不依赖外部字体、图片或 CDN。

验证命令：`npm test`、`npm run build`。批量逻辑与表单测试位于 `demoState.spec.js`、`DownloadForm.spec.js`，覆盖日期校验、能力限制、提交快照、逐批完成和重试恢复。
