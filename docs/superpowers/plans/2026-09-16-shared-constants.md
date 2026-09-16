# Shared Constants Implementation Plan

**Goal:** 在独立工作区提取 Java 后端重复使用、语义相同的固定值，并替换调用点。

**Architecture:** 通用常量放入 `tensor-plugin-api`，模块专用常量保留在所属模块；单类内部重复值使用私有常量。复用已有枚举和框架常量，保持接口、序列化、SQL 和校验行为不变。

**Tech Stack:** Java 21、Maven、JUnit 5、Spring Boot。

## Constraints

- 使用最小代码；新建文件加入 Git。
- 基于当前 HEAD 创建 `.worktrees/shared-constants` / `refactor/shared-constants`。
- 普通空值检查中的参数标签、SQL 语法片段、无业务含义的 0/1 和独立测试样本不做机械合并。
- 用户已确认仅处理 Java 后端。

## Tasks

- [x] 创建独立工作区，扫描生产 Java 代码的重复字面量和现有常量。
- [x] 运行 Maven 基线测试，确认原有行为。
- [x] 提取通用校验规则、字段名和分页限制，替换所有对应生产调用点。
- [x] 提取 Tushare 接口标识、任务默认值和模块内部重复值，复用现有枚举与框架常量。
- [x] 复查剩余重复值和逐文件差异，运行 Maven 测试及构建，核对接口和架构测试。
- [x] 将新建文件加入 Git，交付工作区位置、变更摘要和验证结果。

## Verification

运行 `mvn -o -f data-plane/pom.xml test`。既有测试使用独立字面量验证协议、参数绑定、元数据、JSON 和 SQL 结果，不随生产常量替换。

环境记录：沙箱内 Mockito 自挂载失败；使用获准的沙箱外 Maven 运行完成基线与最终验证。

## Results

- 新增 9 个常量类；复用已有枚举、请求头、哈希算法、UTC 和媒体类型常量。
- `mvn -o -f data-plane/pom.xml verify` 通过，包含前端构建及打包契约验证。
- 收尾后运行 `mvn -o -f data-plane/pom.xml verify -Dskip.installnodenpm -Dskip.npm`：1,132 项 Java 测试和 4 项打包契约测试通过，0 失败、0 错误、0 跳过；复用前一步已验证的前端产物。
- `git diff --check` 通过；独立只读审查确认无遗留问题。
- 所有变更新增文件已加入独立工作区 Git 暂存区，未提交或合并。
