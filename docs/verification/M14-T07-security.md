# M14-T07 security verification

This document is generated from this run; failed and not-run checks prevent acceptance.

Invocation: `sh scripts/security/verify-release.sh`. `M14_SECURITY_JAR` selects the frozen input whose SHA-256 is recorded in `identities`; `PATH` and `JAVA_HOME` select the reported tool versions.

```json
{
  "task": "M14-T07",
  "startedAt": "2026-09-06T16:07:48.278549+00:00",
  "checks": {
    "preflight": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:07:48.278823+00:00",
      "details": {
        "port8080InitiallyFree": true,
        "sanitizedChildEnvironment": true
      },
      "finishedAt": "2026-09-06T16:07:49.795331+00:00"
    },
    "source_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:07:49.795366+00:00",
      "details": {
        "files": 569,
        "snapshotFromHead": true
      },
      "finishedAt": "2026-09-06T16:07:50.274209+00:00"
    },
    "jar_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:07:50.274240+00:00",
      "details": {
        "entries": 18063,
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
      "finishedAt": "2026-09-06T16:07:50.909381+00:00"
    },
    "maven": {
      "status": "fail",
      "exitCode": 1,
      "startedAt": "2026-09-06T16:07:50.909405+00:00",
      "reason": "tests-or-enforcer-incomplete",
      "finishedAt": "2026-09-06T16:12:45.051178+00:00"
    },
    "backend_audit": {
      "status": "fail",
      "exitCode": 1,
      "startedAt": "2026-09-06T16:12:46.787791+00:00",
      "reason": "dependency-scanner-failed",
      "details": {
        "scannerExitCode": 1,
        "reportPresent": false,
        "failureCategories": [
          "nvd-invalid-api-key",
          "cisa-http-403",
          "vulnerability-data-missing",
          "report-missing"
        ]
      },
      "finishedAt": "2026-09-06T16:14:56.980693+00:00"
    },
    "frontend_audit": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-06T16:12:45.051225+00:00",
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
      "finishedAt": "2026-09-06T16:12:46.787751+00:00"
    },
    "browser": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:07.951446+00:00",
      "details": {},
      "finishedAt": "2026-09-06T16:15:16.498500+00:00"
    },
    "database_setup": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:14:56.980723+00:00",
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
      "finishedAt": "2026-09-06T16:15:03.555497+00:00"
    },
    "startup": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:03.555530+00:00",
      "details": {
        "rootHealthReady": true,
        "successfulMigrations": 6,
        "emptyBusinessTables": 49
      },
      "finishedAt": "2026-09-06T16:15:07.897138+00:00"
    },
    "S01": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:07.897184+00:00",
      "reason": "probe-group-incomplete",
      "details": {
        "pass": 10,
        "fail": 1,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:07.926338+00:00"
    },
    "S02": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-06T16:15:16.498527+00:00",
      "details": {
        "pass": 5,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:16.498546+00:00"
    },
    "S03": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-06T16:15:16.498552+00:00",
      "details": {
        "pass": 1,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:16.498561+00:00"
    },
    "S04": {
      "status": "pass",
      "exitCode": 0,
      "startedAt": "2026-09-06T16:15:16.498564+00:00",
      "details": {
        "pass": 1,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:16.498572+00:00"
    },
    "S05": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:17.814456+00:00",
      "reason": "probe-group-incomplete",
      "details": {
        "pass": 0,
        "fail": 12,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:17.814510+00:00"
    },
    "S06": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:18.436202+00:00",
      "reason": "probe-group-incomplete",
      "details": {
        "pass": 0,
        "fail": 6,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:18.436251+00:00"
    },
    "S07": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:19.052287+00:00",
      "details": {
        "pass": 6,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:19.052341+00:00"
    },
    "S08": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:19.052353+00:00",
      "details": {
        "responsesChecked": 66,
        "headerFailures": [],
        "jsResources": 1,
        "cssResources": 1,
        "pass": 6,
        "fail": 0,
        "not-run": 0
      },
      "finishedAt": "2026-09-06T16:15:19.075916+00:00"
    },
    "stub": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:19.075936+00:00",
      "details": {
        "observedStubCalls": 2,
        "unexpectedStubCalls": 0,
        "configuredUpstream": "loopback-stub",
        "jvmExternalUpstreamCalls": "not-measured"
      },
      "finishedAt": "2026-09-06T16:15:19.075953+00:00"
    },
    "database_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:19.075957+00:00",
      "details": {
        "tables": 49,
        "credentialMatches": 0
      },
      "finishedAt": "2026-09-06T16:15:19.240549+00:00"
    },
    "log_scan": {
      "status": "fail",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:19.705944+00:00",
      "reason": "completion-events-not-unique",
      "details": {
        "requests": [
          {
            "requestId": "9e59f7a0-4643-44af-a4a3-e3dab9f5d3f1",
            "operation": "download",
            "completionEvents": 1
          },
          {
            "requestId": "4c6ae21c-2824-4fe9-956a-9a2f1a1aa456",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "a466864e-0baa-4bc7-a4b5-8900c0f82475",
            "operation": "download",
            "completionEvents": 1
          },
          {
            "requestId": "3739e644-929a-452d-b3a3-44bf5597d43b",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "38767386-9586-4efd-935b-86e02b2a1280",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "de39c40b-dd1e-4b7d-98cd-ac8966006a25",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "ef071a17-c657-4d7c-921f-5b5c8ff0facc",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "a90197a6-c24e-465a-936e-9c5554329e96",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "d2fc6593-e81f-4318-8d61-acf2d09b3ba7",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "bfc42812-f9d3-4b08-b8a3-279e254e6d6b",
            "operation": "query",
            "completionEvents": 1
          },
          {
            "requestId": "934a9ebf-b76c-4424-be5d-0bdf562657a5",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "ef883c19-e21d-4f11-bf6c-6a842213f364",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "86c28919-908c-4fe7-ae24-b27e209e992b",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "4e4cf80f-6401-4dae-82c9-e3aa327fdc7f",
            "operation": "query",
            "completionEvents": 0
          },
          {
            "requestId": "0674b70a-e1c3-4fc1-87a6-60f03dd11164",
            "operation": "query",
            "completionEvents": 1
          }
        ],
        "completionEvents": 11
      },
      "finishedAt": "2026-09-06T16:15:19.718520+00:00"
    },
    "artifact_scan": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:20.109769+00:00",
      "details": {
        "files": 1299,
        "resolvedExecutableLinks": 0,
        "hits": 0
      },
      "finishedAt": "2026-09-06T16:15:20.947710+00:00"
    },
    "identity_final": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:20.050230+00:00",
      "details": {
        "jarSha256": "acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef",
        "productionInputsUnchanged": true
      },
      "finishedAt": "2026-09-06T16:15:20.109736+00:00"
    },
    "cleanup": {
      "status": "pass",
      "exitCode": null,
      "startedAt": "2026-09-06T16:15:19.240588+00:00",
      "details": {
        "browser": true,
        "jvm": true,
        "stub": true,
        "container": true,
        "volumes": true,
        "privateInputs": true,
        "port8080": true,
        "jvmExitCode": 143,
        "volumesRemoved": 1,
        "privateInputFiles": 3
      },
      "finishedAt": "2026-09-06T16:15:20.050199+00:00"
    },
    "report_scan": {
      "status": "pass",
      "exitCode": 0
    }
  },
  "probes": {
    "S01.health": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "healthStructure": {
        "componentsPresent": true,
        "detailsPresent": false,
        "groupsPresent": true,
        "extraFieldCount": 2,
        "statusUp": true
      },
      "reason": "health-details-exposed"
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
      "requestId": "9e59f7a0-4643-44af-a4a3-e3dab9f5d3f1",
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
      "requestId": "a466864e-0baa-4bc7-a4b5-8900c0f82475",
      "rowUnchanged": true
    },
    "S07.final-row": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "0674b70a-e1c3-4fc1-87a6-60f03dd11164",
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
      "requestId": "97c806ff-0408-43a3-a7c6-4a5f3074467b",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.list.PUT": {
      "status": "fail",
      "method": "PUT",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "a94a6ba9-0a71-447f-9ccb-1da24abaf3b1",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.list.PATCH": {
      "status": "fail",
      "method": "PATCH",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "35d525f1-1cec-40e8-bb8a-63633cd4990d",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.list.DELETE": {
      "status": "fail",
      "method": "DELETE",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "30db774b-de0e-4aaf-ad2d-53be1778f47a",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.POST": {
      "status": "fail",
      "method": "POST",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "fe640f08-8944-4055-a39b-9e83106b8296",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.PUT": {
      "status": "fail",
      "method": "PUT",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "60c0e9ea-e9aa-4f8d-bdb6-6a8168a030e0",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.PATCH": {
      "status": "fail",
      "method": "PATCH",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "36fb6e2d-054a-44d1-8262-8ac06b5f2168",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.definition.DELETE": {
      "status": "fail",
      "method": "DELETE",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "910fa639-0bb7-4037-8521-1954fb40f8bc",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.POST": {
      "status": "fail",
      "method": "POST",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "ad066925-87f5-4969-abf4-2f7c873a9821",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.PUT": {
      "status": "fail",
      "method": "PUT",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "83f26d90-1b90-4f70-ab99-a778c7f0014a",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.PATCH": {
      "status": "fail",
      "method": "PATCH",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "51b3e5b6-5b30-4ba4-ac0c-63c213922658",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S05.records.DELETE": {
      "status": "fail",
      "method": "DELETE",
      "httpStatus": 500,
      "code": "INTERNAL_ERROR",
      "requestId": "32422ba1-7fd5-47e0-917a-ed5a0c3480a8",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "write-method-not-rejected"
    },
    "S06.table": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "38767386-9586-4efd-935b-86e02b2a1280",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.column": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "de39c40b-dd1e-4b7d-98cd-ac8966006a25",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.columns": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "ef071a17-c657-4d7c-921f-5b5c8ff0facc",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.sort": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "a90197a6-c24e-465a-936e-9c5554329e96",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.orderBy": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "d2fc6593-e81f-4318-8d61-acf2d09b3ba7",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S06.sql": {
      "status": "fail",
      "method": "GET",
      "httpStatus": 200,
      "requestId": "bfc42812-f9d3-4b08-b8a3-279e254e6d6b",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true,
      "reason": "parameter-not-rejected"
    },
    "S07.tsCode": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "934a9ebf-b76c-4424-be5d-0bdf562657a5",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.page": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "ef883c19-e21d-4f11-bf6c-6a842213f364",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.pageSize": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "86c28919-908c-4fe7-ae24-b27e209e992b",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.tradeDateFrom": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "4e4cf80f-6401-4dae-82c9-e3aa327fdc7f",
      "rowsUnchanged": true,
      "stubCallsUnchanged": true
    },
    "S07.apiName": {
      "status": "pass",
      "method": "GET",
      "httpStatus": 400,
      "code": "PARAM_INVALID",
      "requestId": "7e696c9e-00a2-4d6e-a051-188620c74017",
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
      "step": "docker-version",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 7,
      "step": "playwright-preflight",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 8,
      "step": "source-head",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 9,
      "step": "source-protection",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 10,
      "step": "untracked-protection",
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
      "step": "random-values",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 13,
      "step": "tracked-files",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 14,
      "step": "source-snapshot",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 15,
      "step": "maven-security-tests",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 16,
      "step": "frontend-dependency-audit",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 17,
      "step": "backend-dependency-audit",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 18,
      "step": "mysql-container-create",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 19,
      "step": "container-ownership",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 20,
      "step": "container-loopback",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 21,
      "step": "mysql-ready",
      "exitCode": 1,
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
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 25,
      "step": "mysql-least-privilege",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 26,
      "step": "mysql-grant-verification",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 27,
      "step": "mysql-account-verification",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 28,
      "step": "mysql-empty-schema",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 29,
      "step": "migration-verification",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 30,
      "step": "business-table-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 31,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 32,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "step": "browser-driver",
      "exitCode": 0
    },
    {
      "id": 33,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 34,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 35,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 36,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 37,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 38,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 39,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 40,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 41,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 42,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 43,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 44,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 45,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 46,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 47,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 48,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 49,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 50,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 51,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 52,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 53,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 54,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 55,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 56,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 57,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 58,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 59,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 60,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 61,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 62,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 63,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 64,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 65,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 66,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 67,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 68,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 69,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 70,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 71,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 72,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 73,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 74,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 75,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 76,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 77,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 78,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 79,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 80,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 81,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 82,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 83,
      "step": "business-column-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 84,
      "step": "business-row-fingerprint",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 85,
      "step": "business-credential-scan",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 86,
      "step": "cleanup-container-owner",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 87,
      "step": "cleanup-volume-inventory",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 88,
      "step": "cleanup-container-remove",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 89,
      "step": "cleanup-container-absent",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 90,
      "step": "cleanup-volume-absent",
      "exitCode": 1,
      "timedOut": false
    },
    {
      "id": 91,
      "step": "final-source-head",
      "exitCode": 0,
      "timedOut": false
    },
    {
      "id": 92,
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
    "subprocess_output": 92,
    "tracked_source_files": 569,
    "jar_entries_recursive": 18063,
    "http": 46,
    "browser_driver": 90,
    "browser_request": 42,
    "browser_http": 27,
    "browser_dom": 12,
    "browser_console": 1,
    "browser_log": 1,
    "business_tables": 49,
    "application_log": 1,
    "artifact_files": 1299
  },
  "cleanup": true,
  "identities": {
    "jarSha256": "acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef",
    "scriptSha256": "41f0d54f071bf545e2e59a28b532d8c335ef6a53a86639be10a6728357d9afec",
    "lockSha256": "f76bd867cca88d219e8bb196466f81511fe714db0e2a0277f8ca84415362c3dd",
    "sourceCommit": "741376b605dc37ca9075b17abe98cf254230d6ad"
  },
  "finishedAt": "2026-09-06T16:15:20.947727+00:00",
  "requestIds": [
    {
      "requestId": "9e59f7a0-4643-44af-a4a3-e3dab9f5d3f1",
      "operation": "download"
    },
    {
      "requestId": "4c6ae21c-2824-4fe9-956a-9a2f1a1aa456",
      "operation": "query"
    },
    {
      "requestId": "a466864e-0baa-4bc7-a4b5-8900c0f82475",
      "operation": "download"
    },
    {
      "requestId": "3739e644-929a-452d-b3a3-44bf5597d43b",
      "operation": "query"
    },
    {
      "requestId": "38767386-9586-4efd-935b-86e02b2a1280",
      "operation": "query"
    },
    {
      "requestId": "de39c40b-dd1e-4b7d-98cd-ac8966006a25",
      "operation": "query"
    },
    {
      "requestId": "ef071a17-c657-4d7c-921f-5b5c8ff0facc",
      "operation": "query"
    },
    {
      "requestId": "a90197a6-c24e-465a-936e-9c5554329e96",
      "operation": "query"
    },
    {
      "requestId": "d2fc6593-e81f-4318-8d61-acf2d09b3ba7",
      "operation": "query"
    },
    {
      "requestId": "bfc42812-f9d3-4b08-b8a3-279e254e6d6b",
      "operation": "query"
    },
    {
      "requestId": "934a9ebf-b76c-4424-be5d-0bdf562657a5",
      "operation": "query"
    },
    {
      "requestId": "ef883c19-e21d-4f11-bf6c-6a842213f364",
      "operation": "query"
    },
    {
      "requestId": "86c28919-908c-4fe7-ae24-b27e209e992b",
      "operation": "query"
    },
    {
      "requestId": "4e4cf80f-6401-4dae-82c9-e3aa327fdc7f",
      "operation": "query"
    },
    {
      "requestId": "0674b70a-e1c3-4fc1-87a6-60f03dd11164",
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
    "pass": 18,
    "fail": 6,
    "not-run": 0
  }
}
```

Local loopback controls were measured only where marked pass. Remote HTTPS, an internal network or identity proxy, database TLS, a proxy response budget of at least 130 seconds, and a shutdown window covering all phases remain deployment requirements. M14-T08 release readiness is a separate decision.
