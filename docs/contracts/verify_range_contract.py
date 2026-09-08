#!/usr/bin/env python3
"""Offline structural and semantic checks for the RANGE-T03 contracts."""

from __future__ import annotations

import copy
import hashlib
import json
import re
from calendar import monthrange
from datetime import datetime
from pathlib import Path
from urllib.parse import unquote

import yaml
from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "docs/contracts/openapi-v1.yaml"
T01 = ROOT / "docs/research/RANGE-T01-source-evidence.json"
T02 = ROOT / "docs/research/RANGE-T02-calendar-evidence.json"
ERRORS = ROOT / "docs/contracts/error-codes.md"
TRACE = ROOT / "docs/traceability/tensor-range-requirements.md"
BOARD = ROOT / "docs/task-handoffs/tensor-range/tensor-range-task-board.md"

LEGACY_PATHS = {
    "/api/v1/data-sources",
    "/api/v1/data-sources/{pluginId}/apis",
    "/api/v1/downloads",
    "/api/v1/data-sources/{pluginId}/datasets",
    "/api/v1/data-sources/{pluginId}/datasets/{apiName}",
    "/api/v1/data-sources/{pluginId}/datasets/{apiName}/records",
}
LEGACY_RECORDS_SHA256 = "f892d05ef84cac87ce4122ea639cf8f8472b3d4363865e33d325ec3baa51f980"
LEGACY_PAGE_SHA256 = "c2e5e2c4eb6f6390da1bd0a9f37e4c7b7844d62365ae34d440b38e3dba6e6ec3"
LEGACY_SCHEMA_SHA256 = {
    "DatasetSummary": "ee2b93f0a1fb5731e99099c8ce48d4f437b4b20e7818610b5f87b58c9c1f6464",
    "DatasetDefinitionResponse": "57aafa19809578f80aa5b40420e98b9d7ccbcad3ba6bf7ccef0e6b8933f44ef4",
}

