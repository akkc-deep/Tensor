#!/bin/sh
set -eu
set +x
umask 077
case $#:$* in 0:|1:--self-test) ;; *) printf '%s\n' 'security gate: invalid arguments' >&2; exit 2 ;; esac
owned_root=$(mktemp -d /private/tmp/tensor-m14-t07.XXXXXXXX)
chmod 700 "$owned_root"
if [ "${1:-}" = --self-test ]; then
    trap 'rm -rf "$owned_root"' 0
fi
cat >"$owned_root/gate.py" <<'PY'
import base64
import copy
import hashlib
import http.client
import io
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import stat
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class GateError(Exception):
    def __init__(self, label, details=None):
        super().__init__(label)
        self.details = details

def require(value, label):
    if not value:
        raise GateError(label)

def docker_absence_verdict(exit_code, output, kind, identifier):
    prefix,suffix = {
        'container':(rb'(?i:error: no such object: |error response from daemon: no such container: )',b''),
        'volume':(rb'(?i:error response from daemon: get )',rb'(?i:: no such volume)'),
    }[kind]
    require(exit_code == 1 and re.fullmatch(prefix + re.escape(identifier.encode()) + suffix,output.strip()),'cleanup-' + kind + '-absence-unconfirmed')

def patterns(secrets):
    result = set()
    for secret in secrets:
        if not secret:
            continue
        raw = secret.encode()
        result.update((raw, json.dumps(secret)[1:-1].encode(),
                       urllib.parse.quote(secret, safe='').encode(),
                       urllib.parse.quote_plus(secret, safe='').encode(),
                       base64.b64encode(raw), base64.urlsafe_b64encode(raw),
                       ''.join('\\u%04x' % ord(c) for c in secret).encode(),
                       ''.join('%%%02X' % c for c in raw).encode(),
                       ''.join('%%%02x' % c for c in raw).encode()))
    return tuple(result)

HEADERS = {
    'content-security-policy': "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'",
    'x-content-type-options': 'nosniff', 'x-frame-options': 'DENY',
    'referrer-policy': 'no-referrer',
    'permissions-policy': 'camera=(), microphone=(), geolocation=()',
    'cross-origin-opener-policy': 'same-origin',
}
TEST_CLASSES = ('ModuleDependencyTest', 'ForbiddenGitCapabilityTest', 'ObservabilityTest',
                'ProductionWebConfigurationTest', 'QuerySqlFactoryTest', 'UpsertSqlFactoryTest',
                'DatasetControllerIT')
REQUIRED = ('preflight', 'source_scan', 'jar_scan', 'maven', 'backend_audit', 'frontend_audit', 'browser',
            'database_setup', 'startup', 'S01', 'S02', 'S03', 'S04', 'S05', 'S06', 'S07', 'S08',
            'stub', 'database_scan', 'log_scan', 'artifact_scan', 'identity_final', 'cleanup', 'report_scan')
HTML = '<img src=x onerror="window.__m14_t07_xss=1"><script>window.__m14_t07_xss=1</script>'
FORBIDDEN = ('M14_T07_UPSTREAM_DETAIL', "x' OR 1=1 --", '1 OR 1=1', 'SELECT 1',
             'jdbc:mysql:', 'java.sql.', 'SQLException', 'org.springframework.',
             '/Users/', '/private/tmp/', 'BOOT-INF/classes')
PRIVATE_LOG_FORBIDDEN = ('M14_T07_UPSTREAM_DETAIL', "x' OR 1=1 --", '1 OR 1=1', 'SELECT 1')

# These decision functions are shared by the offline counterexamples and runtime.
def scan_bytes(data, secret_patterns):
    require(isinstance(data, bytes), 'scan-bytes-invalid')
    hits = sum(data.count(secret) for secret in secret_patterns)
    if hits:
        raise GateError('secret-detected', {'hits': hits})
    return 0

def reflection_verdict(data, submitted, response_body=None, response_headers=()):
    for value in submitted:
        encoded = patterns([value])
        if len(value) > 8:
            require(not any(pattern in data for pattern in encoded),'submitted-value-reflected')
            continue
        # Short values need context. Only validated transport counters and request
        # IDs are metadata; the full credential scan still covers every header.
        body = response_body if response_body is not None else data
        try:
            body = json.loads(body)
        except (ValueError,UnicodeError):
            body = body.decode(errors='replace')
        def reflected(item):
            if isinstance(item,dict):
                for key,child in item.items():
                    if key == 'requestId' and isinstance(child,str) and re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}',child):
                        continue
                    if reflected(key) or reflected(child):
                        return True
                return False
            if isinstance(item,list):
                return any(reflected(child) for child in item)
            content = str(item).encode()
            return any(re.search((rb'(?<![0-9])' if pattern.isdigit() else rb'(?<![A-Za-z0-9])') + re.escape(pattern) + (rb'(?![0-9])' if pattern.isdigit() else rb'(?![A-Za-z0-9])'),content) for pattern in encoded)
        require(not reflected(body),'submitted-value-reflected')
        for name,content in response_headers:
            if name.lower() in ('content-length','age','retry-after') and re.fullmatch(r'[0-9]+',content):
                continue
            if name.lower() == 'x-request-id' and re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}',content):
                continue
            require(not reflected(name) and not reflected(content),'submitted-value-reflected')

def scan_file(path, secret_patterns):
    try:
        info = path.lstat()
        require(stat.S_ISREG(info.st_mode) and info.st_mode & 0o444, 'scan-file-unreadable')
        overlap = max(map(len, secret_patterns), default=1) - 1
        tail = b''
        with path.open('rb') as stream:
            while True:
                data = stream.read(1024 * 1024)
                if not data:
                    break
                scan_bytes(tail + data, secret_patterns)
                tail = (tail + data)[-overlap:] if overlap else b''
    except (OSError, ValueError):
        raise GateError('scan-file-unreadable') from None
    return 0

def scan_artifact_file(path, owned, secret_patterns):
    if not path.is_symlink():
        scan_file(path,secret_patterns)
        return path
    modules = owned/'snapshot/control-plane/node_modules'
    try:
        require(path.parent == modules/'.bin' and path.lstat().st_uid == os.getuid(),'artifact-link-unexpected')
        link = os.readlink(path)
        scan_bytes(str(path.relative_to(owned)).encode() + b'\n' + os.fsencode(link),secret_patterns)
        require(not os.path.isabs(link),'artifact-link-escape')
        target = path.resolve(strict=True)
        require(target.is_relative_to(modules.resolve(strict=True)) and target.stat().st_uid == os.getuid(),'artifact-link-escape')
        scan_file(target,secret_patterns)
        return target
    except (OSError,RuntimeError):
        raise GateError('artifact-link-invalid') from None

def scan_zip(data, secret_patterns, visit=None, depth=0):
    require(depth < 12, 'zip-recursion-limit')
    count = 0
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            require(bool(archive.infolist()), 'zip-empty')
            for item in archive.infolist():
                scan_bytes(item.filename.encode(), secret_patterns)
                if item.is_dir():
                    continue
                require(item.file_size < 512 * 1024 * 1024, 'zip-entry-too-large')
                require(not stat.S_ISLNK(item.external_attr >> 16), 'zip-symlink')
                content = archive.read(item)
                scan_bytes(content, secret_patterns)
                count += 1
                if visit:
                    visit(item.filename, content, depth)
                if item.filename.lower().endswith(('.jar', '.zip')):
                    count += scan_zip(content, secret_patterns, visit, depth + 1)
    except (OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError, EOFError):
        raise GateError('zip-invalid') from None
    return count

def query_verdict(status, body):
    require(status == 400 and isinstance(body, dict) and body.get('code') == 'PARAM_INVALID', 'parameter-not-rejected')

def method_verdict(status):
    require(status == 405, 'write-method-not-rejected')

def path_verdict(status):
    require(status in (400, 404), 'identifier-not-rejected')

def normalize_headers(entries):
    headers = {}
    critical = set(HEADERS) | {'cache-control','access-control-allow-origin','access-control-allow-credentials','x-request-id','content-type','location'}
    for name,value in entries:
        name = name.lower()
        require(name not in headers or name not in critical,'ambiguous-response-header')
        headers[name] = value
    return headers

def header_verdict(headers, path):
    require(all(headers.get(key) == value for key, value in HEADERS.items()), 'security-header-mismatch')
    require('access-control-allow-origin' not in headers and 'access-control-allow-credentials' not in headers, 'cors-permission-exposed')
    expected = 'public, max-age=31536000, immutable' if path.startswith('/assets/') else 'no-cache' if path in ('/downloads', '/datasets') else 'no-store'
    require(headers.get('cache-control') == expected, 'cache-policy-mismatch')

def actuator_verdict(status, body, health):
    if health:
        try:
            require(status == 200 and json.loads(body) == {'status':'UP'}, 'health-details-exposed')
        except (ValueError, UnicodeError):
            raise GateError('health-json-invalid') from None
    else:
        require(status == 404 and not re.search(br'<(?:!doctype|html|script|body)\b', body, re.I), 'actuator-exposed')

def health_structure(body):
    body = body if isinstance(body,dict) else {}
    return {'componentsPresent':'components' in body,'detailsPresent':'details' in body,'groupsPresent':'groups' in body,'extraFieldCount':len(set(body)-{'status'}),'statusUp':body.get('status') == 'UP'}

def scanner_diagnostics(output, exit_code, report_present):
    categories = []
    for label,pattern in [('nvd-invalid-api-key',br'Invalid API Key'),('nvd-http-failure',br'(?:NVD|nvd\.nist)[^\n]*(?:HTTP[^\n]*[45][0-9]{2})'),('cisa-http-403',br'cisa[^\n]*\b403\b'),('vulnerability-data-missing',br'NoDataException'),('assembly-analyzer-unavailable',br'Assembly Analyzer could not be initialized'),('node-lockfile-missing',br'No lock file exists[^\n]*false negatives'),('node-modules-missing',br'node_modules directory does not exist'),('oss-index-credentials-missing',br'OSS Index Analyzer disabled due to missing credentials')]:
        if re.search(pattern,output,re.I):
            categories.append(label)
    if not report_present:
        categories.append('report-missing')
    return {'scannerExitCode':exit_code,'reportPresent':report_present,'failureCategories':categories}

def completion_verdict(line):
    marker = 'tensor.operation.completed'
    require(marker in line,'completion-marker-missing')
    text = line.split(marker,1)[1].strip()
    fields, cursor = {}, 0
    for match in re.finditer(r'([A-Za-z]+)=(\[[^\]]*\]|\S+)',text):
        require(not text[cursor:match.start()].strip() and match[1] not in fields,'completion-fields-invalid')
        fields[match[1]] = match[2]
        cursor = match.end()
    require(not text[cursor:].strip(),'completion-trailing-content')
    operation = fields.get('operation')
    require(operation in ('download','query'),'completion-operation-invalid')
    common = {'requestId','operation','pluginId','apiName','durationMs','outcome','failureStage','errorCode'}
    counts = {'sourceRowCount','insertedRows','updatedRows'} if operation == 'download' else {'page','pageSize','resultCount','totalElements'}
    summary = 'paramSummary' if operation == 'download' else 'filterNames'
    require(set(fields) == common | counts | {summary},'completion-fields-not-exact')
    require(bool(re.fullmatch('[A-Za-z0-9-]{1,100}',fields['requestId'])) and fields['pluginId'] == 'tushare_pro' and fields['apiName'] == 'stock_company','completion-identity-invalid')
    allowed_summary = ('[exchange]',) if operation == 'download' else ('[]','[ts_code]')
    require(fields[summary] in allowed_summary,'completion-summary-discloses-values')
    require(fields['durationMs'].isdigit() and all(re.fullmatch(r'(?:[0-9]+|unavailable)',fields[key]) for key in counts),'completion-counters-invalid')
    require(fields['outcome'] in ('success','failure','empty') and fields['failureStage'] in ('none','parameter','registration','source','adapter','persistence','query') and re.fullmatch(r'(?:none|[A-Z][A-Z0-9_]*)',fields['errorCode']),'completion-outcome-invalid')
    return fields

def npm_verdict(report, exit_code):
    require(isinstance(report, dict) and not report.get('error') and report.get('auditReportVersion') == 2, 'npm-report-invalid')
    metadata = report.get('metadata', {})
    counts = metadata.get('vulnerabilities', {})
    coverage = metadata.get('dependencies', {})
    require(isinstance(report.get('vulnerabilities'), dict) and all(isinstance(counts.get(k), int) and counts[k] >= 0 for k in ('info','low','moderate','high','critical','total')), 'npm-counts-invalid')
    require(isinstance(coverage.get('total'), int) and coverage['total'] > 0 and coverage.get('prod', 0) > 0 and coverage.get('dev', 0) > 0, 'npm-coverage-empty')
    advisories = []
    for name, item in report['vulnerabilities'].items():
        require(isinstance(item, dict) and item.get('severity') in ('info','low','moderate','high','critical'), 'npm-severity-missing')
        require(bool(re.fullmatch(r'[@A-Za-z0-9_.\-/]+', name)), 'npm-package-invalid')
        advisories.append({'package':name, 'severity':item['severity'], 'advisoryIds':[v['source'] for v in item.get('via', []) if isinstance(v, dict) and isinstance(v.get('source'), int)]})
    summary = {'counts':{k:counts[k] for k in ('info','low','moderate','high','critical','total')}, 'coverage':{k:coverage[k] for k in ('prod','dev','optional','peer','peerOptional','total') if isinstance(coverage.get(k),int)}, 'advisories':advisories}
    if exit_code != 0 or counts['high'] or counts['critical'] or any(x['severity'] in ('high','critical') for x in advisories):
        raise GateError('npm-audit-failed', summary)
    return summary

