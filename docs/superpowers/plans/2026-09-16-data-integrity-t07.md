# DATA-INTEGRITY-T07 Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans. 本计划执行已确认的专属设计，不重新决定产品口径。

**Goal:** 独立后台检查、已提交进度、预算和中断恢复。
**Architecture:** T06固定计划→Runner→T03/T04只读单元→T05原子报告；Coordinator管理单工作线程和受理门禁。
**Tech Stack:** Java 21、Spring JDBC、MySQL 8.4.6、JUnit/Testcontainers。
**Spec:** `docs/task-designs/DATA-INTEGRITY-T07-design.md`（全部接口、错误、用例与命令以此为准）。

## Global Constraints

- 用最小代码实现。新文件加入Git；保留已有暂存基线，不全量提交或合并。
- 只写报告表，不变更证券数据、迁移或下载队列；不实施T08来源规则。
- 每单元同快照，规则异常隔离；FAIL > UNKNOWN > WARN > PASS。
- workers=1；默认500/500000/20000/120/1800；计数等于允许、时间到deadline停止。

### Task 1: 仓库状态与历史解码

按专属设计第2、3、5节实现 `IntegrityCheckJson.readScope/readDescriptor` 与仓库 `start/complete/terminate/interruptUnfinished/progress`。只修改这两份生产文件及其测试。先写严格解码、状态CAS、原子终止、进度和恢复测试观察RED，再实现并运行对应测试。

- [x] 失败测试及最小实现
- [x] 真实MySQL验证、审查

### Task 2: 执行器与来源读取计划

按专属设计第1–4节实现Runner、ReadPlanner、来源兼容hook及Budget取消。以RunnerIT的固定多单元、持久缺失问题作为第一项RED；逐项补齐定义变化、异常隔离、预算、任务中断、无扫描报告和参考授权。

- [x] RunnerIT RED
- [x] 最小实现、专项MySQL及边界测试

### Task 3: 协调器与应用生命周期

按专属设计第6节实现Coordinator、Service门禁和配置；验证启动恢复、新请求拒绝/旧请求重放、单worker、停止等待/排空及应用上下文。

- [x] 生命周期RED→GREEN
- [x] 后端回归、独立审查、验收文档与看板完成
- [x] T08专属设计及交接准备；新增文件加入Git
