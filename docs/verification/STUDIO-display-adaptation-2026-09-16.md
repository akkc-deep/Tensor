# Studio 2K / 4K 显示适配与 Demo 控件对齐

日期：2026-09-16。对象为正式 Studio 的下载、数据查看、设置和任务详情。延续 [一致性检查](STUDIO-consistency-audit-2026-09-16.md)，按固定 Demo 的布局和交互样式实现大屏适配。

## 显示行为

默认自动模式按浏览器 CSS 视口宽度选择比例：小于 2240px 为 100%，2240–2879px 为 125%，2880–3519px 为 150%，3520px 起为 200%。因此未启用系统缩放时，1920×1080 为 100%、2560×1440 为 125%、3840×2160 为 200%。

系统和浏览器缩放已经体现在 CSS 视口中，不再乘 `devicePixelRatio`。例如物理 4K、系统 200% 时，约 1920 CSS px 的窗口按 100% 布局。使用窗口宽度可以同时处理分屏、多显示器及窗口调整。

“外观设置 → 显示与布局 → 显示比例”提供自动、100%、125%、150%、175%、200%。选择保存在 `tensor-display-scale`；存储不可用时仍立即应用并提示仅本次生效。窗口过窄会临时降低手动比例，扩大窗口后恢复原选择。

尺寸以 10px 根单位的 rem 表达，正常文字仍是 14px；所有正式页面和第三方控件共用显示比例。表格列宽与表体最大高度同步更新。没有使用整页 CSS zoom，浮层定位及详情的 `100dvh` 保持浏览器原生坐标。

主内容上限随比例缩放：1490px、1862.5px、2980px 分别对应 100%、125%、200%，页面居中，避免在 4K 上文字过小或内容无限拉伸。

## 本次对齐

- 文本、日期、枚举与目录搜索的悬停边框、焦点光环和过渡采用 Demo 规则。
- 下载／查询按钮统一悬停、按下背景、1px 基准位移和键盘焦点；禁用模式按钮透明度统一为 0.55。
- 详情关闭按钮基准尺寸改为 34px；分页前后按钮采用 34px 箭头外观，保留中文可访问名称、页码与条数选择。
- 主题、模式和按钮补齐过渡；减少动态效果时停止过渡与位移，强制颜色模式保留可见输入焦点。
- 成功色统一为 Demo 的 `#28745a`，任务失败／中断状态采用警告色；错误信息继续保留错误色。
- 同步放大 Element Plus 下拉文字、选项、分页与长文本提示的字号／行高；下载表单在宽视口下采用 Demo 的 32px 基准横向留白。

正式页面仍保留真实数据源、搜索式数据集选择、完整字段、业务状态、服务端分页和自定义主题。因此对齐的是布局与共同控件的尺寸及交互，不将不同业务内容描述为整页逐像素相同。固定 Demo 源码没有为本次验证而改动。

## 验证

环境：Node 24.15.0、Playwright Chromium，开发服务器 `127.0.0.1:4174`。全部业务请求采用受控 HTTP 夹具，不访问真实数据库或上游数据源。

- 单元测试：35 个文件，607 项通过。新增 6 项显示状态测试覆盖自动比例、系统像素密度、手动设置恢复、窗口缩小、异常存储、非法值和清理。
- 生产构建通过；仍有既有大于 500 kB 的 chunk 提示（app 958.39 kB，gzip 304.92 kB），本轮未做拆包优化。
- 完整浏览器回归执行 157 项，首轮 154 项通过、3 项失败：分页隐藏文字被溢出检查计入；设置新增显示状态后旧定位器匹配两项；旧键盘测试只接受 outline，未识别 Demo 焦点光环。已分别简化隐藏方式、限定主题状态定位器，并对输入控件明确断言主题边框及 2px 光环，保留按钮／导航的 outline 断言。相关 5 个套件复测 75 项，其中 74 项通过，四主题用例仍保留旧成功色 RGB 和硬轮廓预期；按 Demo 绿色与焦点光环更新后，该用例单独复测通过（1/1，包含四主题、存储降级、业务页颜色）。最终没有未解决的失败。
- 新增浏览器用例检查设置持久化／自动恢复、输入键盘焦点、按钮悬停／按压／禁用／减少动态效果；在 1920、2560、3840 宽度验证输入高度、下拉字号与定位、已加载任务及批次、详情边界、查询与整页溢出；并验证 2K 调整到 4K 后表格和多行全文提示。
- 独立审查发现并修正两项：详情测试缺少 detail/batches 夹具；4K 长文本提示字号放大后行高仍固定 20px。后者先复现 2K 期望 25px、实际 20px，再改为可缩放的 2rem。
- 原有 1024／1280／1440 的 Demo 几何断言保留精确比较，没有为缩放误差放宽阈值。使用 10px 根单位消除了最初 14px 根单位转换造成的布局舍入误差。

运行命令（在 `control-plane`，Node 24.15.0）：

```bash
npm test
npm run build
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-display.spec.js studio-acceptance.spec.js studio-shell.spec.js studio-catalog.spec.js studio-form.spec.js studio-submission.spec.js studio-tasks.spec.js studio-detail.spec.js studio-recovery.spec.js studio-datasets.spec.js studio-table.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-display.spec.js studio-shell.spec.js studio-tasks.spec.js studio-table.spec.js ui-redesign.spec.js
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- ui-redesign.spec.js -g '设置零 API'
```

参考源码核对命令输出为空：

```bash
git diff 2ae5963 -- control-plane/src/demos ':!control-plane/src/demos/README.md' ':!control-plane/src/demos/live'
```

## 截图

| 页面 | 2K | 4K |
| --- | --- | --- |
| 下载 | [2560](studio-display-2026-09-16/downloads-2560.png) | [3840](studio-display-2026-09-16/downloads-3840.png) |
| 数据查看 | [2560](studio-display-2026-09-16/datasets-2560.png) | [3840](studio-display-2026-09-16/datasets-3840.png) |
| 完整任务详情 | [2560](studio-display-2026-09-16/detail-2560.png) | [3840](studio-display-2026-09-16/detail-3840.png) |
| 设置 | [2560](studio-display-2026-09-16/settings-2560.png) | [3840](studio-display-2026-09-16/settings-3840.png) |
| 多行全文提示 | [2560](studio-display-2026-09-16/tooltip-2560.png) | [3840](studio-display-2026-09-16/tooltip-3840.png) |

测试覆盖 PC 桌面 Chromium；不以本轮夹具结果宣称真实下载、数据库写入或其他浏览器已验证。开发服务器仍记录 T10 已存在的 `ResizeObserver loop completed with undelivered notifications`，本轮未将其作为显示适配问题修复。临时 4174 服务已停止。
