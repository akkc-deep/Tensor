# ISSUE-004-T01：主题计算与全局样式基础

## Goal

用一个应用级主题状态将已确认 palette 应用于根节点和 Element Plus。

## Scope

仅主题纯计算、存储降级、App 初始化及 CSS 映射；不改导航、布局和业务流程。

## Approach

`utils/theme.js` 导出 `DEFAULT_ACCENT = '#2857b4'`、`createTheme(value)` 和 `contrastRatio(a,b)`。只接受字符串 #RRGGBB（大小写均可），输出标准小写 `{ requested, applied, colors }`，非法返回 null。完整默认 colors 为 bg #edf2f6、surface #ffffff、raised #f3f6fa、nav #f9fbfd、line #c8d3e0、text #142a42、muted #52677d、accentBg #e8efff、accent #2857b4、success #14785e、error #b72d47。自定义混色比例及亮度计算精确沿用已确认 HTML 的主题脚本；操作色向黑色逐次混合 0%–100%，取对五种表面和白字均达到 5.5:1 的首个结果。

`composables/useTheme.js` 导出 createThemeState、useTheme 及用于 provide 的 Symbol。createThemeState 在每次 App setup 创建，不使用模块单例。返回 requested、applied、storageStatus 三个只读 ref 和 apply(value)、reset()；成功应用返回 true，非法返回 false 且完全不写状态、DOM、存储。将 colors 写为根 --tensor-bg / surface / raised / nav / line / text / muted / accent-bg / accent / success / error。初始化在子页挂载前读 tensor-issue004-accent，只持久化输入 HEX；损坏/缺失值回退完整默认。读失败、写失败为 preview-only，不阻止预览；reset 应用默认并尝试保存，成功为 saved。

App.vue provide 共享状态。CSS 以语义变量映射 Element Plus 主色 light/dark、文字、背景、边框、fill、disabled、success/danger 及浅色系列；普通/hover/active 主按钮保持深色操作背景与白字。根映射覆盖 teleport 弹层、tooltip、固定列，业务不复制颜色计算。布局样式保留至 T02。

## Files

新建 control-plane/src/utils/theme.js、theme.spec.js；control-plane/src/composables/useTheme.js、useTheme.spec.js。修改 control-plane/src/App.vue、App.spec.js、style.css。共用计算只有 theme.js，共享状态只有 useTheme.js。

## Tests

在 control-plane 使用 Node 24.15.0：先写 theme.spec.js 并运行 `npm test -- src/utils/theme.spec.js` 验证缺失能力失败。覆盖完整默认 palette、大小写、非法值、白黄绿黑和 #b52c63 对全部表面的 5.5:1。状态测试覆盖保存/恢复、损坏、读写抛错、非法输入零变化、完整重置、不同应用实例隔离。App 测初始化变量及提供共享实例。运行 `npm test -- src/utils/theme.spec.js src/composables/useTheme.spec.js src/App.spec.js` 和 `npm run build` 均退出 0。

## Acceptance

完整默认值精确匹配；合法主题整页应用且操作色达标；非法值不改变任何已应用值；存储故障仍预览；重置恢复完整 palette；针对性测试和构建通过，无 API 导入。

## Risks

Element Plus 控件覆盖必须连同各交互态考虑；正式浏览器的计算样式检查在 T06 完成，不以纯计算测试冒充视觉验收。
