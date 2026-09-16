# STUDIO-T03 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T03-design.md`。
- 环境：Node 24.15.0、Chromium、本机 Vite `http://127.0.0.1:4174`。
- 视觉基准：任务板锁定提交 `2ae5963` 的 Studio Demo；未修改 Demo。

## 已实现与验收范围

中栏采用 Studio 下载方式分段按钮和原生日期、月份、枚举、代码及文本控件。single／range 字段、默认值、枚举和能力限制来自真实 HTTP 元数据；普通参数单列，批量起止日期按能力明确指定的字段分组。倒序返回的端点仍按开始／结束正确校验，不通过字段名猜测。

保留必填、枚举、日期关联、代码与元数据 pattern 校验及首错聚焦；代码 trim／大写、日期／月份紧凑格式和选填空值省略保持可用。重复点击当前模式保留参数，切换模式重置；选填枚举可清空，关联日期修改后清除旧顺序错误。校验前读取原生 date／month 的 badInput，避免输入不完整时静默丢失选填条件。

不支持与待验证的批量按钮禁用并显示服务端原因。完整性说明区分 RESPONSE_ONLY、VERIFIED_RULE、CONFIRMED_ROW_LIMIT，保留 int64 行数上限精度，按 splittable 决定是否说明区间拆分，不承诺执行前已完整。无批次预览或 Demo 业务依赖。

提交、接收／恢复和任务列表沿用当前流程，视觉迁移仍归 T04 及后续任务；本项不准备 T04。

## 验证结果

以下 npm 命令在 `control-plane` 下执行，PATH 使用 Node 24.15.0。浏览器命令设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`。

| 检查 | 命令 | 结果 |
|---|---|---|
| 表单行为 RED → GREEN | `npm test -- src/components/download/DynamicParameterForm.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js` | 新模式按钮、日期分组和选填枚举测试先失败；实施后首轮 52 项通过 |
| 原生不完整日期 RED → GREEN | `npm test -- src/components/download/DynamicParameterForm.spec.js src/views/DownloadView.spec.js` | 先复现被当作空值接受；覆盖不发 input 的边界后 33 项通过，独立审查复跑同样通过 |
| 最终单元／集成 | `npm test` | 37 文件、582 项通过 |
| 最终构建 | `npm run build` | 正式入口及两个 Demo 通过；保留既有主包超过 500kB 提示 |
| 离线采集测试 | `node --test e2e/tushare-range-evidence.test.js` | 111 项通过；同步模式按钮，并更新此前依赖旧带编号导航／目录标题的测试替身 |
| 修复定向复验 | `npm run test:e2e -- studio-form.spec.js ui-redesign.spec.js -g '单次／批量表单\|不完整\|设置零 API\|生成八张' --max-failures=3` | 7 项通过：三视口对照、DATE／MONTH 真实键盘输入、四主题与验收截图 |
| 静态检查 | 修改 E2E 的 Node `--check`、impeccable detector 检查两个修改 Vue 文件、`git diff --check` | 通过，detector 无发现 |

最终执行 `npm run test:e2e -- studio-form.spec.js studio-catalog.spec.js studio-shell.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js`：**84 项全部通过**，包括表单 8、目录 6、外壳 3、业务 UI 49、任务 15、股票参数 3。全部针对最终生产代码；40 个接口单次参数矩阵和自定义 RANGE 参数请求均覆盖。

最终截图单独执行 `npm run test:e2e -- studio-form.spec.js -g '单次／批量表单'`，3 项全部通过，对齐有效值和焦点后导出六张对照图，并逐张查看确认。初次对照捕获了 Demo 颜色过渡中间帧，测试已关闭过渡再比较；旧 Element Plus 日期弹层断言改为原生控件类型、焦点和主题色检查。

## 视觉证据

同视口、同模式、同有效参数（000001.SZ，单次 2026-08-07，批量 2026-06-01 至 2026-08-07）对照，截图前移除焦点。三视口均断言模式按钮、范围容器和日期输入的尺寸、字号与颜色，无页面横向溢出。

- [1440px 单次表单](studio-t03/form-single-1440.png)／[Demo](studio-t03/demo-single-1440.png)
- [1440px 批量表单](studio-t03/form-range-1440.png)／[Demo](studio-t03/demo-range-1440.png)
- [1024px 批量表单](studio-t03/form-range-1024.png)／[Demo](studio-t03/demo-range-1024.png)

正式版使用服务端日期标签（如“交易开始日期”），增加真实能力说明；Demo 仍有演示条、示例任务、模拟提交与实时范围错误。正式参数延续提交时校验；对照截图采用有效输入，避免混用这两种错误状态。来源选择、目录排序、提交／接收与任务栏差异不属于本项表单几何比较。

## 审查与边界

独立只读审查指出原生日期分段输入可能不发 input 事件，选填值会被误当空白；已通过浏览器复现，校验前读取真实 validity 后 DATE／MONTH 场景通过。复核未发现新问题，审查的 1024／1440 表单没有溢出或裁切。

浏览器测试用受控 HTTP 响应验证正式前端 API 路径、能力解析、请求参数和任务行为，没有执行真实账号采集。依赖后端／数据库／凭证的 fixture-flow、dataset-query、download-outcomes、tushare-metadata、tushare-live、download-task-lifecycle 已同步受影响控件定位并完成语法检查，未执行完整在线套件。

新增表单 E2E、设计、本文及六张截图已加入 Git，保留此前 T01／T02 变更，未创建提交。