def dependency_verdict(report, exit_code, inventory, modules):
    require(isinstance(report, dict), 'dependency-report-missing')
    info = report.get('scanInfo', {})
    require(info.get('engineVersion') == '13.0.0' and not info.get('analysisExceptions') and not report.get('analysisExceptions'), 'dependency-analysis-failed')
    date = report.get('projectInfo', {}).get('reportDate')
    sources = info.get('dataSource', [])
    require(isinstance(date, str) and bool(date) and isinstance(sources, list) and any('NVD' in s.get('name','').upper() and s.get('timestamp') for s in sources), 'dependency-update-unverified')
    deps = report.get('dependencies')
    require(isinstance(deps, list) and len(deps) > 0, 'dependency-coverage-empty')
    covered, hashes, refs, advisories, unresolved, high = {}, {}, set(), [], 0, 0
    entries = list(deps)
    for dep in entries:
        require(isinstance(dep, dict) and not dep.get('analysisExceptions'), 'dependency-analysis-failed')
        related = dep.get('relatedDependencies', [])
        require(isinstance(related, list), 'dependency-related-invalid')
        entries.extend(related)
        coordinates = []
        for package in dep.get('packages', dep.get('packageIds', [])):
            identifier = package.get('id', '')
            match = re.fullmatch(r'pkg:maven/([^/]+)/([^@]+)@([^?]+)(?:\?.*)?', identifier)
            if match:
                coordinate = ':'.join(urllib.parse.unquote(v) for v in match.groups())
                require(bool(re.fullmatch(r'[A-Za-z0-9_.:+\-]+', coordinate)), 'dependency-coordinate-invalid')
                coordinates.append(coordinate)
        filename = dep.get('fileName', '')
        if coordinates and filename:
            covered.setdefault(filename, set()).update(coordinates)
            hashes.setdefault(filename, set()).add(dep.get('sha256'))
        refs.update(str(ref) for ref in dep.get('projectReferences', []))
        for vuln in dep.get('vulnerabilities', []):
            severity = str(vuln.get('severity', '')).upper()
            scores = [vuln.get(key, {}).get(score) for key, score in [('cvssv2','score'),('cvssv3','baseScore'),('cvssv4','baseScore')]]
            scores = [float(score) for score in scores if isinstance(score, (int, float)) and 0 <= score <= 10]
            missing = severity not in ('LOW','MEDIUM','MODERATE','HIGH','CRITICAL','INFORMATIONAL','NONE')
            if missing and not scores:
                unresolved += 1
            risky = severity in ('HIGH','CRITICAL') or any(score >= 7 for score in scores)
            high += int(risky)
            identifier = vuln.get('name', '')
            require(bool(re.fullmatch(r'[A-Za-z0-9_.:\-]+', identifier)), 'dependency-advisory-invalid')
            advisories.append({'id':identifier, 'coordinates':coordinates, 'severity':severity if not missing else 'UNSPECIFIED', 'cvss':max(scores) if scores else None, 'severityAssessedByCvss':missing and bool(scores)})
    missing_jars = []
    mappings = []
    for filename, identity in inventory.items():
        coordinates = identity['coordinates']
        found = covered.get(filename, set())
        if not found or identity['sha256'] not in hashes.get(filename, set()) or (coordinates and not set(coordinates).issubset(found)):
            missing_jars.append(filename)
            continue
        if not coordinates and not any(filename == c.split(':')[1] + '-' + c.split(':')[2] + '.jar' for c in found):
            missing_jars.append(filename)
            continue
        mappings.append({'jar':filename, 'sha256':identity['sha256'], 'coordinates':sorted(found)})
    missing_modules = [module for module in modules if not any(module in ref for ref in refs)]
    require(bool(re.fullmatch(r'[0-9T: .+\-Z]+', date)), 'dependency-report-date-invalid')
    updates = []
    for source in sources:
        require(isinstance(source.get('name'),str) and re.fullmatch(r'[A-Za-z0-9 ()_.:\-]+',source['name']) and isinstance(source.get('timestamp'),str) and re.fullmatch(r'[A-Za-z0-9T: .+\-Z]+',source['timestamp']), 'dependency-update-fields-invalid')
        updates.append({'name':source['name'],'timestamp':source['timestamp']})
    summary = {'version':'13.0.0', 'reportDate':date, 'dataSources':updates, 'dependencies':len(deps), 'jarCoverage':mappings, 'missingJars':missing_jars, 'missingModules':missing_modules, 'highOrCritical':high, 'unassessedSeverity':unresolved, 'advisories':advisories}
    if exit_code != 0 or missing_jars or missing_modules or high or unresolved:
        raise GateError('dependency-audit-failed', summary)
    return summary

def test_verdict(reports, exit_code, enforcer):
    result = {}
    for file in reports:
        try:
            root = ET.parse(file).getroot()
            name = root.get('name', '').split('.')[-1]
            if name not in TEST_CLASSES:
                continue
            require(name not in result, 'test-report-duplicate')
            values = {key:int(root.get(key, '-1')) for key in ('tests','failures','errors','skipped')}
            require(values['tests'] > 0 and len(root.findall('testcase')) == values['tests'] and all(values[k] == 0 for k in ('failures','errors','skipped')), 'test-report-not-passing')
            require(not root.findall('.//failure') and not root.findall('.//error') and not root.findall('.//skipped'), 'test-report-not-passing')
            result[name] = values
        except (OSError, ValueError, ET.ParseError):
            raise GateError('test-report-invalid') from None
    require(set(result) == set(TEST_CLASSES) and exit_code == 0 and enforcer, 'tests-or-enforcer-incomplete')
    return {'classes':result, 'tests':sum(item['tests'] for item in result.values()), 'enforcer':True}

def final_verdict(report):
    checks = report.get('checks', {})
    require(report.get('cleanup') is True and all(isinstance(checks.get(name), dict) and checks[name].get('status') == 'pass' for name in REQUIRED), 'required-gates-incomplete')

def render_report(report, secret_patterns, directory):
    data = json.dumps(report, ensure_ascii=False, indent=2).encode()
    scan_bytes(data, secret_patterns)
    lines = ['# M14-T07 security verification', '', 'This document is generated from this run; failed and not-run checks prevent acceptance.', '', 'Invocation: `sh scripts/security/verify-release.sh`. `M14_SECURITY_JAR` selects the frozen input whose SHA-256 is recorded in `identities`; `PATH` and `JAVA_HOME` select the reported tool versions.', '', '```json', data.decode(), '```', '', 'Local loopback controls were measured only where marked pass. Remote HTTPS, an internal network or identity proxy, database TLS, a proxy response budget of at least 130 seconds, and a shutdown window covering all phases remain deployment requirements. M14-T08 release readiness is a separate decision.', '']
    markdown = '\n'.join(lines).encode()
    scan_bytes(markdown, secret_patterns)
    for name, content in [('outcome.json', data), ('evidence.md', markdown)]:
        destination = directory / name
        require(not destination.is_symlink(), 'report-symlink')
        destination.write_bytes(content)
        destination.chmod(0o600)
        scan_file(destination, secret_patterns)

def node_tool():
    installed = Path.home() / '.nvm/versions/node/v24.15.0/bin/node'
    return str(installed) if installed.is_file() else shutil.which('node') or 'node'

def top_fields(path):
    # Read only the public top-level fields value; skip other values as a stream.
    try:
        with path.open(encoding='utf-8') as stream:
            def char():
                value = stream.read(1)
                require(bool(value), 'template-truncated')
                return value
            def nonspace():
                value = char()
                while value.isspace():
                    value = char()
                return value
            def value(first, capture):
                result, stack, quoted, escaped = [], [], False, False
                current = first
                while True:
                    if capture:
                        result.append(current)
                        require(len(result) < 16384, 'template-fields-too-large')
                    if quoted:
                        if escaped:
                            escaped = False
                        elif current == '\\':
                            escaped = True
                        elif current == '"':
                            quoted = False
                    elif current == '"':
                        quoted = True
                    elif current in '[{':
                        stack.append(current)
                    elif current in ']}':
                        require(bool(stack), 'template-value-invalid')
                        stack.pop()
                    if not quoted and not stack:
                        return ''.join(result)
                    current = char()
            require(nonspace() == '{', 'template-not-object')
            while True:
                require(nonspace() == '"', 'template-key-invalid')
                key = json.loads(value('"', True))
                require(nonspace() == ':', 'template-colon-invalid')
                first = nonspace()
                if first in '"[{':
                    captured = value(first, key == 'fields')
                    delimiter = nonspace()
                else:
                    delimiter = first
                    while delimiter not in ',}':
                        delimiter = char()
                    captured = ''
                if key == 'fields':
                    fields = json.loads(captured)
                    require(isinstance(fields, list) and len(fields) == len(set(fields)) == 18 and all(isinstance(f, str) and re.fullmatch(r'[a-z][a-z0-9_]*', f) for f in fields), 'template-fields-invalid')
                    return fields
                require(delimiter == ',', 'template-fields-missing')
    except (OSError, ValueError, UnicodeError):
        raise GateError('template-unreadable') from None

def now():
    return datetime.now(timezone.utc).isoformat()

