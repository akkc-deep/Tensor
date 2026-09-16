# 注解参数恢复验证

## 范围与结果

- 隔离工作区：`.worktrees/restore-annotation-literals`；分支：`refactor/restore-annotation-literals`；基线：`a72189d`。
- 按用户要求恢复所有生产 Java 注解中由此前常量重构引入的替换，共 **14 个文件、35 处注解**。
- 恢复类型：`Bean`、`Qualifier`、`DefaultValue`、`GetMapping`、`RequestMapping`、`PathVariable`、`JsonProperty`、`JsonPropertyOrder`、`SuppressWarnings`、`ConditionalOnProperty`。
- 使用 JDK 语法树逐项比较，全部 **236 个生产注解**与重构前提交 `2ae5963` 的源码一致。
- 删除因此失去用途的 **7 个常量**及无用导入，包括仅剩空壳的 `WebConstants` 类。
- `JavaConstantAudit` 排除注解参数，继续检查方法体、字段初始化器及其他业务代码。

## 验证

```sh
java scripts/JavaConstantAudit.java
mvn -o -f data-plane/pom.xml verify
git diff --check
```

- 扫描 **153** 个生产 Java 文件，未处理重复值 **0** 组。
- 扫描器 **23** 个临时边界用例通过，包括嵌套注解、数字注解、被注解的方法体和字段初始化器。
- 完整构建于 **2026-09-16 04:08:28（Asia/Shanghai）**成功：**1,132** 项 Java 测试、**4** 项打包 JAR 测试通过，无失败、错误或跳过。
- 首次构建有一项 WireMock 本地连接错误；同一代码单独重跑该测试类的 30 项测试全部通过，随后完整构建通过，连接错误未复现。
- 独立审查通过；测试及前端源码无改动。
