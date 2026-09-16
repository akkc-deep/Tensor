# STUDIO-T05 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T05-design.md`。
- 环境：Node 24.15.0、Chromium、JDK 21、MySQL 8.4.6（Colima／Testcontainers），本机 Vite `http://127.0.0.1:4174`。
- 视觉基准：任务板锁定的 Studio Demo（提交 `2ae5963`），Demo 未修改。

## 实现结果

正式右栏改为 Studio 任务卡片，提供全部／进行中／已完成／需处理四组、真实总数、刷新和分页。名称链接仍打开现有真实任务详情；卡片直接展示状态、计划进度、错误与完整性提示，参数、批次分项、大整数行数、写入次数和时间保留在原生“参数与进度”展开区。批次计划未就绪明确展示，终态不会暗示仍在执行。

任务 API 新增互斥于旧 `status` 的 `statusGroup=ACTIVE|DONE|ERROR`。数据库先按分组和其他条件筛选，再计数、稳定排序和分页；列表任务状态、批次计数、页成员和 total 在同一 REPEATABLE_READ 事务中读取。原单状态／提交标识查询和响应结构保持兼容。

标题数字和页脚明确对应当前分组的服务端 total；不显示无法从当前响应准确得出的全局进行中徽标，不在前端过滤当前页冒充完整结果。历史接口只有 apiName／pluginId，因此使用真实编码；不导入 Demo 名称快照。详情弹窗及快捷重试／恢复由后续任务负责。

## 验证

前端命令在 `control-plane` 下执行，PATH 使用 Node 24.15.0；浏览器命令设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`。

| 检查 | 命令 | 结果 |
|---|---|---|
| 新行为 RED → GREEN | `npm test -- src/api/downloadTasks.spec.js src/composables/useDownloadTaskList.spec.js src/components/download/DownloadTaskList.spec.js src/views/DownloadView.spec.js` | 先复现分组查询、状态方法和卡片断言失败，实施后通过 |
| 全量单元／集成 | `npm test` | 37 文件、599 项通过；最终空态修复后相关组件 30 项复验通过 |
| 终态提示 RED → GREEN | `npm test -- src/components/download/DownloadTaskList.spec.js` | FAILED／PARTIAL_FAILED／INTERRUPTED 的未生成计划场景先 3 项失败，修复后 30 项通过 |
| 构建 | `npm run build` | 通过；保留既有产物大于 500kB 的提示 |
| 浏览器回归 | `npm run test:e2e -- studio-tasks.spec.js download-tasks.spec.js studio-submission.spec.js studio-shell.spec.js ui-redesign.spec.js` | 88 项通过，零失败（2.6 分钟） |
| 后端定向测试 | 下方命令 A | 47 项通过：Core 6、App 41，零失败／错误／跳过 |
| Core／Plugin API 单元 | 下方命令 B | 304 项通过：Plugin API 88、Core 216，零失败／错误 |
| 真实 MySQL 集成 | 下方命令 C | `DownloadTaskRepositoryIT` 18 项通过，零失败／错误／跳过 |
| 静态检查 | `git diff --check`；Impeccable detector（两个修改的 Vue 视图） | 无空白错误；detector `[]` |

命令 A（仓库根目录）：

```sh
mvn -o -f data-plane/pom.xml -pl tensor-app -am \
  -Dskip.npm=true -Dskip.installnodenpm=true \
  -DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar \
  -Dtest=DownloadTaskRequestArgumentResolverTest,DownloadTaskControllerTest,DownloadTaskQueryServiceTest,DownloadTaskContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令 B：

```sh
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  -DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar \
  -Dit.test=DownloadTaskRepositoryIT \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify
```

