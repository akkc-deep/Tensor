# ISSUE-010 health 验证

日期：2026-09-07。范围与设计见 [ISSUE-010](../issues/problems/ISSUE-010-health-component-exposure.md)。

## 构建身份（黑盒验证前冻结）

- 工作区基线：`221bf61`；独立临时快照包含本轮 health 修复及工作区已有 POM 改动。
- 构建源码快照提交：`8adb96ea481f9fd90d4438198ee9478b95efd312`。
- 验证副本提交：`d393a32f19e5304e6a6fc6f476a861f859cffce5`；相比构建快照将安全脚本中的旧固定 JAR SHA 替换为以下新 SHA，并把全部回环 8080 端口引用统一改为 18080，所有安全判定保持原样。
- 新生产 JAR：`/private/tmp/tensor-issue-010-7385m328/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。
- JAR SHA-256：`6ed11c7dad6900cbad70f6af5244e34dbe5cd5527434bb35f447e2dd7c023f7f`。
- 验证脚本 SHA-256：`49bb3beacbd8f5298ca8c251d96d305dd4feb1f133a61bce8cd525753a39113a`。
- 历史生产 JAR、`scripts/security/verify-release.sh` 和 `M14-T07-security.md` 均保留原样。

## 自动回归与构建

- RED：修复前真实 MySQL 集成测试失败，根 health 比仅 status 的预期多出 components 和 groups；1 test / 1 failure / 0 errors / 0 skipped。
- GREEN：`ProductionApplicationContextIT,ObservabilityTest,ProductionWebConfigurationTest` 共 47 项通过，0 failures/errors/skipped。覆盖三端点完整 JSON、安全头/no-store、有无 Token、隐藏管理和组件路径、停止数据库后根 503/DOWN 且探针仍 200/UP。
- 首次 GREEN 尝试受继承的 Token 配置影响，metadata 无 Token 断言失败；使用清洁环境重跑后通过。未将该次失败算作成功。
- 独立快照 `mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 verify` 成功；后端单测 385 项、生产 JAR 合同 4 项，0 failures/errors/skipped；前端 170 项单测通过。
- 定向测试命令同上，将 verify 替换为 `-pl tensor-app -am -Dskip.installnodenpm -Dskip.npm -Dtest=ProductionApplicationContextIT,ObservabilityTest,ProductionWebConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`。仅定向后端回归跳过前端执行，完整 verify 未跳过。
- 运行使用 `env -i`，只传工具所需 PATH/HOME/JAVA_HOME/LANG 及本机 Docker 连接；不继承 TENSOR/SPRING 配置。MySQL 8.4.6 容器由测试创建并清理。
- 独立代码审查无严重或重要问题，确认 Boot 扩展退让/继承操作发现及原生状态映射保持有效。

## 完整安全验证

验证副本 `--self-test` 的 309 项离线反例全部通过。首次正式预检因清洁 PATH 缺少 MySQL CLI 而停止，未启动 JVM/数据库；补充 `/usr/local/mysql/bin` 后预检发现 8080 已有服务，未停止该服务；登记端口 18080 的验证副本身份后重跑同一 JAR。正式运行已结束，结果见下。

复现入口（在上面登记的临时验证快照目录执行）：

```sh
env -i PATH="/usr/local/mysql/bin:$PATH" HOME="$HOME" JAVA_HOME="$JAVA_HOME" LANG=en_US.UTF-8 \
  M14_SECURITY_JAR=/private/tmp/tensor-issue-010-7385m328/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  M14_MAVEN_REPO=/private/tmp/tensor-m2 sh scripts/security/verify-release.sh
