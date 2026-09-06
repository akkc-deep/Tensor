# ISSUE-007：dividend 真实适配失败且缺少字段诊断

## 当前阶段与授权

按用户持续执行/继续修复的授权开展独立产品诊断。根因尚未确定，不猜测修复、不修改生产代码/类型/业务键。M14-T05 保持 BLOCKED；诊断不计为页面验收或成功重试。

## 真实证据

用户运行1gpnb4ru：139秒，28 passed / 1 failed / 11 did not run；stock_company和stk_holdernumber均真实通过。dividend原样例返回ADAPTER_TYPE_INVALID，唯一完成事件为adapter阶段，requestId `4c5a2c20-e8e9-426d-a0b0-a758f881779f`，320ms，失败计数unavailable。初始6迁移/50表全空，失败后的dividend表0不是EMPTY。真实POST37/records57，fixture2/3，99业务完成事件逐ID唯一，扫描停机与DB/卷/私密材料清理通过。

证据提交 `e3013b1`，整篇已扫描SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。运行Git7042223，JAR SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`。完整响应、具体失败字段和值均未保留；现有日志只有错误码/阶段。历史dividend模板0行，不能像此前两个问题那样从历史数据复现。

## 诊断设计

使用自有0700临时目录中的一次性Python启动器和已编译Java诊断类，直接加载本轮原样验收包解出的模块/依赖，原包及其hash保持不变。启动器先检查Java21、源JAR/manifest和全部诊断材料的固定hash与权限，再创建独占使用标志。Token只读取用户当前终端的 `TENSOR_TUSHARE_TOKEN`，仅经环境进入Java，不复制到文件、argv或日志。

Java仅对固定 `dividend` 执行冻结manifest中唯一原参数一次，使用原 `TushareProClient`、元数据和 `GenericDatasetAdapter`。没有额外接口、参数选择、重试、浏览器、应用HTTP、数据库连接或持久化；这一次是已知产品失败的诊断，不是权限探测。所有响应/适配行只在内存中存在，不打印或保存值。

只保留固定公开错误码、当前诊断独立测量的source/adapted计数、已校验字段名/逻辑类型、来源行号、受限来源类型与格式类别。错误消息只用于内存精确匹配既有安全转换/重复键消息，不输出异常正文或堆栈；不能把本次诊断位置补写成历史页面失败位置。未经安全消息/字段白名单识别的异常只记固定unknown类别。子进程stdout/stderr仅在父进程内存捕获，唯一白名单JSON再次校验并扫描真实Token及JSON转义形式后，才写0600 `safe-result.json`。不保存原始进程日志、响应或真实行；不将诊断exit0当作接口通过。

维持原客户端5秒连接/120秒响应上限，父进程150秒预算，每15秒固定进度；超过预算终止自有Java，正常停止与强制停止共用单一10秒清理截止时间；无法确认退出时如实记cleanup_incomplete/javaExited=false，不声称清理成功，绝不自动重试。诊断未开始不产生上游请求。完成后保留无秘密的诊断材料及安全结果，实际响应随进程退出释放。

## 本地验证与后续

先用合成行验证诊断投影：DATE格式失败、DECIMAL尺度失败、字符串超长、冲突键、SUCCESS和EMPTY；校验只能输出允许字段/枚举，源值不出现在结果中。启动器以合成Token进行离线投影/秘密拒绝/单次门禁检查；独立复审后才交用户已有Token终端单条命令。

取得真实字段/格式证据后再定最小修复和回归。若实际数据出现非空，同时单独处理冻结历史EMPTY预期与当前上游结果的差异，保留原manifest/参数和历史事实；不得直接把适配错误转成EMPTY、删除dividend或放宽所有日期。修复验证及新产物接入成立前不恢复M14-T05、不准备全矩阵重跑。

## 单次诊断交付

诊断材料已就绪，尚未真实执行。用户在已有 `TENSOR_TUSHARE_TOKEN` 的终端仅执行一次：

```sh
python3 /private/tmp/tensor-issue-007-diagnostic.shhiyk_p/diagnose.py
```

执行预算150秒、清理至多另10秒，每15秒固定进度。工具环境不继承用户终端Token，因此本次实际请求需由已有Token的终端启动；无需再次配置Token。启动器不接受额外参数、拒绝复用，不启动应用/数据库、不重跑40接口。独立诊断的exit0只表示产生可读安全报告，不表示dividend或页面验收通过。

最终启动器SHA `a58ac7549486ba0cb1e0c04690279835dc023cd3493b9e655d80dc0bb114cb90`；seal SHA `88dd34d1b3e9f4dad20ce6fbcabe600db9ed9a4fd39f55553bf5dbcc567fcadb`。58个封存文件包括Java源、两份编译类、54个模块/依赖JAR和合成测试；54个JAR逐一与冻结源包的嵌套内容核对一致。控制目录及子目录0700，所有文件0600，最终Java21/原manifest/JAR/文件hash与权限preflight通过。Python文件哈希使用标准分块SHA256，不依赖Python3.11新API。

离线Java命令使用该目录classes及54个固定lib构成classpath执行 `DividendDiagnostic --self-test`，6项投影用例通过；`python3 /private/tmp/tensor-issue-007-diagnostic.shhiyk_p/test_launch.py` 的11项测试通过，覆盖白名单和结构拒绝、错误/成功/空投影、重复报告拒绝、Token与JSON转义拒绝、独占单次门禁、真实自有进程超时退出、篡改拒绝，以及持续不退出时主/补救清理总等待仅5+5秒。没有真实请求。独立安全复审无Critical/Important，唯一Minor（极端清理可能多等5秒）已修复并定点关闭，无剩余发现。

执行后只消费此控制目录的 `safe-result.json`，先核对diagnosticOnly、固定API/包/manifest身份、单次调用数、secretScanPassed与javaExited，再读取固定白名单投影。`used.json` 是一次性使用标记，不删除或绕过它重试。真实响应、源行、适配行与原始stdout/stderr不落盘。M14-T05保持BLOCKED，下一步由真实诊断证据决定最小修复。
