# ISSUE-003：数据库交互逻辑较复杂

## 当前阶段

[Spring JDBC 数据库层重构方案](../proposals/ISSUE-003-spring-jdbc-complexity-reduction.md) 的架构设计已确认，待文档复核；尚未修改生产代码。

## 问题描述

当前项目与数据库交互使用 Spring JDBC，而不是 MyBatis。查询、写入、SQL 生成、参数绑定和结果映射包含较多手写逻辑，整体实现和维护较复杂。

## 已知事实

- `tensor-core` 依赖 `spring-jdbc`，项目当前没有 MyBatis 依赖。
- `ExistingKeyRepository` 和 `GenericUpsertRepository` 直接使用 `JdbcTemplate`。
- `UpsertSqlFactory`、`QuerySqlFactory` 和 `ExistingKeyRepository` 手工生成动态 SQL。
- `JdbcValueBinder` 和各 Repository 手工处理 JDBC 类型绑定及结果映射。
- `SchemaInspector` 直接使用 `DataSource` 和 `DatabaseMetaData` 检查运行时表结构。
- 数据集的表名、列、业务键和逻辑类型由运行时元数据决定，数据库访问并非固定表结构下的简单 CRUD。

## 方案设计阶段已回答的问题

- 引入 MyBatis 能否实质减少当前动态 SQL、参数绑定和结果映射的复杂度？
- 运行时表名和列名如何保持白名单校验及安全引用？
- 动态数据集的查询、批量 upsert 和已存在业务键查询分别适合采用哪种实现方式？
- `SchemaInspector` 是否继续保留原生 JDBC 实现？
- 如何保持现有事务、批处理、分页、类型转换和异常行为不变？
- 若不引入 MyBatis，是否存在更小的 Spring JDBC 封装方式可以收敛重复逻辑？

## 约束

- 先比较方案再决定是否引入 MyBatis，不把技术替换本身当作目标。
- 保持数据集运行时元数据驱动能力。
- 保持 SQL 标识符校验、参数化查询和现有事务边界。
- 不改变数据库结构、插件协议、HTTP 契约或现有业务行为。
- 遵循仓库的最小代码原则。

## 已确认决策

- 不迁移 MyBatis；当前复杂度主要来自运行时动态表、动态列和动态业务键，切换框架不能消除这些规则。
- 允许修改 Service、Repository 和 Spring 装配，但不修改数据库、Flyway、插件协议或 HTTP 契约。
- 为每个已接纳的数据集启动时生成不可变 `DatasetJdbcPlan`，避免重复解析固定元数据和 SQL。
- 将三个现有 Repository 收敛为 `DatasetWriteRepository` 和 `DatasetReadRepository`。
- 写入 Repository 封装已有键查询、精确计数和 Batch Upsert；读取 Repository 封装 COUNT、页码归一、Page Query 和结果映射。
- Service 继续管理用例边界；写入锁和事务仍由 `PersistenceService` 管理。
- 将重复的 JDBC 类型映射、参数绑定和结果读取收敛到唯一的 `JdbcValueCodec`。
- `DatasetStartupValidator` 同样使用 `JdbcValueCodec` 的类型映射，避免启动校验与运行时读写规则漂移。
- 保留 `SqlIdentifierPolicy` 和 `UpsertSqlFactory`；删除旧 Repository、`JdbcValueBinder`、`QuerySql` 和 `QuerySqlFactory`。
- `SchemaInspector` 继续使用原生 JDBC `DatabaseMetaData`，不纳入业务 Repository。
- 具体结构、行为边界和验收方式以已确认的 [方案](../proposals/ISSUE-003-spring-jdbc-complexity-reduction.md)为准。

## 非目标

- 不在问题记录阶段修改生产代码或依赖。
- 不在未验证收益前全面重写持久化层。
- 不把 Flyway 数据库迁移职责纳入 ORM 或 Mapper 替换范围。

## 后续产物

1. 已确认架构、待文档复核的 [Spring JDBC 数据库层重构方案](../proposals/ISSUE-003-spring-jdbc-complexity-reduction.md)。
2. 实施计划和可独立验收的任务。
3. 实现、回归测试及验收证据。

## 关闭条件

- 数据库访问方案已经确认并完成实施。
- 手写数据库交互逻辑按设计得到收敛，且动态数据集能力保持不变。
- 查询、写入、事务、批处理和错误行为的回归测试通过。
- 实施结果及验证证据已记录。