命令 C：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  -Dtest=DownloadTaskRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test
```

浏览器压力切换尺寸／主题期间，Vite 输出过 `ResizeObserver loop completed with undelivered notifications`；该轮 88 项功能与布局断言通过，此记录不表示整轮控制台完全无警告。

项目未绑定 Failsafe，命令 B 实际只运行单元测试；命令 C 显式通过 Surefire 执行 MySQL 集成测试。本地 Mockito 自附加受限，通过显式 javaagent 运行，不跳过相关测试。47 与 304 中有重复的 Core 测试，不能将三行简单相加为不同测试总数。

## 关键场景和修复

- 浏览器使用 66 条交错状态记录，分别得到进行中 22／已完成 11／需处理 33 条；验证 20 条每页时第 2 页、切组回第一页、50 条每页和总数一致。
- MySQL 使用每组三组各 21 条任务，验证筛选在分页前发生、稳定排序、来源／接口／提交标识交集和越尾空页；并发事务中更新任务与批次状态，原查询仍返回同一旧快照，后续查询看到新状态。
- 验证快速切组丢弃旧响应；同范围刷新失败保留旧数据及更新时间，切组失败清空旧范围；空分组可返回全部，越尾空页可返回第一页。
- 验证 5 秒轮询、隐藏／离页暂停及返回立即查询原分组；既有单元测试继续覆盖 5／10／30 秒失败退避、合并刷新、卸载和过期请求。受理单次／批量任务后列表重新查询，保持服务端数据来源。
- 验证原生 details 键盘展开、精确 int64 数字、64 字符来源／接口、超长无空格错误和参数；页面及右栏不横向溢出。
- 视觉审查发现零结果页脚显示“第 1 / 0 页”，新增断言先复现失败，再改为只显示当前分组和 0 个任务；组件 30 项及最终构建复验通过，原七张非零结果截图不受影响。
- 独立代码审查发现未生成计划的失败／中断任务显示“进度自动更新”，已通过 RED → GREEN 改为“查看失败原因”／“执行已中断”，复审确认关闭。
- 初次浏览器运行遇到 Vite 过期依赖缓存 504；使用 `--force` 重启本机 Vite 后复验。轮询测试的路由跳转未等设置页挂载，导致模拟时钟提前推进；补充等待页面就绪后通过，未修改生产轮询机制。每页数量测试点击了被 Element Plus 占位文本遮挡的内部输入，改为点击实际选择器入口。

## 桌面对照和截图

任务标题的横向位置、字体大小、字重、颜色及筛选间距在 1024／1280／1440px 与 Demo 对照通过。右栏沿用浅色背景、蓝色筛选下划线及分隔行；服务端卡片增加必要的完整性／错误反馈、完整字段展开和真实分页，所以行高及总高度随内容变化。演示区的示例记录替换为刷新按钮。截图数据由浏览器夹具生成，用于界面验收，不作为真实下载结果。

- [1024px 正式卡片](studio-t05/tasks-1024.png)／[Demo 对照](studio-t05/demo-tasks-1024.png)
- [1280px 正式卡片](studio-t05/tasks-1280.png)／[Demo 对照](studio-t05/demo-tasks-1280.png)
- [1440px 正式卡片](studio-t05/tasks-1440.png)／[Demo 对照](studio-t05/demo-tasks-1440.png)
- [1024px 长字段和展开区](studio-t05/long-task-1024.png)

独立视觉审查确认三个 PC 宽度及长字段布局可交付；两项收尾（证据持久化、零结果页脚）复核均为 resolved，最终 disposition 为 ship。该收尾复核限定这两项，七张已验收截图字节保持一致。

列表内部滚动保留完整当前页；截图中超出可见高度的条目和展开字段可滚动查看。

## 范围

未运行真实 Tushare 上游采集、持有真实令牌的在线端到端验收或部署；本任务以真实 MySQL 集成测试验证查询与事务、受控 HTTP 浏览器测试验证前端流程。前后端需共同发布新分组参数。未实施 T06 详情弹窗或 T07 列表快捷操作；T06 状态保留 NOT_STARTED。
