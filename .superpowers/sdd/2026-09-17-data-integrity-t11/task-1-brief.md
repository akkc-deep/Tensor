### Task 1: 创建、预览、历史与导航

**Files:** 新增 `control-plane/src/utils/integrityForm.js`、`components/integrity/{IntegrityCheckForm,IntegrityScopePreview,IntegrityCheckHistory}.vue`、`views/{IntegrityView,IntegrityCheckView}.vue` 及各自 spec；修改 `router/index.js`、`layouts/AppLayout.vue` 及相关导航 spec；仅必要时补充 scoped 或共享样式。

**Interfaces:** `parseIntegritySymbols(text, pluginId)` 返回有序去重股票数组；`validateIntegritySelection(selection, capability, today)` 返回 `{valid,errors,plannedUnits,rangeDays}`。form 使用 v-model 选择值，预览只读，历史自行 GET。页面使用 T10 `loadCapabilities/confirmCapability/prepareSubmission/submit/recoverSubmission/resendSubmission/dispose`；历史用 `listIntegrityChecks(criteria,{signal})`。

- [ ] 首个 RED：为未实现函数提供空返回骨架，测试 `parseIntegritySymbols('999999.sz，999999.SZ 600000.sh','tushare_pro')` 得到 `['999999.SZ','600000.SH']`；两股票、一 STOCK_DATE、一 NON_STOCK 应为3单元，limits=3可提交、limits=2不可提交。实际观察断言失败。
- [ ] 实现纯解析和验证，补齐空股票/接口、格式、严格日期、上海当天、闭区间天数、未知API/描述和每项限额测试。
- [ ] 先写真实组件行为测试，再实现完整专属设计第1–6节：来源/分类并发隔离、默认全选、输入锁定、待添加文本提交、能力变化保留已失效选择、确认及恢复、请求ID分别显示、历史精确分页/失败保留与最小详情入口。
- [ ] 运行专项及完整单元测试，修复由本项引起的回归，构建成功；变更仅按精确路径暂存。

```sh
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane test -- src/utils/integrityForm.spec.js src/components/integrity src/views/IntegrityView.spec.js src/views/IntegrityCheckView.spec.js src/layouts/AppLayout.spec.js
```
