# M13-T03 生产配置、CORS、SPA fallback 和优雅停机——任务设计

任务编号：`M13-T03`

对应任务：[M13-T03](../superpowers/plans/tensor-modules/M13-packaging-runbook.md#task-m13-t03-生产-webcors-与停机配置20hjavayaml)

实施产物：由 `tensor-app` 在同一 Spring Boot 进程中安全提供 Vue 静态资源、历史路由 fallback 和 `/api/v1`；生产默认关闭 CORS，并在停机时为现有写事务保留足够的优雅退出窗口

## Goal

让 M13-T02 已打入可执行 JAR 的 Vue 控制面能够在直接刷新 `/downloads`、`/datasets` 和其他无扩展名 UI 路径时恢复到同一个 SPA 入口，同时保证 `/api/v1`、`/actuator`、`/assets` 和文件型路径永远不会被成功 HTML 响应掩盖。生产继续采用同源访问且默认无 CORS；开发环境只有在显式提供单一精确 origin 时才能跨源访问 `/api/v1/**`。应用启用 Spring Boot 优雅停机，并让 Web Server 停机阶段的 70 秒等待窗口大于现有 60 秒写事务上限。

## Scope

包含：

- 修改 `data-plane/tensor-app/src/main/resources/application.yml`，增加 `tensor.web.dev-allowed-origin=${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}`、`server.shutdown=graceful` 和 `spring.lifecycle.timeout-per-shutdown-phase=70s`；
- 创建 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`，在 Servlet Web 应用中注册显式 SPA forward 和可选开发 CORS；
- 创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java`，以不启动数据库或网络的 MockMvc/YAML 合同覆盖路由、404、缓存、CORS、健康暴露和停机配置；
- 保持 `/`、`/index.html`、哈希资源、已存在 API 和 Actuator handler 的既有行为；
- 保持 `WebSecurityHeadersConfiguration` 已冻结的安全头与缓存规则：`/assets/**` 为一年 public immutable，`/`、`/index.html`、`/api/**` 和 `/actuator/**` 为 `no-store`，其他路径为 `no-cache`；
- 保留 Tushare 120 秒读取上限和前端 Axios 130 秒上限，并把生产反向代理响应超时不得低于 130 秒记录为部署约束；
- 执行严格 RED、聚焦测试、完整 reactor、范围、格式和 Git 跟踪检查；
- 实现提交精确包含一修改、两新增文件，提交消息固定为 `feat(app): configure production web delivery`。

排除：

- 不修改 `WebSecurityHeadersConfiguration`、`RequestIdFilter`、`GlobalExceptionHandler`、Controller、Core、plugin、POM、前端源码/配置、OpenAPI、migration 或 M13-T02 打包合同；
- 不新增 Spring Security、认证、Cookie/credential CORS、origin pattern、通配 origin、多个 origin、自动重试、异步 MVC、请求取消或第二个 HTTP 进程；
- 不把未知 API、Actuator、assets、带扩展名路径或非 GET/HEAD 请求 fallback 到 HTML；
- 不在应用内实现反向代理，也不创建或修改代理配置、运行说明或 smoke script；这些分别属于部署环境和 M13-T04；
- 不改变 Tushare `connect-timeout: 5s`、`read-timeout: 120s`、Axios `130000ms`、持久化事务 `60s` 或现有 Actuator exposure；
- 不提交 `target`、前端 `dist`、下载的 Node/npm 或其他生成物。

## Approach

### 生产配置合同

在现有 `application.yml` 中增加以下精确配置，不移动或改写已有数据库、Tushare、分页和 Actuator值：

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 70s

server:
  shutdown: graceful

tensor:
  web:
    dev-allowed-origin: ${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}
```

`TENSOR_DEV_CORS_ALLOWED_ORIGIN` 缺失、空字符串或纯空白时表示完全不注册 CORS。非空值作为一个未修剪、未规范化的精确 origin 交给 Spring MVC `allowedOrigins`；不使用 `allowedOriginPatterns`。值 `*` 必须在配置构造时拒绝，防止环境误配把“单一精确 origin”扩大为任意来源。其他不能与浏览器 `Origin` 精确相等的错误值自然不匹配，保持 fail-closed。

`server.shutdown=graceful` 让 Spring Boot 停止接受新请求并等待在途请求完成。`spring.lifecycle.timeout-per-shutdown-phase=70s` 是每个停机阶段的上限，不是整个 JVM 的绝对总时限；其中 Web Server 优雅停机阶段拥有 70 秒，覆盖 `PersistenceService` 新事务的 60 秒 timeout 并保留 10 秒余量。

### 显式 SPA forward

`SpaWebConfiguration` 声明 `@Configuration(proxyBeanMethods = false)`、`@ConditionalOnWebApplication(SERVLET)` 并实现 `WebMvcConfigurer`。它暴露一个同文件内、无公共 API 的 MVC controller，使用返回值 `forward:/index.html` 进行服务器内部转发；不重定向、不改变浏览器地址，也不读取或复制 index 内容。

controller 只注册 GET 映射，HEAD 由 Spring MVC 的 GET/HEAD 语义一并支持：

1. `/`；
2. 单段路径 `/{first}`，其中 `first` 不含 `.` 且不精确等于小写 `api`、`actuator`、`assets`；
3. 多段路径 `/{first}/{*rest}`，其中 `first` 使用同一排除正则，方法体再要求 `rest` 不含 `.`。

首段排除大小写敏感且只排除完整段：`api`、`actuator`、`assets` 不匹配，`api2`、`assets-demo` 仍是合法 UI 首段。任一路径段出现 `.` 都视为文件型路径；多段映射发现 `rest` 含点号时抛出 `NoResourceFoundException`，由现有缺失资源处理得到空 404。精确正则、dot 检查和 forward 逻辑只存在一份私有实现，避免两个映射产生漂移。

Spring MVC 的具体 Controller handler 继续优先于 SPA controller；`/api/v1/**` 的已知接口不受影响。被正则排除或文件型的请求落回现有静态资源 handler：已存在的 `/index.html`、`/favicon.svg` 和 `/assets/**` 正常读取，缺失资源由现有 `MissingResourceHandler` 变成空 404。forward 后的 `/index.html` 因含点号不会再次命中 SPA controller，因此没有递归。

路径结果固定为：

| 请求 | 结果 | `Cache-Control` |
|---|---|---|
| `/` | 内部 forward 到 `/index.html`，200 | `no-store` |
| `/index.html` | 真实静态入口，200 | `no-store` |
| `/downloads`、`/datasets`、`/reports/daily`、未知无扩展名 UI 路径 | 内部 forward 到 `/index.html`，200 | `no-cache` |
| 已存在的 `/assets/<hash>.js|css` | 真实静态资源，200 | `public, max-age=31536000, immutable` |
| `/api`、未知 `/api/v1/**`、`/actuator/**`、`/assets/**` | 不 fallback；不存在时空 404 | `/api` 为 `no-cache`，`/api/**`/Actuator 为 `no-store`，assets 保持 immutable 规则 |
| `/favicon.ico`、`/foo.json`、`/reports/file.csv` 等不存在文件 | 不 fallback；空 404 | 沿用当前 path-based 规则 |

`WebSecurityHeadersConfiguration` 的 `OncePerRequestFilter` 在原始请求路径上只运行一次。因而 UI forward 响应保留原路径的 `no-cache`，而直接入口仍为更严格的 `no-store`；本任务不重复写 header，也不把缓存策略移入 SPA controller。

### 开发 CORS

`SpaWebConfiguration.addCorsMappings` 只有在 origin 非 blank 时才调用 `registry.addMapping("/api/v1/**")`，并固定：

- `allowedOrigins(devAllowedOrigin)`：恰好一个精确值；
- `allowedMethods("GET", "POST", "OPTIONS")`；
- `allowedHeaders("Content-Type", "X-Request-Id")`；
- `exposedHeaders("X-Request-Id")`；
- `allowCredentials(false)`；
- 不显式设置 `maxAge`，沿用 Spring 当前默认预检缓存行为。

同源请求不依赖 CORS。合法 origin 的实际 GET/POST 响应回显精确 `Access-Control-Allow-Origin` 并暴露 `X-Request-Id`；合法预检只允许上述方法和 header。错误 origin、DELETE、`Authorization` 或其他未批准 header 被 Spring CORS processor 拒绝且不返回允许 origin。UI、`/assets/**` 和 `/actuator/**` 没有 CORS mapping；默认空配置下任何路径都不产生允许跨源响应的 header。

### 超时顺序与直接依赖兼容性

运行顺序固定为：Tushare 同步读取上限 `120s` 小于 Axios 客户端 `130s`，生产反向代理的响应超时大于或等于 `130s`。当前同步 Servlet 请求没有独立的应用处理 timeout；添加 `spring.mvc.async.request-timeout` 或把 Tomcat connection timeout误当处理 timeout 都不能证明该顺序，因此本任务不新增此类配置。代理不在仓库内且不属于三个批准文件，`>=130s` 只作为 M13-T04 运行说明和 M14 发布验收的强制输入。

- M09-T06 提供当前 `application.yml`、只公开 health 的 Actuator、缺失资源空 404，以及按请求路径产生安全/缓存头的 `WebSecurityHeadersConfiguration`；本任务追加配置和 SPA/CORS，不修改或复制这些行为；
- M13-T02 提供唯一 Boot JAR及 `BOOT-INF/classes/static/index.html`、哈希 JS/CSS；SPA forward 和静态资源测试直接消费这些已验证资源，不从 `control-plane/dist` 或源码读取替代品。

两个依赖互补且无冲突：M09-T06 固定安全生产 Web 基线，M13-T02 固定运行时静态资源的位置和内容；M13-T03 只在其上增加请求路由与生命周期策略。

## Files

创建：

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`：Servlet 条件下的 SPA forward controller 和开发 CORS 配置；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java`：无数据库/网络的生产 Web、YAML、路由、404、缓存和 CORS 合同。

修改：

- `data-plane/tensor-app/src/main/resources/application.yml`：增加开发 origin 占位符、Spring Boot graceful shutdown 和每阶段 70 秒停机上限。

不创建、修改或删除其他实现文件。实现提交精确包含上述两新增、一修改文件；设计、计划、交接、看板、`.idea/misc.xml`、`target` 和其他生成物不得混入实现提交。

## Tests

所有命令从仓库根目录运行。

### 测试结构

`ProductionWebConfigurationTest` 使用 JUnit 5、Spring Test、MockMvc、`AnnotationConfigWebApplicationContext` 和 `YamlPropertySourceLoader`，不使用 Mockito、不增加依赖、不启动 `TensorApplication`、嵌入式服务器、数据库、Docker 或网络。每个动态 Web fixture：

1. 使用 `MockServletContext`，注册 `@EnableWebMvc` 的测试内配置、真实 `SpaWebConfiguration` 和真实 `WebSecurityHeadersConfiguration`；
2. 测试内配置只模拟 Spring Boot 已有的 `classpath:/static/` 资源 handler，不创建生产 handler 或放宽路径规则；
3. 通过 Spring Test property source 分别注入 blank、合法和拒绝用 origin；
4. 从真实 FilterRegistrationBean 取出安全 header filter 加到 MockMvc；
5. 每次测试结束关闭 context，避免 handler、property 或 filter 状态泄漏。

测试从生成后的 classpath `static/index.html` 中解析一个被入口实际引用的哈希 JS 或 CSS 文件名，禁止硬编码 Vite hash、读取 `control-plane/dist` 或回退源文件。

场景至少覆盖：

1. YAML 精确包含 `${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}`、`graceful`、`70s`、既有 Tushare `120s`，并保持 discovery disabled、exposure 只有 health、probes enabled、details never；
2. `/`、`/downloads`、`/datasets`、`/unknown`、`/reports/daily` 均为 200 internal forward，forward URL 精确为 `/index.html` 且无 `Location`；
3. `/index.html` 和入口实际引用的哈希 asset 是 200 真实资源；
4. `/api`、`/api/v1/missing`、`/actuator/missing`、`/assets/missing.js`、`/missing.json`、`/reports/file.csv` 是空 404，响应不含 index 标记；
5. `/`、`/index.html` 和 API 404 为 `no-store`，UI fallback 为 `no-cache`，存在/缺失的 `/assets/**` 均为 `public, max-age=31536000, immutable`；每类响应继续具有既有六个安全头；
6. blank origin 下跨源实际请求无 `Access-Control-Allow-Origin`/`Expose-Headers`/`Allow-Credentials`，预检不成功；
7. 合法 origin 对 `/api/v1/**` 的 GET、POST 和对应 OPTIONS 预检成功，origin 精确回显，允许两个请求头并暴露 `X-Request-Id`，`Access-Control-Allow-Credentials` 缺席；
8. 同一 origin 对 UI、asset、Actuator 不获得允许 header；错误 origin、DELETE、`Authorization` 预检被拒绝且无允许 origin；
9. 配置 `*` 时创建 `SpaWebConfiguration` 失败，证明环境变量不能开启 wildcard。

### 严格 RED

先只创建完整 `ProductionWebConfigurationTest.java`，不创建生产类、不修改 YAML，运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am \
  -Dtest=ProductionWebConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期固定前端 20 files / 120 tests 和资源复制先成功，上游无匹配测试继续，app 在 testCompile 只因 `SpaWebConfiguration` 不存在而失败。前端、依赖解析、其他编译错误、数据库、网络或错误测试选择失败不是有效 RED。不要提交 RED 状态。

### GREEN 与完整回归

完成生产类和 YAML 后原样重跑聚焦命令，预期所有 `ProductionWebConfigurationTest` 场景通过且不连接数据库、Docker 或网络。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am clean verify
```

预期 Maven 依次完成固定前端 120/120、全部既有和新增后端 Surefire 测试、Boot 3.5.16 repackage 与现有 Failsafe `PackagedJarContractTest` 4/4，reactor 退出 0；允许既有 Element Plus 大 chunk 和 Mockito动态 agent 提示，不允许 skip、失败或新增 warning。若沙箱仅阻止 Mockito/Byte Buddy self-attach，应在正常 JVM 权限下原样重跑，不能删测、skip 或改变 JVM/Mockito 配置。

范围、格式、配置和禁止能力门禁：

```bash
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
rg -n 'dev-allowed-origin|TENSOR_DEV_CORS_ALLOWED_ORIGIN|shutdown|timeout-per-shutdown-phase' \
  data-plane/tensor-app/src/main/resources/application.yml
rg -n 'allowedOriginPatterns|allowCredentials[(]true[)]|setStatus|sendRedirect|RedirectView|Authorization|Cookie|spring-security' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java
git diff -- data-plane/tensor-app/pom.xml control-plane \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-tushare data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java
```

预期格式检查退出 0；scoped status 在实现提交前精确显示一修改、两新增；YAML 扫描只显示批准的三项新增合同和既有 timeout；禁止项扫描无命中；受保护路径无差异。暂存后 `git diff --cached --name-status` 必须精确匹配 Files 节，三个文件均由 Git 跟踪。

## Acceptance

- `/`、`/downloads`、`/datasets` 和任意符合规则的未知 UI GET/HEAD 通过 `forward:/index.html` 返回 200，不重定向且保持原始浏览器 URL；
- `/index.html` 和入口引用的哈希资源继续作为真实 classpath 资源返回；不存在的 API、Actuator、assets 和任意带扩展名路径保持空 404，绝不返回 SPA HTML；
- 保留六个安全头及冻结缓存策略：入口/API/Actuator不缓存、UI fallback 必须重验证、assets 一年 immutable；
- CORS 缺省完全关闭；显式单一非 wildcard origin 只对 `/api/v1/**` 开放批准的方法/header，暴露 `X-Request-Id`，不允许 credential；错误来源、方法、header 和非 API 路径不获得允许响应；
- `application.yml` 精确声明环境化 dev origin、graceful shutdown 和每阶段 `70s`，保持 Tushare `120s`、Actuator health-only 和其他既有生产配置；
- 生产 timeout ordering 明确保持 `120s < 130s <= proxy response timeout`；仓库内不伪造代理或同步请求处理 timeout；
- 聚焦命令与 `mvn -f data-plane/pom.xml -pl tensor-app -am clean verify` 退出 0，现有打包合同仍为 4/4；
- 未修改 POM、安全 header 配置、Controller、前端、业务模块、migration、运行说明或代理配置，未提交生成物；
- 实现提交消息为 `feat(app): configure production web delivery`，精确包含 Files 节的一修改、两新增文件。

## Risks

- Spring Boot 3.5.16 固定的 Spring Framework 6.2.19 已通过独立 `PathPatternParser` 探针验证单段负向正则和尾部 `{*rest}` 可解析，并对 `/downloads`、`/reports/daily`、`/api`、`/api/v1/missing`、`/assets/app.js` 和 `/index.html` 得到设计规定的匹配结果；实现必须使用该已验证 pattern，不改为会吞掉静态资源的 catch-all。
- 多段路径的 dot 检查会把带点号的客户端路由永久视为文件路径，这是项目所有者批准的安全边界；未来若前端需要此类路由，必须另行修改设计。
- `timeout-per-shutdown-phase` 是每阶段而非整个进程的总上限；外部进程管理器的强制终止窗口仍需在 M13-T04 设为足够大，不能把 70 秒误写成 JVM 总时限。
- CORS 只解决浏览器来源控制，不提供身份认证或公网访问控制；生产仍应保持同源和空 origin，M14-T07 将验证发布安全边界。
- Maven 到达 app `generate-resources` 时会重新安装固定 Node/npm、运行前端 120 项测试并生成未跟踪 `target`；这些是既有阻断门禁，不得暂存。Mockito/Byte Buddy self-attach 受沙箱限制时只能在正常 JVM 权限下原命令重跑。
