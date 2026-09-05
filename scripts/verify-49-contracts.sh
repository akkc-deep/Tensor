#!/bin/sh
set -eu
set +x
umask 077

fail() {
  printf 'M14-T04 failed: %s\n' "$1" >&2
  exit 1
}

maven_failure() {
  printf 'M14-T04 failed: maven-contracts\n' >&2
  return "$1"
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || fail script-directory
repository=$(CDPATH= cd -- "$script_dir/.." && pwd -P) || fail repository-root
manifest="$repository/docs/data-template/manifest.json"
maven_repository=${M14_MAVEN_REPO:-/private/tmp/tensor-m2}

case "$maven_repository" in
  /*) ;;
  *) fail maven-repository-path ;;
esac

for command in git tar mvn java python3 docker; do
  command -v "$command" >/dev/null 2>&1 || fail "missing-$command"
done
[ -d "$maven_repository" ] || fail maven-repository-missing
[ "$(git -C "$repository" branch --show-current)" = main ] || fail branch
head_commit=$(git -C "$repository" rev-parse HEAD) || fail git-head

protected_status() {
  git -C "$repository" status --short --untracked-files=all -- \
    ':(glob)data-plane/**/pom.xml' \
    ':(glob)data-plane/**/src/**' \
    control-plane/src control-plane/index.html \
    control-plane/package.json control-plane/package-lock.json \
    control-plane/vite.config.js control-plane/vitest.config.js \
    docs/data-template
}

before_status=$(protected_status) || fail input-status
if [ -n "$before_status" ]; then
  printf '%s\n' "$before_status" >&2
  fail tracked-input-drift
fi

java_major=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
[ "$java_major" = 21 ] || fail java-version

owned_root=$(mktemp -d "${TMPDIR:-/private/tmp}/tensor-m14-t04.XXXXXXXX") || fail temporary-directory
chmod 700 "$owned_root" || fail temporary-permissions
snapshot="$owned_root/snapshot"
archive="$owned_root/source.tar"
helper="$owned_root/contract_gate.py"
maven_log="$owned_root/maven.log"
maven_status="$owned_root/maven-status.json"
report_summary="$owned_root/report-summary.json"
resource_summary="$owned_root/resource-summary.json"
evidence="$owned_root/verification.json"
owned_containers="$owned_root/docker-owned.txt"

set +e
maven_failure 37 2>"$owned_root/maven-failure-probe.log"
maven_failure_probe=$?
set -e
[ "$maven_failure_probe" -eq 37 ] || fail maven-exit-probe
[ "$(cat "$owned_root/maven-failure-probe.log")" = 'M14-T04 failed: maven-contracts' ] || fail maven-diagnostic-probe

cat >"$helper" <<'PY'
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import warnings
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree as ET

MANIFEST_SHA = "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2"
APIS = (
    "stock_basic stock_company hs_const income balancesheet cashflow fina_indicator fina_audit "
    "fina_mainbz stk_rewards stk_holdernumber broker_recommend trade_cal margin daily weekly monthly "
    "adj_factor suspend_d daily_basic moneyflow stk_limit moneyflow_hsgt hsgt_top10 hk_hold top_list "
    "top_inst margin_detail block_trade slb_len slb_sec slb_sec_detail forecast express dividend "
    "disclosure_date repurchase share_float stk_holdertrade top10_holders top10_floatholders new_share "
    "namechange stk_managers pledge_stat pledge_detail index_classify index_member index_member_all"
).split()
REPORTS = (
    (
        "tensor-plugin-tushare/target/surefire-reports/"
        "TEST-com.akkc.tensor.plugin.tushare.metadata.TushareMetadataContractTest.xml",
        "com.akkc.tensor.plugin.tushare.metadata.TushareMetadataContractTest",
        50,
        {"hasExactManifestAndExpectationCoverage": 1, "matchesIndependentContract": 49},
    ),
    (
        "tensor-app/target/surefire-reports/"
        "TEST-com.akkc.tensor.db.FlywaySchemaContractIT.xml",
        "com.akkc.tensor.db.FlywaySchemaContractIT",
        52,
        {
            "productionSchemasMatchDatasetDefinitions": 49,
            "migratesAndValidatesRepeatablyOnMySql846": 1,
            "fixtureSchemaMatchesContract": 1,
            "keepsV6InTestOutputOnly": 1,
        },
    ),
    (
        "tensor-app/target/failsafe-reports/"
        "TEST-com.akkc.tensor.build.PackagedJarContractTest.xml",
        "com.akkc.tensor.build.PackagedJarContractTest",
        4,
        {
            "packagesOnlyTheProductionExecutableJarAndItsContractedContents": 1,
            "rejectsNestedTushareYamlPaths": 1,
            "rejectsDuplicateArchiveEntries": 1,
            "rejectsMalformedUtf8": 1,
        },
    ),
)


class GateError(Exception):
    pass


def check(condition, name):
    if not condition:
        raise GateError(name)


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def exact_manifest(path, enforce_hash=True):
    path = Path(path)
    raw = path.read_bytes()
    if enforce_hash:
        check(hashlib.sha256(raw).hexdigest() == MANIFEST_SHA, "manifest-hash")
    try:
        root = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise GateError("manifest-json") from error
    check(isinstance(root, dict), "manifest-object")
    entries = root.get("interfaces")
    check(isinstance(entries, list) and len(entries) == 49, "manifest-count")
    names = []
    filenames = []
    for entry in entries:
        check(isinstance(entry, dict), "manifest-entry")
        name = entry.get("api_name")
        filename = entry.get("filename")
        check(isinstance(name, str) and re.fullmatch(r"[a-z][a-z0-9_]{1,63}", name), "manifest-api")
        check(filename == f"{name}.json", "manifest-filename")
        check(entry.get("query_mode") in {"trade_date", "ann_date", "snapshot", "range"}, "manifest-query-mode")
        params = entry.get("params", [])
        check(isinstance(params, list), "manifest-params")
        for sample in params:
            check(isinstance(sample, dict), "manifest-param-object")
            check(all(re.fullmatch(r"[a-z][a-z0-9_]{1,63}", key) for key in sample), "manifest-param-key")
        names.append(name)
        filenames.append(filename)
    check(len(set(names)) == 49 and len(set(filenames)) == 49, "manifest-unique")
    check(set(names) == set(APIS), "manifest-api-set")
    return entries


def integer_attribute(root, name):
    try:
        return int(root.attrib[name])
    except (KeyError, TypeError, ValueError) as error:
        raise GateError(f"report-{name}") from error


def method_name(case_name, allowed):
    matches = [name for name in allowed if case_name == name or case_name.startswith(name + "(")]
    check(len(matches) == 1, "report-method")
    return matches[0]


def validate_report(path, class_name, expected_tests, methods):
    path = Path(path)
    check(path.is_file(), "report-missing")
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise GateError("report-xml") from error
    check(root.tag.rsplit("}", 1)[-1] == "testsuite", "report-root")
    check(root.attrib.get("name") == class_name, "report-suite-name")
    cases = root.findall(".//testcase")
    check(len(cases) == expected_tests, "report-case-count")
    check(integer_attribute(root, "tests") == expected_tests, "report-tests")
    for attribute in ("failures", "errors", "skipped"):
        check(integer_attribute(root, attribute) == 0, f"report-{attribute}")
    check(not root.findall(".//failure"), "report-failure-node")
    check(not root.findall(".//error"), "report-error-node")
    check(not root.findall(".//skipped"), "report-skipped-node")
    identities = [(case.attrib.get("name"), case.attrib.get("classname")) for case in cases]
    check(all(name and owner == class_name for name, owner in identities), "report-case-identity")
    check(len(set(identities)) == expected_tests, "report-duplicate-case")
    actual_methods = {name: 0 for name in methods}
    for name, _ in identities:
        actual_methods[method_name(name, methods)] += 1
    check(actual_methods == methods, "report-method-counts")
    return {
        "path": str(path),
        "sha256": sha256(path),
        "className": class_name,
        "tests": len(cases),
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "methods": actual_methods,
    }


def validate_failsafe_summary(path):
    path = Path(path)
    check(path.is_file(), "failsafe-summary-missing")
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise GateError("failsafe-summary-xml") from error
    values = {}
    for name in ("completed", "errors", "failures", "skipped"):
        node = root.find(name)
        try:
            values[name] = int((node.text or "").strip())
        except (AttributeError, TypeError, ValueError) as error:
            raise GateError(f"failsafe-summary-{name}") from error
    check(values == {"completed": 4, "errors": 0, "failures": 0, "skipped": 0}, "failsafe-summary-counts")
    failure_message = root.find("failureMessage")
    if failure_message is not None:
        check(not "".join(failure_message.itertext()).strip(), "failsafe-summary-message")
    return values


def validate_reports(data_plane, output):
    data_plane = Path(data_plane)
    expected_by_directory = {}
    for relative, _, _, _ in REPORTS:
        report_path = Path(relative)
        expected_by_directory.setdefault(report_path.parent, set()).add(report_path.name)
    for directory, expected in expected_by_directory.items():
        actual = {path.name for path in (data_plane / directory).glob("TEST-*.xml") if path.is_file()}
        check(actual == expected, "report-suite-files")
    reports = [validate_report(data_plane / relative, class_name, count, methods)
               for relative, class_name, count, methods in REPORTS]
    summary_path = data_plane / "tensor-app/target/failsafe-reports/failsafe-summary.xml"
    summary = validate_failsafe_summary(summary_path)
    result = {
        "reports": reports,
        "failsafeSummary": {"path": str(summary_path), "sha256": sha256(summary_path), **summary},
    }
    Path(output).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(output, 0o600)
    return result


def record_generated_report_counts(data_plane, status_path):
    data_plane = Path(data_plane)
    status_path = Path(status_path)
    status = json.loads(status_path.read_text(encoding="utf-8"))
    check(isinstance(status.get("exitCode"), int), "maven-status-exit-code")
    generated = []
    for relative, _, _, _ in REPORTS:
        path = data_plane / relative
        item = {"path": relative, "exists": path.is_file()}
        if path.is_file():
            try:
                root = ET.parse(path).getroot()
                item.update({name: int(root.attrib[name])
                             for name in ("tests", "failures", "errors", "skipped")})
                item["xmlReadable"] = True
            except (ET.ParseError, OSError, KeyError, TypeError, ValueError):
                item["xmlReadable"] = False
        generated.append(item)
    status["generatedReports"] = generated
    status_path.write_text(json.dumps(status, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(status_path, 0o600)
    return status


def unique_zip(source, name):
    try:
        archive = zipfile.ZipFile(source)
    except (OSError, zipfile.BadZipFile) as error:
        raise GateError(f"{name}-zip") from error
    entries = archive.namelist()
    check(len(entries) == len(set(entries)), f"{name}-duplicate-entry")
    return archive


def validate_resources(snapshot, entries, output):
    snapshot = Path(snapshot)
    api_names = [entry["api_name"] for entry in entries]
    expected_names = {f"{api}.yaml" for api in api_names}
    source_dir = snapshot / "data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro"
    check(source_dir.is_dir(), "source-yaml-directory")
    source_files = [path for path in source_dir.rglob("*") if path.is_file()]
    check(all(path.parent == source_dir and path.suffix == ".yaml" for path in source_files), "source-yaml-layout")
    check({path.name for path in source_files} == expected_names and len(source_files) == 49, "source-yaml-set")
    source_hashes = {path.name: sha256(path) for path in source_files}

    jar_path = snapshot / "data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar"
    check(jar_path.is_file(), "production-jar-missing")
    with unique_zip(jar_path, "production-jar") as outer:
        nested_name = "BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar"
        check(nested_name in outer.namelist(), "tushare-library-missing")
        direct = [name for name in outer.namelist()
                  if name.startswith("datasets/tushare_pro/") and not name.endswith("/")]
        check(not direct, "outer-tushare-resource-copy")
        packaged_hashes = None
        for name in outer.namelist():
            if not (name.startswith("BOOT-INF/lib/") and name.endswith(".jar")):
                continue
            with unique_zip(io.BytesIO(outer.read(name)), "nested-library") as nested:
                resources = [item for item in nested.namelist()
                             if item.startswith("datasets/tushare_pro/") and not item.endswith("/")]
                if name != nested_name:
                    check(not resources, "other-library-resource-copy")
                    continue
                expected_paths = {f"datasets/tushare_pro/{file_name}" for file_name in expected_names}
                check(set(resources) == expected_paths and len(resources) == 49, "packaged-yaml-set")
                packaged_hashes = {
                    Path(resource).name: hashlib.sha256(nested.read(resource)).hexdigest()
                    for resource in resources
                }
        check(packaged_hashes is not None, "packaged-yaml-missing")
        check(packaged_hashes == source_hashes, "packaged-yaml-bytes")
    result = {
        "productionJar": {"path": str(jar_path), "sha256": sha256(jar_path)},
        "sourceYaml": {"count": len(source_files), "names": sorted(expected_names)},
        "packagedYaml": {"count": len(packaged_hashes), "location": nested_name},
    }
    Path(output).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(output, 0o600)
    return result


def write_suite(path, class_name, methods, *, tests=None, failures=0, errors=0, skipped=0,
                duplicate=False, node=None):
    names = []
    for method, count in methods.items():
        names.extend([method if count == 1 else f"{method}()[{index}]" for index in range(1, count + 1)])
    if duplicate and len(names) > 1:
        names[-1] = names[0]
    root = ET.Element("testsuite", name=class_name, tests=str(len(names) if tests is None else tests),
                      failures=str(failures), errors=str(errors), skipped=str(skipped))
    for index, name in enumerate(names):
        case = ET.SubElement(root, "testcase", name=name, classname=class_name)
        if index == 0 and node:
            ET.SubElement(case, node)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def make_valid_reports(root):
    data_plane = Path(root)
    for relative, class_name, count, methods in REPORTS:
        path = data_plane / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        write_suite(path, class_name, methods)
    summary = data_plane / "tensor-app/target/failsafe-reports/failsafe-summary.xml"
    summary.write_text(
        "<failsafe-summary><completed>4</completed><errors>0</errors><failures>0</failures>"
        "<skipped>0</skipped><failureMessage /></failsafe-summary>", encoding="utf-8")


def make_valid_resources(root):
    root = Path(root)
    source = root / "data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro"
    source.mkdir(parents=True)
    for api in APIS:
        (source / f"{api}.yaml").write_bytes(f"contract:{api}\n".encode())
    inner_buffer = io.BytesIO()
    with zipfile.ZipFile(inner_buffer, "w") as inner:
        inner.writestr("datasets/tushare_pro/", b"")
        for api in APIS:
            inner.write(source / f"{api}.yaml", f"datasets/tushare_pro/{api}.yaml")
    jar = root / "data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar"
    jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(jar, "w") as outer:
        outer.writestr("BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar", inner_buffer.getvalue())


def rejects(action, name):
    try:
        action()
    except GateError:
        return
    raise GateError(f"probe-accepted-{name}")


def self_probe(root):
    probe_root = Path(root) / "synthetic-probes"
    if probe_root.exists():
        shutil.rmtree(probe_root)
    probe_root.mkdir()
    valid = probe_root / "reports"
    make_valid_reports(valid)
    validate_reports(valid, probe_root / "valid-reports.json")
    maven_status = probe_root / "maven-status.json"
    maven_status.write_text('{"exitCode": 37}\n', encoding="utf-8")
    recorded = record_generated_report_counts(valid, maven_status)
    check(recorded["exitCode"] == 37, "probe-maven-exit-code")
    check([item.get("tests") for item in recorded["generatedReports"]] == [50, 52, 4],
          "probe-generated-report-counts")
    first_relative, class_name, _, methods = REPORTS[0]
    first = valid / first_relative

    missing = probe_root / "missing"
    shutil.copytree(valid, missing)
    (missing / first_relative).unlink()
    rejects(lambda: validate_reports(missing, probe_root / "missing.json"), "missing-report")

    for name, mutate in (
        ("zero-tests", lambda path: write_suite(path, class_name, methods, tests=0)),
        ("skipped", lambda path: write_suite(path, class_name, methods, skipped=1, node="skipped")),
        ("duplicate", lambda path: write_suite(path, class_name, methods, duplicate=True)),
        ("failure", lambda path: write_suite(path, class_name, methods, failures=1, node="failure")),
        ("count-mismatch", lambda path: write_suite(path, class_name, methods, tests=51)),
    ):
        target = probe_root / name
        shutil.copytree(valid, target)
        mutate(target / first_relative)
        rejects(lambda target=target: validate_reports(target, probe_root / f"{name}.json"), name)
    malformed = probe_root / "malformed"
    shutil.copytree(valid, malformed)
    (malformed / first_relative).write_text("<testsuite>", encoding="utf-8")
    rejects(lambda: validate_reports(malformed, probe_root / "malformed.json"), "malformed-xml")

    extra = probe_root / "extra-suite"
    shutil.copytree(valid, extra)
    extra_report = extra / Path(first_relative).parent / "TEST-extra-target-suite.xml"
    write_suite(extra_report, class_name, methods)
    rejects(lambda: validate_reports(extra, probe_root / "extra-suite.json"), "extra-target-suite")

    resources = probe_root / "resources"
    make_valid_resources(resources)
    synthetic_entries = [{"api_name": api} for api in APIS]
    validate_resources(resources, synthetic_entries, probe_root / "valid-resources.json")

    wrong = probe_root / "wrong-api"
    shutil.copytree(resources, wrong)
    source = wrong / "data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro"
    (source / f"{APIS[-1]}.yaml").rename(source / "wrong_api.yaml")
    rejects(lambda: validate_resources(wrong, synthetic_entries, probe_root / "wrong.json"), "wrong-api")

    nested = probe_root / "nested-yaml"
    shutil.copytree(resources, nested)
    extra = nested / "data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/nested/extra.yaml"
    extra.parent.mkdir()
    extra.write_text("extra\n", encoding="utf-8")
    rejects(lambda: validate_resources(nested, synthetic_entries, probe_root / "nested.json"), "nested-yaml")

    duplicate = probe_root / "duplicate-zip"
    shutil.copytree(resources, duplicate)
    jar = duplicate / "data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar"
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(jar, "a") as outer:
            outer.writestr("BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar", b"duplicate")
    rejects(lambda: validate_resources(duplicate, synthetic_entries, probe_root / "duplicate.json"), "duplicate-zip")
    container_log = probe_root / "containers.log"
    container_log.write_text(
        f"Container testcontainers/ryuk:0.12.0 is starting: {'1' * 64}\n"
        f"Container mysql:8.4.6 is starting: {'2' * 64}\n"
        f"Container unrelated/image:1 is starting: {'3' * 64}\n",
        encoding="ascii",
    )
    baseline = probe_root / "container-baseline.txt"
    baseline.write_text(f"{'3' * 64}\n", encoding="ascii")
    check(
        write_owned_container_ids(container_log, baseline, probe_root / "owned-containers.txt", 0) == ['1' * 64, '2' * 64],
        "probe-owned-container-identities",
    )
    missing_log = probe_root / "missing-container.log"
    missing_log.write_text(
        f"Container testcontainers/ryuk:0.12.0 is starting: {'1' * 64}\n",
        encoding="ascii",
    )
    rejects(
        lambda: write_owned_container_ids(missing_log, baseline, probe_root / "missing-containers.txt", 0),
        "missing-owned-container",
    )
    check(
        write_owned_container_ids(missing_log, baseline, probe_root / "partial-containers.txt", 37) == ['1' * 64],
        "probe-partial-failure-containers",
    )
    empty_log = probe_root / "empty-container.log"
    empty_log.write_text("", encoding="ascii")
    check(
        write_owned_container_ids(empty_log, baseline, probe_root / "empty-containers.txt", 37) == [],
        "probe-empty-failure-containers",
    )
    overlapping_baseline = probe_root / "overlapping-baseline.txt"
    overlapping_baseline.write_text(f"{'1' * 64}\n", encoding="ascii")
    rejects(
        lambda: write_owned_container_ids(container_log, overlapping_baseline,
                                          probe_root / "overlapping-containers.txt", 0),
        "preexisting-owned-container",
    )
    return 11


def run_maven(snapshot, repository, log_path, status_path):
    allowed = {
        "PATH", "HOME", "JAVA_HOME", "TMPDIR", "LANG", "LC_ALL",
        "DOCKER_HOST", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH",
        "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "TESTCONTAINERS_HOST_OVERRIDE",
    }
    environment = {key: value for key, value in os.environ.items() if key in allowed}
    command = [
        "mvn", "-B", "-ntp", f"-Dmaven.repo.local={repository}",
        "-f", str(Path(snapshot) / "data-plane/pom.xml"), "-pl", "tensor-app", "-am",
        "-Dtest=TushareMetadataContractTest,FlywaySchemaContractIT",
        "-Dsurefire.failIfNoSpecifiedTests=false", "verify",
    ]
    started = datetime.now(timezone.utc).isoformat()
    with Path(log_path).open("xb") as log:
        process = subprocess.run(command, cwd=snapshot, env=environment, stdin=subprocess.DEVNULL,
                                 stdout=log, stderr=subprocess.STDOUT, check=False)
    os.chmod(log_path, 0o600)
    status = {
        "startedAt": started,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "exitCode": process.returncode,
    }
    Path(status_path).write_text(json.dumps(status, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(status_path, 0o600)
    return process.returncode


def write_owned_container_ids(log_path, baseline_path, output, maven_exit):
    text = Path(log_path).read_text(encoding="utf-8", errors="replace")
    pattern = re.compile(
        r"Container (testcontainers/ryuk:0\.12\.0|mysql:8\.4\.6) is starting: ([0-9a-f]{64})"
    )
    by_image = {}
    for image, container_id in pattern.findall(text):
        by_image.setdefault(image, set()).add(container_id)
    identities = {container_id for ids in by_image.values() for container_id in ids}
    baseline = set(Path(baseline_path).read_text(encoding="ascii").splitlines())
    if maven_exit == 0:
        check(set(by_image) == {"testcontainers/ryuk:0.12.0", "mysql:8.4.6"}, "owned-container-images")
        check(all(len(ids) == 1 for ids in by_image.values()), "owned-container-identities")
        check(not baseline.intersection(identities), "owned-container-preexisting")
    identities = sorted(identities - baseline)
    Path(output).write_text("".join(f"{container_id}\n" for container_id in identities), encoding="ascii")
    os.chmod(output, 0o600)
    return identities


def main():
    command = sys.argv[1]
    if command == "preflight":
        entries = exact_manifest(sys.argv[2])
        probes = self_probe(sys.argv[3])
        print(json.dumps({"manifestCount": len(entries), "syntheticRejections": probes}))
    elif command == "maven":
        sys.exit(run_maven(*sys.argv[2:6]))
    elif command == "post-maven":
        record_generated_report_counts(*sys.argv[2:4])
    elif command == "reports":
        validate_reports(sys.argv[2], sys.argv[3])
    elif command == "resources":
        entries = exact_manifest(sys.argv[3])
        validate_resources(sys.argv[2], entries, sys.argv[4])
    elif command == "containers":
        write_owned_container_ids(sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5]))
    else:
        raise GateError("unknown-command")


if __name__ == "__main__":
    try:
        main()
    except GateError as error:
        print(f"M14-T04 gate rejected: {error}", file=sys.stderr)
        sys.exit(1)
PY
chmod 600 "$helper" || fail helper-permissions

preflight=$(python3 "$helper" preflight "$manifest" "$owned_root") || fail synthetic-contract-probes
[ "$preflight" = '{"manifestCount": 49, "syntheticRejections": 11}' ] || fail preflight-result

git -C "$repository" archive --format=tar --output="$archive" "$head_commit" || fail git-archive
mkdir "$snapshot" || fail snapshot-directory
tar -xf "$archive" -C "$snapshot" || fail snapshot-extract
[ "$(python3 - "$snapshot/docs/data-template/manifest.json" <<'PY'
import hashlib, sys
print(hashlib.sha256(open(sys.argv[1], 'rb').read()).hexdigest())
PY
)" = 37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2 ] || fail snapshot-manifest

docker_before="$owned_root/docker-before.txt"
docker_after="$owned_root/docker-after.txt"
docker ps -aq --no-trunc | sort >"$docker_before" || fail docker-inventory-before
chmod 600 "$docker_before"

set +e
python3 "$helper" maven "$snapshot" "$maven_repository" "$maven_log" "$maven_status"
maven_exit=$?
set -e
python3 "$helper" post-maven "$snapshot/data-plane" "$maven_status" || fail maven-status-evidence

set +e
python3 "$helper" reports "$snapshot/data-plane" "$report_summary"
report_exit=$?
python3 "$helper" resources "$snapshot" "$snapshot/docs/data-template/manifest.json" "$resource_summary"
resource_exit=$?
set -e
python3 "$helper" containers "$maven_log" "$docker_before" "$owned_containers" "$maven_exit" || fail owned-container-evidence

docker_deadline=$(( $(date +%s) + 30 ))
while :; do
  docker ps -aq --no-trunc | sort >"$docker_after" || fail docker-inventory-after
  remaining_containers=$(comm -12 "$owned_containers" "$docker_after")
  [ -z "$remaining_containers" ] && break
  [ "$(date +%s)" -ge "$docker_deadline" ] && break
  sleep 1
done
chmod 600 "$docker_after"
if [ -n "$remaining_containers" ]; then
  printf '%s\n' "$remaining_containers" >"$owned_root/docker-leftovers.txt"
  chmod 600 "$owned_root/docker-leftovers.txt"
  while IFS= read -r container; do
    [ -n "$container" ] && docker rm -f "$container" >/dev/null 2>&1 || true
  done <"$owned_root/docker-leftovers.txt"
  fail owned-container-leftover
fi

if [ "$maven_exit" -ne 0 ]; then
  maven_failure "$maven_exit" || exit $?
fi
[ "$report_exit" -eq 0 ] || fail report-contracts
[ "$resource_exit" -eq 0 ] || fail resource-contracts

after_status=$(protected_status) || fail final-input-status
if [ "$after_status" != "$before_status" ]; then
  printf '%s\n' "$after_status" >&2
  fail input-changed-during-run
fi

python3 - "$repository" "$head_commit" "$manifest" "$maven_status" "$report_summary" \
  "$resource_summary" "$evidence" <<'PY'
import hashlib, json, os, sys
from pathlib import Path

repository, head, manifest, maven_path, reports_path, resources_path, output = sys.argv[1:]

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

task_files = [
    "scripts/verify-49-contracts.sh",
    "control-plane/e2e/tushare-metadata.spec.js",
    "docs/verification/M14-T04-49-contracts.md",
]
for relative in task_files:
    if not (Path(repository) / relative).is_file():
        raise SystemExit("M14-T04 evidence rejected: task-file-missing")

result = {
    "version": 1,
    "task": "M14-T04",
    "head": head,
    "taskFiles": {relative: sha(Path(repository) / relative) for relative in task_files},
    "manifest": {"path": "docs/data-template/manifest.json", "sha256": sha(manifest), "count": 49},
    "maven": json.loads(Path(maven_path).read_text(encoding="utf-8")),
    "reports": json.loads(Path(reports_path).read_text(encoding="utf-8")),
    "resources": json.loads(Path(resources_path).read_text(encoding="utf-8")),
    "contracts": {
        "sourceYaml": 49,
        "productionTables": 49,
        "packagedYaml": 49,
        "fixtureAdditionalTables": 1,
        "tableEvidence": "successful FlywaySchemaContractIT result-level assertions",
        "fixtureTotals": {"businessTables": 50, "totalColumns": 1007, "primaryKeys": 50},
    },
    "syntheticRejections": 11,
}
Path(output).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.chmod(output, 0o600)
PY

printf 'M14-T04 contracts passed: metadata=50 schema=52 package=4 yaml=49 tables=49 packaged=49\n'
printf 'M14-T04 private evidence: %s\n' "$evidence"