class Runtime:
    def __init__(self, directory):
        self.directory, self.root = directory, Path.cwd()
        self.inputs = {name:os.environ.get(name, '') for name in ('PATH','JAVA_HOME','HOME','DOCKER_HOST','TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE','M14_SECURITY_JAR','M14_MAVEN_REPO','M14_SECURITY_NVD_API_KEY')}
        self.node = node_tool()
        self.real_home = Path.home()
        os.environ.clear()
        self.home = directory / 'home'
        self.home.mkdir(mode=0o700)
        for name in ('npm-user.config','npm-global.config'):
            config = self.home/name
            config.write_bytes(b'')
            config.chmod(0o600)
        self.env = {'PATH':str(Path(self.node).parent) + ':' + self.inputs['PATH'], 'HOME':str(self.home), 'LANG':'C.UTF-8', 'LC_ALL':'C.UTF-8', 'TMPDIR':str(directory), 'JAVA_HOME':self.inputs['JAVA_HOME'], 'MAVEN_OPTS':'-Duser.home=' + str(self.home), 'MAVEN_USER_HOME':str(self.home / '.m2'), 'NPM_CONFIG_USERCONFIG':str(self.home/'npm-user.config'), 'NPM_CONFIG_GLOBALCONFIG':str(self.home/'npm-global.config'), 'PLAYWRIGHT_BROWSERS_PATH':str(self.real_home / 'Library/Caches/ms-playwright')}
        self.report = {'task':'M14-T07', 'startedAt':now(), 'checks':{name:{'status':'not-run','exitCode':None} for name in REQUIRED}, 'probes':{}, 'commands':[], 'environment':{}, 'scanCoverage':{}, 'cleanup':False}
        self.secrets, self.secret_patterns = [], ()
        self.private_inputs, self.container, self.jvm, self.stub, self.browser = [], None, None, None, None
        self.jvm_stream, self.stub_thread = None, None
        self.stub_count, self.stub_failures, self.fatal = 0, 0, False
        self.request_ids, self.http_count, self.header_failures = {}, 0, []
        self.browser_cleanup, self.port_owned = True, False
        self.snapshot = directory / 'snapshot'
        self.inventory, self.business_tables = {}, []
        self.known_artifacts = set()
        self.command_index, self.running = 0, None
        for item in ('S01.health','S01.liveness','S01.readiness','S02.sources','S02.datasets','S02.apis','S02.definition','S02.page','S03.page','S04.page','S07.final-row','S08.GET','S08.OPTIONS'):
            self.report['probes'][item] = {'status':'not-run'}
        for resource in ('root','env','configprops','metrics','beans','heapdump','logfile','mappings'):
            self.report['probes']['S01.' + resource] = {'status':'not-run'}
        for path in ('list','definition','records'):
            for method in ('POST','PUT','PATCH','DELETE'):
                self.report['probes']['S05.' + path + '.' + method] = {'status':'not-run'}
        for parameter in ('table','column','columns','sort','orderBy','sql'):
            self.report['probes']['S06.' + parameter] = {'status':'not-run'}
        for parameter in ('tsCode','page','pageSize','tradeDateFrom','apiName'):
            self.report['probes']['S07.' + parameter] = {'status':'not-run'}

    def gate(self, label, action):
        check = self.report['checks'][label]
        check.update(status='fail', startedAt=now())
        print('security gate: ' + label + ' running', flush=True)
        try:
            details = action()
            check.update(status='pass', details=details or {})
            return True
        except GateError as error:
            check['reason'] = str(error)
            if error.details is not None:
                check['details'] = error.details
            if str(error) == 'secret-detected':
                self.fatal = True
            return False
        except Exception:
            check['reason'] = 'operation-failed'
            return False
        finally:
            check['finishedAt'] = now()
            print('security gate: ' + label + ' ' + check['status'], flush=True)

    def scan(self, data, category, public=False, submitted=(), response_body=None, response_headers=()):
        try:
            scan_bytes(data, self.secret_patterns)
            if public:
                require(all(value.encode() not in data for value in FORBIDDEN), 'unsafe-public-detail')
                reflection_verdict(data,submitted,response_body,response_headers)
        except GateError:
            self.fatal = True
            raise
        self.report['scanCoverage'][category] = self.report['scanCoverage'].get(category, 0) + 1

    def run(self, label, args, *, cwd=None, extra=None, data=None, timeout=120, gate=None):
        self.command_index += 1
        output = self.directory / ('command-%04d.log' % self.command_index)
        self.known_artifacts.add(output)
        environment = {**self.env, **(extra or {})}
        command = {'id':self.command_index, 'step':label, 'exitCode':None, 'timedOut':False}
        self.report['commands'].append(command)
        try:
            with output.open('wb') as stream:
                child = subprocess.Popen(args, cwd=cwd or self.root, env=environment,
                                         stdin=subprocess.PIPE if data is not None else subprocess.DEVNULL,
                                         stdout=stream, stderr=stream)
                self.running = (child, command)
                started = time.monotonic()
                supplied = False
                while True:
                    try:
                        child.communicate(input=data if not supplied else None, timeout=min(30, timeout))
                        break
                    except subprocess.TimeoutExpired:
                        supplied = True
                        if time.monotonic() - started >= timeout:
                            command['timedOut'] = True
                            child.terminate()
                            try:
                                child.wait(timeout=30)
                            except subprocess.TimeoutExpired:
                                child.kill()
                                child.wait()
                            break
                        print('security gate: ' + label + ' running', flush=True)
                command['exitCode'] = child.returncode
                self.running = None
        except OSError:
            command['reason'] = 'child-start-failed'
            raise GateError('child-start-failed') from None
        finally:
            if gate:
                self.report['checks'][gate]['exitCode'] = command['exitCode']
        result = output.read_bytes()
        self.scan(result, 'subprocess_output')
        require(not command['timedOut'], 'child-timeout')
        return command['exitCode'], result

    def success(self, label, args, **kwargs):
        code, data = self.run(label, args, **kwargs)
        require(code == 0, 'child-exit-nonzero')
        return data

    def preflight(self):
        require((self.root / 'docs/task-designs/M14-T07-design.md').is_file(), 'repository-root-required')
        node_version = self.success('node-version', [self.node, '--version']).decode().strip()
        match = re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)', node_version)
        require(match and (24,15,0) <= tuple(map(int, match.groups())) < (25,0,0), 'node-version-invalid')
        self.report['environment']['node'] = node_version
        for command, arguments, pattern in [('java',['-version'],r'version "(21\.[^" ]+)"'), ('mvn',['-version'],r'Apache Maven (3\.9\.[0-9]+)'), ('npm',['--version'],r'^(11\.[0-9]+\.[0-9]+)'), ('mysql',['--version'],r'Ver ([0-9.]+)'), ('docker',['version','--format','{{.Server.Version}}'],r'^([0-9.]+)')]:
            executable = shutil.which(command, path=self.env['PATH'])
            require(bool(executable), 'required-tool-missing')
            setattr(self, command, executable)
            if command == 'docker':
                endpoint = self.inputs['DOCKER_HOST']
                if not endpoint:
                    extra = {'HOME':str(self.real_home), 'DOCKER_CONFIG':str(self.real_home / '.docker')}
                    endpoint = self.success('docker-context', [executable,'context','inspect','--format','{{.Endpoints.docker.Host}}'], extra=extra).decode().strip()
                require(endpoint.startswith('unix:///') and stat.S_ISSOCK(Path(endpoint[7:]).stat().st_mode), 'docker-endpoint-not-local')
                self.env['DOCKER_HOST'] = endpoint
                self.env['TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE'] = '/var/run/docker.sock'
            version = self.success(command + '-version', [executable] + arguments).decode()
            matched = re.search(pattern, version, re.M)
            require(bool(matched), 'tool-version-invalid')
            self.report['environment'][command] = matched.group(1)
            if command == 'mvn':
                require(bool(re.search(r'Java version: 21\.', version)), 'maven-java-version-invalid')
        self.report['environment']['python'] = '.'.join(map(str, sys.version_info[:3]))
        browser = self.success('playwright-preflight', [self.node, str(self.directory/'browser.mjs')], data=json.dumps({'mode':'preflight','root':str(self.root)}).encode(), timeout=30)
        self.report['environment']['playwright'] = json.loads(browser)
        jar = Path(self.inputs['M14_SECURITY_JAR'])
        require(jar.is_absolute() and not jar.is_symlink(), 'jar-path-invalid')
        info = jar.stat()
        require(stat.S_ISREG(info.st_mode) and info.st_mode & 0o444 and info.st_uid == os.getuid(), 'jar-file-invalid')
        self.jar = jar
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        require(digest == 'acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef', 'jar-identity-mismatch')
        self.report['identities'] = {'jarSha256':digest, 'scriptSha256':hashlib.sha256((self.root/'scripts/security/verify-release.sh').read_bytes()).hexdigest(), 'lockSha256':hashlib.sha256((self.root/'control-plane/package-lock.json').read_bytes()).hexdigest()}
        head = self.success('source-head', ['git','rev-parse','HEAD']).decode().strip()
        require(bool(re.fullmatch('[a-f0-9]{40}', head)), 'source-head-invalid')
        self.report['identities']['sourceCommit'] = head
        dirty = self.success('source-protection', ['git','diff','--name-only','HEAD','--','data-plane','control-plane','docs/data-template','docs/contracts'])
        require(not dirty.strip(), 'production-inputs-dirty')
        for tree in ('data-plane','control-plane'):
            unknown = self.success('untracked-protection', ['git','ls-files','--others','--exclude-standard','--',tree]).decode().splitlines()
            require(not any('/src/' in name or name.endswith(('pom.xml','package.json','package-lock.json')) for name in unknown), 'production-inputs-untracked')
        with socket.socket() as listener:
            try:
                listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                listener.bind(('127.0.0.1',8080))
            except OSError:
                raise GateError('port-8080-occupied') from None
        self.port_owned = True
        self.maven_repo = Path(self.inputs['M14_MAVEN_REPO'] or '/private/tmp/tensor-m2')
        require(self.maven_repo.is_absolute() and self.maven_repo.is_dir(), 'maven-repository-invalid')
        generated = self.success('random-values', [self.node,'-e',"process.stdout.write(JSON.stringify(Array.from({length:4},()=>require('node:crypto').randomBytes(24).toString('hex'))))"])
        # Random suffixes have no credential prefix. Remove their private transient log.
        random_values = json.loads(generated)
        require(len(random_values) == 4 and all(re.fullmatch('[a-f0-9]{48}', x) for x in random_values), 'random-values-invalid')
        random_log = self.directory / ('command-%04d.log' % self.command_index)
        random_log.unlink(); self.known_artifacts.remove(random_log)
        self.token, self.password, self.admin_password = ['M14_T07_TOKEN_' + random_values[0], 'M14_T07_DB_' + random_values[1], 'M14_T07_ADMIN_' + random_values[2]]
        self.owner = random_values[3]
        self.secrets = [self.token, self.password, self.admin_password]
        if self.inputs['M14_SECURITY_NVD_API_KEY']:
            self.secrets.append(self.inputs['M14_SECURITY_NVD_API_KEY'])
        self.secret_patterns = patterns(self.secrets)
        self.schema = 'tensor_m14_t07_' + self.owner[:16]
        self.username = 'm14t07_' + self.owner[:16]
        self.fields = top_fields(self.root/'docs/data-template/stock_company.json')
        self.report['environment'].update(profile='default', bind='loopback', productionDatasets=49)
        return {'port8080InitiallyFree':True, 'sanitizedChildEnvironment':True}

    def scan_source(self):
        listing = self.success('tracked-files', ['git','ls-files','-z'])
        files = [self.root / os.fsdecode(name) for name in listing.split(b'\0') if name]
        require(bool(files), 'source-scan-empty')
        for file in files:
            scan_file(file, self.secret_patterns)
        self.report['scanCoverage']['tracked_source_files'] = len(files)
        archive = self.success('source-snapshot', ['git','archive','--format=tar','HEAD'])
        self.snapshot.mkdir(mode=0o700)
        with tarfile.open(fileobj=io.BytesIO(archive)) as bundle:
            require(all(not m.issym() and not m.islnk() and not Path(m.name).is_absolute() and '..' not in Path(m.name).parts for m in bundle.getmembers()), 'snapshot-entry-invalid')
            bundle.extractall(self.snapshot, filter='data')
        self.modules = [node.text for node in ET.parse(self.snapshot/'data-plane/pom.xml').getroot().findall('{*}modules/{*}module')]
        require(len(self.modules) == 5, 'reactor-modules-invalid')
        return {'files':len(files), 'snapshotFromHead':True}

    def scan_jar(self):
        names, migrations, modules = [], [], []
        self.packaged_libraries = self.directory/'packaged-libraries'
        self.packaged_libraries.mkdir(mode=0o700)
        def visit(name, data, depth):
            names.append(name)
            if '/db/migration/' in '/' + name and name.endswith('.sql'):
                migrations.append(Path(name).name)
            if depth == 0 and name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                filename = Path(name).name
                if filename.startswith('tensor-'):
                    modules.append(filename)
                else:
                    coordinates = []
                    with zipfile.ZipFile(io.BytesIO(data)) as library:
                        for item in library.namelist():
                            if item.startswith('META-INF/maven/') and item.endswith('/pom.properties'):
                                props = dict(line.split('=',1) for line in library.read(item).decode().splitlines() if '=' in line and not line.startswith('#'))
                                if all(props.get(k) for k in ('groupId','artifactId','version')):
                                    coordinates.append(':'.join(props[k] for k in ('groupId','artifactId','version')))
                    require(bool(re.fullmatch('[A-Za-z0-9_.-]+',filename)), 'jar-filename-invalid')
                    (self.packaged_libraries/filename).write_bytes(data)
                    self.inventory[filename] = {'coordinates':coordinates,'sha256':hashlib.sha256(data).hexdigest()}
        count = scan_zip(self.jar.read_bytes(), self.secret_patterns, visit)
        for filename, identity in self.inventory.items():
            if identity['coordinates'] or not filename.startswith('spring-boot-jarmode-tools-'):
                continue
            version = filename.removeprefix('spring-boot-jarmode-tools-').removesuffix('.jar')
            base = 'https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-jarmode-tools/' + version + '/' + filename.removesuffix('.jar')
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open(base + '.jar', timeout=30) as response:
                require(hashlib.sha256(response.read()).hexdigest() == identity['sha256'], 'packaged-metadata-jar-mismatch')
            with opener.open(base + '.pom', timeout=30) as response:
                pom = response.read()
            scan_bytes(pom, self.secret_patterns)
            (self.packaged_libraries / (filename.removesuffix('.jar') + '.pom')).write_bytes(pom)
        require(sorted(re.match(r'V(\d+)__', name).group(1) for name in migrations) == ['1','2','3','4','5','7'], 'jar-migrations-invalid')
        require(sorted(modules) == ['tensor-core-1.0-SNAPSHOT.jar','tensor-plugin-api-1.0-SNAPSHOT.jar','tensor-plugin-tushare-1.0-SNAPSHOT.jar'], 'jar-modules-invalid')
        resources = [name for name in names if 'datasets/tushare_pro/' in name and name.endswith(('.yml','.yaml'))]
        require(len(resources) == 49 and not any('fixture' in name.lower() or '/test-classes/' in name or 'application-acceptance' in name for name in names), 'jar-resources-invalid')
        self.report['scanCoverage']['jar_entries_recursive'] = count
        return {'entries':count, 'thirdPartyJars':len(self.inventory), 'migrations':sorted(migrations), 'productionModules':modules, 'datasetResources':len(resources)}

    def maven_tests(self):
        args = [self.mvn,'-B','-ntp','-f','data-plane/pom.xml','-pl','tensor-app','-am','-Dmaven.repo.local=' + str(self.maven_repo),'-Dtest=' + ','.join(TEST_CLASSES),'-Dsurefire.failIfNoSpecifiedTests=false','test']
        code, output = self.run('maven-security-tests', args, cwd=self.snapshot, timeout=1800, gate='maven')
        reports = list((self.snapshot/'data-plane').glob('*/target/surefire-reports/TEST-*.xml'))
        enforcer = b'ban-git-capabilities' in output and bool(re.search(br'(?:maven-enforcer-plugin|enforcer):3\.6\.3:enforce', output))
        return test_verdict(reports, code, enforcer)

    def backend_audit(self):
        output, cache = self.directory/'dependency-report', self.directory/'dependency-cache'
        output.mkdir(mode=0o700); cache.mkdir(mode=0o700)
        args = [self.mvn,'-B','-ntp','-f','data-plane/pom.xml','-Dmaven.repo.local=' + str(self.maven_repo),'org.owasp:dependency-check-maven:13.0.0:aggregate','-DfailBuildOnCVSS=7','-DfailOnError=true','-DautoUpdate=true','-DskipTestScope=false','-DskipProvidedScope=false','-DskipRuntimeScope=false','-Dformat=JSON','-Dodc.outputDirectory=' + str(output),'-DdataDirectory=' + str(cache),'-DnvdApiKeyEnvironmentVariable=M14_SECURITY_NVD_API_KEY','-DscanDirectory=' + str(self.packaged_libraries)]
        extra = {'M14_SECURITY_NVD_API_KEY':self.inputs['M14_SECURITY_NVD_API_KEY']} if self.inputs['M14_SECURITY_NVD_API_KEY'] else {}
        if not extra:
            args.append('-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz')
        code, scanned_output = self.run('backend-dependency-audit', args, cwd=self.snapshot, extra=extra, timeout=7200, gate='backend_audit')
        report_path = output/'dependency-check-report.json'
        diagnostics = scanner_diagnostics(scanned_output,code,report_path.exists())
        if not report_path.exists():
            raise GateError('dependency-scanner-failed' if code else 'dependency-report-missing',diagnostics)
        scan_file(report_path, self.secret_patterns)
        try:
            report = json.loads(report_path.read_bytes())
        except (OSError, ValueError):
            raise GateError('dependency-report-invalid',diagnostics) from None
        try:
            summary = dependency_verdict(report, code, self.inventory, self.modules)
        except GateError as error:
            raise GateError(str(error),{**(error.details or {}),'scanner':diagnostics}) from None
        if diagnostics['failureCategories']:
            raise GateError('dependency-scanner-incomplete', {**summary, 'scanner':diagnostics})
        return summary

    def frontend_audit(self):
        code, output = self.run('frontend-dependency-audit', [self.npm,'audit','--package-lock-only','--audit-level=high','--json'], cwd=self.snapshot/'control-plane', timeout=600, gate='frontend_audit')
        try:
            report = json.loads(output)
        except ValueError:
            raise GateError('npm-report-invalid') from None
        return npm_verdict(report, code)

    def private_file(self, name, content):
        path = self.directory/name
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'w') as stream:
            stream.write(content)
        info = path.lstat()
        require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600 and info.st_uid == os.getuid(), 'private-input-permissions')
        self.private_inputs.append(path)
        return path

    def mysql_query(self, sql, *, admin=False, label='database-query', tolerate=False):
        defaults = self.admin_defaults if admin else self.app_defaults
        args = [self.mysql,'--defaults-file=' + str(defaults),'--no-login-paths','--batch','--skip-column-names','--raw']
        if not admin:
            args.append('--database=' + self.schema)
        code, output = self.run(label, args, data=sql.encode(), timeout=15)
        if not tolerate:
            require(code == 0, 'database-query-failed')
        return code, output.decode().strip()

    def database_setup(self):
        initial = self.private_file('mysql.env', 'MYSQL_ROOT_PASSWORD=' + self.admin_password + '\nMYSQL_ROOT_HOST=%\n')
        cidfile = self.directory/'container.cid'
        try:
            code, _ = self.run('mysql-container-create', [self.docker,'run','--detach','--cidfile',str(cidfile),'--label','org.tensor.m14-t07.owner=' + self.owner,'--env-file',str(initial),'--publish','127.0.0.1::3306','mysql:8.4.6'], timeout=180)
        finally:
            if cidfile.is_file() and not cidfile.is_symlink():
                cid = cidfile.read_text().strip()
                if re.fullmatch('[a-f0-9]{64}', cid):
                    self.container = cid
        require(code == 0 and self.container is not None, 'container-create-failed')
        cid = self.container
        label = self.success('container-ownership', [self.docker,'inspect','--format','{{index .Config.Labels "org.tensor.m14-t07.owner"}}',cid]).decode().strip()
        require(label == self.owner, 'container-ownership-invalid')
        ports = json.loads(self.success('container-loopback', [self.docker,'inspect','--format','{{json .NetworkSettings.Ports}}',cid]))
        require(set(ports) <= {'3306/tcp','33060/tcp'} and isinstance(ports.get('3306/tcp'), list) and len(ports['3306/tcp']) == 1, 'container-port-invalid')
        binding = ports['3306/tcp'][0]
        require(binding.get('HostIp') == '127.0.0.1' and not ports.get('33060/tcp'), 'container-not-loopback')
        self.mysql_port = int(binding['HostPort'])
        require(1 <= self.mysql_port <= 65535, 'container-port-invalid')
        self.admin_defaults = self.private_file('admin.defaults', '[client]\nhost=127.0.0.1\nport=' + str(self.mysql_port) + '\nuser=root\npassword=' + self.admin_password + '\nprotocol=TCP\n')
        started, source = time.monotonic(), None
        while time.monotonic() - started < 120:
            code, output = self.mysql_query("SELECT @@version, SUBSTRING_INDEX(USER(),'@',-1);", admin=True, label='mysql-ready', tolerate=True)
            if code == 0:
                parts = output.split('\t')
                require(len(parts) == 2 and parts[0] == '8.4.6', 'mysql-server-version-invalid')
                source = parts[1]
                break
            time.sleep(2)
        require(source is not None and re.fullmatch('[A-Za-z0-9.:-]+', source) and '%' not in source, 'mysql-source-host-invalid')
        self.source_host = source
        self.mysql_query("CREATE DATABASE `" + self.schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs; CREATE USER '" + self.username + "'@'" + source + "' IDENTIFIED BY '" + self.password + "'; GRANT CREATE,SELECT,INSERT,UPDATE,ALTER,INDEX ON `" + self.schema + "`.* TO '" + self.username + "'@'" + source + "';", admin=True, label='mysql-least-privilege')
        self.app_defaults = self.private_file('app.defaults', '[client]\nhost=127.0.0.1\nport=' + str(self.mysql_port) + '\nuser=' + self.username + '\npassword=' + self.password + '\nprotocol=TCP\n')
        _, grants = self.mysql_query('SHOW GRANTS;', label='mysql-grant-verification')
        lines = grants.splitlines()
        require(len(lines) == 2 and not any(value in grants for value in ('GRANT OPTION','ALL PRIVILEGES','DELETE','DROP')), 'mysql-grants-excessive')
        scope = [line for line in lines if ' ON `' + self.schema + '`.* TO ' in line]
        require(len(scope) == 1 and set(scope[0].split(' ON ')[0].removeprefix('GRANT ').replace(' ','').split(',')) == {'CREATE','SELECT','INSERT','UPDATE','ALTER','INDEX'}, 'mysql-grants-invalid')
        require(any(line.startswith('GRANT USAGE ON *.* TO ') for line in lines), 'mysql-global-grants-invalid')
        _, identity = self.mysql_query("SELECT USER(),CURRENT_USER(),DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=DATABASE();", label='mysql-account-verification')
        require(identity.split('\t') == [self.username + '@' + source, self.username + '@' + source,'utf8mb4','utf8mb4_0900_as_cs'], 'mysql-account-source-mismatch')
        _, count = self.mysql_query('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();', label='mysql-empty-schema')
        require(count == '0', 'mysql-schema-not-empty')
        self.report['environment']['mysqlServer'] = '8.4.6'
        return {'sourceHostSha256':hashlib.sha256(source.encode()).hexdigest(), 'privileges':['CREATE','SELECT','INSERT','UPDATE','ALTER','INDEX'], 'charset':'utf8mb4', 'collation':'utf8mb4_0900_as_cs', 'isolatedSchemaInitiallyEmpty':True}

    def start_stub(self):
        runtime = self
        class Stub(BaseHTTPRequestHandler):
            def setup(self):
                self.request.settimeout(15)
                super().setup()
            def log_message(self, *args):
                pass
            def do_POST(self):
                runtime.stub_count += 1
                valid = False
                try:
                    length = int(self.headers.get('Content-Length','0'))
                    require(0 < length < 65536 and self.path == '/', 'stub-request-invalid')
                    body = json.loads(self.rfile.read(length))
                    valid = isinstance(body, dict) and set(body) == {'api_name','token','params','fields'} and body['api_name'] == 'stock_company' and body['token'] == runtime.token and body['params'] == {'exchange':'SZSE'} and body['fields'] == ','.join(runtime.fields) and runtime.stub_count <= 2
                except Exception:
                    valid = False
                if not valid:
                    runtime.stub_failures += 1
                    runtime.fatal = True
                    self.send_response(500); self.end_headers(); return
                if runtime.stub_count == 1:
                    row = {'ts_code':'000001.SZ','exchange':'SZSE','introduction':HTML}
                    status, data = 200, {'code':0,'msg':None,'data':{'fields':runtime.fields,'items':[[row.get(f) for f in runtime.fields]]}}
                else:
                    status, data = 401, {'code':-2001,'msg':runtime.token + ' M14_T07_UPSTREAM_DETAIL'}
                payload = json.dumps(data).encode()
                self.send_response(status); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(payload))); self.end_headers(); self.wfile.write(payload)
            def do_GET(self):
                runtime.stub_failures += 1; runtime.fatal = True
                self.send_response(405); self.end_headers()
        self.stub = ThreadingHTTPServer(('127.0.0.1',0), Stub)
        self.stub.daemon_threads = False
        self.stub_thread = threading.Thread(target=self.stub.serve_forever, daemon=True)
        self.stub_thread.start()

    def http(self, path, *, method='GET', headers=None, body=None, observe=True, submitted=()):
        require(not self.fatal, 'dynamic-stopped')
        require(path.startswith('/') and not path.startswith('//'), 'http-target-invalid')
        connection = http.client.HTTPConnection('127.0.0.1',8080,timeout=5)
        started = time.monotonic()
        deadline = None
        try:
            connection.connect()
            connected_socket = connection.sock
            def expire():
                try:
                    connected_socket.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            deadline = threading.Timer(max(0.1,15 - (time.monotonic() - started)),expire)
            deadline.start()
            connection.sock.settimeout(max(0.1,15 - (time.monotonic() - started)))
            connection.request(method, path, body=body, headers=headers or {})
            response = connection.getresponse()
            data = response.read(32 * 1024 * 1024 + 1)
            require(len(data) <= 32 * 1024 * 1024 and time.monotonic() - started <= 15, 'http-budget-exceeded')
            status, header_entries = response.status, response.getheaders()
        except (OSError, http.client.HTTPException):
            raise GateError('http-request-failed') from None
        finally:
            if deadline:
                deadline.cancel()
            connection.close()
        self.scan(json.dumps(header_entries).encode() + b'\n' + data, 'http', public=True,submitted=submitted,response_body=data,response_headers=header_entries)
        response_headers = normalize_headers(header_entries)
        if observe:
            self.observe_headers(response_headers, urllib.parse.urlsplit(path).path, status)
        return status, response_headers, data

    def observe_headers(self, headers, path, status):
        self.http_count += 1
        try:
            header_verdict(headers, path)
        except GateError as error:
            self.header_failures.append({'responseId':self.http_count,'reason':str(error), 'httpStatus':status})

    def startup(self):
        self.start_stub()
        # Recheck immediately before launch; never terminate an unrelated port owner.
        with socket.socket() as listener:
            try:
                listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                listener.bind(('127.0.0.1',8080))
            except OSError:
                raise GateError('port-8080-occupied-before-launch') from None
        environment = {**self.env,'TENSOR_DB_URL':'jdbc:mysql://127.0.0.1:' + str(self.mysql_port) + '/' + self.schema,'TENSOR_DB_USERNAME':self.username,'TENSOR_DB_PASSWORD':self.password,'TENSOR_TUSHARE_TOKEN':self.token,'TENSOR_TUSHARE_BASE_URL':'http://127.0.0.1:' + str(self.stub.server_port) + '/'}
        self.jvm_log = self.directory/'application.log'
        self.known_artifacts.add(self.jvm_log)
        self.jvm_stream = self.jvm_log.open('wb')
        self.jvm = subprocess.Popen([self.java,'-jar',str(self.jar),'--server.address=127.0.0.1','--server.port=8080'],env=environment,stdin=subprocess.DEVNULL,stdout=self.jvm_stream,stderr=self.jvm_stream)
        self.jvm_pid = self.jvm.pid
        started, ready = time.monotonic(), False
        while time.monotonic() - started < 90:
            require(self.jvm.poll() is None, 'jvm-exited-before-ready')
            scan_file(self.jvm_log, self.secret_patterns)
            try:
                status, _, body = self.http('/actuator/health', observe=False)
                if status == 200 and json.loads(body).get('status') == 'UP':
                    ready = True; break
            except GateError:
                if self.fatal:
                    raise
            except ValueError:
                pass
            time.sleep(1)
        require(ready, 'root-health-deadline')
        _, migrations = self.mysql_query("SELECT version,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;", label='migration-verification')
        require(migrations.splitlines() == [str(v) + '\t1' for v in (1,2,3,4,5,7)], 'production-migrations-invalid')
        _, tables = self.mysql_query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME;", label='business-table-inventory')
        self.business_tables = tables.splitlines()
        require(len(self.business_tables) == 49 and all(re.fullmatch('tushare_pro__[a-z][a-z0-9_]*', table) for table in self.business_tables), 'business-table-inventory-invalid')
        require(all(row['rows'] == 0 for row in self.database_fingerprint()), 'business-tables-not-empty')
        return {'rootHealthReady':True, 'successfulMigrations':6, 'emptyBusinessTables':49}

    def database_fingerprint(self):
        _, columns = self.mysql_query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME,ORDINAL_POSITION;", label='business-column-inventory')
        mapping = {name:[] for name in self.business_tables}
        for line in columns.splitlines():
            table, column = line.split('\t')
            require(table in mapping and re.fullmatch('[a-z][a-z0-9_]*',column), 'business-column-invalid')
            mapping[table].append(column)
        self.business_columns = mapping
        statements = []
        for index, (table, fields) in enumerate(mapping.items()):
            require(bool(fields), 'business-columns-empty')
            expression = 'CONCAT_WS(CHAR(31),' + ','.join("COALESCE(CAST(`" + field + "` AS CHAR),'<NULL>')" for field in fields) + ')'
            statements.append("SELECT " + str(index) + ",COUNT(*),COALESCE(SHA2(GROUP_CONCAT(SHA2(" + expression + ",256) ORDER BY SHA2(" + expression + ",256) SEPARATOR ','),256),'empty') FROM `" + table + '`;')
        _, output = self.mysql_query('\n'.join(statements), label='business-row-fingerprint')
        results = []
        for line in output.splitlines():
            index, count, digest = line.split('\t')
            require(index.isdigit() and count.isdigit() and (digest == 'empty' or re.fullmatch('[a-f0-9]{64}',digest)), 'business-fingerprint-invalid')
            results.append({'tableId':int(index),'rows':int(count),'sha256':digest})
        require(len(results) == 49, 'business-fingerprint-incomplete')
        return results

    def remember(self, body, headers, operation):
        request_id = body.get('requestId') if isinstance(body, dict) else None
        require(isinstance(request_id, str) and re.fullmatch('[A-Za-z0-9-]{1,100}', request_id) and headers.get('x-request-id') == request_id, 'request-id-invalid')
        require(request_id not in self.request_ids, 'request-id-duplicate')
        self.request_ids[request_id] = operation

    def probe(self, identifier, path, verdict, *, method='GET', payload=None, headers=None, remember=False, unchanged=None, submitted=()):
        outcome = self.report['probes'][identifier]
        outcome.update(status='fail', method=method)
        try:
            status, response_headers, data = self.http(path, method=method, body=payload, headers=headers,submitted=submitted)
            outcome['httpStatus'] = status
            try:
                body = json.loads(data)
            except ValueError:
                body = None
            if identifier in ('S01.health','S01.liveness','S01.readiness'):
                outcome['healthStructure'] = health_structure(body)
            if isinstance(body, dict):
                if body.get('code') in ('PARAM_INVALID','SOURCE_AUTH_FAILED','INTERNAL_ERROR','DATASET_NOT_FOUND','PLUGIN_NOT_FOUND'):
                    outcome['code'] = body['code']
                request_id = body.get('requestId')
                if isinstance(request_id, str) and re.fullmatch('[A-Za-z0-9-]{1,100}', request_id):
                    outcome['requestId'] = request_id
                if remember:
                    self.remember(body, response_headers, 'query')
            if unchanged is not None:
                outcome['rowsUnchanged'] = self.database_fingerprint() == unchanged
                outcome['stubCallsUnchanged'] = self.stub_count == 2
            verdict(status, body, data)
            if unchanged is not None:
                require(outcome['rowsUnchanged'] and outcome['stubCallsUnchanged'], 'read-only-state-changed')
            outcome['status'] = 'pass'
        except GateError as error:
            outcome['reason'] = str(error)
        except Exception:
            outcome['reason'] = 'probe-failed'
        return outcome['status'] == 'pass'

    def group_verdict(self, prefix):
        outcomes = [value for key,value in self.report['probes'].items() if key.startswith(prefix + '.')]
        counts = {status:sum(value['status'] == status for value in outcomes) for status in ('pass','fail','not-run')}
        if not outcomes or counts['fail'] or counts['not-run']:
            raise GateError('probe-group-incomplete', counts)
        return counts

    def s01(self):
        for label, path in [('health','/actuator/health'),('liveness','/actuator/health/liveness'),('readiness','/actuator/health/readiness')]:
            if self.fatal:
                break
            self.probe('S01.' + label, path, lambda s,b,d: actuator_verdict(s,d,True))
        for label in ('root','env','configprops','metrics','beans','heapdump','logfile','mappings'):
            if self.fatal:
                break
            self.probe('S01.' + label, '/actuator' + ('' if label == 'root' else '/' + label), lambda s,b,d: actuator_verdict(s,d,False))
        return self.group_verdict('S01')

    def s02_descriptors(self):
        def descriptor_safety(body):
            if isinstance(body, dict):
                require(not any(re.search(r'(?:token|password|secret|authorization|base.?url|jdbc)', key, re.I) for key in body), 'descriptor-sensitive-key')
                for value in body.values():
                    descriptor_safety(value)
            elif isinstance(body, list):
                for value in body:
                    descriptor_safety(value)
        def sources(status, body, data):
            descriptor_safety(body)
            require(status == 200 and isinstance(body,list) and len(body) == 1 and body[0].get('pluginId') == 'tushare_pro' and all(body[0].get(key) is True for key in ('enabled','credentialConfigured','downloadAvailable')), 'production-source-descriptor-invalid')
        def datasets(status, body, data):
            descriptor_safety(body)
            require(status == 200 and isinstance(body,list) and len(body) == 49 and len({item.get('apiName') for item in body}) == 49 and all(item.get('pluginId','tushare_pro') == 'tushare_pro' for item in body), 'production-dataset-descriptors-invalid')
        def definition(status, body, data):
            descriptor_safety(body)
            require(status == 200 and isinstance(body,dict) and body.get('pluginId') == 'tushare_pro' and body.get('apiName') == 'stock_company' and [column.get('name') for column in body.get('columns',[])] == self.fields, 'production-definition-invalid')
        for label,path,verdict in [('sources','/api/v1/data-sources',sources),('datasets',self.dataset_path,datasets),('apis','/api/v1/data-sources/tushare_pro/apis',datasets),('definition',self.definition_path,definition)]:
            if self.fatal:
                break
            self.probe('S02.' + label,path,verdict)

    def browser_probes(self):
        configuration = {'mode':'runtime','root':str(self.root),'fields':self.fields,'html':HTML,'definition':self.definition_path,'records':self.records_path}
        error_path = self.directory/'browser.log'
        self.known_artifacts.add(error_path)
        self.browser_cleanup = False
        with error_path.open('wb') as errors:
            child = subprocess.Popen([self.node,str(self.directory/'browser.mjs')],env=self.env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=errors,text=True,bufsize=1)
            self.browser = child
            child.stdin.write(json.dumps(configuration) + '\n'); child.stdin.flush()
            timer = threading.Timer(240, child.terminate)
            timer.start()
            result = None
            try:
                for line in child.stdout:
                    ok = not self.fatal
                    message = {}
                    try:
                        self.scan(line.encode(), 'browser_driver')
                        message = json.loads(line)
                        value = message.get('value', {})
                        kind = message.get('type')
                        if kind == 'surface':
                            category = value.get('category')
                            require(category in ('dom','console','request','http','pageerror'), 'browser-category-invalid')
                            self.scan(value.get('data','').encode(), 'browser_' + category, public=category != 'request')
                        elif kind == 'http':
                            self.scan(json.dumps(value.get('headerEntries',[])).encode() + value.get('body','').encode(), 'browser_http', public=True)
                            self.observe_headers(normalize_headers(value['headerEntries']), value['path'], value['status'])
                        elif kind == 'requestId':
                            request_id = value.get('requestId')
                            require(isinstance(request_id,str) and re.fullmatch('[A-Za-z0-9-]{1,100}',request_id) and value.get('operation') in ('download','query') and request_id not in self.request_ids, 'browser-request-id-invalid')
                            self.request_ids[request_id] = value['operation']
                        elif kind == 'probe':
                            identifier = value.get('id')
                            require(identifier in ('S02.page','S03.page','S04.page') and value.get('status') in ('pass','fail'), 'browser-probe-invalid')
                            self.report['probes'][identifier] = {k:v for k,v in value.items() if k != 'id'}
                        elif kind == 'result':
                            result = value
                        else:
                            raise GateError('browser-message-invalid')
                    except Exception:
                        ok = False
                        self.fatal = True
                    if 'id' in message:
                        try:
                            child.stdin.write(json.dumps({'id':message['id'],'ok':ok}) + '\n'); child.stdin.flush()
                        except (OSError, BrokenPipeError):
                            break
                child.wait(timeout=30)
            finally:
                timer.cancel()
                if child.poll() is None:
                    child.terminate()
                    try:
                        child.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        self.fatal = True
                child.stdin.close(); child.stdout.close()
                self.report['commands'].append({'step':'browser-driver','exitCode':child.poll()})
                for name in ('S02','S03','S04'):
                    self.report['checks'][name]['exitCode'] = child.poll()
                self.browser = None if child.poll() is not None else child
        scan_file(error_path, self.secret_patterns)
        self.scan(error_path.read_bytes(), 'browser_log')
        require(isinstance(result,dict), 'browser-result-missing')
        self.browser_cleanup = result.get('cleanup') is True
        self.fatal = self.fatal or result.get('fatal',True)
        self.browser_result = {'requestCount':result.get('requestCount'), 'downloadCount':result.get('downloadCount'), 'cleanup':self.browser_cleanup, 'unexpectedNetworkOrSurface':self.fatal}
        self.assets = result.get('assets', [])
        require(isinstance(self.assets,list) and all(re.fullmatch(r'/assets/[A-Za-z0-9_.\-/]+\.(js|css)',asset) for asset in self.assets), 'browser-asset-inventory-invalid')
        require(child.returncode == 0 and self.browser_cleanup and not self.fatal, 'browser-driver-failed')

    def http_matrix(self):
        self.baseline = self.database_fingerprint()
        for label,path in [('list',self.dataset_path),('definition',self.definition_path),('records',self.records_path)]:
            for method in ('POST','PUT','PATCH','DELETE'):
                if self.fatal:
                    break
                self.probe('S05.' + label + '.' + method,path,lambda s,b,d: method_verdict(s),method=method,payload=b'{}',headers={'Content-Type':'application/json'},unchanged=self.baseline)
        self.gate('S05', lambda:self.group_verdict('S05'))
        for key,value in [('table','other_table'),('column','introduction'),('columns','*'),('sort','ts_code DESC'),('orderBy','ts_code'),('sql','SELECT 1')]:
            if self.fatal:
                break
            self.probe('S06.' + key,self.records_path + '?' + urllib.parse.urlencode({key:value}),lambda s,b,d: query_verdict(s,b),remember=True,unchanged=self.baseline,submitted=(value,) if key in ('sql','sort') else ())
        self.gate('S06', lambda:self.group_verdict('S06'))
        for key,value in [('tsCode',"x' OR 1=1 --"),('page','1 OR 1=1'),('pageSize','101'),('tradeDateFrom','2026-08-07')]:
            if self.fatal:
                break
            self.probe('S07.' + key,self.records_path + '?' + urllib.parse.urlencode({key:value}),lambda s,b,d: query_verdict(s,b),remember=True,unchanged=self.baseline,submitted=(value,))
        if not self.fatal:
            identifier_payload = "stock_company' OR 1=1 --"
            self.probe('S07.apiName',self.dataset_path + '/' + urllib.parse.quote(identifier_payload,safe='') + '/records',lambda s,b,d: path_verdict(s),unchanged=self.baseline,submitted=(identifier_payload,))
        def final_row(status, body, data):
            require(status == 200 and isinstance(body,dict) and body.get('totalElements') == 1 and len(body.get('items',[])) == 1 and all(body['items'][0].get(field) == {'ts_code':'000001.SZ','exchange':'SZSE','introduction':HTML}.get(field) for field in self.fields), 'final-row-invalid')
        if not self.fatal:
            self.probe('S07.final-row',self.records_path + '?tsCode=000001.SZ',final_row,remember=True,unchanged=self.baseline)
        self.gate('S07', lambda:self.group_verdict('S07'))

    def s08(self):
        for path in ['/downloads','/datasets'] + getattr(self,'assets',[]):
            if self.fatal:
                break
            label = 'S08.resource-' + str(len([k for k in self.report['probes'] if k.startswith('S08.resource-')]))
            self.report['probes'][label] = {'status':'not-run'}
            self.probe(label,path,lambda s,b,d: require(s == 200, 'resource-http-status'))
        origin = {'Origin':'https://m14-t07.invalid'}
        for method in ('GET','OPTIONS'):
            if self.fatal:
                break
            headers = {**origin, **({'Access-Control-Request-Method':'GET','Access-Control-Request-Headers':'Content-Type,X-Request-Id'} if method == 'OPTIONS' else {})}
            self.probe('S08.' + method,self.dataset_path,lambda s,b,d:require(s in (200,403,405), 'cors-http-status'),method=method,headers=headers)
        summary = {'responsesChecked':self.http_count, 'headerFailures':self.header_failures, 'jsResources':sum(a.endswith('.js') for a in getattr(self,'assets',[])), 'cssResources':sum(a.endswith('.css') for a in getattr(self,'assets',[]))}
        if self.header_failures or not summary['jsResources'] or not summary['cssResources']:
            raise GateError('security-headers-incomplete',summary)
        summary.update(self.group_verdict('S08'))
        return summary

    def database_scan(self):
        require(len(self.business_tables) == 49, 'business-scan-incomplete')
        self.database_fingerprint()
        statements = []
        for index, table in enumerate(self.business_tables):
            fields = self.business_columns[table]
            terms = ["COALESCE(INSTR(CAST(`" + field + "` AS CHAR),'" + secret + "'),0)>0" for field in fields for secret in self.secrets[:3]]
            statements.append('SELECT ' + str(index) + ',COUNT(*) FROM `' + table + '` WHERE ' + ' OR '.join(terms) + ';')
        _, output = self.mysql_query('\n'.join(statements),label='business-credential-scan')
        rows = [line.split('\t') for line in output.splitlines()]
        require(len(rows) == 49 and all(index.isdigit() and count.isdigit() for index,count in rows), 'business-scan-invalid')
        hits = sum(int(count) for _,count in rows)
        require(hits == 0, 'business-credential-persisted')
        self.report['scanCoverage']['business_tables'] = 49
        return {'tables':49,'credentialMatches':hits}

    def scan_logs(self):
        require(hasattr(self,'jvm_log'), 'application-log-missing')
        scan_file(self.jvm_log,self.secret_patterns)
        data = self.jvm_log.read_bytes()
        self.scan(data, 'application_log')
        require(all(value.encode() not in data for value in PRIVATE_LOG_FORBIDDEN), 'unsafe-log-detail')
        lines = data.decode(errors='strict').splitlines()
        disclosure = re.compile(r'''\b(?:SELECT\b[^\r\n]*\bFROM|INSERT\s+INTO|DELETE\s+FROM|UPDATE\b[^\r\n]*\bSET|(?:CREATE|ALTER|DROP)\s+TABLE)\b|\b(?:params|parameters|requestBody|sql|tsCode|exchange)["']?\s*[:=]''',re.I)
        require(not any(disclosure.search(line) for line in lines),'ordinary-log-query-disclosure')
        events = [completion_verdict(line) for line in lines if 'tensor.operation.completed' in line]
        counts = []
        for request_id,operation in self.request_ids.items():
            matches = [event for event in events if event['requestId'] == request_id and event['operation'] == operation]
            counts.append({'requestId':request_id,'operation':operation,'completionEvents':len(matches)})
        details = {'requests':counts,'completionEvents':len(events)}
        if not counts or not all(row['completionEvents'] == 1 for row in counts) or len(events) != len(counts):
            raise GateError('completion-events-not-unique',details)
        return details

    def cleanup(self):
        details = {'browser':self.browser_cleanup, 'jvm':True, 'stub':True, 'container':True, 'volumes':True, 'privateInputs':True, 'port8080':True}
        if self.running:
            child, command = self.running
            child.terminate()
            try:
                child.wait(timeout=30)
            except subprocess.TimeoutExpired:
                child.kill(); child.wait()
            command['exitCode'] = child.returncode
            details['interruptedChild'] = True
            self.running = None
        if self.browser is not None:
            self.browser.terminate()
            try:
                self.browser.wait(timeout=30)
            except subprocess.TimeoutExpired:
                pass
            details['browser'] = False
        if self.jvm is not None:
            if self.jvm.poll() is None:
                require(self.jvm.pid == self.jvm_pid, 'jvm-ownership-invalid')
                self.jvm.terminate()
                for _ in range(6):
                    try:
                        self.jvm.wait(timeout=30)
                        break
                    except subprocess.TimeoutExpired:
                        print('security gate: owned JVM stopping',flush=True)
                details['jvm'] = self.jvm.poll() in (0,143,-signal.SIGTERM)
            else:
                details['jvm'] = self.jvm.returncode == 0
            details['jvmExitCode'] = self.jvm.poll()
            if self.jvm_stream:
                self.jvm_stream.close()
        if self.stub is not None:
            try:
                self.stub.shutdown(); self.stub.server_close()
                self.stub_thread.join(timeout=5)
                details['stub'] = not self.stub_thread.is_alive()
            except Exception:
                details['stub'] = False
        # Final ordinary logs and artifacts are scanned after producers stop, before container deletion.
        if hasattr(self,'jvm_log'):
            self.gate('log_scan',self.scan_logs)
        if self.container:
            try:
                label = self.success('cleanup-container-owner',[self.docker,'inspect','--format','{{index .Config.Labels "org.tensor.m14-t07.owner"}}',self.container]).decode().strip()
                require(label == self.owner,'cleanup-container-owner-mismatch')
                mounts = json.loads(self.success('cleanup-volume-inventory',[self.docker,'inspect','--format','{{json .Mounts}}',self.container]))
                volumes = [m['Name'] for m in mounts if m.get('Type') == 'volume']
                require(all(re.fullmatch('[A-Za-z0-9_.-]+',volume) for volume in volumes),'cleanup-volume-id-invalid')
                self.success('cleanup-container-remove',[self.docker,'rm','--force','--volumes',self.container])
                code,output = self.run('cleanup-container-absent',[self.docker,'inspect','--format','{{.Id}}',self.container])
                docker_absence_verdict(code,output,'container',self.container)
                for volume in volumes:
                    code,output = self.run('cleanup-volume-absent',[self.docker,'volume','inspect','--format','{{.Name}}',volume])
                    docker_absence_verdict(code,output,'volume',volume)
                details['volumesRemoved'] = len(volumes)
            except Exception:
                details['container'] = False; details['volumes'] = False
        for path in self.private_inputs:
            try:
                info = path.lstat()
                require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600 and info.st_uid == os.getuid(),'cleanup-input-invalid')
                path.unlink()
                require(not path.exists(),'cleanup-input-still-present')
            except Exception:
                details['privateInputs'] = False
        details['privateInputFiles'] = len(self.private_inputs)
        if self.port_owned:
            try:
                with socket.socket() as listener:
                    listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                    listener.bind(('127.0.0.1',8080))
            except OSError:
                details['port8080'] = False
        self.report['cleanup'] = all(details[key] is True for key in ('browser','jvm','stub','container','volumes','privateInputs','port8080'))
        if not self.report['cleanup']:
            raise GateError('cleanup-incomplete',details)
        return details

    def scan_artifacts(self):
        for path in self.known_artifacts:
            scan_file(path,self.secret_patterns)
        count, resolved_links = 0, 0
        def unreadable(error):
            raise GateError('artifact-directory-unreadable')
        for parent, directories, files in os.walk(self.directory,onerror=unreadable,followlinks=False):
            for name in directories:
                path = Path(parent)/name
                require(not path.is_symlink() and path.stat().st_mode & 0o500 == 0o500,'artifact-directory-unreadable')
            for name in files:
                path = Path(parent)/name
                if path in self.private_inputs:
                    require(not path.exists(),'private-input-not-deleted')
                    continue
                target = scan_artifact_file(path,self.directory,self.secret_patterns)
                resolved_links += int(path.is_symlink())
                if target.suffix.lower() in ('.jar','.zip'):
                    scan_zip(target.read_bytes(),self.secret_patterns)
                count += 1
        require(count > 0,'artifact-scan-empty')
        self.report['scanCoverage']['artifact_files'] = count
        return {'files':count,'resolvedExecutableLinks':resolved_links,'hits':0}

    def identity_final(self):
        require(hasattr(self,'jar'),'jar-identity-not-established')
        digest = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        require(digest == self.report['identities']['jarSha256'],'jar-changed')
        require(hashlib.sha256((self.root/'control-plane/package-lock.json').read_bytes()).hexdigest() == self.report['identities']['lockSha256'],'lockfile-changed')
        require(hashlib.sha256((self.root/'scripts/security/verify-release.sh').read_bytes()).hexdigest() == self.report['identities']['scriptSha256'],'script-changed')
        require(self.success('final-source-head',['git','rev-parse','HEAD']).decode().strip() == self.report['identities']['sourceCommit'],'source-head-changed')
        dirty = self.success('final-source-protection',['git','diff','--name-only','HEAD','--','data-plane','control-plane','docs/data-template','docs/contracts'])
        require(not dirty.strip(),'production-inputs-changed')
        return {'jarSha256':digest,'productionInputsUnchanged':True}

    def finalize(self):
        self.gate('cleanup',self.cleanup)
        self.gate('identity_final',self.identity_final)
        self.gate('artifact_scan',self.scan_artifacts)
        self.report['finishedAt'] = now()
        self.report['requestIds'] = [{'requestId':key,'operation':value} for key,value in self.request_ids.items()]
        self.report['browser'] = getattr(self,'browser_result',{'status':'not-run'})
        self.report['checks']['report_scan'].update(status='pass',exitCode=0)
        try:
            final_verdict(self.report)
            code = 0
        except GateError:
            code = 1
        self.report['exitCode'] = code
        self.report['counts'] = {status:sum(check['status'] == status for check in self.report['checks'].values()) for status in ('pass','fail','not-run')}
        try:
            render_report(self.report,self.secret_patterns,self.directory)
        except Exception:
            self.report['checks']['report_scan'].update(status='fail',reason='final-report-scan-failed')
            print('security gate: final-report-scan-failed',flush=True)
            return 1
        # Release canary references only after rendered JSON/Markdown and cleanup checks.
        self.secrets.clear(); self.secret_patterns = ()
        self.token = self.password = self.admin_password = None
        self.inputs.clear()
        print('security gate: outcome ' + str(self.directory/'outcome.json'),flush=True)
        print('security gate: evidence ' + str(self.directory/'evidence.md'),flush=True)
        return code

    def execute(self):
        try:
            if not self.gate('preflight',self.preflight):
                return
            source = self.gate('source_scan',self.scan_source)
            jar = self.gate('jar_scan',self.scan_jar)
            if not (source and jar) or self.fatal:
                return
            self.gate('maven',self.maven_tests)
            if not self.fatal:
                self.gate('frontend_audit',self.frontend_audit)
            if not self.fatal:
                self.gate('backend_audit',self.backend_audit)
            if self.fatal or not self.gate('database_setup',self.database_setup):
                return
            if not self.gate('startup',self.startup):
                return
            self.dataset_path = '/api/v1/data-sources/tushare_pro/datasets'
            self.definition_path = self.dataset_path + '/stock_company'
            self.records_path = self.definition_path + '/records'
            self.gate('S01',self.s01)
            if not self.fatal:
                self.s02_descriptors()
                self.gate('browser',self.browser_probes)
                for name in ('S02','S03','S04'):
                    self.gate(name,lambda n=name:self.group_verdict(n))
            if not self.fatal and self.report['probes']['S03.page']['status'] == 'pass':
                self.http_matrix()
            if not self.fatal:
                self.gate('S08',self.s08)
            self.gate('stub',lambda: self.stub_verdict())
            if not self.fatal:
                self.gate('database_scan',self.database_scan)
        except BaseException:
            self.report['controllerFailure'] = 'interrupted-or-unexpected'
        finally:
            self.exit_code = self.finalize()

    def stub_verdict(self):
        require(self.stub_count == 2 and self.stub_failures == 0,'stub-call-contract-failed')
        return {'observedStubCalls':self.stub_count,'unexpectedStubCalls':self.stub_failures,'configuredUpstream':'loopback-stub','jvmExternalUpstreamCalls':'not-measured'}

