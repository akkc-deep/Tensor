# Tensor UI Demo · ISSUE-017

**锁定版本：`ISSUE-017-v2-20260909`。** 用户于 2026-09-09 确认“锁定这一版方案”。[设计基准](../ISSUE-017-ui-redesign-v2.md)与[源码指纹](baseline.sha256)用于后续正式实施和验收。

三页可操作预览：数据查看、数据下载、外观与设置。根据用户反馈参考苹果官网，采用系统字体、中性灰白、蓝色主操作及统一的圆角控件。正式前端尚未应用此方案。

## 打开

直接用浏览器打开 [index.html](index.html)，或在仓库根目录运行：

```sh
python3 -m http.server 4177 --bind 127.0.0.1 --directory docs/issues/proposals/ISSUE-017-demo
```

访问 <http://127.0.0.1:4177/>。所有脚本、字体和数据均在本目录，无需构建或连接后端。修改后刷新页面即可；浏览器偏好按访问地址分别保存。

## 可以体验什么

- 数据查看：搜索数据集、证券代码与日期筛选、查询、重置、分页、三档行高、字段标识开关、宽表滚动及长文本全文。
- 数据下载：接口搜索、按接口切换参数、模拟成功／空结果／失败／慢速反馈，以及使用原参数重试。
- 外观与设置：主题色、HEX 校验与对比度修正、字体比较、减少动画和偏好保存。
- 选择器支持方向键、Enter 选择与 Escape 关闭；弹层随控件等宽，分页菜单可向上展开。

查看页的两排筛选项共用列宽与间距；下载页的选择器和请求参数共用列宽与左右留白。表头默认只显示中文名称，技术标识可通过“字段名”展开。文本左对齐，日期居中，数字使用居中的统一数值区域并按小数点对齐。

桌面标准行高 44px，紧凑 38px，舒适 52px；窄屏紧凑行高至少 40px。数字尺寸按当前字体的实际等宽字形计算；原始数值不转成 JavaScript Number，复制时保留完整精度。

## 数据与字体来源

| 样例 | 来源与范围 |
| --- | --- |
| 日线行情 | [daily.json](../../../data-template/daily.json) 前 120 条，14 列 |
| 上市公司基本信息 | [stock_company.json](../../../data-template/stock_company.json) 前 120 条，21 列 |
| 资产负债表 | 仓库接口元数据的 155 列，36 条合成记录；原仓库响应没有数据行 |
| 高精度与长文本 | 5 条合成记录，覆盖 19 位整数、38 位精度小数、不同小数位、末尾零、负零、空值及长文本 |

来源插件与接口沿用真实标识；没有入库时间时显示空值。下载回执是本地模拟，计数用于演示界面，不代表实际下载或写入。

视觉参考：[Apple 中国 MacBook Air 购买页](https://www.apple.com.cn/shop/buy-mac/macbook-air)。默认使用设备上的系统字体；Apple 设备使用系统拉丁字体与苹方中文，其他平台按本地字体回退。未打包 Apple 字体。

可切换的 `TensorSans.woff2` 来自 [Google Fonts 的 Noto Sans SC](https://github.com/google/fonts/tree/main/ofl/notosanssc)，由完整可变字体转换为 WOFF2，保留动态中文所需字形，约 7.42 MiB，采用 `font-display: swap`。许可见 [FONT-LICENSE.txt](assets/FONT-LICENSE.txt)。Vue 3.5.42 使用本地生产构建，许可见 [VUE-LICENSE.txt](assets/VUE-LICENSE.txt)。

## 验证

仓库已安装 `control-plane` 的 Playwright 依赖与 Chromium 后，在仓库根目录运行：

```sh
node scripts/verify-ui-demo.cjs
# 或检查正在运行的预览
node scripts/verify-ui-demo.cjs http://127.0.0.1:4177/
```

脚本检查两页表单对齐、表头与正文边界、数字复制及小数点位置、155 列滚动、菜单宽度与键盘操作、查询与下载反馈、主题持久化、减少动画，以及 1440 / 1024 / 768 / 390 / 360px 下三页的溢出情况。截图输出到系统临时目录的 `tensor-issue017-review`。

用户已确认当前 Demo 的视觉方案。五个标准视口的自动检查已通过；另行发现的 901px 查询区约 22px 横向溢出记录为正式落地前待办，锁定后不继续调整此 Demo。此独立预览的检查不等同于正式前端的构建、业务回归或生产验收。