```

没有改动主仓库安全脚本的默认冻结身份；上述验证副本可由原脚本确定性地替换固定 SHA 与端口得到。新构建会产生新的 JAR 身份，必须重新登记后验证。

## 验收结果

ISSUE-010 已解决。新生产包 S01 的 11 个探针全部通过：三个 health 端点均为 HTTP 200/UP，components/details/groups 全部不存在，extraFieldCount=0；其余八个 Actuator 路径均为 404。数据库故障语义由上面的真实 MySQL 集成回归验证。

完整安全检查为 **20 pass / 4 fail / 0 not-run，退出 1**；不能报告全局安全门禁或发布准入通过。

| 剩余失败 | 本次结果 | 已登记问题 |
| --- | --- | --- |
| S05 | 12 个非只读方法反例失败 | ISSUE-011 |
| S06 | 6 个额外查询参数反例失败 | ISSUE-012 |
| log_scan | 4 个失败查询缺少完成事件 | ISSUE-013 |
| backend_audit | NVD 访问失败，缺少有效漏洞数据和报告 | ISSUE-015 |

本次 Maven 安全门禁的 81 项测试及 Enforcer 通过，可作为 ISSUE-014 后续处理的新证据；本任务不修改其他 issue 的状态。前端依赖审计、页面合成下载/查询/上游失败、HTML 文本呈现、S07/S08、安全输出扫描与身份复核均通过。清理确认自有 JVM、stub、浏览器、容器、卷和私有凭证输入均已清理，18080 释放；原 8080 服务保留。

完整机器报告来源：`/private/tmp/tensor-m14-t07.u0aaACa4/evidence.md`。原始 outcome.json SHA-256：`4936f247f7948e358e6a5d8092209e9f54a024f125201e5d0dc26e41b4d214d7`。以下原样保留报告；其中 task=M14-T07 表示复用的检查套件，本次运行身份属于 ISSUE-010，不替换历史验收结果。

# M14-T07 security verification

This document is generated from this run; failed and not-run checks prevent acceptance.

Invocation: `sh scripts/security/verify-release.sh`. `M14_SECURITY_JAR` selects the frozen input whose SHA-256 is recorded in `identities`; `PATH` and `JAVA_HOME` select the reported tool versions.

```json
{
  "task": "M14-T07",
  "startedAt": "2026-09-07T02:27:57.348448+00:00",
  "checks": {
    "preflight": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:27:57.348702+00:00",
      "details": {
        "port18080InitiallyFree": true,
        "sanitizedChildEnvironment": true
      },
      "finishedAt": "2026-09-07T02:27:58.617683+00:00"
    },
    "source_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:27:58.617721+00:00",
      "details": {
        "files": 617,
        "snapshotFromHead": true
      },
      "finishedAt": "2026-09-07T02:27:59.053733+00:00"
    },
    "jar_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:27:59.053777+00:00",
      "details": {
        "entries": 18065,
        "thirdPartyJars": 50,
        "migrations": [
          "V1__create_basic_and_organization_tables.sql",
          "V2__create_market_and_trading_tables.sql",
          "V3__create_connect_and_slb_tables.sql",
          "V4__create_financial_tables.sql",
          "V5__create_corporate_and_governance_tables.sql",
          "V7__version_dividend_business_key.sql"
        ],
        "productionModules": [
          "tensor-core-1.0-SNAPSHOT.jar",
          "tensor-plugin-api-1.0-SNAPSHOT.jar",
          "tensor-plugin-tushare-1.0-SNAPSHOT.jar"
        ],
        "datasetResources": 49
      },
      "finishedAt": "2026-09-07T02:27:59.685895+00:00"
    },
    "maven": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-07T02:27:59.685920+00:00",
      "details": {
        "classes": {
          "ProductionWebConfigurationTest": {
            "tests": 28,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          },
          "ObservabilityTest": {
            "tests": 18,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          },
          "DatasetControllerIT": {
            "tests": 8,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          },
          "ModuleDependencyTest": {
            "tests": 1,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          },
          "ForbiddenGitCapabilityTest": {
            "tests": 12,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          },
          "QuerySqlFactoryTest": {
            "tests": 8,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          },
          "UpsertSqlFactoryTest": {
            "tests": 6,
            "failures": 0,
            "errors": 0,
            "skipped": 0
          }
        },
        "tests": 81,
        "enforcer": true
      },
      "finishedAt": "2026-09-07T02:28:34.151604+00:00"
    },
    "backend_audit": {
      "status": "fail",
      "exitCode": 1,
      "startedAt": "2026-09-07T02:28:35.577666+00:00",
      "reason": "dependency-scanner-failed",
      "details": {
        "scannerExitCode": 1,
        "reportPresent": false,
        "failureCategories": [
          "nvd-invalid-api-key",
          "vulnerability-data-missing",
          "report-missing"
        ]
      },
      "finishedAt": "2026-09-07T02:28:40.878633+00:00"
    },
    "frontend_audit": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-07T02:28:34.151656+00:00",
      "details": {
        "counts": {
          "info": 0,
          "low": 0,
          "moderate": 0,
          "high": 0,
          "critical": 0,
          "total": 0
        },
        "coverage": {
          "prod": 74,
          "dev": 123,
          "optional": 28,
          "peer": 0,
          "peerOptional": 0,
          "total": 196
        },
        "advisories": []
      },
      "finishedAt": "2026-09-07T02:28:35.577622+00:00"
    },
    "browser": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:28:51.949921+00:00",
      "details": {},
      "finishedAt": "2026-09-07T02:29:00.023168+00:00"
    },
    "database_setup": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:28:40.878679+00:00",
      "details": {
        "sourceHostSha256": "346840d5a3d9fe9b61ce99955bb98df3db872090732c96aa5df1d83cb1f3e85a",
        "privileges": [
          "CREATE",
          "SELECT",
          "INSERT",
          "UPDATE",
          "ALTER",
          "INDEX"
        ],
        "charset": "utf8mb4",
        "collation": "utf8mb4_0900_as_cs",
        "isolatedSchemaInitiallyEmpty": true
      },
      "finishedAt": "2026-09-07T02:28:47.554278+00:00"
    },
    "startup": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:28:47.554328+00:00",
      "details": {
        "rootHealthReady": true,
        "successfulMigrations": 6,
        "emptyBusinessTables": 49
      },
      "finishedAt": "2026-09-07T02:28:51.899900+00:00"
    },
    "S01": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:28:51.899954+00:00",
      "details": {
        "pass": 11,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:28:51.923617+00:00"
    },
    "S02": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-07T02:29:00.023219+00:00",
      "details": {
        "pass": 5,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:00.023245+00:00"
    },
    "S03": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-07T02:29:00.023255+00:00",
      "details": {
        "pass": 1,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:00.023269+00:00"
    },
    "S04": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-07T02:29:00.023276+00:00",
      "details": {
        "pass": 1,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:00.023288+00:00"
    },
    "S05": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:01.382504+00:00",
      "reason": "probe-group-incomplete",
      "details": {
        "pass": 0,
        "fail": 12,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:01.382570+00:00"
    },
    "S06": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:02.032823+00:00",
      "reason": "probe-group-incomplete",
      "details": {
        "pass": 0,
        "fail": 6,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:02.032897+00:00"
    },
    "S07": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:02.673201+00:00",
      "details": {
        "pass": 6,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:02.673271+00:00"
    },
    "S08": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:02.673285+00:00",
      "details": {
        "responsesChecked": 66,
        "headerFailures": [],
        "jsResources": 1,
        "cssResources": 1,
        "pass": 6,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-07T02:29:02.696499+00:00"
    },
    "stub": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:02.696558+00:00",
      "details": {
        "observedStubCalls": 2,
        "unexpectedStubCalls": 0,
        "configuredUpstream": "loopback-stub",
        "jvmExternalUpstreamCalls": "not-measured"
      },
      "finishedAt": "2026-09-07T02:29:02.696578+00:00"
    },
    "database_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:02.696587+00:00",
      "details": {
        "tables": 49,
        "credentialMatches": 0
      },
      "finishedAt": "2026-09-07T02:29:02.868647+00:00"
    },
    "log_scan": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:03.811847+00:00",
      "reason": "completion-events-not-unique",
      "details": {
        "requests": [
          {
            "requestId": "ab0630a8-eae8-4fbc-81f9-54378fcf2ee8",
            "operation": "download",
            "completionEvents": 1
          },
          {
            "requestId": "684e3e85-4cd7-4074-8828-246a7e498bcc",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "1258272e-b6e9-4958-bb5e-196660c065d5",
            "operation": "download",
            "completionEvents": 1
          },
          {
            "requestId": "d9e47a1e-5503-4f20-8b11-96c3dde33567",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "089d041b-98a6-44df-b988-c6515093c4c2",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "e473c2bb-7f2e-4457-8093-7044c80252e2",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "6e42983d-8e0f-4518-8112-62482ae610a3",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "2631a01b-17b5-4f28-8345-b8af53c3dca2",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "bf430a5b-2213-4041-8aef-6d9cce3ff03b",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "edb8bcd0-ef6a-437d-96fe-da4453d3b317",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "e6ab92c7-5ab0-4bec-ad07-0cb994887003",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "a7270d76-2066-4514-a275-9fbfd62b6eee",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "214e9fc0-4fb0-40cf-91a9-962b19ab5327",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "156e284f-458c-4380-98eb-5bd485c16983",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "a6b65646-32dd-4d05-961b-67ad37a851f8",
            "operation": "query",
            "completionEvents": 1
          }
        ],
        "completionEvents": 11
      },
      "finishedAt": "2026-09-07T02:29:03.837846+00:00"
    },
    "artifact_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:04.235705+00:00",
      "details": {
        "files": 19028,
        "resolvedExecutableLinks": 16,
        "hits": 0
      },
      "finishedAt": "2026-09-07T02:29:07.076880+00:00"
    },
    "identity_final": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:04.193466+00:00",
      "details": {
        "jarSha256": "6ed11c7dad6900cbad70f6af5244e34dbe5cd5527434bb35f447e2dd7c023f7f",
        "productionInputsUnchanged": true
      },
      "finishedAt": "2026-09-07T02:29:04.235659+00:00"
    },
    "cleanup": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-07T02:29:02.868694+00:00",
      "details": {
        "browser": true,
        "jvm": true,
        "stub": true,
        "container": true,
        "volumes": true,
        "privateInputs": true,
        "port18080": true,
        "jvmExitCode": 143,
        "volumesRemoved": 1,
        "privateInputFiles": 3
      },
      "finishedAt": "2026-09-07T02:29:04.193425+00:00"
    },
    "report_scan": {
      "status": "pass",
      "exitCode": 0
    }
  },
  "probes": {
    "S01.health": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200,
      "healthStructure": {
        "componentsPresent": false,
        "detailsPresent": false,
        "groupsPresent": false,
        "extraFieldCount": 0,
        "statusUp": true
      }
    },
    "S01.liveness": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200,
      "healthStructure": {
        "componentsPresent": false,
        "detailsPresent": false,
        "groupsPresent": false,
        "extraFieldCount": 0,
        "statusUp": true
      }
    },
    "S01.readiness": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200,
      "healthStructure": {
        "componentsPresent": false,
        "detailsPresent": false,
        "groupsPresent": false,
        "extraFieldCount": 0,
        "statusUp": true
      }
    },
    "S02.sources": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S02.datasets": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S02.apis": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S02.definition": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S02.page": {
      "status": "pass",
      "definitionColumns": 18
    },
    "S03.page": {
      "status": "pass",
      "httpStatus": 200,
      "requestId": "ab0630a8-eae8-4fbc-81f9-54378fcf2ee8",
      "sourceRows": 1,
      "insertedRows": 1,
      "updatedRows": 0,
      "htmlText": true
    },
    "S04.page": {
      "status": "pass",
      "httpStatus": 502,
      "code": "SOURCE_AUTH_FAILED",
      "retryable": false,
      "requestId": "1258272e-b6e9-4958-bb5e-196660c065d5",
      "rowUnchanged": true
    },
    "S07.final-row": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "a6b65646-32dd-4d05-961b-67ad37a851f8",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S08.GET": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S08.OPTIONS": {
      "status": "pass",
      "method": "OPTIONS",
      "httpStatus": 403
    },
    "S01.root": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.env": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.configprops": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.metrics": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.beans": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.heapdump": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.logfile": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S01.mappings": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 404
    },
    "S05.list.POST": {
      "status": "fail",
      "method": "POST",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "98e42443-fb77-4e42-af0d-ad95417f7eb7",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.list.PUT": {
      "status": "fail",
      "method": "PUT",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "520257b2-0923-4544-a289-e4181727eaf9",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.list.PATCH": {
      "status": "fail",
      "method": "PATCH",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "b5264b8e-9328-40d8-bc08-d3ac3e51f59d",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.list.DELETE": {
      "status": "fail",
      "method": "DELETE",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "df6580ba-e506-409d-8a12-696007e3ccf8",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.POST": {
      "status": "fail",
      "method": "POST",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "d5da5936-3db5-4847-93bb-36e75e773eb3",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.PUT": {
      "status": "fail",
      "method": "PUT",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "a28f5541-0148-4fe1-be62-429f7af6a18d",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.PATCH": {
      "status": "fail",
      "method": "PATCH",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "5f0c9eb6-6cdb-4d8d-bfc3-6468514b0e1e",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.DELETE": {
      "status": "fail",
      "method": "DELETE",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "1d917087-0495-44fa-a651-cf3b15d0695b",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.POST": {
      "status": "fail",
      "method": "POST",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "a7f49370-9a01-493c-ac9e-6e090f9bfa45",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.PUT": {
      "status": "fail",
      "method": "PUT",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "e69f00cc-5ce6-4b7d-9854-c5e2f85bc152",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.PATCH": {
      "status": "fail",
      "method": "PATCH",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "9ffe502a-664b-4e57-8012-2b3bcd64645e",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.DELETE": {
      "status": "fail",
      "method": "DELETE",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "41926f39-d4cf-41fd-a26a-7d8a8b28898a",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S06.table": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "089d041b-98a6-44df-b988-c6515093c4c2",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.column": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "e473c2bb-7f2e-4457-8093-7044c80252e2",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.columns": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "6e42983d-8e0f-4518-8112-62482ae610a3",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.sort": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "2631a01b-17b5-4f28-8345-b8af53c3dca2",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.orderBy": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "bf430a5b-2213-4041-8aef-6d9cce3ff03b",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.sql": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "edb8bcd0-ef6a-437d-96fe-da4453d3b317",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S07.tsCode": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "e6ab92c7-5ab0-4bec-ad07-0cb994887003",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.page": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "a7270d76-2066-4514-a275-9fbfd62b6eee",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.pageSize": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "214e9fc0-4fb0-40cf-91a9-962b19ab5327",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.tradeDateFrom": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "156e284f-458c-4380-98eb-5bd485c16983",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.apiName": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "30e19685-ccf1-4a89-8d79-baa6ec8732a8",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S08.resource-0": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S08.resource-1": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S08.resource-2": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    },
    "S08.resource-3": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200
    }
  },
  "commands": [
    {
      "id": 1,
      "step": "node-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 2,
      "step": "java-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 3,
      "step": "mvn-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 4,
      "step": "npm-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 5,
      "step": "mysql-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 6,
      "step": "docker-context",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 7,
      "step": "docker-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 8,
      "step": "playwright-preflight",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 9,
      "step": "source-head",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 10,
      "step": "source-protection",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 11,
      "step": "untracked-protection",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 12,
      "step": "untracked-protection",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 13,
      "step": "random-values",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 14,
      "step": "tracked-files",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 15,
      "step": "source-snapshot",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 16,
      "step": "maven-security-tests",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 17,
      "step": "frontend-dependency-audit",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 18,
      "step": "backend-dependency-audit",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 19,
      "step": "mysql-container-create",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 20,
      "step": "container-ownership",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 21,
      "step": "container-loopback",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 22,
      "step": "mysql-ready",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 23,
      "step": "mysql-ready",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 24,
      "step": "mysql-ready",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 25,
      "step": "mysql-ready",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 26,
      "step": "mysql-least-privilege",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 27,
      "step": "mysql-grant-verification",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 28,
      "step": "mysql-account-verification",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 29,
      "step": "mysql-empty-schema",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 30,
      "step": "migration-verification",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 31,
      "step": "business-table-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 32,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 33,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "step": "browser-driver",
      "exitCode": 0
    },
    {
      "id": 34,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 35,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 36,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 37,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 38,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 39,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 40,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 41,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 42,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 43,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 44,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 45,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 46,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 47,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 48,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 49,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 50,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 51,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 52,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 53,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 54,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 55,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 56,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 57,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 58,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 59,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 60,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 61,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 62,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 63,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 64,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 65,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 66,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 67,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 68,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 69,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 70,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 71,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 72,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 73,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 74,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 75,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 76,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 77,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 78,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 79,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 80,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 81,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 82,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 83,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 84,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 85,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 86,
      "step": "business-credential-scan",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 87,
      "step": "cleanup-container-owner",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 88,
      "step": "cleanup-volume-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 89,
      "step": "cleanup-container-remove",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 90,
      "step": "cleanup-container-absent",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 91,
      "step": "cleanup-volume-absent",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 92,
      "step": "final-source-head",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 93,
      "step": "final-source-protection",
      "exitCode": 0,
      "timedOut": false
    }
  ],
  "environment": {
    "node": "v24.15.0",
    "java": "21.0.11",
    "mvn": "3.9.15",
    "npm": "11.12.1",
    "mysql": "8.4.11",
    "docker": "29.5.2",
    "python": "3.11.5",
    "playwright": {
      "version": "1.62.1",
      "browserInstalled": true
    },
    "profile": "default",
    "bind": "loopback",
    "productionDatasets": 49,
    "mysqlServer": "8.4.6"
  },
  "scanCoverage": {
    "subprocess_output": 93,
    "tracked_source_files": 617,
    "jar_entries_recursive": 18065,
    "http": 46,
    "browser_driver": 90,
    "browser_request": 42,
    "browser_http": 27,
    "browser_dom": 12,
    "browser_console": 1,
    "browser_log": 1,
    "business_tables": 49,
    "application_log": 1,
    "artifact_files": 19028
  },
  "cleanup": true,
  "identities": {
    "jarSha256": "6ed11c7dad6900cbad70f6af5244e34dbe5cd5527434bb35f447e2dd7c023f7f",
    "scriptSha256": "49bb3beacbd8f5298ca8c251d96d305dd4feb1f133a61bce8cd525753a39113a",
    "lockSha256": "f76bd867cca88d219e8bb196466f81511fe714db0e2a0277f8ca84415362c3dd",
    "sourceCommit": "d393a32f19e5304e6a6fc6f476a861f859cffce5"
  },
  "finishedAt": "2026-09-07T02:29:07.076917+00:00",
  "requestIds": [
    {
      "requestId": "ab0630a8-eae8-4fbc-81f9-54378fcf2ee8",
      "operation": "download"
    },
    {
      "requestId": "684e3e85-4cd7-4074-8828-246a7e498bcc",
      "operation": "query"
    },
    {
      "requestId": "1258272e-b6e9-4958-bb5e-196660c065d5",
      "operation": "download"
    },
    {
      "requestId": "d9e47a1e-5503-4f20-8b11-96c3dde33567",
      "operation": "query"
    },
    {
      "requestId": "089d041b-98a6-44df-b988-c6515093c4c2",
      "operation": "query"
    },
    {
      "requestId": "e473c2bb-7f2e-4457-8093-7044c80252e2",
      "operation": "query"
    },
    {
      "requestId": "6e42983d-8e0f-4518-8112-62482ae610a3",
      "operation": "query"
    },
    {
      "requestId": "2631a01b-17b5-4f28-8345-b8af53c3dca2",
      "operation": "query"
    },
    {
      "requestId": "bf430a5b-2213-4041-8aef-6d9cce3ff03b",
      "operation": "query"
    },
    {
      "requestId": "edb8bcd0-ef6a-437d-96fe-da4453d3b317",
      "operation": "query"
    },
    {
      "requestId": "e6ab92c7-5ab0-4bec-ad07-0cb994887003",
      "operation": "query"
    },
    {
      "requestId": "a7270d76-2066-4514-a275-9fbfd62b6eee",
      "operation": "query"
    },
    {
      "requestId": "214e9fc0-4fb0-40cf-91a9-962b19ab5327",
      "operation": "query"
    },
    {
      "requestId": "156e284f-458c-4380-98eb-5bd485c16983",
      "operation": "query"
    },
    {
      "requestId": "a6b65646-32dd-4d05-961b-67ad37a851f8",
      "operation": "query"
    }
  ],
  "browser": {
    "requestCount": 21,
    "downloadCount": 2,
    "cleanup": true,
    "unexpectedNetworkOrSurface": false
  },
  "exitCode": 1,
  "counts": {
    "pass": 20,
    "fail": 4,
    "not-run": 0
  }
}
```

Local loopback controls were measured only where marked pass. Remote HTTPS, an internal network or identity proxy, database TLS, a proxy response budget of at least 130 seconds, and a shutdown window covering all phases remain deployment requirements. M14-T08 release readiness is a separate decision.