def review_cases(directory, check, selected=None):
    if selected in (None,'cleanup-inspect'):
        container, volume = 'a'*64, 'm14-t07-owned-volume'
        absent = {'container':('error: no such object: ' + container + '\n').encode(),
                  'volume':('Error response from daemon: get ' + volume + ': no such volume\n').encode()}
        def inspect_case(kind=None,code=1,output=None):
            runtime = Runtime.__new__(Runtime)
            runtime.browser_cleanup, runtime.port_owned, runtime.fatal = True, False, False
            runtime.running = runtime.browser = runtime.jvm = runtime.stub = None
            runtime.private_inputs, runtime.report, runtime.secret_patterns = [], {'scanCoverage':{}}, ()
            runtime.container, runtime.owner, runtime.docker = container, 'synthetic-owner', 'docker'
            commands = {
                'cleanup-container-owner':(['docker','inspect','--format','{{index .Config.Labels "org.tensor.m14-t07.owner"}}',container],0,b'synthetic-owner\n'),
                'cleanup-volume-inventory':(['docker','inspect','--format','{{json .Mounts}}',container],0,json.dumps([{'Type':'volume','Name':volume}]).encode()),
                'cleanup-container-remove':(['docker','rm','--force','--volumes',container],0,container.encode()),
                'cleanup-container-absent':(['docker','inspect','--format','{{.Id}}',container],1,absent['container']),
                'cleanup-volume-absent':(['docker','volume','inspect','--format','{{.Name}}',volume],1,absent['volume']),
            }
            def run(label,args):
                expected,status,data = commands[label]
                require(args == expected,'cleanup-inspection-test-command')
                if label == 'cleanup-' + str(kind) + '-absent':
                    status,data = code,output
                runtime.scan(data,'subprocess_output')
                return status,data
            runtime.run = run
            return runtime.cleanup()
        check('cleanup-explicit-container-and-volume-absence',inspect_case)
        check('cleanup-explicit-container-daemon-absence',lambda:inspect_case('container',1,('Error response from daemon: No such container: ' + container).encode()))
        check('cleanup-explicit-container-legacy-absence',lambda:inspect_case('container',1,('Error: No such object: ' + container).encode()))
        for kind,identifier in (('container',container),('volume',volume)):
            cases = [('daemon',1,b'Cannot connect to the Docker daemon at unix:///synthetic/docker.sock. Is the docker daemon running?'),
                     ('transport',1,b'error during connect: synthetic I/O error'),
                     ('authorization',1,b'Error response from daemon: authorization denied'),
                     ('unknown',1,b'Error: inspection unavailable'),('empty',1,b''),
                     ('present',0,identifier.encode()),('success-absence',0,absent[kind]),
                     ('wrong-exit',2,absent[kind]),
                     ('wrong-id',1,absent[kind].replace(identifier.encode(),b'other-owned-id')),
                     ('wrong-id-case',1,absent[kind].replace(identifier.encode(),identifier.upper().encode())),
                     ('mixed-error',1,absent[kind] + b'Cannot connect to the Docker daemon')]
            for label,code,output in cases:
                check('cleanup-' + kind + '-inspect-' + label,lambda k=kind,c=code,o=output:inspect_case(k,c,o),True)
    if selected in (None,'cleanup'):
        def cleanup_case(kind):
            with socket.socket() as server:
                server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                server.bind(('127.0.0.1',0)); server.listen(1)
                port = server.getsockname()[1]
                if kind == 'time-wait':
                    with socket.socket() as client:
                        client.settimeout(5); client.connect(('127.0.0.1',port))
                        with server.accept()[0] as connection:
                            connection.settimeout(5); connection.shutdown(socket.SHUT_WR)
                            require(client.recv(1) == b'','cleanup-test-server-eof')
                            client.shutdown(socket.SHUT_WR)
                            require(connection.recv(1) == b'','cleanup-test-client-eof')
                if kind != 'active':
                    server.close()
                runtime = Runtime.__new__(Runtime)
                runtime.browser_cleanup, runtime.port_owned = True, True
                runtime.running = runtime.browser = runtime.jvm = runtime.stub = runtime.container = None
                runtime.private_inputs, runtime.report = [], {}
                original = socket.socket
                class LocalSocket(original):
                    def bind(self,address):
                        require(address == ('127.0.0.1',8080),'cleanup-test-address')
                        return super().bind(('127.0.0.1',port))
                try:
                    socket.socket = LocalSocket
                    return runtime.cleanup()
                finally:
                    socket.socket = original
        check('cleanup-active-listener',lambda:cleanup_case('active'),True)
        check('cleanup-clean-close',lambda:cleanup_case('clean'))
        check('cleanup-time-wait',lambda:cleanup_case('time-wait'))
    if selected in (None,'artifacts'):
        def artifact_case(kind):
            owned = directory/('artifact-' + kind)
            binaries = owned/'snapshot/control-plane/node_modules/.bin'
            binaries.mkdir(parents=True)
            target = binaries.parent/'package/tool.js'
            target.parent.mkdir(); target.write_bytes(b'safe executable')
            link = binaries/'tool'
            if kind == 'escape':
                target = directory/'outside.js'; target.write_bytes(b'safe'); link.symlink_to(os.path.relpath(target,link.parent))
            elif kind == 'unexpected':
                (owned/'unexpected-link').symlink_to(target)
            else:
                link.symlink_to('../package/tool.js')
            if kind == 'missing': target.unlink()
            if kind == 'unreadable': target.chmod(0)
            if kind == 'secret': target.write_bytes(b'M14_T07_TOKEN_artifact_probe')
            runtime = Runtime.__new__(Runtime)
            runtime.directory, runtime.known_artifacts, runtime.private_inputs = owned,set(),[]
            runtime.secret_patterns = patterns(['M14_T07_TOKEN_artifact_probe'])
            runtime.report = {'scanCoverage':{}}
            try:
                return runtime.scan_artifacts()
            finally:
                if target.exists(): target.chmod(0o600)
        check('artifact-owned-npm-link',lambda:artifact_case('clean'))
        for kind in ('escape','unexpected','missing','unreadable','secret'):
            check('artifact-link-' + kind,lambda k=kind:artifact_case(k),True)
    if selected in (None,'health-evidence'):
        def health_case(body,expected_status,expected):
            runtime = Runtime.__new__(Runtime)
            runtime.report = {'probes':{'S01.health':{'status':'not-run'}}}
            runtime.http = lambda *args,**kwargs:(200,{},json.dumps(body).encode())
            runtime.probe('S01.health','/actuator/health',lambda s,b,d:actuator_verdict(s,d,True))
            outcome = runtime.report['probes']['S01.health']
            require(outcome['status'] == expected_status and outcome.get('healthStructure') == expected,'health-evidence-test-failed')
            require('private-detail' not in json.dumps(outcome),'health-evidence-unsafe')
        check('health-failure-safe-structure',lambda:health_case({'status':'UP','components':{'private-detail':'private-detail'},'details':{},'groups':['private-detail']},'fail',{'componentsPresent':True,'detailsPresent':True,'groupsPresent':True,'extraFieldCount':3,'statusUp':True}))
        check('health-status-only-structure',lambda:health_case({'status':'UP'},'pass',{'componentsPresent':False,'detailsPresent':False,'groupsPresent':False,'extraFieldCount':0,'statusUp':True}))
    if selected in (None,'scanner-evidence'):
        def scanner_case():
            owned=directory/'scanner-evidence'; owned.mkdir()
            runtime=Runtime.__new__(Runtime)
            runtime.directory, runtime.snapshot, runtime.maven_repo, runtime.packaged_libraries = owned,owned,owned,owned
            runtime.mvn='mvn'; runtime.inputs={'M14_SECURITY_NVD_API_KEY':''}; runtime.secret_patterns=()
            runtime.run=lambda *args,**kwargs:(1,b'NVD: Invalid API Key (length=0)\nCISA https://www.cisa.gov/kev HTTP 403\nNoDataException: private-detail\n')
            try:
                runtime.backend_audit()
            except GateError as error:
                require(str(error)=='dependency-scanner-failed' and error.details=={'scannerExitCode':1,'reportPresent':False,'failureCategories':['nvd-invalid-api-key','cisa-http-403','vulnerability-data-missing','report-missing']},'scanner-evidence-test-failed')
                return
            raise GateError('scanner-failure-accepted')
        check('scanner-safe-failure-evidence',scanner_case)
    if selected in (None, 'headers', 'reflection', 'short-reflection'):
        from unittest.mock import patch
        def response_case(entries, body=b'{}', status=200, **options):
            runtime = Runtime.__new__(Runtime)
            runtime.fatal, runtime.http_count, runtime.header_failures = False, 0, []
            runtime.secret_patterns = patterns(['M14_T07_TOKEN_review_canary'])
            runtime.report = {'scanCoverage':{}}
            class Socket:
                def settimeout(self, value):
                    pass
                def shutdown(self, value):
                    pass
            class Connection:
                def __init__(self, *args, **kwargs):
                    self.sock = Socket()
                def connect(self):
                    pass
                def request(self, *args, **kwargs):
                    pass
                def close(self):
                    pass
                def getresponse(self):
                    class Response:
                        def read(self, limit):
                            return body
                        def getheaders(self):
                            return entries
                    response = Response()
                    response.status = status
                    return response
            with patch('http.client.HTTPConnection',Connection):
                return runtime.http('/api/v1/probe',**options)
        clean_headers = list({**HEADERS,'cache-control':'no-store'}.items())
    if selected in (None, 'headers'):
        check('headers-all-occurrences-clean',lambda:response_case(clean_headers))
        check('headers-earlier-canary',lambda:response_case(clean_headers + [('X-Debug','M14_T07_TOKEN_review_canary'),('X-Debug','safe')]),True)
        check('headers-ambiguous-security-duplicate',lambda:response_case(clean_headers + [('X-Frame-Options','SAMEORIGIN'),('X-Frame-Options','DENY')]),True)
    if selected in (None, 'reflection'):
        payload = "stock_company' OR 1=1 --"
        def reflected(data):
            status,_,_ = response_case(clean_headers,data,404,submitted=(payload,))
            path_verdict(status)
        check('identifier-reflection-clean',lambda:reflected(b'{}'))
        for number, encoded in enumerate(patterns([payload])):
            check('identifier-reflection-' + str(number),lambda data=encoded:reflected(b'prefix ' + data),True)
        check('identifier-reflection-form-url',lambda:reflected(urllib.parse.quote_plus(payload,safe='').encode()),True)
    if selected in (None, 'short-reflection'):
        collision_id = '10100000-0000-4000-8000-000000000000'
        error = {'requestId':'00000000-0000-4000-8000-000000000000','code':'PARAM_INVALID','message':'Invalid parameter','retryable':False,'fieldErrors':[]}
        def short_response(body, entries=clean_headers):
            status,_,data = response_case(entries,json.dumps(body).encode(),400,submitted=('101',))
            if isinstance(body,dict) and body.get('code') == 'PARAM_INVALID':
                query_verdict(status,json.loads(data))
        check('short-reflection-clean-uuid',lambda:short_response({**error,'requestId':collision_id}))
        check('short-reflection-clean-transport-headers',lambda:short_response(error,clean_headers + [('Content-Length','101'),('Age','1010'),('X-Request-Id',collision_id)]))
        check('short-reflection-validation-header',lambda:short_response(error,clean_headers + [('X-Validation-Error','Invalid pageSize=101')]),True)
        check('short-reflection-other-header',lambda:short_response(error,clean_headers + [('X-Rejected-Value','101')]),True)
        check('short-reflection-invalid-counter-header',lambda:short_response(error,clean_headers + [('Content-Length','Invalid pageSize=101')]),True)
        check('short-reflection-invalid-request-id-header',lambda:short_response(error,clean_headers + [('X-Request-Id','101')]),True)
        check('short-reflection-message',lambda:short_response({**error,'message':'Invalid pageSize 101'}),True)
        check('short-reflection-field-error',lambda:short_response({**error,'fieldErrors':[{'field':'pageSize','message':'Invalid value101'}]}),True)
        check('short-reflection-numeric-field',lambda:short_response({**error,'rejectedValue':101}),True)
        check('short-reflection-nested-field',lambda:short_response({**error,'details':{'value':'101'}}),True)
        check('short-reflection-scalar-body',lambda:short_response(101),True)
        check('short-reflection-string-body',lambda:short_response('101'),True)
        for number,encoded in enumerate(patterns(['101'])):
            check('short-reflection-encoded-' + str(number),lambda value=encoded:short_response({**error,'message':value.decode()}),True)
        check('short-reflection-secret-header',lambda:short_response(error,clean_headers + [('X-Debug','M14_T07_TOKEN_review_canary')]),True)
        check('short-reflection-secret-request-id',lambda:short_response({**error,'requestId':'M14_T07_TOKEN_review_canary'}),True)
    if selected in (None, 'logs'):
        request_id = '11111111-1111-1111-1111-111111111111'
        common = 'tensor.operation.completed requestId=' + request_id + ' operation='
        query = common + 'query pluginId=tushare_pro apiName=stock_company filterNames=[ts_code] page=1 pageSize=50 resultCount=1 totalElements=1 durationMs=2 outcome=success failureStage=none errorCode=none'
        download = common + 'download pluginId=tushare_pro apiName=stock_company paramSummary=[exchange] sourceRowCount=1 insertedRows=1 updatedRows=0 durationMs=2 outcome=success failureStage=none errorCode=none'
        def log_case(text, operation='query'):
            runtime = Runtime.__new__(Runtime)
            runtime.fatal, runtime.secret_patterns = False, patterns(['M14_T07_TOKEN_log_probe'])
            runtime.report = {'scanCoverage':{}}
            runtime.request_ids = {request_id:operation}
            runtime.jvm_log = directory/'review-application.log'
            runtime.jvm_log.write_text(text + '\n')
            return runtime.scan_logs()
        check('completion-query-clean',lambda:log_case(query))
        check('completion-download-clean',lambda:log_case(download,'download'))
        check('completion-full-params',lambda:log_case(query + ' params={tsCode=000001.SZ}'),True)
        check('completion-full-sql',lambda:log_case(query + ' sql=SELECT introduction FROM tushare_pro__stock_company'),True)
        check('ordinary-log-full-sql',lambda:log_case('SELECT introduction FROM tushare_pro__stock_company\n' + query),True)
        check('completion-filter-value',lambda:log_case(query.replace('[ts_code]','[ts_code=000001.SZ]')),True)
        check('completion-download-value',lambda:log_case(download.replace('[exchange]','[exchange=SZSE]'),'download'),True)
        check('completion-duplicate-field',lambda:log_case(query + ' page=1'),True)
        jdbc = 'INFO FlywayExecutor Database: jdbc:mysql://127.0.0.1:3306/synthetic (MySQL 8.4)\n'
        check('ordinary-log-jdbc-metadata',lambda:log_case(jdbc + query))
        check('ordinary-log-jdbc-secret',lambda:log_case(jdbc + 'M14_T07_TOKEN_log_probe\n' + query),True)
        stack = 'java.sql.SQLException: unavailable\n\tat org.springframework.synthetic.Service.read(Service.java:1)\n'
        check('ordinary-log-stack-metadata',lambda:log_case(stack + query))
        for number,encoded in enumerate(patterns(['M14_T07_TOKEN_log_probe'])):
            check('ordinary-log-stack-secret-' + str(number),lambda value=encoded:log_case(stack + '\tat synthetic.Frame ' + value.decode() + '\n' + query),True)
        for number,value in enumerate(('M14_T07_UPSTREAM_DETAIL',"x' OR 1=1 --",'1 OR 1=1','SELECT 1','SELECT introduction FROM synthetic','params={tsCode=000001.SZ}')):
            check('ordinary-log-stack-disclosure-' + str(number),lambda text=value:log_case(stack + '\tat synthetic.Frame ' + text + '\n' + query),True)
        def public_log(value=jdbc):
            runtime=Runtime.__new__(Runtime)
            runtime.secret_patterns=(); runtime.report={'scanCoverage':{}}
            runtime.scan(value.encode(),'http',public=True)
        check('public-jdbc-still-rejected',public_log,True)
        for marker in ('java.sql.','SQLException','org.springframework.'):
            check('public-class-still-rejected-' + marker,lambda value=marker:public_log(value),True)
    if selected in (None, 'npm'):
        inherited = dict(os.environ)
        try:
            os.environ.clear()
            os.environ.update({key:inherited[key] for key in ('PATH','HOME','JAVA_HOME') if key in inherited})
            private = directory/'npm-preflight'
            private.mkdir(mode=0o700)
            runtime = Runtime(private)
        finally:
            os.environ.clear(); os.environ.update(inherited)
        npm = shutil.which('npm',path=runtime.env['PATH'])
        def npm_check():
            code, output = runtime.run('npm-config-self-test',[npm,'--version'],timeout=15)
            require(code == 0 and re.fullmatch(br'11\.[0-9]+\.[0-9]+\s*',output),'npm-config-preflight-failed')
        check('npm-config-preflight',npm_check)