MODE_COUNTS = {
    "TRADE_DATE_RANGE": 19,
    "ANN_DATE_RANGE": 15,
    "MONTH_RANGE": 1,
    "NATIVE_RANGE": 3,
    "ORIGINAL_PARAMS": 11,
}
PARAM_APIS = {
    "RangeParams": {
        "adj_factor", "block_trade", "daily", "daily_basic", "hk_hold",
        "hsgt_top10", "margin_detail", "moneyflow", "moneyflow_hsgt",
        "monthly", "slb_len", "slb_sec", "slb_sec_detail", "stk_limit",
        "suspend_d", "top_inst", "top_list", "weekly", "disclosure_date",
        "dividend", "express", "forecast", "repurchase", "share_float",
        "stk_holdertrade", "top10_floatholders", "top10_holders",
        "broker_recommend", "namechange", "new_share",
    },
    "StockRangeParams": {
        "balancesheet", "cashflow", "fina_audit", "fina_indicator",
        "fina_mainbz", "income",
    },
    "ExchangeIdRangeParams": {"margin"},
    "ExchangeRangeParams": {"trade_cal"},
    "EmptyParams": {
        "index_classify", "index_member", "index_member_all", "pledge_detail",
        "pledge_stat", "stk_managers",
    },
    "StockParams": {"stk_holdernumber", "stk_rewards"},
    "HsTypeParams": {"hs_const"},
    "ListStatusParams": {"stock_basic"},
    "ExchangeParams": {"stock_company"},
}
EXTRA_PARAMS = {
    "StockRangeParams": {"ts_code": "000001.SZ"},
    "ExchangeIdRangeParams": {"exchange_id": "SSE"},
    "ExchangeRangeParams": {"exchange": "SSE"},
    "StockParams": {"ts_code": "000001.SZ"},
    "HsTypeParams": {"hs_type": "SH"},
    "ListStatusParams": {"list_status": "L"},
    "ExchangeParams": {"exchange": "SSE"},
}
PARAM_ORDER = {
    "RangeParams": ("start_date", "end_date"),
    "StockRangeParams": ("ts_code", "start_date", "end_date"),
    "ExchangeIdRangeParams": ("exchange_id", "start_date", "end_date"),
    "ExchangeRangeParams": ("exchange", "start_date", "end_date"),
    "EmptyParams": (),
    "StockParams": ("ts_code",),
    "HsTypeParams": ("hs_type",),
    "ListStatusParams": ("list_status",),
    "ExchangeParams": ("exchange",),
}
OLD_DATES = ("trade_date", "ann_date", "month")
NEW_ERROR_META = {
    "SOURCE_REQUEST_UNCONFIRMED": (409, False),
    "CALENDAR_UNCONFIRMED": (502, True),
    "SOURCE_TRUNCATED": (502, False),
    "SOURCE_COMPLETENESS_UNCONFIRMED": (502, False),
    "DATA_CONFLICT": (422, False),
    "RETRY_TASK_NOT_FOUND": (404, False),
    "DOWNLOAD_BUSY": (409, True),
    "RETRY_TASK_INVALID": (409, False),
    "TASK_RECORD_SAVE_UNCONFIRMED": (500, False),
    "COMMIT_UNCONFIRMED": (500, False),
}
EXPECTED_TASKS = {
    1: ("04,05,14,15", "18,19,20"), 2: ("06,08,12,13", "18,20"),
    3: ("05,13,14", "18,20"), 4: ("05,08,13,15", "18,19"),
    5: ("05,06,08,12", "18,19"), 6: ("05,14,15", "18,19"),
    7: ("11,12,16", "18,19"), 8: ("09,11,12", "18,20"),
    9: ("09,11,12", "18"), 10: ("07,12,13", "18"),
    11: ("07,09", "18,20"), 12: ("07,12,13", "18"),
    13: ("11,12,14,16", "18,19"), 14: ("04,05,08,09,13", "18,20"),
    15: ("06,12,13", "18,19"), 16: ("06,08", "18,20"),
    17: ("06,12,13,14", "18,19"), 18: ("04,06,08,15", "18,19,20"),
    19: ("05,14,15", "18,19"), 20: ("15,16,17", "19"),
    21: ("10,11,13,17", "18,19"), 22: ("05,10,13,14,17", "18,19"),
    23: ("12,13,14,17", "18,19"), 24: ("10,11,13", "18"),
    25: ("04,05,13,14,17", "18,19"),
    26: ("10,11,12,13,14,16", "18,19"),
    27: ("12,13,16,17", "18,19"), 28: ("12,13,14,16", "18,19"),
    29: ("07,09", "18,20"),
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def canonical_sha256(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
    return hashlib.sha256(encoded).hexdigest()


def pointer(document: object, ref: str) -> object:
    require(ref.startswith("#/"), f"non-local $ref is forbidden: {ref}")
    node = document
    for token in ref[2:].split("/"):
        token = unquote(token).replace("~1", "/").replace("~0", "~")
        require(isinstance(node, dict) and token in node, f"missing $ref target: {ref}")
        node = node[token]
    return node


def walk(node: object):
    yield node
    if isinstance(node, dict):
        for value in node.values():
            yield from walk(value)
    elif isinstance(node, list):
        for value in node:
            yield from walk(value)


def dereference(document: dict, node: object) -> object:
    if isinstance(node, list):
        return [dereference(document, value) for value in node]
    if not isinstance(node, dict):
        return node
    if "$ref" in node:
        resolved = copy.deepcopy(pointer(document, node["$ref"]))
        siblings = {key: value for key, value in node.items() if key != "$ref"}
        if siblings:
            require(isinstance(resolved, dict), "$ref siblings require an object schema")
            resolved.update(siblings)
        return dereference(document, resolved)
    return {key: dereference(document, value) for key, value in node.items()}


def validator(document: dict, schema: object) -> Draft202012Validator:
    return Draft202012Validator(dereference(document, schema), format_checker=FormatChecker())


def valid(document: dict, schema: object, instance: object) -> bool:
    return validator(document, schema).is_valid(instance)


def expect_invalid(document: dict, schema: object, instance: object, label: str) -> None:
    require(not valid(document, schema, instance), f"invalid case accepted: {label}")


def expect_assertion(action, label: str) -> None:
    try:
        action()
    except AssertionError:
        return
    raise AssertionError(f"semantic invalid case accepted: {label}")


def parse_date(value: str):
    require(isinstance(value, str) and re.fullmatch(r"[0-9]{8}", value) is not None,
            f"not YYYYMMDD: {value!r}")
    try:
        return datetime.strptime(value, "%Y%m%d").date()
    except ValueError as exc:
        raise AssertionError(f"invalid Gregorian date: {value}") from exc


def validate_range(params: dict) -> int:
    start = parse_date(params.get("start_date"))
    end = parse_date(params.get("end_date"))
    days = (end - start).days + 1
    require(days >= 1, "start_date must be on or before end_date")
    require(days <= 31, "range must contain at most 31 natural days")
    return days


def covered_months(start: str, end: str) -> list[str]:
    validate_range({"start_date": start, "end_date": end})
    current = parse_date(start).replace(day=1)
    stop = parse_date(end).replace(day=1)
    values = []
    while current <= stop:
        values.append(current.strftime("%Y%m"))
        year, month = current.year, current.month
        current = current.replace(
            year=year + (month == 12), month=1 if month == 12 else month + 1,
            day=monthrange(year + (month == 12), 1 if month == 12 else month + 1)[0],
        ).replace(day=1)
    return values


def parse_iso_date(value: str):
    require(isinstance(value, str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value) is not None,
            f"not YYYY-MM-DD: {value!r}")
    try:
        return datetime.strptime(value, "%Y-%m-%d").date()
    except ValueError as exc:
        raise AssertionError(f"invalid Gregorian date: {value}") from exc


def check_scope_semantics(scope: dict) -> None:
    if scope["targetType"] == "REQUEST":
        require(scope["targetValue"] == "", "REQUEST targetValue must be empty")
    else:
        require(bool(scope["targetValue"].strip()) and "," not in scope["targetValue"],
                "STOCK targetValue must be one normalized code")
    time_type, value = scope["timeType"], scope["timeValue"]
    if time_type == "NONE":
        require(value == "", "NONE timeValue must be empty")
    elif time_type == "DATE":
        parse_iso_date(value)
    elif time_type == "MONTH":
        require(re.fullmatch(r"[0-9]{4}-(0[1-9]|1[0-2])", value) is not None,
                "MONTH timeValue is invalid")
    elif time_type == "RANGE":
        require(isinstance(value, str) and value.count("/") == 1, "RANGE must contain two dates")
        start, end = map(parse_iso_date, value.split("/"))
        require(start < end, "RANGE must be ordered and truly multi-day")
    else:
        raise AssertionError(f"unknown timeType: {time_type}")


def check_result_semantics(
    result: dict,
    *,
    http200: bool,
    known_not_started: int | None,
    task_identity_confirmed: bool,
) -> None:
    outcome = result["outcome"]
    completed, failed = result["completedUnits"], result["failedUnits"]
    rows = (result["sourceRowCount"], result["insertedRows"], result["updatedRows"])
    failures = result["failures"]
    require(len(failures) == failed, "failures must correspond one-to-one with failedUnits")
    require(len({tuple(item[key] for key in ("targetType", "targetValue", "timeType", "timeValue"))
                 for item in failures}) == len(failures), "failure scopes must not be folded or duplicated")
    for failure in failures:
        check_scope_semantics(failure)
    for field in ("notStartedScopes", "unconfirmedScopes"):
        for scope in result[field]:
            check_scope_semantics(scope)
    require(result["notStartedUnits"] == known_not_started,
            "notStartedUnits differs from the scenario's known/unknown unit set")
    if result["notStartedUnits"] == 0:
        require(not result["notStartedScopes"], "zero notStartedUnits cannot carry an unstarted scope")
    if result["taskId"] is not None:
        require(task_identity_confirmed and result["remainingFailedUnits"] is not None
                and result["remainingFailedUnits"] > 0,
                "taskId requires independently confirmed task identity and remaining count")
    if result["failureRecordStatus"] == "NOT_REQUIRED":
        require(result["taskId"] is None and result["remainingFailedUnits"] == 0,
                "NOT_REQUIRED cannot expose a task")
    if http200:
        require(outcome != "UNCONFIRMED", "UNCONFIRMED cannot be HTTP 200")
        require(result["notStartedUnits"] == 0 and not result["notStartedScopes"]
                and not result["unconfirmedScopes"], "normal response cannot contain unfinished work")
        require(result["failureRecordStatus"] != "UNCONFIRMED", "normal response cannot have unknown records")
    if completed == 0:
        require(rows == (0, 0, 0), "zero completed units cannot have confirmed row subtotals")
    if outcome == "SUCCESS":
        require(failed == 0 and result["sourceRowCount"] > 0, "SUCCESS counts differ")
    elif outcome == "EMPTY":
        require(failed == 0 and completed > 0 and rows == (0, 0, 0), "EMPTY counts differ")
    elif outcome == "NO_OPEN_DATES":
        require(completed == failed == 0 and rows == (0, 0, 0)
                and result["skippedClosedDates"] > 0, "NO_OPEN_DATES counts differ")
    elif outcome == "PARTIAL":
        require(completed > 0 and failed > 0, "PARTIAL counts differ")
    elif outcome == "FAILED":
        require(completed == 0 and failed > 0, "FAILED counts differ")
    elif outcome == "UNCONFIRMED":
        require(not http200 and result["failureRecordStatus"] == "UNCONFIRMED",
                "UNCONFIRMED must be an error result with unknown record status")
    else:
        raise AssertionError(f"unknown outcome: {outcome}")
    if outcome in {"PARTIAL", "FAILED"} and http200:
        require(result["failureRecordStatus"] == "CONFIRMED" and result["taskId"] is not None
                and result["remainingFailedUnits"] > 0, "normal failures require a confirmed saved task")


def check_summary_semantics(summary: dict, *, expected_scope_count: int | None = None) -> None:
    scopes = summary["failedScopes"]
    require(summary["failedItemCount"] == len(scopes) >= 1, "failedItemCount/scopes differ")
    if expected_scope_count is not None:
        require(len(scopes) == expected_scope_count, "saved scopes differ from scenario facts")
    keys = ("targetType", "targetValue", "timeType", "timeValue")
    require(len({tuple(scope[key] for key in keys) for scope in scopes}) == len(scopes),
            "saved failure scopes must not be folded")
    for scope in scopes:
        check_scope_semantics(scope)
    status, original = summary["originalDateRangeStatus"], summary["originalDateRange"]
    if status == "RECORDED":
        require(isinstance(original, dict), "RECORDED requires originalDateRange")
        validate_range({"start_date": original["startDate"], "end_date": original["endDate"]})
    else:
        require(original is None, f"{status} requires null originalDateRange")


def check_detail_semantics(detail: dict, *, expected_scope_count: int | None = None) -> None:
    check_summary_semantics(detail, expected_scope_count=expected_scope_count)
    keys = ("targetType", "targetValue", "timeType", "timeValue")
    item_scopes = [{key: item[key] for key in keys} for item in detail["items"]]
    require(item_scopes == detail["failedScopes"], "detail items must match failedScopes in order")
    require(detail["failedItemCount"] == len(detail["items"]), "detail item count differs")
    require(item_scopes == sorted(item_scopes, key=lambda item: tuple(item[key] for key in keys)),
            "detail items are not in stable selector order")
    require(detail["canExecute"] == (detail["executionBlocker"] is None),
            "canExecute must be true iff executionBlocker is null")
    modes = {item["apiName"]: item["mode"] for item in json.loads(T01.read_text())["interfaces"]}
    mode = modes.get(detail["apiName"])
    params = detail["taskParams"]
    has_start, has_end = "start_date" in params, "end_date" in params
    endpoints = (params.get("start_date"), params.get("end_date"))
    status, original = detail["originalDateRangeStatus"], detail["originalDateRange"]
    if mode == "ORIGINAL_PARAMS" and (has_start or has_end):
        expected_status = "UNCONFIRMED"
    elif mode is None or has_start != has_end:
        expected_status = "UNCONFIRMED"
    elif not has_start:
        expected_status = "NOT_APPLICABLE" if mode == "ORIGINAL_PARAMS" else "NOT_RECORDED"
    else:
        try:
            validate_range({"start_date": endpoints[0], "end_date": endpoints[1]})
            expected_status = "RECORDED"
        except AssertionError:
            expected_status = "UNCONFIRMED"
    require(status == expected_status, "originalDateRangeStatus differs from saved taskParams facts")
    if status == "RECORDED":
        require(original == {"startDate": endpoints[0], "endDate": endpoints[1]},
                "RECORDED range must exactly copy both saved endpoints")
    if status == "UNCONFIRMED":
        require(not detail["canExecute"] and detail["executionBlocker"] is not None,
                "unconfirmed original range must block execution")


def example_entries(operation: dict):
    body = operation.get("requestBody", {}).get("content", {}).get("application/json", {})
    for name, item in body.get("examples", {}).items():
        yield f"request.{name}", body["schema"], item["value"], None
    for status, response in operation.get("responses", {}).items():
        media = response.get("content", {}).get("application/json", {})
        for name, item in media.get("examples", {}).items():
            yield f"response.{status}.{name}", media["schema"], item["value"], status


def request_for(target: dict) -> dict:
    schema_name = target["paramsSchema"]
    params = dict(EXTRA_PARAMS.get(schema_name, {}))
    if target["mode"] != "ORIGINAL_PARAMS":
        params.update(start_date="20260901", end_date="20260901")
    return {"pluginId": "tushare_pro", "apiName": target["apiName"], "params": params}


def policy_semantic(target: dict) -> str:
    if target["mode"] == "TRADE_DATE_RANGE":
        return "TRADE_DATE"
    if target["mode"] == "ANN_DATE_RANGE":
        return "ANN_DATE"
    if target["mode"] == "MONTH_RANGE":
        return "COVERED_MONTH"
    if target["mode"] == "ORIGINAL_PARAMS":
        return "NONE"
    return {"trade_cal": "CALENDAR_DATE", "new_share": "IPO_DATE", "namechange": "ANN_DATE"}[target["apiName"]]


def descriptor_for(target: dict) -> dict:
    schema_name = target["paramsSchema"]
    enums = {
        "exchange_id": ["SSE", "SZSE", "BSE"],
        "exchange": ["SSE", "SZSE", "BSE"],
        "hs_type": ["SH", "SZ"],
        "list_status": ["L", "P", "D"],
    }
    parameters = []
    for name in PARAM_ORDER[schema_name]:
        item = {"name": name, "label": name, "required": True}
        if name in {"start_date", "end_date"}:
            item.update(type="DATE_RANGE_MEMBER",
                        relatedParameter="end_date" if name == "start_date" else "start_date")
        elif name == "ts_code":
            item["type"] = "TS_CODE"
        elif name in enums:
            item.update(type="ENUM", allowedValues=enums[name])
        parameters.append(item)
    current = yaml.safe_load((ROOT / "data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro"
                              / f"{target['apiName']}.yaml").read_text())
    return {
        "apiName": target["apiName"],
        "displayName": target["apiName"],
        "category": "contract",
        "queryMode": current["queryMode"],
        "parameters": parameters,
        "downloadPolicy": {
            "mode": target["mode"],
            "dateSemantic": policy_semantic(target),
            "description": "Target form only; actual filtering and completeness still require source verification.",
            "calendarProfile": target["calendarProfile"],
            "limits": None if target["mode"] == "ORIGINAL_PARAMS" else {"maxRangeDays": 31},
        },
    }


def check_descriptor_semantics(target: dict, descriptor: dict) -> None:
    policy = descriptor["downloadPolicy"]
    require(policy["mode"] == target["mode"], "descriptor mode differs from target")
    require(policy["dateSemantic"] == policy_semantic(target), "descriptor date semantic differs from target")
    require(policy["calendarProfile"] == target["calendarProfile"], "descriptor calendar differs from target")
    expected_limits = None if target["mode"] == "ORIGINAL_PARAMS" else {"maxRangeDays": 31}
    require(policy["limits"] == expected_limits, "descriptor limits differ from target")
    names = [item["name"] for item in descriptor["parameters"]]
    require(names == list(PARAM_ORDER[target["paramsSchema"]]), "descriptor parameter projection differs")
    current = yaml.safe_load((ROOT / "data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro"
                              / f"{target['apiName']}.yaml").read_text())
    require(descriptor["queryMode"] == current["queryMode"], "queryMode changed during download projection")
    old_date_names = {"trade_date", "ann_date", "month", "start_date", "end_date"}
    current_extra = [item["name"] for item in current["parameters"] if item["name"] not in old_date_names]
    expected_extra = [name for name in PARAM_ORDER[target["paramsSchema"]]
                      if name not in {"start_date", "end_date"}]
    require(current_extra == expected_extra, "original non-date parameter changed or reordered")
    for item in descriptor["parameters"]:
        if item["name"] in {"start_date", "end_date"}:
            require(item["type"] == "DATE_RANGE_MEMBER"
                    and item["relatedParameter"] != item["name"], "range projection pair differs")


def expect_descriptor_invalid(spec: dict, target: dict, descriptor: dict, label: str) -> None:
    schema = spec["components"]["schemas"]["ApiDescriptor"]
    schema_valid = valid(spec, schema, descriptor)
    try:
        check_descriptor_semantics(target, descriptor)
        semantic_valid = True
    except AssertionError:
        semantic_valid = False
    require(not (schema_valid and semantic_valid), f"invalid descriptor accepted: {label}")


def check_structure(spec: dict) -> None:
    require(spec.get("openapi") == "3.1.0", "OpenAPI must be 3.1.0")
    for node in walk(spec):
        if isinstance(node, dict) and "$ref" in node:
            pointer(spec, node["$ref"])
    for name, schema in spec["components"]["schemas"].items():
        try:
            Draft202012Validator.check_schema(schema)
        except Exception as exc:
            raise AssertionError(f"invalid component schema {name}: {exc}") from exc

    operation_ids = []
    for path_item in spec["paths"].values():
        for method, operation in path_item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            operation_ids.append(operation["operationId"])
            for status, response in operation["responses"].items():
                require("X-Request-Id" in response.get("headers", {}),
                        f"{operation['operationId']} {status} lacks X-Request-Id")
    require(len(operation_ids) == len(set(operation_ids)), "operationId values must be unique")
    require({"listRetryTasks", "getRetryTask", "executeRetryTask"} <= set(operation_ids),
            "retry task operationIds missing")
    new_paths = set(spec["paths"]) - LEGACY_PATHS
    require(new_paths == {
        "/api/v1/retry-tasks", "/api/v1/retry-tasks/{taskId}",
        "/api/v1/retry-tasks/{taskId}/execute",
    }, f"unexpected new paths: {new_paths}")
    require(set(spec["paths"]) == LEGACY_PATHS | new_paths, "legacy path removed")
    lower_paths = " ".join(spec["paths"]).lower()
    require(all(word not in lower_paths for word in ("cancel", "reconcile", "verify")),
            "cancellation/reconciliation endpoint added")
    require(not any(isinstance(node.get("name"), str)
                    and node["name"] in {"Idempotency-Key", "If-Match"}
                    for node in walk(spec) if isinstance(node, dict)),
            "idempotency/version claim header added")


def check_metadata_policy(spec: dict) -> None:
    schemas = spec["components"]["schemas"]
    descriptor = schemas["ApiDescriptor"]
    require("downloadPolicy" in descriptor["required"], "ApiDescriptor.downloadPolicy must be required")
    require(descriptor["properties"]["downloadPolicy"] == {"$ref": "#/components/schemas/DownloadPolicy"},
            "ApiDescriptor.downloadPolicy must reference the shared schema")
    parameter = descriptor["properties"]["parameters"]["items"]
    require("DATE_RANGE_MEMBER" in parameter["properties"]["type"]["enum"],
            "DATE_RANGE_MEMBER projection type missing")
    related_rule = next((item for item in parameter["allOf"]
                         if item.get("if", {}).get("properties", {}).get("type", {}).get("const") == "DATE_RANGE_MEMBER"), None)
    require(related_rule and "relatedParameter" in related_rule["then"]["required"],
            "DATE_RANGE_MEMBER must require relatedParameter")

    policy = schemas["DownloadPolicy"]
    fields = {"mode", "dateSemantic", "description", "calendarProfile", "limits"}
    require(policy.get("additionalProperties") is False and set(policy["required"]) == fields
            and set(policy["properties"]) == fields, "DownloadPolicy must be a strict fully-required object")
    require(policy["properties"]["mode"]["enum"] == list(MODE_COUNTS), "DownloadPolicy modes differ")
    require(set(policy["properties"]["dateSemantic"]["enum"]) == {
        "TRADE_DATE", "ANN_DATE", "COVERED_MONTH", "CALENDAR_DATE", "IPO_DATE", "NONE",
    }, "date semantics differ")
    calendar_schema = policy["properties"]["calendarProfile"]
    require(set(calendar_schema["enum"]) == {"C-A", "C-M", "C-S", "C-N", "C-X", None},
            "calendar profiles differ")
    require(policy["properties"]["description"].get("minLength") == 1,
            "download policy description must be non-empty")
    limits = dereference(spec, policy["properties"]["limits"])
    object_limits = [node for node in walk(limits) if isinstance(node, dict)
                     and "maxRangeDays" in node.get("properties", {})]
    require(any(node.get("additionalProperties") is False
                and set(node.get("required", [])) == {"maxRangeDays"}
                and node["properties"]["maxRangeDays"].get("const") == 31
                for node in object_limits), "download limit must contain a strict maxRangeDays=31 object")

    descriptor_schema = schemas["ApiDescriptor"]
    targets = spec["x-tensor-range-targets"]
    for target in targets:
        descriptor = descriptor_for(target)
        require(valid(spec, descriptor_schema, descriptor),
                f"valid projected descriptor rejected: {target['apiName']}")
        check_descriptor_semantics(target, descriptor)
    sample = targets[0]
    descriptor = descriptor_for(sample)
    del descriptor["downloadPolicy"]
    expect_invalid(spec, descriptor_schema, descriptor, "descriptor missing downloadPolicy")

    descriptor = descriptor_for(sample)
    descriptor["downloadPolicy"].update(mode="ANN_DATE_RANGE", dateSemantic="ANN_DATE", calendarProfile=None)
    expect_descriptor_invalid(spec, sample, descriptor, "wrong target mode")
    descriptor = descriptor_for(sample)
    descriptor["downloadPolicy"]["calendarProfile"] = "C-M"
    expect_descriptor_invalid(spec, sample, descriptor, "wrong target calendar")
    descriptor = descriptor_for(sample)
    descriptor["downloadPolicy"]["limits"] = None
    expect_descriptor_invalid(spec, sample, descriptor, "range without limits")
    original = next(target for target in targets if target["mode"] == "ORIGINAL_PARAMS")
    descriptor = descriptor_for(original)
    descriptor["downloadPolicy"]["limits"] = {"maxRangeDays": 31}
    expect_descriptor_invalid(spec, original, descriptor, "original parameters with range limits")
    descriptor = descriptor_for(sample)
    descriptor["parameters"].reverse()
    expect_descriptor_invalid(spec, sample, descriptor, "reversed range projection")


def check_targets(spec: dict) -> None:
    targets = spec["x-tensor-range-targets"]
    require(len(targets) == 49, "target extension must contain 49 rows")
    require(all(set(row) == {"apiName", "mode", "paramsSchema", "calendarProfile", "liveExcluded"}
                for row in targets), "target extension row keys differ")
    require(len({row["apiName"] for row in targets}) == 49, "duplicate target apiName")

    t01 = json.loads(T01.read_text())["interfaces"]
    expected_modes = {row["apiName"]: row["mode"] for row in t01}
    actual_modes = {row["apiName"]: row["mode"] for row in targets}
    require(actual_modes == expected_modes, "target names/modes differ from T01")
    for mode, count in MODE_COUNTS.items():
        require(list(actual_modes.values()).count(mode) == count, f"wrong count for {mode}")

    t02 = json.loads(T02.read_text())["interfaces"]
    expected_calendars = {row["apiName"]: row["calendarProfile"] for row in t02}
    require({row["apiName"]: row["calendarProfile"] for row in targets if row["calendarProfile"]}
            == expected_calendars, "calendar profiles differ from T02")
    expected_excluded = {row["apiName"] for row in t01 if row["live"]["status"] == "EXCLUDED_ISSUE_008"}
    require({row["apiName"] for row in targets if row["liveExcluded"]} == expected_excluded,
            "live exclusions differ from T01/ISSUE-008")

    expected_param_map = {api: schema for schema, apis in PARAM_APIS.items() for api in apis}
    require({row["apiName"]: row["paramsSchema"] for row in targets} == expected_param_map,
            "params schema mapping differs")
    expected_shapes = {
        "RangeParams": ({"start_date", "end_date"}, {}),
        "StockRangeParams": ({"ts_code", "start_date", "end_date"}, {}),
        "ExchangeIdRangeParams": ({"exchange_id", "start_date", "end_date"}, {"exchange_id": ["SSE", "SZSE", "BSE"]}),
        "ExchangeRangeParams": ({"exchange", "start_date", "end_date"}, {"exchange": ["SSE", "SZSE", "BSE"]}),
        "EmptyParams": (set(), {}),
        "StockParams": ({"ts_code"}, {}),
        "HsTypeParams": ({"hs_type"}, {"hs_type": ["SH", "SZ"]}),
        "ListStatusParams": ({"list_status"}, {"list_status": ["L", "P", "D"]}),
        "ExchangeParams": ({"exchange"}, {"exchange": ["SSE", "SZSE", "BSE"]}),
    }
    for name, (fields, enums) in expected_shapes.items():
        schema = spec["components"]["schemas"][name]
        require(schema.get("additionalProperties") is False and set(schema.get("required", [])) == fields
                and set(schema.get("properties", {})) == fields, f"strict params shape differs: {name}")
        for field, values in enums.items():
            require(schema["properties"][field].get("enum") == values, f"enum differs: {name}.{field}")
            bad = dict(EXTRA_PARAMS.get(name, {}))
            if "start_date" in fields:
                bad.update(start_date="20260901", end_date="20260901")
            bad[field] = "INVALID"
            expect_invalid(spec, schema, bad, f"{name}.{field} invalid enum")
    for name in ("RangeParams", "StockRangeParams", "ExchangeIdRangeParams", "ExchangeRangeParams"):
        for field in ("start_date", "end_date"):
            field_schema = dereference(spec, spec["components"]["schemas"][name]["properties"][field])
            require(field_schema.get("pattern") == "^[0-9]{8}$",
                    f"date pattern differs: {name}.{field}")
    stock_schema = dereference(spec, spec["components"]["schemas"]["StockParams"]["properties"]["ts_code"])
    stock_pattern = stock_schema["pattern"]
    require(stock_pattern == r"^\s*[A-Za-z0-9]+\.[A-Za-z0-9]+\s*$", "stock normalization input pattern differs")
    request_schema = spec["components"]["schemas"]["DownloadRequest"]
    for target in targets:
        request = request_for(target)
        require(valid(spec, request_schema, request), f"valid target request rejected: {target['apiName']}")
        bad = copy.deepcopy(request)
        bad["params"]["unknown"] = "x"
        expect_invalid(spec, request_schema, bad, f"{target['apiName']} unknown param")
        if target["mode"] != "ORIGINAL_PARAMS":
            validate_range(request["params"])
            for endpoint in ("start_date", "end_date"):
                bad = copy.deepcopy(request)
                del bad["params"][endpoint]
                expect_invalid(spec, request_schema, bad, f"{target['apiName']} missing {endpoint}")
            for legacy in OLD_DATES:
                bad = copy.deepcopy(request)
                bad["params"][legacy] = "20260901"
                expect_invalid(spec, request_schema, bad, f"{target['apiName']} legacy {legacy}")
        else:
            for field in ("start_date", "end_date"):
                bad = copy.deepcopy(request)
                bad["params"][field] = "20260901"
                expect_invalid(spec, request_schema, bad, f"{target['apiName']} added {field}")
        required_extra = set(EXTRA_PARAMS.get(target["paramsSchema"], {}))
        for field in required_extra:
            bad = copy.deepcopy(request)
            del bad["params"][field]
            expect_invalid(spec, request_schema, bad, f"{target['apiName']} missing {field}")

    expect_invalid(spec, request_schema,
                   {"pluginId": "tushare_pro", "apiName": "unknown_api", "params": {}},
                   "unknown Tushare api")
    require(valid(spec, request_schema,
                  {"pluginId": "fixture", "apiName": "download_outcomes", "params": {"scenario": "success"}}),
            "fixture scenario request no longer accepted")
    bad_type = request_for(next(row for row in targets if row["apiName"] == "daily"))
    bad_type["params"]["start_date"] = 20260901
    expect_invalid(spec, request_schema, bad_type, "non-string date")
    multi_stock = request_for(next(row for row in targets if row["apiName"] == "income"))
    multi_stock["params"]["ts_code"] = "000001.SZ,000002.SZ"
    expect_invalid(spec, request_schema, multi_stock, "multiple stocks")
    require(validate_range({"start_date": "20260101", "end_date": "20260131"}) == 31, "31 days rejected")
    for params in (
        {"start_date": "20260101", "end_date": "20260201"},
        {"start_date": "20260102", "end_date": "20260101"},
        {"start_date": "20260229", "end_date": "20260229"},
    ):
        try:
            validate_range(params)
        except AssertionError:
            pass
        else:
            raise AssertionError(f"semantic range accepted: {params}")
    require(validate_range({"start_date": "20240229", "end_date": "20240229"}) == 1,
            "valid leap day rejected")
    require(validate_range({"start_date": "20251231", "end_date": "20260101"}) == 2,
            "cross-year range rejected")
    require(covered_months("20260131", "20260302") == ["202601", "202602", "202603"],
            "covered month expansion differs")


def check_examples(spec: dict) -> None:
    seen = set()
    for path, path_item in spec["paths"].items():
        for method, operation in path_item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            for label, schema, value, status in example_entries(operation):
                errors = list(validator(spec, schema).iter_errors(value))
                require(not errors, f"{path} {label} invalid: {errors[0].message if errors else ''}")
                seen.add(label.split(".")[-1])
                if isinstance(value, dict) and "requestId" in value:
                    nested = value.get("downloadResult")
                    require(not nested or nested["requestId"] == value["requestId"],
                            f"requestId mismatch in {path} {label}")
                    if status is not None:
                        response = operation["responses"][status]
                        header_example = response["headers"]["X-Request-Id"].get("example")
                        require(header_example is None or header_example == value["requestId"],
                                f"header/body requestId mismatch in {path} {label}")
                ref = schema.get("$ref") if isinstance(schema, dict) else None
                if ref == "#/components/schemas/RetryTaskPage":
                    for summary in value["items"]:
                        check_summary_semantics(summary)
                elif ref == "#/components/schemas/RetryTaskDetail":
                    check_detail_semantics(value)
    required = {
        "dailySameDay", "incomeRange", "marginRange", "brokerRecommendThreeMonths",
        "tradeCalendarSameDay", "stockBasic", "indexClassify", "partialUnits", "empty",
        "allClosed", "allFailed", "recordSaveUnknown", "commitUnknown",
        "calendarUnconfirmed", "tasks", "detail", "twoStocksSameDay",
        "notApplicable", "notRecorded", "unconfirmedOriginalRange", "pluginOffline",
        "notFound", "busy", "success", "partialRetry", "emptyTasks",
    }
    require(required <= seen, f"required examples missing: {sorted(required - seen)}")

    schemas = spec["components"]["schemas"]
    scope = schemas["RecoveryScope"]
    valid_scopes = [
        {"targetType": "REQUEST", "targetValue": "", "timeType": "NONE", "timeValue": ""},
        {"targetType": "REQUEST", "targetValue": "", "timeType": "DATE", "timeValue": "2026-09-03"},
        {"targetType": "REQUEST", "targetValue": "", "timeType": "MONTH", "timeValue": "2026-09"},
        {"targetType": "REQUEST", "targetValue": "", "timeType": "RANGE", "timeValue": "2026-09-01/2026-09-02"},
        {"targetType": "STOCK", "targetValue": "000001.SZ", "timeType": "DATE", "timeValue": "2026-09-03"},
    ]
    for value in valid_scopes:
        require(valid(spec, scope, value), f"valid recovery scope rejected: {value}")
        check_scope_semantics(value)
    for value in (
        {"targetType": "REQUEST", "targetValue": "000001.SZ", "timeType": "DATE", "timeValue": "2026-09-03"},
        {"targetType": "STOCK", "targetValue": "", "timeType": "DATE", "timeValue": "2026-09-03"},
        {"targetType": "STOCK", "targetValue": "000001.SZ,000002.SZ", "timeType": "DATE", "timeValue": "2026-09-03"},
        {"targetType": "REQUEST", "targetValue": "", "timeType": "NONE", "timeValue": "2026-09-03"},
        {"targetType": "REQUEST", "targetValue": "", "timeType": "RANGE", "timeValue": "2026-09-03/2026-09-03"},
        {"targetType": "REQUEST", "targetValue": "", "timeType": "DATE", "timeValue": "2026-02-29"},
    ):
        schema_valid = valid(spec, scope, value)
        try:
            check_scope_semantics(value)
            semantic_valid = True
        except AssertionError:
            semantic_valid = False
        require(not (schema_valid and semantic_valid), f"invalid recovery scope accepted: {value}")


def find_example(spec: dict, name: str) -> tuple[str, dict]:
    for path_item in spec["paths"].values():
        for method, operation in path_item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            for label, _schema, value, status in example_entries(operation):
                if label.endswith(f".{name}"):
                    return status or "request", value
    raise AssertionError(f"example not found: {name}")


def check_results(spec: dict) -> None:
    success_schema = spec["components"]["schemas"]["DownloadResponse"]
    execution_schema = spec["components"]["schemas"]["DownloadExecutionResult"]
    expected = {
        "partialUnits": ("PARTIAL", 2, 1, 0, 0, 20, 18, 2),
        "empty": ("EMPTY", 1, 0, 0, 0, 0, 0, 0),
        "allClosed": ("NO_OPEN_DATES", 0, 0, 0, 2, 0, 0, 0),
        "allFailed": ("FAILED", 0, 1, 0, 0, 0, 0, 0),
        "recordSaveUnknown": ("UNCONFIRMED", 1, 1, None, 0, 10, 10, 0),
        "commitUnknown": ("UNCONFIRMED", 1, 0, None, 0, 10, 10, 0),
    }
    keys = ("outcome", "completedUnits", "failedUnits", "notStartedUnits",
            "skippedClosedDates", "sourceRowCount", "insertedRows", "updatedRows")
    facts = {
        "partialUnits": (0, True), "empty": (0, False), "allClosed": (0, False),
        "allFailed": (0, True), "recordSaveUnknown": (None, False),
        "commitUnknown": (None, False),
    }
    for name, values in expected.items():
        status, outer = find_example(spec, name)
        result = outer.get("downloadResult", outer)
        require(tuple(result[key] for key in keys) == values, f"wrong counts in {name}")
        require(valid(spec, execution_schema, result), f"execution result rejected: {name}")
        if status == "200":
            require(valid(spec, success_schema, result), f"HTTP 200 result rejected: {name}")
            check_result_semantics(result, http200=True, known_not_started=facts[name][0],
                                   task_identity_confirmed=facts[name][1])
        else:
            require(not valid(spec, success_schema, result), f"error result accepted as HTTP 200: {name}")
            check_result_semantics(result, http200=False, known_not_started=facts[name][0],
                                   task_identity_confirmed=facts[name][1])
    status, calendar_error = find_example(spec, "calendarUnconfirmed")
    require(status == "502" and "downloadResult" not in calendar_error,
            "pre-execution calendar error must omit downloadResult")

    _, empty = find_example(spec, "empty")
    mutated = copy.deepcopy(empty)
    mutated["sourceRowCount"] = 1
    expect_invalid(spec, success_schema, mutated, "EMPTY with source rows")
    _, failed = find_example(spec, "allFailed")
    mutated = copy.deepcopy(failed)
    mutated["taskId"] = None
    expect_invalid(spec, success_schema, mutated, "FAILED without saved task")
    mutated = copy.deepcopy(empty)
    mutated["outcome"] = "UNCONFIRMED"
    expect_invalid(spec, success_schema, mutated, "UNCONFIRMED as HTTP 200")

    mutated = copy.deepcopy(failed)
    mutated.update(sourceRowCount=1, insertedRows=1)
    expect_invalid(spec, success_schema, mutated, "zero completed units with positive rows")
    expect_assertion(
        lambda: check_result_semantics(mutated, http200=True, known_not_started=0,
                                       task_identity_confirmed=True),
        "FAILED S=0 with positive R/I",
    )

    _, commit_outer = find_example(spec, "commitUnknown")
    commit = commit_outer["downloadResult"]
    known_one = copy.deepcopy(commit)
    known_one["notStartedUnits"] = 1
    known_one["notStartedScopes"] = [
        {"targetType": "REQUEST", "targetValue": "", "timeType": "DATE", "timeValue": "2026-09-04"}
    ]
    check_result_semantics(known_one, http200=False, known_not_started=1,
                           task_identity_confirmed=False)
    known_zero = copy.deepcopy(commit)
    known_zero["notStartedUnits"] = 0
    known_zero["notStartedScopes"] = []
    check_result_semantics(known_zero, http200=False, known_not_started=0,
                           task_identity_confirmed=False)
    expect_assertion(
        lambda: check_result_semantics(known_zero, http200=False, known_not_started=None,
                                       task_identity_confirmed=False),
        "unknown remaining set misreported as N=0",
    )

    _, save_outer = find_example(spec, "recordSaveUnknown")
    existing_task = copy.deepcopy(save_outer["downloadResult"])
    existing_task["taskId"] = "11111111-1111-4111-8111-111111111111"
    existing_task["remainingFailedUnits"] = 2
    check_result_semantics(existing_task, http200=False, known_not_started=None,
                           task_identity_confirmed=True)
    expect_assertion(
        lambda: check_result_semantics(existing_task, http200=False, known_not_started=None,
                                       task_identity_confirmed=False),
        "pre-generated or otherwise unproven task identity",
    )


def check_retry_contract(spec: dict) -> None:
    paths = spec["paths"]
    listing = paths["/api/v1/retry-tasks"]["get"]
    require(set(paths["/api/v1/downloads"]["post"]["responses"]) == {"200", "400", "409", "500", "502", "504"},
            "download response status set differs")
    require(set(listing["responses"]) == {"200", "400", "500"}, "retry list status set differs")
    require(set(paths["/api/v1/retry-tasks/{taskId}"]["get"]["responses"]) == {"200", "400", "404", "500"},
            "retry detail status set differs")
    require(set(paths["/api/v1/retry-tasks/{taskId}/execute"]["post"]["responses"])
            == {"200", "400", "404", "409", "500", "502", "504"},
            "retry execute status set differs")
    parameters = {item["name"]: item for item in listing["parameters"]}
    require(parameters["page"]["schema"] == {"type": "integer", "minimum": 1, "default": 1},
            "retry page contract differs")
    require(parameters["pageSize"]["schema"] == {"type": "integer", "enum": [20, 50, 100], "default": 20},
            "retry pageSize contract differs")
    require(all(parameters[name]["schema"]["pattern"] == "^[a-z][a-z0-9_]{1,63}$"
                for name in ("pluginId", "apiName")), "retry filters must use identifier pattern")
    execute = paths["/api/v1/retry-tasks/{taskId}/execute"]["post"]
    require("requestBody" not in execute, "retry execute must not declare a body")
    require("zero bytes" in execute["description"] and all(token in execute["description"] for token in ("{}", "null", "PARAM_INVALID")),
            "retry execute body rejection is incomplete")
    for path in ("/api/v1/retry-tasks/{taskId}", "/api/v1/retry-tasks/{taskId}/execute"):
        param = spec["paths"][path]["parameters"][0]
        require(param["name"] == "taskId" and param["schema"].get("format") == "uuid", "taskId must be UUID")
    text = " ".join(str(value) for node in walk(listing) if isinstance(node, dict) for value in node.values() if isinstance(value, str))
    require(all(value in text for value in ("updatedAt DESC", "taskId DESC", "last page", "page=1")),
            "retry pagination/sort descriptions incomplete")

    _, tasks = find_example(spec, "tasks")
    _, detail = find_example(spec, "detail")
    require(tasks["pageSize"] == 20 and tasks["items"][0]["failedItemCount"] == 2,
            "retry list example differs")
    for summary in tasks["items"]:
        check_summary_semantics(summary)
    check_detail_semantics(detail)
    require(detail["taskParams"] == {"exchange_id": "SSE", "start_date": "20260901", "end_date": "20260910"},
            "detail taskParams differs")
    require(detail["originalDateRange"] == {"startDate": "20260901", "endDate": "20260910"}
            and detail["originalDateRangeStatus"] == "RECORDED", "recorded original range differs")
    require([item["timeValue"] for item in detail["items"]] == ["2026-09-03", "2026-09-07"],
            "detail items must preserve separate non-contiguous dates")
    require(detail["failedScopes"] == [{key: item[key] for key in ("targetType", "targetValue", "timeType", "timeValue")}
                                        for item in detail["items"]], "detail scopes/items mismatch")
    require(detail["failedItemCount"] == len(detail["items"]), "detail count mismatch")
    _, partial_retry = find_example(spec, "partialRetry")
    check_detail_semantics(partial_retry)
    require(partial_retry["originalDateRange"] == {"startDate": "20260901", "endDate": "20260910"}
            and [item["timeValue"] for item in partial_retry["items"]] == ["2026-09-07"],
            "partial retry must preserve original 1-10 range and only current day 7")
    _, empty_tasks = find_example(spec, "emptyTasks")
    require(empty_tasks == {"requestId": empty_tasks["requestId"], "page": 1, "pageSize": 20,
                            "totalElements": 0, "totalPages": 0, "items": []},
            "empty retry page normalization differs")
    status_examples = {}
    for name, status in (("notApplicable", "NOT_APPLICABLE"), ("notRecorded", "NOT_RECORDED"),
                         ("unconfirmedOriginalRange", "UNCONFIRMED")):
        _, value = find_example(spec, name)
        status_examples[name] = value
        require(value["originalDateRangeStatus"] == status and value["originalDateRange"] is None,
                f"wrong original range null meaning: {name}")
    _, unconfirmed = find_example(spec, "unconfirmedOriginalRange")
    require(not unconfirmed["canExecute"] and unconfirmed["executionBlocker"],
            "unconfirmed original range must be blocked")
    _, offline = find_example(spec, "pluginOffline")
    require(offline["pluginDisplayName"] is None and offline["apiDisplayName"] is None,
            "offline plugin fallback example differs")
    _, stocks = find_example(spec, "twoStocksSameDay")
    check_detail_semantics(stocks, expected_scope_count=2)
    require(len({scope["targetValue"] for scope in stocks["failedScopes"]}) == 2,
            "same-day stocks were collapsed")
    fabricated = copy.deepcopy(detail)
    del fabricated["taskParams"]["end_date"]
    require(valid(spec, spec["components"]["schemas"]["RetryTaskDetail"], fabricated),
            "single-end negative should reach semantic validation")
    expect_assertion(lambda: check_detail_semantics(fabricated),
                     "single saved endpoint fabricated as RECORDED")
    folded = copy.deepcopy(stocks)
    folded["failedItemCount"] = 1
    folded["failedScopes"] = folded["failedScopes"][:1]
    folded["items"] = folded["items"][:1]
    expect_assertion(lambda: check_detail_semantics(folded, expected_scope_count=2),
                     "two same-day stocks folded into one saved scope")
    for name in ("notRecorded", "notApplicable"):
        explicit_null = copy.deepcopy(status_examples[name])
        explicit_null["taskParams"].update(start_date=None, end_date=None)
        require(valid(spec, spec["components"]["schemas"]["RetryTaskDetail"], explicit_null),
                f"{name} explicit-null negative should reach semantic validation")
        expect_assertion(lambda value=explicit_null: check_detail_semantics(value),
                         f"{name} explicit null endpoints treated as absent")


def parse_error_table() -> dict[str, tuple[int, bool]]:
    rows = {}
    pattern = re.compile(r"^\| `([A-Z0-9_]+)` \| ([0-9]{3}) \| `(true|false)` \|")
    for line in ERRORS.read_text().splitlines():
        match = pattern.match(line)
        if match:
            rows[match.group(1)] = (int(match.group(2)), match.group(3) == "true")
    return rows


def check_errors_and_baseline(spec: dict) -> None:
    catalog = parse_error_table()
    enum = set(spec["components"]["schemas"]["ApiError"]["properties"]["code"]["enum"])
    require(enum == set(catalog), "ApiError enum and error catalog differ")
    require(len(catalog) == 26 and all(catalog[code] == metadata for code, metadata in NEW_ERROR_META.items()),
            "new error metadata differs")
    error_text = ERRORS.read_text()
    require(all(phrase in error_text for phrase in (
        "Before business execution", "During business execution", "Storage failure or unknown commit",
        "zero bytes", "notStartedUnits", "do not retry", "manual",
    )), "error stage rules incomplete")
    for name in ("partialUnits", "allFailed", "recordSaveUnknown", "commitUnknown", "calendarUnconfirmed", "notFound", "busy"):
        _, value = find_example(spec, name)
        error = value.get("downloadResult", value)
        for failure in error.get("failures", []):
            require(failure["errorCode"] in catalog, f"uncatalogued failure example code: {name}")
        if "code" in value:
            require(value["code"] in catalog, f"uncatalogued ApiError example code: {name}")
            require(value["retryable"] == catalog[value["code"]][1], f"wrong retryable flag: {name}")
    for path_item in spec["paths"].values():
        for method, operation in path_item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            for label, schema, value, status in example_entries(operation):
                if isinstance(schema, dict) and schema.get("$ref") == "#/components/schemas/ApiError":
                    require(value["code"] in catalog, f"uncatalogued ApiError example: {label}")
                    require(value["retryable"] == catalog[value["code"]][1], f"wrong retryable flag: {label}")
                    require(status is not None, f"ApiError example without status: {label}")

    new_records = spec["paths"]["/api/v1/data-sources/{pluginId}/datasets/{apiName}/records"]
    new_page = spec["components"]["schemas"]["PageResponse"]
    require(canonical_sha256(new_records) == LEGACY_RECORDS_SHA256,
            "existing records query contract changed")
    require(canonical_sha256(new_page) == LEGACY_PAGE_SHA256,
            "PageResponse exact-value/string contract changed")
    for name, expected_hash in LEGACY_SCHEMA_SHA256.items():
        require(canonical_sha256(spec["components"]["schemas"][name]) == expected_hash,
                f"unchanged metadata/query schema changed: {name}")


def markdown_rows(text: str, prefix: str) -> list[list[str]]:
    rows = []
    for line in text.splitlines():
        if line.startswith(f"| {prefix}"):
            rows.append([cell.strip() for cell in line.strip().strip("|").split("|")])
    return rows


def check_traceability(spec: dict) -> None:
    text = TRACE.read_text()
    require("| AC | PRD | TRD | Contract | Implementation tasks | Verification tasks | Expected evidence | Result |" in text,
            "traceability header differs")
    rows = markdown_rows(text, "AC-PRD-RANGE-")
    require(len(rows) == 29 and len({row[0] for row in rows}) == 29, "trace must have 29 unique AC rows")
    require(all(len(row) == 8 and all(cell for cell in row) for row in rows), "trace cells must be non-empty")
    require([row[0] for row in rows] == [f"AC-PRD-RANGE-{number:02d}" for number in range(1, 30)],
            "trace AC order/coverage differs")
    for number, row in enumerate(rows, 1):
        expected_impl, expected_verify = EXPECTED_TASKS[number]
        actual_impl = ",".join(re.findall(r"RANGE-T([0-9]{2})", row[4]))
        actual_verify = ",".join(re.findall(r"RANGE-T([0-9]{2})", row[5]))
        require((actual_impl, actual_verify) == (expected_impl, expected_verify),
                f"task mapping differs for AC {number:02d}")
        require(row[7] == "未执行；T03仅合同校验", f"trace result overclaims AC {number:02d}")
    board_text = BOARD.read_text()
    for task in sorted(set(re.findall(r"RANGE-T[0-9]{2}", "\n".join(" ".join(row[4:6]) for row in rows)))):
        require(task.lower() in board_text.lower(), f"trace task absent from board: {task}")
    for target in re.findall(r"\]\(([^)#]+)(?:#[^)]+)?\)", text):
        path = (TRACE.parent / unquote(target)).resolve()
        require(path.exists(), f"broken trace link: {target}")
    schema_names = set(spec["components"]["schemas"])
    for name in re.findall(r"openapi-v1\.yaml#/components/schemas/([A-Za-z0-9]+)", text):
        require(name in schema_names, f"trace references missing schema: {name}")


def run_checks(spec: dict, *, quiet: bool = False) -> None:
    checks = [
        ("OpenAPI structure/paths", check_structure),
        ("metadata download policy", check_metadata_policy),
        ("49 targets and request migration", check_targets),
        ("all request/response examples and scopes", check_examples),
        ("result counters and uncertainty boundary", check_results),
        ("retry task contract", check_retry_contract),
        ("error catalog and unchanged query", check_errors_and_baseline),
        ("29-row traceability", check_traceability),
    ]
    for label, check in checks:
        check(spec)
        if not quiet:
            print(f"PASS {label}")


def check_mutations(spec: dict) -> None:
    mutations = []

    changed = copy.deepcopy(spec)
    operation = changed["paths"]["/api/v1/retry-tasks"]["get"]
    next(item for item in operation["parameters"] if item["name"] == "pageSize")["schema"]["default"] = 50
    mutations.append(("pageSize default 50", changed))

    changed = copy.deepcopy(spec)
    changed["x-tensor-range-targets"].pop()
    mutations.append(("delete target", changed))

    changed = copy.deepcopy(spec)
    range_params = changed["components"]["schemas"]["RangeParams"]
    range_params["properties"]["trade_date"] = {"type": "string", "pattern": "^[0-9]{8}$"}
    mutations.append(("restore daily trade_date", changed))

    changed = copy.deepcopy(spec)
    changed["components"]["schemas"]["DownloadResponse"] = copy.deepcopy(
        changed["components"]["schemas"]["DownloadExecutionResult"]
    )
    mutations.append(("allow UNCONFIRMED as 200", changed))

    for label, changed in mutations:
        try:
            run_checks(changed, quiet=True)
        except AssertionError:
            print(f"PASS mutation rejected: {label}")
        else:
            raise AssertionError(f"mutation escaped checks: {label}")


def main() -> None:
    spec = yaml.safe_load(OPENAPI.read_text())
    run_checks(spec)
    check_mutations(spec)
    print("PASS summary: 49 targets (19/15/1/3/11), 38 migrated, 11 retained, 29 AC, 10 new errors, 3 new paths")


if __name__ == "__main__":
    main()
