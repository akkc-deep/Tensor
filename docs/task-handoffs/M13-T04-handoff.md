# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M13-T03`
- **Next task:** `M13-T04`
- **Design document:** `docs/task-designs/M13-T04-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M13-T04`
- **Title:** 全新环境运行说明和启动 smoke test
- **Goal:** 只凭分发的单 JAR、运行说明与 smoke 脚本，在 Java 21/MySQL 8.4 环境完成首次启动、就绪、页面访问和正常停止；缺少 Tushare Token 不妨碍首跑与查询。
- **Scope:** 只创建 `docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`scripts/smoke-test.sh`。固定 schema/账号、八个环境入口、启动/健康/页面、停机、备份/前向回退说明和四项只读 HTTP smoke；临时环境验证运行步骤，不修改 Java/YAML/POM/前端/migration，不实现下载或后续 E2E。
- **Acceptance criteria:** runbook 可在仅含分发文件的全新运行目录复现，真实应用账号与 MySQL 新 schema 完成 V1–V5/49 业务表初始化；缺 Token 首跑与重启后四项 GET 均通过；脚本语法、失败状态/内容/已知秘密/参数/清理场景满足设计；完整 `mvn -f data-plane/pom.xml clean verify`、范围/格式/链接/Git 门禁通过；保留 `120s < 130s <= proxy`、每阶段 70s 停机与 health-only/生产同源边界。实现提交 `docs: add reproducible Tensor first-run guide` 精确包含三个新增文件，脚本为 100755。

## Dependencies

### `M13-T03`

- **Artifact:** `docs/task-designs/M13-T03-design.md`；当前 `data-plane/tensor-app/src/main/resources/application.yml`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java`；打包产物 `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` 与其中的静态资源、生产 V1–V5 和 Tushare 元数据。`target` 是可重新构建的未跟踪产物，不能加入 Git。
- **Decision:** 同一 Servlet/JAR 提供页面与 `/api/v1`；无扩展名 UI GET/HEAD forward 到真实 index，API/Actuator/assets/文件型请求保留各自 handler/404；既有安全头及缓存策略不变。生产默认不注册 CORS；仅显式单 origin 开放 API，blank/逗号/末尾斜杠保持关闭，精确 `*` 拒绝启动；不开 credentials。graceful 每阶段 70s，现有事务 60s、上游读 120s、前端 130s。
- **Rationale:** 本任务说明和验证已经冻结的单 JAR 运行行为，避免首跑要求源码、Vite 或真实下载凭证，并防止把 UI fallback、CORS、health 或停机时间解释成超出实际能力的保证。
- **Constraint:** 代理响应超时至少 130s；外部终止窗口必须考虑全部实际 Spring 停机阶段和资源清理，不能把 70s 当 JVM 总限。缺 Token 时元数据与查询仍可用、下载不可用，health 的数据库检查与 Token 状态独立。生产仅公开 health 家族，所有秘密只由后端环境/只读配置注入。49 是业务表数，Flyway history 另计，生产不执行 V6/fixture。不改变任何已交付生产文件或用全局管理员账号绕过应用权限验证。
- **Usage:** 从上述实际配置整理环境表、缓存/CORS/timeout/健康说明；从构建产物复制 JAR到全新运行目录，使用只读 GET 检查 health、downloads、datasets、data-sources，再执行正常停止/重启验收。只把 TRD 14/19/附录 B 与 OpenAPI 列表契约作为运行说明来源，不增加看板直接依赖。
- **Readiness evidence:** 权威看板以 `455d253` 记录 M13-T03 `IN_PROGRESS -> COMPLETED`；实现 `5a2237d`、测试修正 `1b599c2`、设计修正 `34b7260`。最终主控原样运行正常 JVM 权限的 `mvn -f data-plane/pom.xml -pl tensor-app -am clean verify` 于 2026-09-05 13:20 退出 0：前端 120，后端 79/75/93/12/109 共 368，Failsafe 4，全部零失败/错误/跳过，Boot repackage 成功。五项 mutation、最终 Web 28/28、真实 Spring 组件发现/测试 helper 排除、JAR/范围/格式/跟踪门禁通过；任务复审与整体审查无遗留项，最终 `Ready to merge: Yes`。这些证据证明配置和打包可供消费；全新环境实际运行验证仍是 M13-T04 要完成的工作。

唯一直接依赖内部无冲突：页面/API/CORS/cache/health 配置和单 JAR 资源属于同一已验证运行产物，M13-T04 不改变这些接口，只补充操作说明与黑盒首跑检查。设计已由 `f8aa577` 完成并回填为看板同一精确路径。

## Start Here

按顺序读取：

1. `docs/task-designs/M13-T04-design.md`，完整读取。
2. 本交接，以及 `docs/task-handoffs/tensor-v1-task-board.md` 的 M13-T04 行与详情。
3. `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 Global Constraints、Task M13-T04、Module Gate。
4. `docs/task-designs/M13-T03-design.md`、当前 `application.yml` 和已打包 JAR 的公开运行接口。
5. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 14.1–14.3、19.1–19.5、附录 B，以及 `docs/contracts/openapi-v1.yaml` 的数据源列表契约。

首个实施动作：确认三个目标文件仍不存在且没有重叠用户修改，按已完成设计创建 `scripts/smoke-test.sh` 的四项只读 probe 和失败/秘密检查，先运行 `sh -n scripts/smoke-test.sh`，再用临时 HTTP 响应桩逐项验证设计中的正常与失败矩阵。随后写两份 runbook，再按设计运行完整构建和独立 MySQL/运行目录的真实首跑验收；无需重新选择公开接口或补写设计。

## Risks

- MySQL 服务、独立 schema、客户端工具和运行时凭证是实际首跑验证条件；当前机器能定位 Java、Docker、curl、sh，但这不代表 MySQL 验收已执行。不能用 MockMvc 或响应桩代替真实打包实例。
- 当前 schema 最小应用权限来自 V1–V5 与既有查询/Upsert；必须以该账号实际启动验证，若工具行为要求额外权限，按具体失败证据做 schema 范围内最小调整，不默认使用管理员账号。
- 70s Web 停机阶段覆盖 60s 写事务，但小于上游 120s 读取上限；不能承诺每次在途下载都能完整结束。首跑正常停止等待 JVM 退出，不设置外部自动强杀倒计时。
- smoke 只检查指定 HTTP 内容标记与已知秘密，不替代完整 JSON schema、真实 Tushare、在途事务压力或 M14 安全/E2E 验收。
- 保留用户既有 `.idea/misc.xml` 和 Maven `target/`。沙箱仅阻止既有 Byte Buddy self-attach 时原样移至正常 JVM 权限，不 skip、不改 Mockito/JVM/POM。