def self_test(directory):
    token = 'M14_T07_TOKEN_self_test_+"/='
    secrets = patterns([token, 'M14_T07_DB_self_test', 'M14_T07_ADMIN_self_test'])
    failures, total = [], 0
    def check(label, action, reject=False):
        nonlocal total
        total += 1
        try:
            action()
            if reject:
                failures.append(label + ':counterexample-accepted')
        except GateError:
            if not reject:
                failures.append(label + ':clean-control-rejected')
        except Exception:
            failures.append(label + ':unexpected-exception')
    for category in ('source', 'http', 'log', 'artifact'):
        check(category + '-clean', lambda: scan_bytes(b'ordinary safe output', secrets))
        for number, secret in enumerate(secrets):
            check(category + '-encoded-' + str(number), lambda s=secret: scan_bytes(b'prefix ' + s, secrets), True)
            injected = directory / (category + '-' + str(number) + '.bin')
            injected.write_bytes(b'prefix ' + secret)
            check(category + '-file-encoded-' + str(number), lambda p=injected: scan_file(p,secrets), True)
    clean = directory / 'clean.bin'
    clean.write_bytes(b'clean')
    check('file-clean', lambda: scan_file(clean, secrets))
    check('file-missing', lambda: scan_file(directory / 'missing', secrets), True)
    clean.chmod(0)
    check('file-unreadable', lambda: scan_file(clean, secrets), True)
    clean.chmod(0o600)
    link = directory / 'link'
    link.symlink_to(clean)
    check('file-symlink', lambda: scan_file(link, secrets), True)
    def archive(contents):
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
            for name, value in contents.items():
                z.writestr(name, value)
        return out.getvalue()
    check('zip-clean', lambda: scan_zip(archive({'nested.jar': archive({'entry': b'clean'})}), secrets))
    for number, secret in enumerate(secrets):
        check('zip-nested-' + str(number), lambda s=secret: scan_zip(archive({'nested.jar': archive({'entry': s})}), secrets), True)
    check('zip-corrupt', lambda: scan_zip(b'PK-not-a-zip', secrets), True)
    check('zip-corrupt-nested', lambda: scan_zip(archive({'nested.jar': b'bad'}), secrets), True)
    check('http-query-clean', lambda: query_verdict(400, {'code': 'PARAM_INVALID'}))
    check('http-query-ignored', lambda: query_verdict(200, {'code': 'PARAM_INVALID'}), True)
    check('http-query-wrong-code', lambda: query_verdict(400, {'code': 'INTERNAL_ERROR'}), True)
    check('http-method-clean', lambda: method_verdict(405))
    check('http-method-write', lambda: method_verdict(200), True)
    check('http-path-clean', lambda: path_verdict(404))
    check('http-path-server-error', lambda: path_verdict(500), True)
    headers = {**HEADERS, 'cache-control': 'no-store'}
    check('headers-clean', lambda: header_verdict(headers, '/api/v1/probe'))
    for name in HEADERS:
        check('header-missing-' + name, lambda n=name: header_verdict({k:v for k,v in headers.items() if k != n}, '/api/v1/probe'), True)
    check('cors-permissive', lambda: header_verdict({**headers, 'access-control-allow-origin': '*'}, '/api/v1/probe'), True)
    check('cache-permissive', lambda: header_verdict({**headers, 'cache-control': 'public'}, '/api/v1/probe'), True)
    check('actuator-clean', lambda: actuator_verdict(200, b'{"status":"UP"}', True))
    check('actuator-details', lambda: actuator_verdict(200, b'{"status":"UP","components":{}}', True), True)
    check('actuator-spa', lambda: actuator_verdict(404, b'<!doctype html><html>spa</html>', False), True)
    check('actuator-exposed', lambda: actuator_verdict(200, b'{}', False), True)
    audit = {'auditReportVersion': 2, 'vulnerabilities': {}, 'metadata': {'vulnerabilities': {k:0 for k in ('info','low','moderate','high','critical','total')}, 'dependencies': {'prod':1, 'dev':1, 'total':2}}}
    check('npm-clean', lambda: npm_verdict(audit, 0))
    for severity in ('high', 'critical'):
        bad = copy.deepcopy(audit)
        bad['metadata']['vulnerabilities'][severity] = 1
        bad['vulnerabilities']['unsafe'] = {'severity':severity, 'via':[]}
        check('npm-' + severity, lambda b=bad: npm_verdict(b, 1), True)
    check('npm-error', lambda: npm_verdict({'error': {'code':'NETWORK'}}, 1), True)
    check('npm-missing', lambda: npm_verdict(None, 0), True)
    check('npm-exit', lambda: npm_verdict(audit, 2), True)
    dependency = {'scanInfo': {'engineVersion':'13.0.0', 'dataSource':[{'name':'NVD API Last Checked','timestamp':'2026-09-06T00:00:00Z'}]}, 'projectInfo': {'reportDate':'2026-09-06T00:00:00Z'}, 'dependencies':[{'fileName':'library-1.jar', 'sha256':'a'*64, 'packages':[{'id':'pkg:maven/org.example/library@1'}], 'projectReferences':['module'], 'vulnerabilities':[]}]}
    inventory = {'library-1.jar': {'coordinates':['org.example:library:1'],'sha256':'a'*64}}
    check('dependency-clean', lambda: dependency_verdict(dependency, 0, inventory, ['module']))
    for severity in ('HIGH', 'CRITICAL', ''):
        bad = copy.deepcopy(dependency)
        bad['dependencies'][0]['vulnerabilities'] = [{'name':'CVE-2026-0001', 'severity':severity}]
        check('dependency-severity-' + (severity or 'missing'), lambda b=bad: dependency_verdict(b, 0, inventory, ['module']), True)
    for label, bad, code in [('missing', None, 0), ('exit', dependency, 1), ('error', {**dependency, 'analysisExceptions':[{}]}, 0), ('empty', {**dependency, 'dependencies':[]}, 0)]:
        check('dependency-' + label, lambda b=bad,c=code: dependency_verdict(b, c, inventory, ['module']), True)
    check('dependency-coverage', lambda: dependency_verdict(dependency, 0, {'other-1.jar':{'coordinates':[],'sha256':'b'*64}}, ['module']), True)
    check('dependency-exact-bytes', lambda: dependency_verdict(dependency, 0, {'library-1.jar':{'coordinates':['org.example:library:1'],'sha256':'b'*64}}, ['module']), True)
    check('dependency-reactor', lambda: dependency_verdict(dependency, 0, inventory, ['missing']), True)
    bundled = copy.deepcopy(dependency)
    bundled['dependencies'][0]['relatedDependencies'] = [{'fileName':'bundled-1.jar', 'sha256':'b'*64, 'packageIds':[{'id':'pkg:maven/org.example/bundled@1'}]}]
    bundled_inventory = {**inventory, 'bundled-1.jar':{'coordinates':['org.example:bundled:1'], 'sha256':'b'*64}}
    check('dependency-bundled-coverage', lambda: dependency_verdict(bundled, 0, bundled_inventory, ['module']))
    check('dependency-bundled-exact-bytes', lambda: dependency_verdict(bundled, 0, {**bundled_inventory, 'bundled-1.jar':{'coordinates':['org.example:bundled:1'], 'sha256':'c'*64}}, ['module']), True)
    for label, extra in [('high', {'vulnerabilities':[{'name':'CVE-2026-0001','severity':'HIGH'}]}), ('analysis-error', {'analysisExceptions':[{}]})]:
        bad = copy.deepcopy(bundled)
        bad['dependencies'][0]['relatedDependencies'][0].update(extra)
        check('dependency-bundled-' + label, lambda b=bad: dependency_verdict(b, 0, inventory, ['module']), True)
    def scanner_report_case(label, output):
        owned = directory / ('scanner-report-' + label); owned.mkdir()
        runtime = Runtime.__new__(Runtime)
        runtime.directory, runtime.snapshot, runtime.maven_repo, runtime.packaged_libraries = owned, owned, owned, owned
        runtime.mvn = 'mvn'; runtime.inputs = {'M14_SECURITY_NVD_API_KEY':''}; runtime.secret_patterns = ()
        runtime.inventory, runtime.modules = inventory, ['module']
        def run(*args, **kwargs):
            (owned / 'dependency-report/dependency-check-report.json').write_text(json.dumps(dependency))
            return 0, output
        runtime.run = run
        return runtime.backend_audit()
    check('scanner-report-clean', lambda: scanner_report_case('clean', b'Analysis Complete'))
    for label, output in [('assembly', b'.NET Assembly Analyzer could not be initialized'), ('node-lock', b'No lock file exists - this will result in false negatives'), ('node-modules', b'the node_modules directory does not exist'), ('oss-credentials', b'Sonatype OSS Index Analyzer disabled due to missing credentials')]:
        check('scanner-report-' + label, lambda l=label,o=output: scanner_report_case(l, o), True)
    reports = []
    for name in TEST_CLASSES:
        file = directory / ('TEST-' + name + '.xml')
        file.write_text('<testsuite name="' + name + '" tests="1" failures="0" errors="0" skipped="0"><testcase name="test"/></testsuite>')
        reports.append(file)
    check('tests-clean', lambda: test_verdict(reports, 0, True))
    check('tests-empty', lambda: test_verdict([], 0, True), True)
    check('tests-exit', lambda: test_verdict(reports, 1, True), True)
    check('tests-enforcer', lambda: test_verdict(reports, 0, False), True)
    zero = directory / 'zero.xml'
    zero.write_text('<testsuite name="ModuleDependencyTest" tests="0" failures="0" errors="0" skipped="0"/>')
    check('tests-zero', lambda: test_verdict([zero] + reports[1:], 0, True), True)
    complete = {'checks': {name:{'status':'pass', 'exitCode':0} for name in REQUIRED}, 'cleanup': True}
    check('final-clean', lambda: final_verdict(complete))
    check('final-cleanup-failed', lambda: final_verdict({**complete, 'cleanup':False}), True)
    check('final-incomplete', lambda: final_verdict({'checks': {'S01':'pass'}, 'cleanup':False}), True)
    failed = copy.deepcopy(complete)
    failed['checks']['S06']['status'] = 'fail'
    check('final-gate-failed', lambda: final_verdict(failed), True)
    check('report-clean', lambda: render_report(complete, secrets, directory))
    check('report-secret', lambda: render_report({**complete, 'note':token}, secrets, directory), True)
    template = directory/'template.json'
    expected_fields = ['field_' + str(n) for n in range(18)]
    template.write_text(json.dumps({'data':[['synthetic skipped value']],'fields':expected_fields}))
    check('template-streamed-fields',lambda:require(top_fields(template) == expected_fields,'template-test-failed'))
    review_cases(directory,check)
    node = subprocess.run([node_tool(), str(directory / 'browser.mjs')],
                          input=json.dumps({'mode':'self-test', 'root':str(Path.cwd()), 'html':HTML}).encode(),
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          env={'PATH':os.environ.get('PATH',''), 'HOME':str(Path.home()), 'LANG':'C.UTF-8'}, timeout=60)
    total += 2
    node_result = json.loads(node.stdout or b'{}')
    if node.returncode != 0 or node_result.get('domSelfTest') != 'pass':
        failures.append('html-parser-counterexample:' + ('accepted' if node_result.get('label') == 'html-parser-counterexample' else 'browser-unavailable'))
    print(json.dumps({'selfTest':'fail' if failures else 'pass', 'checks':total, 'failures':failures}))
    return 1 if failures else 0

