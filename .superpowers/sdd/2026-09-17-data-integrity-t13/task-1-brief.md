### Task 1: Fixture 规则及真实 HTTP/SQL

**Files:** fixture 模块 FixtureIntegrityRules.java、FixturePlugin.java、FixtureConfiguration.java；新增 FixtureIntegrityExtensionRule.java；对应 fixture tests；新增 app fixture/IntegrityFixtureFlowIT.java。

**Interfaces:** fixture 配置 `tensor.plugins.fixture.integrity-version` 仅2/3，默认2；六完整性端点合同保持；SQL fixture__fixture_daily使用现有7列。

- [ ] 新建真实 HTTP IT，首先用 PROVEN_EXTRA Jan1..19+21 请求 Jan1..21；断言 `coverageRate="0.950000"`，`expectedCount="20"`，`matchedCount="19"`，`missingCount="1"`，`extraCount="1"`，Jan20 MISSING及Jan21 EXTRA。
- [ ] 运行 `mvn -o -f data-plane/pom.xml -Dtest=IntegrityFixtureFlowIT -Dsurefire.failIfNoSpecifiedTests=false test`，观察 UNKNOWN/null 的行为失败。
- [ ] 实现设计第1节的可靠窗口和版本规则；保留原场景，非法配置阻止启动；规则3为通用 FIELD PASS且合成证据。
- [ ] 扩展IT验证六端点/重放/冲突、可靠空/未知/无记录股票、递归CTE独立SQL复算、证券逐行及SHA256、上游计数0。
- [ ] 同库应用重启2→3，A旧JSON/响应不变；旧submission重放A，旧hash新ID409，新hash B显示@3/extension；覆盖fixture版本/注册/窗口单测。
- [ ] 运行专项测试，登记真实RED/GREEN命令及计数，精确暂存新增与修改文件；不提交。
