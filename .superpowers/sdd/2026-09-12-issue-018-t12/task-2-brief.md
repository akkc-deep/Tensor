### Task 2: 真实应用生命周期

**Files:** `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskLifecycleIT.java`、`control-plane/e2e/download-task-lifecycle.spec.js`。
- [ ] 按专属设计建立生产 Servlet/MySQL/http_test 来源和测试控制入口；先运行缺失 spec 的子进程验证硬失败。
- [ ] 完成 flow 两场景、resume 一场景；子进程严格报告验证、真实回执丢失、同库故障快照和最小权限测试。
- [ ] 用 LifecycleIT 实际运行，核对 SQL/calls/版本/attempt 与浏览器截图，不依赖模拟 API。


Read full docs/task-designs/ISSUE-018-T12-design.md for exact Task2 requirements (real application/testing sections and fault matrix rows assigned LifecycleIT). Scope ownership is exclusively DownloadTaskLifecycleIT.java and download-task-lifecycle.spec.js. Root owns Playwright config and all other files. No subagents. No commits; stage only your files. Follow repository minimum-code rule. Reuse exact T09 ControllerIT Source/adapter and ApplicationConfigurationIT boot patterns after full reads. Production 40/RANGE gates unchanged. Implement actual lost response, privilege and restart scenarios as designed; do not reduce assertions to status-only. First build missing-spec failure then complete browser spec. Coordinate before Maven builds to avoid simultaneous target cleanup. Environment verified Java21, Node target/frontend/node v24.15.0, Docker29.5.2 via Colima, Chromium1234. Tool escalations for Docker/Maven/browser are within user-authorized testing; normal sandbox may deny sockets. Prefix Maven env DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock. Root CORS green Maven currently finishes soon. Write full report including command/exit/count/log, file list, concerns to sibling task-2-report.md; final message only status/test summary/concerns.
