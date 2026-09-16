# STUDIO-T03：单次／批量参数表单

## Goal

正式下载工作台按真实 single／range 能力呈现 Studio 参数表单，保留规范化、校验及提交参数快照。任务身份以 `docs/task-handoffs/studio-frontend-task-board.md` 为准。

## Scope

- 迁移下载方式按钮、参数、批量日期范围、必填／选填提示、校验错误及能力说明；仅 PC。
- 沿用 T02 选中接口及能力加载状态，保留现有默认模式（可批量则 RANGE）及模式切换重置。
- 提交按钮、受理／恢复和任务列表继续使用现有实现，由 T04 及后续任务迁移；本轮仅完成 T03，不准备后继任务。

## Approach

- `DownloadView.vue` 移除中栏重复的下载配置面板标题，采用 Demo 的分隔线、下载方式标题和两个原生 button，使用 `aria-pressed`。保持现有 `selectMode`、`locked` 和 `canSubmit`；再次点击当前模式不清空输入。
- 可用性来自 `single.available` 和 `range.availability`，不支持／待验证按钮禁用并分别显示“不支持”／“待验证”，原因使用服务端 `unavailableReason`。两种模式均不可用时提交禁用。加载失败沿用 T02。
- `DynamicParameterForm.vue` 保持 `parameters`、`disabled` 以及暴露的 `validate`、`normalizedValues`、`reset` 接口，增加可空 `range` 能力参数。单次参数按服务端顺序；批量普通参数先展示，`startParameter`、`endParameter` 对应字段按起止顺序展示在 `${dateLabel}范围` 下，注明包含起止当天。不通过字段名猜测日期角色。
- 控件直接渲染为原生 input／select：DATE 和 DATE_RANGE_MEMBER 为 date，MONTH 为 month，TS_CODE／TEXT 为 text，ENUM 枚举保持原值；必填枚举含空“请选择”，选填枚举含空“全部”可清空。默认值继续由 `useParameterForm` 转为显示格式。
- 控件有 name、关联 label、required／aria-required、aria-invalid 和关联说明／错误，继续使用 `useFormValidation` 聚焦。原生日期／月份分段未填完可能不触发 input，校验前重新读取 `validity.badInput`，在选填空值省略前报错并阻止提交。不使用浏览器 pattern 代替现有规范化校验：代码 trim＋大写后按通用格式及服务端 pattern 校验；日期和月份转 YYYYMMDD／YYYYMM；选填空值省略。
- 分组后的参数顺序同时用于校验和焦点，避免 RANGE 元数据起止字段倒序时错误比较。保留 SINGLE 既有互相关联日期的描述顺序语义。修改关联日期时清除该对旧校验错误，下一次校验重新计算；任何输入变动均使旧快照失效。
- SINGLE 显示“单次请求，结果不代表完整历史”。RANGE 的 RESPONSE_ONLY 保留明确未确认／可能截断提示；VERIFIED_RULE 显示采用已验证完整性规则；CONFIRMED_ROW_LIMIT 精确保留行数上限，并只在 splittable 为真时说明按规则拆分。均不承诺执行前已完成数据，不展示批次预览，不引用 Demo 状态或规划器。
- 样式沿用 Demo：单列普通参数、43px 控件、标签两端必填状态、21px 字段间距、范围两列 16px 间距、分段按钮 34px 高；使用现有 tensor 主题变量和可见键盘焦点。保持 1024／1280／1440 桌面范围。

## Files

- 修改 `control-plane/src/views/DownloadView.vue`、`control-plane/src/components/download/DynamicParameterForm.vue` 和对应测试。
- 最小修改 `control-plane/src/composables/useDownloadFlow.js` 的重复模式选择保护及 `useParameterForm.js` 的关联日期旧错误清理；补相应验证。
- 新增 `control-plane/e2e/studio-form.spec.js`，同步受影响 E2E 的模式与原生控件选择器，保留业务断言。
- 新增本文、`docs/verification/STUDIO-T03.md` 和 PC 对照截图，回填任务板。新增文件加入 Git。

## Tests

在 `control-plane` 使用 Node 24.15.0：

1. `npm test -- src/components/download/DynamicParameterForm.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js`：先观察新增控件／分组／重复选择测试失败，实施后全部通过；覆盖规范化、必填、枚举、pattern、日期关系、聚焦、禁用、模式切换和能力说明。
2. `npm test` 与 `npm run build`：全部单元／集成及构建通过。
3. 本地 Vite 4174 下设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`，执行 `npm run test:e2e -- studio-form.spec.js studio-catalog.spec.js studio-shell.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js`：受控 HTTP 响应验证真实前端能力接线、参数快照、错误阻止提交及既有业务回归。
4. 同视口、同模式对照 Demo 与正式表单，验证模式控件／字段／范围尺寸、主题、键盘与无横向溢出，保存截图。
5. `git diff --check`，受影响 E2E 的 Node 语法检查及 impeccable detector。依赖后端或账号的套件同步选择器但不声称真实采集已验收。

## Acceptance

- 字段和枚举来自选中模式的能力元数据；切换清空旧参数，重复模式点击保留当前输入。
- 必填、枚举、日期／月份、相关日期排序、代码及服务端 pattern 校验有效，错误可见并聚焦，规范化只包含当前参数。
- 不支持和待验证模式无法提交批量请求；完整性文案与规则匹配，无批次预览。
- 1024／1280／1440 PC 控件布局、键盘交互和 Demo 对照完成；相关测试和构建通过；新增文件 Git 跟踪。

## Risks

- 原生日期控件外观随桌面浏览器／操作系统变化；本轮使用 Chromium 验证。
- 正式能力默认 RANGE，Demo 默认 SINGLE；对照需显式统一模式。服务端描述和必填性优先于 Demo 快照。
- T04 前提交／接收界面仍保持现有结构。本轮受控 HTTP 测试不证明上游账号与真实下载服务可用。
