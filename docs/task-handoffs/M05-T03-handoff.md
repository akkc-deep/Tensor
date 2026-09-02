# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M05-T02`
- **Next task:** `M05-T03`
- **Design document:** `docs/task-designs/M05-T03-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID / title:** `M05-T03`——元数据驱动参数校验。
- **Goal:** 在 `tensor-core` 中建立唯一的后端参数准入边界，只依据 `ApiDescriptor.parameters` 拒绝未知、缺失和无效输入，并返回可直接传给插件下载 SPI 的有序不可变规范化字符串 map。
- **Scope:** 精确创建 `ParameterValidator.java`、`ValidatedParameters.java` 和 `ParameterValidatorTest.java`；内嵌公开只读的 `ParameterValidationException`/`FieldError`；冻结 required/default/optional、六类参数、整串 pattern、声明顺序范围、两阶段错误聚合和安全消息；不修改 POM、plugin-api、Tushare metadata 或既有 core 类型，不实现 Spring/REST、插件调用、列转换、适配、下载或持久化。
- **Acceptance criteria:** 公开表面与设计一致；成功结果只含已声明、按描述符顺序排列的规范化字符串且不可变；用户错误确定地产生 `PARAM_REQUIRED|PARAM_INVALID` 与安全字段错误，错误元数据阻止调用；严格 RED/10 项 GREEN、模块 `test`/`verify` 109/109、三层 Enforcer、静态/范围/格式/清理和精确三文件提交门禁全部通过。

## Dependencies

### `M02-T02`

- **Artifact:** `docs/task-designs/M02-T02-designs.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ParameterType.java`、`ParameterDescriptor.java`、`ApiDescriptor.java`。
- **Decision:** 六项 `ParameterType` 为 `DATE|DATE_RANGE_MEMBER|MONTH|TS_CODE|ENUM|TEXT`；`ParameterDescriptor` 保存 name、type、required、defaultValue、allowedValues、pattern、relatedParameter，`ApiDescriptor.parameters` 保持声明顺序且名称唯一。
- **Rationale:** 前后端和插件必须共享一组不可变参数元数据，后端校验不能依赖 API 名称或复制接口专用规则。
- **Constraint:** 不修改、trim 或改写描述符；name/relatedParameter 继续遵守 `^[a-z][a-z0-9_]{1,63}$`；ENUM 有非空 allowedValues，DATE_RANGE_MEMBER 有非自身 relatedParameter；跨参数关系由 core 校验。
- **Usage:** `ParameterValidator` 按参数列表顺序读取全部规则，构造成功结果顺序并驱动 required/default/type/enum/pattern/range 校验。
- **Readiness evidence:** 权威看板为 `COMPLETED`；最终提交 `7984f0c` 精确交付 descriptor 类型，聚焦 19/19、模块 `test`/`verify` 45/45、Enforcer、范围和最终审查均通过。

### `M02-T05`

- **Artifact:** `docs/task-designs/M02-T05-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`、`TensorException.java` 和 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`。
- **Decision:** 参数失败使用 retryable=false 的 `PARAM_REQUIRED` 或 `PARAM_INVALID`；后续受控异常可继承只保存 code/安全 message 的 `TensorException`；插件下载边界接收 `Map<String,Object>`。
- **Rationale:** core 应复用跨模块统一错误分类和既有 SPI 泛型，不创建重复错误枚举或不兼容参数容器。
- **Constraint:** 内嵌参数异常只能接受两项参数错误码，不增加 cause、raw、Token 或额外诊断状态；`ValidatedParameters.values()` 保持 `Map<String,Object>`，实际值全部为 String；不得修改 plugin-api。
- **Usage:** `ParameterValidationException` 继承 `TensorException` 并暴露不可变字段错误；成功 map 可原样交给 `DataSourcePlugin.download`。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `445b941` 与契约修复 `dd495ee` 交付稳定错误/SPI 表面，聚焦 8/8、模块 `verify` 79/79、Enforcer、`jdeps=java.base`、范围和最终无发现复审均通过。

### `M03-T09`

- **Artifact:** `docs/task-designs/M03-T09-design.md`；`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java` 和 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 49 份已验证定义。
- **Decision:** 49 API 的参数全集、声明顺序、required、allowedValues 和 relatedParameter 已由独立期望冻结；当前全部声明参数均 required，pattern/defaultValue 均为 null，三组日期范围均以开始日期在前、结束日期在后并双向关联。
- **Rationale:** 参数校验器必须对真实首期元数据通用工作，不能从被测实现、自举规则或 API 名分支补齐语义。
- **Constraint:** 保持 49 API 参数合同和声明顺序；ENUM 逐字匹配，DATE/MONTH/TS_CODE 使用各自类型规则；范围方向采用项目所有者批准的声明顺序而非参数名猜测。
- **Usage:** 测试以手工构造的真实描述符覆盖 M03 已使用的参数形状，并以 49/49 合同作为无接口特判的兼容基线。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `36230d8` 的定向契约测试 50/50、reactor `test`/`verify` 137/137、三层 Enforcer、JAR 排除、范围/格式/清理和独立审查均通过。

三项依赖的决策与约束一致：M02-T02 定义参数规则形状和顺序，M02-T05 定义错误分类与下载容器，M03-T09 提供经独立门禁验证的 49 API 参数实例；M05-T03 只在 core 中消费这些稳定公开合同，不产生依赖环、不修改上游，也不扩大到插件或 HTTP 编排。未发现冲突。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M05-T03-design.md`；
2. `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 Global Constraints、Task M05-T03 与 Module Gate；
3. `docs/task-designs/M02-T02-designs.md`；
4. `docs/task-designs/M02-T05-design.md`；
5. `docs/task-designs/M03-T09-design.md`；
6. 上述 Dependencies 中列出的当前公开 Java 类型和 49/49 参数契约测试。

首个实施动作：运行 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`，确认现有 plugin-api 79 项、core 20 项、合计 99/99 与三层 Enforcer 通过；随后才按设计完整创建 `ParameterValidatorTest.java` 并在两个生产文件不存在时取得可归因 `testCompile` RED。

## Risks

- Java 正则没有执行超时；v1 已验证元数据当前没有 pattern，未来 pattern 必须保持受信配置，不能来自用户动态输入。
- `Map<String,Object>` 为 SPI 兼容所需；实现和测试必须保证 validator 成功值实际全部为 String，且用有序不可变副本保存。
- 日期范围方向依赖 M03-T09 已冻结的声明顺序；未来若需独立方向字段，应先修订 M02 元数据合同，不能改为参数名/API 名推断。
- 现有 M05-T02 Mockito 测试在受限 JVM 沙箱内可能无法自附加；模块门禁需使用允许 JVM attach 的既定执行环境，动态 agent 提示属于已批准的安全运行时 WARNING。