if __name__ == '__main__':
    directory = Path(sys.argv[1])
    if len(sys.argv) == 3 and sys.argv[2] == '--self-test':
        sys.exit(self_test(directory))
    try:
        runtime = Runtime(directory)
        def interrupted(signum, frame):
            raise KeyboardInterrupt()
        signal.signal(signal.SIGTERM,interrupted)
        signal.signal(signal.SIGINT,interrupted)
        runtime.execute()
        sys.exit(runtime.exit_code)
    except Exception:
        print('security gate: controller-finalization-failed',flush=True)
        sys.exit(1)
PY
cat >"$owned_root/browser.mjs" <<'JS'
import { createRequire } from 'node:module'
import { createInterface } from 'node:readline'
import { access } from 'node:fs/promises'

const input = createInterface({ input: process.stdin })
const pending = new Map()
let next = 0
let initialize
const initialized = new Promise(resolve => { initialize = resolve })
input.on('line', line => {
  let message
  try { message = JSON.parse(line) } catch { process.exitCode = 1; input.close(); return }
  if (message.id === undefined) initialize(message)
  else if (pending.has(message.id)) {
    pending.get(message.id)(message.ok)
    pending.delete(message.id)
  }
})
const config = await initialized
const require = createRequire(config.root + '/control-plane/package.json')
const { chromium, expect:baseExpect } = require('@playwright/test')
const expect = baseExpect.configure({ timeout:15000 })
function check(value) { if (!value) throw new Error('browser-verdict') }
function send(type, value) {
  const id = next++
  return new Promise((resolve, reject) => {
    pending.set(id, ok => ok ? resolve() : reject(new Error('surface-rejected')))
    process.stdout.write(JSON.stringify({ id, type, value }) + '\n')
  })
}
async function domVerdict(locator, expected) {
  check(await locator.textContent() === expected)
  check(await locator.locator('img,script').count() === 0)
  check(await locator.page().evaluate(() => window.__m14_t07_xss === undefined))
}
if (config.mode === 'preflight') {
  try {
    await access(chromium.executablePath())
    check(require('@playwright/test/package.json').version === '1.62.1')
    process.stdout.write(JSON.stringify({ version:'1.62.1', browserInstalled:true }) + '\n')
  } catch { process.exitCode = 1 }
  input.close()
} else if (config.mode === 'self-test') {
  let browser, stage = 'browser-launch'
  try {
    browser = await chromium.launch({ headless:true })
    stage = 'html-parser-counterexample'
    const context = await browser.newContext()
    await context.route('**/*', route => route.abort())
    const page = await context.newPage()
    await page.setContent('<div id="cell"></div>')
    await page.locator('#cell').evaluate((node, value) => { node.textContent = value }, config.html)
    await domVerdict(page.locator('#cell'), config.html)
    await page.locator('#cell').evaluate((node, value) => { node.innerHTML = value }, config.html)
    let rejected = false
    try { await domVerdict(page.locator('#cell'), config.html) } catch { rejected = true }
    check(rejected)
    process.stdout.write('{"domSelfTest":"pass","checks":2}\n')
  } catch { process.stdout.write(JSON.stringify({domSelfTest:'fail',label:stage}) + '\n'); process.exitCode = 1 }
  finally { if (browser) await browser.close(); input.close() }
} else {
  let browser, context, current = 'S02', fatal = false, requestCount = 0, downloadCount = 0
  const surfaces = new Set()
  const assets = new Set()
  const results = { S02:{status:'not-run'}, S03:{status:'not-run'}, S04:{status:'not-run'} }
  function queue(promise) {
    surfaces.add(promise)
    promise.catch(() => { fatal = true }).finally(() => surfaces.delete(promise))
  }
  async function settled() {
    await Promise.allSettled([...surfaces])
    check(!fatal)
  }
  async function scanPage(page) {
    await settled()
    await send('surface', { category:'dom', data:await page.content() })
    check(await page.evaluate(() => window.__m14_t07_xss === undefined))
  }
  function observe(page) {
    page.on('console', message => queue(send('surface', { category:'console', data:message.text() })))
    page.on('pageerror', error => { fatal = true; queue(send('surface', {category:'pageerror',data:error.message})) })
    page.on('request', request => {
      requestCount++
      if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/downloads') downloadCount++
      queue(send('surface', { category:'request', data:request.url() }))
    })
    page.on('response', response => {
      const action = async () => {
        const url = new URL(response.url())
        if (url.pathname.startsWith('/assets/') && /\.(js|css)$/.test(url.pathname)) assets.add(url.pathname)
        const headerEntries = (await response.headersArray()).map(({ name, value }) => [name, value])
        let body = ''
        if (![204, 304].includes(response.status())) body = await response.text()
        await send('http', { category:'browser-http', path:url.pathname, status:response.status(), headerEntries, body })
      }
      queue(action())
    })
  }
  async function select(page, label, option) {
    const combo = page.getByRole('combobox', { name:label, exact:true })
    await combo.focus(); await combo.press('Enter')
    await page.getByRole('option', { name:option }).click()
    await scanPage(page)
  }
  async function bodyOf(response) {
    await settled()
    const body = await response.json()
    await send('surface', { category:'http', data:JSON.stringify(body) })
    return body
  }
  async function remember(response, body, operation) {
    check(typeof body.requestId === 'string' && /^[a-zA-Z0-9-]{1,100}$/.test(body.requestId))
    check(response.headers()['x-request-id'] === body.requestId)
    await send('requestId', { requestId:body.requestId, operation })
    return body.requestId
  }
  async function query(page) {
    await page.goto('http://127.0.0.1:8080/datasets')
    await select(page, '数据源', 'Tushare Pro')
    const definitionWait = page.waitForResponse(r => new URL(r.url()).pathname === config.definition && r.request().method() === 'GET')
    await select(page, '数据集', /^上市公司基本信息stock_company$/)
    const definitionResponse = await definitionWait
    check(definitionResponse.status() === 200)
    const definition = await bodyOf(definitionResponse)
    check(JSON.stringify(definition.columns.map(c => c.name)) === JSON.stringify(config.fields))
    await page.getByLabel('证券代码 (ts_code)', { exact:true }).fill('000001.SZ')
    const waiting = page.waitForResponse(r => new URL(r.url()).pathname === config.records && r.request().method() === 'GET')
    await page.getByRole('button', { name:'查询', exact:true }).click()
    const response = await waiting, body = await bodyOf(response)
    check(response.status() === 200 && body.totalElements === 1 && body.items.length === 1)
    check(body.items[0].ts_code === '000001.SZ' && body.items[0].exchange === 'SZSE')
    check(body.items[0].source_plugin === 'tushare_pro' && body.items[0].source_api === 'stock_company')
    for (const field of config.fields) check(body.items[0][field] === ({ts_code:'000001.SZ',exchange:'SZSE',introduction:config.html}[field] ?? null))
    await remember(response, body, 'query')
    const cell = page.getByRole('cell').filter({ hasText:config.html })
    await expect(cell).toHaveCount(1)
    await domVerdict(cell, config.html)
    await page.mouse.move(8,8)
    await cell.scrollIntoViewIfNeeded()
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    await cell.getByText(config.html,{exact:true}).hover()
    const tooltip = page.getByRole('tooltip').filter({ hasText:config.html })
    await expect(tooltip).toBeVisible()
    await domVerdict(tooltip, config.html)
    for (const role of ['button','link']) await expect(page.getByRole(role, { name:/新增|编辑|删除|导出/ })).toHaveCount(0)
    await scanPage(page)
    return body.items[0]
  }
  try {
    browser = await chromium.launch({ headless:true })
    context = await browser.newContext({ serviceWorkers:'block' })
    await context.route('**/*', async route => {
      const url = new URL(route.request().url())
      const known = ['/downloads','/datasets','/favicon.ico','/api/v1/data-sources','/api/v1/data-sources/tushare_pro/apis','/api/v1/downloads',config.definition,config.records,'/api/v1/data-sources/tushare_pro/datasets'].includes(url.pathname) || url.pathname.startsWith('/assets/')
      if (url.origin !== 'http://127.0.0.1:8080' || !known || !['GET','POST'].includes(route.request().method())) {
        fatal = true; await route.abort(); return
      }
      try { await send('surface', {category:'request',data:route.request().url()}) } catch { fatal = true; await route.abort(); return }
      await route.continue()
    })
    const page = await context.newPage()
    page.setDefaultTimeout(15000); page.setDefaultNavigationTimeout(15000)
    observe(page)
    await page.goto('http://127.0.0.1:8080/downloads')
    await expect(page.getByRole('heading', { name:'数据下载', level:1 })).toBeVisible()
    await select(page, '数据源', 'Tushare Pro')
    await select(page, '数据接口', /^上市公司基本信息stock_company$/)
    await select(page, '交易所', 'SZSE')
    await expect(page.getByRole('button', { name:'开始下载', exact:true })).toBeEnabled()
    await scanPage(page)
    results.S02 = { status:'pass', definitionColumns:config.fields.length }
    await send('probe', { id:'S02.page', ...results.S02 })
    current = 'S03'
    const first = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/downloads' && r.request().method() === 'POST')
    await page.getByRole('button', { name:'开始下载', exact:true }).click()
    const success = await first, successful = await bodyOf(success)
    check(success.status() === 200 && successful.outcome === 'SUCCESS' && successful.sourceRowCount === 1 && successful.insertedRows === 1 && successful.updatedRows === 0)
    check(JSON.stringify(success.request().postDataJSON()) === JSON.stringify({pluginId:'tushare_pro',apiName:'stock_company',params:{exchange:'SZSE'}}))
    const successId = await remember(success, successful, 'download')
    await expect(page.getByRole('heading', { name:/^下载成功/ })).toBeVisible()
    await scanPage(page)
    const queryPage = await context.newPage()
    queryPage.setDefaultTimeout(15000); queryPage.setDefaultNavigationTimeout(15000)
    observe(queryPage)
    const original = await query(queryPage)
    results.S03 = { status:'pass', httpStatus:200, requestId:successId, sourceRows:1, insertedRows:1, updatedRows:0, htmlText:true }
    await send('probe', { id:'S03.page', ...results.S03 })
    current = 'S04'
    const second = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/downloads' && r.request().method() === 'POST')
    await page.getByRole('button', { name:'开始下载', exact:true }).click()
    const failure = await second, failed = await bodyOf(failure)
    check(failure.status() === 502 && failed.code === 'SOURCE_AUTH_FAILED' && failed.retryable === false)
    const failureId = await remember(failure, failed, 'download')
    const alert = page.getByRole('alert')
    await expect(alert.getByRole('heading', { name:'下载失败' })).toBeVisible()
    await expect(alert).toContainText(failureId)
    await expect(alert).toContainText(failed.message)
    await expect(alert.getByRole('button', { name:'使用原参数重试' })).toHaveCount(0)
    await scanPage(page)
    const independent = await context.newPage()
    independent.setDefaultTimeout(15000); independent.setDefaultNavigationTimeout(15000)
    observe(independent)
    check(JSON.stringify(await query(independent)) === JSON.stringify(original))
    results.S04 = { status:'pass', httpStatus:502, code:'SOURCE_AUTH_FAILED', retryable:false, requestId:failureId, rowUnchanged:true }
    await send('probe', { id:'S04.page', ...results.S04 })
    await settled()
    check(downloadCount === 2)
  } catch {
    results[current] = { status:'fail', reason:fatal ? 'surface-or-network-failure' : 'browser-contract-failed' }
    try { await send('probe', { id:current + '.page', ...results[current] }) } catch {}
    process.exitCode = 1
  } finally {
    let cleanup = true
    try { if (context) await context.close(); if (browser) await browser.close() } catch { cleanup = false; process.exitCode = 1 }
    await Promise.allSettled([...surfaces])
    process.stdout.write(JSON.stringify({ type:'result', value:{ results, cleanup, fatal, requestCount, downloadCount, assets:[...assets] } }) + '\n')
    input.close()
  }
}
JS
python3 -I "$owned_root/gate.py" "$owned_root" "$@"
