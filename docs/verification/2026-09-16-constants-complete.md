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
