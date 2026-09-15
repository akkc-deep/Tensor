### Task 3: 普通浏览器与隔离环境

**Files:** `control-plane/playwright.config.js`、专属设计列明的七个普通 spec 及 fixtures、`control-plane/e2e/packaged-test-environment.js`、`.test.js`。
- [x] Node 内置测试先覆盖私有映射文件、四 key/五字段、原环境兼容和原子拒绝，再实现最小 helper。
- [x] 配置 ordinary/task-live/tushare-live 三模式及严格参数校验；普通单 worker，三个 mock preview4173、四验收包8080各自空库。
- [ ] 迁移旧页面同步断言到接收/查询，保留 SQL、敏感信息、网络白名单和全部原场景；精确 V8 数字同步证据。
- [ ] 执行 helper、三模式发现、三个 mock spec，最后以当前验收包执行整个普通套件。
