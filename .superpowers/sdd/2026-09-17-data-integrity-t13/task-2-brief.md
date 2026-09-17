### Task 2: 真实浏览器与可复现环境

**Files:** 新增 control-plane/e2e/integrity-fixture.spec.js、integrity-fixture.helpers.js、scripts/verify-integrity-fixture.py；最小更新download-outcomes.spec.js、dataset-query.spec.js的V9断言。

**Interfaces:** 使用Task1的fixture@2/@3；ACCEPTANCE_JAR、TENSOR_DB_URL/USERNAME/PASSWORD、M14_DB_SCHEMA、M14_MYSQL_DEFAULTS_FILE；脚本接受实际绝对 `--acceptance-jar`。

- [ ] 按设计第4节实现独立真实套件；创建→95%→Jan20完整键→刷新/历史→SQL UPDATE Jan21为20→再次确认→新ID100%/PASS且旧报告95%；证券各次检查前后相同。
- [ ] 验证PROVEN/PROVEN_EMPTY/UNCONFIRMED及同库版本2→3，扩展证据通用展示；1440/1024/390截图和无页面溢出，窄屏键盘问题定位。
- [ ] 实现仅loopback/schema/defaults验证、8080空闲、health、自有JVM关闭及上游计数器；缺环境显式失败。
- [ ] Python启动自有mysql:8.4.6，五schema，随机临时凭据0600及限定授权；串行T13及四旧套件，任何失败汇总非零，异常清理自有资源。
- [ ] V9同步为1..9迁移/55表，保留旧业务断言；构建acceptance JAR后运行Python真实套件并保存安全证据。
- [ ] 精确暂存，不提交。


### Controller constraints / resolved details

- Parent controls staging. Do not git add/commit despite task checkbox wording.
- Five owned schemas may grant CREATE,SELECT,INSERT,UPDATE,ALTER,INDEX,REFERENCES,TRIGGER only. DROP TRIGGER is authorized by TRIGGER; do not grant DROP/DELETE/global privileges.
- Preserve no-token v2 browser flow. Configure enabled Tushare + owned receiver URL without actual upstream credentials; receiver count remains zero. No real external calls.
- Acceptance JAR now exists at absolute worktree path data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar; SHA256 7b4963f4719c56a7e6e2bdbd28de3971969bd2ca3ab06aa537ed080c08e72f60, clean verify1469 Java tests/740 frontend tests passed. Do not run Maven. Parent will not clean until browser runs finish.
- MySQL binary: /usr/local/mysql/bin/mysql; Node: /Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin.
- Collect durable safe report JSON, SQL rows/counts/SHA and three width screenshots beneath docs/verification/data-integrity-t13/real/; sanitize any application logs/errors before persisting.

- Actual-run ruling: permit minimal stale Studio display/navigation selector synchronization in all four legacy suites, additionally tushare-metadata.spec.js and fixture-flow.spec.js. Controller verified packaged static assets are current. Keep production UI and all HTTP/SQL/business/network assertions unchanged. Record exact failures and fixes.
