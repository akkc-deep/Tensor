# Java 生产源码常量提取复核

## 范围

- 工作区：`.worktrees/constants-complete`；分支：`refactor/constants-complete`；基线：`10fb941`。
- 覆盖全部 `src/main/java`，不修改测试及前端源码。
- 保留基础字面值 `0`、`1`、`-1`、`true`、`false`、`null`。
- 异常、校验和日志 message 不提取；撤销上一轮新增的消息常量及消息枚举替换，恢复原文。

## 实现

- 补齐 SQL 片段、字符串、协议字段、配置值、超时、长度限制、批次数量、列索引及接口行数限制。
- 跨模块使用公共常量，局部用途使用私有常量；不同业务含义使用各自的名称，避免因数值相同而绑定无关配置。
- 移除 `ValidationMessages`；保留首次重构前已有的消息设计。
- 使用 JDK `HexFormat` 替代手写十六进制转换，保持指纹结果不变。
- 新增 `scripts/JavaConstantAudit.java`：使用 JDK 语法树扫描，识别注解和文本块，按源码位置消除 record 注解重复计数。
- 检查重复裸字面值，以及仍与已有常量定义重复的裸字面值；集合、运行时初始化和常量表达式内的字面值正常参与检查。只有直接命名的常量定义、基础字面值和消息上下文豁免。

## 验证

在独立工作区运行（JDK 21）：

```sh
java scripts/JavaConstantAudit.java
```

结果：扫描 **154** 个生产 Java 文件，未处理重复值 **0** 组。发现重复时退出码为 `1`，解析失败时为 `2`。

扫描器另用临时源码验证 **17** 个边界场景，全部通过：重复字符串/数字、已有常量匹配、仅常量定义、集合、常量表达式、运行时初始化、常量字段注解、基础值、消息、record 注解、协议字段、unchecked 注解、注释、文本块、测试目录排除、解析失败。

完整 `mvn -o -f data-plane/pom.xml verify` 已通过。最终源码再次运行：

```sh
mvn -o -f data-plane/pom.xml verify -Dskip.installnodenpm -Dskip.npm
git diff --check
```

最终构建于 2026-09-16 03:53:20（Asia/Shanghai）成功：**1,132** 项 Java 测试及 **4** 项打包 JAR 测试全部通过，无失败、错误或跳过。最后一次验证复用完整构建的前端产物。

独立审查通过：SQL 文本、数值边界、时间单位、字节限制及指纹编码保持行为；此前新增的消息提取已撤销，无待解决问题。

## 后续：单次内联字符串及乘法表达式

- 按本轮请求继续覆盖所有 Java 生产源码，将单次出现的非消息字符串也纳入提取范围。
- 注解、日志（含 MDC 上下文）和异常消息保留；已有命名枚举常量保留原设计。
- `DownloadTaskJson` 的任务、快照及输入上限恢复为 `8 * 1024`、`16 * 1024`、`128 * 1024`，数值保持不变。
- 审计新增单次字符串检查，支持命名枚举常量、异常构造器及字面量乘法常量；集合和运行时初始化中的字符串仍需提取。
- 临时边界样例验证：20 项通过，覆盖单次字符串、文本块、注解、record 注解、日志、异常、普通构造器、枚举、乘法、集合、运行时初始化、重复数字及解析错误。原脚本有 7 项不符合新规则，修改后全部通过。
- 最终审计扫描 153 个生产 Java 文件：284 处待提取字符串归零，重复非字符串值为 0。AST 快照比较确认 696 处注解及消息字面量没有新增、删除或改写。
- 保留 `UpsertSqlFactory` 无字段、`GlobalExceptionHandler` 仅日志字段、`TushareProClient` 仅 JSON 静态字段的既有反射契约；需要时将常量放入现有外部常量类。校验注解名通过对应类型获取，保留原来的空值判断行为。

最终验证命令：

```sh
java scripts/JavaConstantAudit.java
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  -Dtest=DownloadTaskRepositoryIT,ExistingKeyRepositoryIT,PersistenceServiceIT,DatasetQueryServiceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml verify -Dskip.installnodenpm -Dskip.npm
git diff --check
```

49 项 MySQL 集成测试通过；最终 Maven 验证于 2026-09-16 11:41:05（Asia/Shanghai）成功，1,132 项 Java 测试及 4 项打包契约测试通过，失败、错误和跳过均为 0。前端复用已有构建产物。

独立任务审查及最终兼容性审查均通过，无待处理问题。
