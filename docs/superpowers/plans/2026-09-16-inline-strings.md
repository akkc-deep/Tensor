# Inline Strings Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review the production refactor while the controller updates the audit.

**Goal:** 将 Java 生产源码中注解、日志和异常消息以外的内联字符串提取为常量，并恢复此前被展开的乘法表达式。

**Architecture:** 优先复用已有枚举和框架常量；局部值使用所属类的私有常量，跨类值放入现有常量类。保持字符串内容、SQL、初始化顺序和运行行为。

**Tech Stack:** Java 21、Maven、JDK AST。

**Spec:** 本轮用户请求及 `AGENTS.md`；沿用此前确认的 Java 后端范围。

## Global Constraints

- 使用最小代码；新文件加入 Git；允许直接在 main 工作。
- 保留现有前端和任务文档的未提交改动。
- 注解、日志及异常消息保持内联；既有消息枚举保持原设计。
- 测试样本保留独立字面量；不改运行行为。

## Task 1: Production constants

**Files:** `data-plane/*/src/main/java/**/*.java`，按 `/tmp/tensor-inline-audit.txt` 清单逐项确认。

- [x] 提取单次出现的业务字符串，包括 SQL、字段名、正则、元数据和协议值。
- [x] 对消息流向人工复核，排除异常及日志文字，避免仅按文件名豁免业务字符串。
- [x] 将 `DownloadTaskJson` 的三个上限恢复为 `8 * 1024`、`16 * 1024`、`128 * 1024`。
- [x] 复查差异，确认注解、消息原文和数值不变。

## Task 2: Audit and verification

**Files:** `scripts/JavaConstantAudit.java`、`docs/verification/2026-09-16-constants-complete.md`。

- [x] 审计报告所有未豁免的内联字符串，继续检查重复数字；常量定义允许字面量乘法。
- [x] 用临时 Java 源码检查单次字符串、注解、日志、异常、枚举消息、乘法常量及运行时初始化边界。
- [x] 运行 `java scripts/JavaConstantAudit.java` 和 `mvn -o -f data-plane/pom.xml verify -Dskip.installnodenpm -Dskip.npm`。
- [x] 独立审查本轮 Java 和审计差异；运行 `git diff --check`，记录结果并将新增计划加入 Git。

## Results

- 153 个 Java 生产文件审计通过，284 处待提取内联字符串归零。
- 3 处乘法表达式恢复；696 处注解及消息字面量原样保留。
- 20 项审计边界样例、49 项 MySQL 集成测试、1,132 项 Java 测试和 4 项打包契约测试通过。
- 独立任务审查通过；保留既有反射契约及空值行为。新计划已加入 Git 版本控制。
