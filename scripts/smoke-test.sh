#!/bin/sh
set -eu

usage() {
    printf '%s\n' 'Usage: smoke-test.sh [http(s)://host[:port][/base-path]]' >&2
    exit 2
}

fail() {
    printf 'Tensor smoke test failed: %s\n' "$1" >&2
    exit 1
}

[ "$#" -le 1 ] || usage
base_url=${1-http://127.0.0.1:8080}
case "$base_url" in
    http://*|https://*) ;;
    *) usage ;;
esac
case "$base_url" in
    *\?*|*\#*|*[[:space:]]*) usage ;;
esac
authority=${base_url#*://}
authority=${authority%%/*}
case "$authority" in
    ''|*@*) usage ;;
esac
while [ "${base_url%/}" != "$base_url" ]; do
    base_url=${base_url%/}
done

command -v curl >/dev/null 2>&1 || fail 'curl unavailable'
umask 077
response_dir=$(mktemp -d "${TMPDIR:-/tmp}/tensor-smoke.XXXXXXXX") 2>/dev/null \
    || fail 'temporary directory'
cleanup() {
    rm -f "$response_dir/headers" "$response_dir/body" 2>/dev/null
    rmdir "$response_dir" 2>/dev/null
}
trap cleanup 0
trap 'fail "interrupted"' HUP INT TERM

check_secrets() {
    if printf '%s\n' "$body" \
            | grep -Eiq '"(token|password|authorization|cookie|jdbcUrl)"[[:space:]]*:' \
        || grep -Eiq '^[[:space:]]*(Authorization|Cookie|Set-Cookie)[[:space:]]*:' \
            "$response_dir/headers" \
        || grep -iq 'jdbc:mysql:' "$response_dir/headers" "$response_dir/body"; then
        fail "$label secrets"
    fi
    # A trailing sentinel preserves newlines for literal, shell-only comparisons.
    response=$(cat "$response_dir/headers" "$response_dir/body"; printf '.')
    response=${response%.}
    if [ -n "${TENSOR_DB_PASSWORD:-}" ]; then
        case "$response" in
            *"$TENSOR_DB_PASSWORD"*) fail "$label secrets" ;;
        esac
    fi
    if [ -n "${TENSOR_TUSHARE_TOKEN:-}" ]; then
        case "$response" in
            *"$TENSOR_TUSHARE_TOKEN"*) fail "$label secrets" ;;
        esac
    fi
}

probe() {
    label=$1
    path=$2
    kind=$3
    # -q must be first: a user's .curlrc must not add redirects, retries or secrets.
    status=$(curl -q --globoff --silent --request GET --proto '=http,https' \
        --connect-timeout 5 --max-time 15 \
        --dump-header "$response_dir/headers" --output "$response_dir/body" \
        --write-out '%{http_code}' --url "$base_url$path" 2>/dev/null) \
        || fail "$label network"
    [ "$status" = 200 ] || fail "$label status"
    body=$(tr '\r\n' '  ' < "$response_dir/body")
    check_secrets
    if [ "$kind" = html ]; then
        grep -Eiq '^Content-Type:[[:space:]]*text/html([[:space:]]*;|[[:space:]]*$)' \
            "$response_dir/headers" || fail "$label content-type"
        grep -Fq '<div id="app"></div>' "$response_dir/body" || fail "$label content"
    else
        grep -Eiq '^Content-Type:[[:space:]]*application/([a-z0-9.+_-]+\+)?json([[:space:]]*;|[[:space:]]*$)' \
            "$response_dir/headers" || fail "$label content-type"
        if [ "$kind" = health ]; then
            # Boot emits the root status first; a nested component UP is insufficient.
            printf '%s\n' "$body" | grep -Eq '^[[:space:]]*\{[[:space:]]*"status"[[:space:]]*:[[:space:]]*"UP"[[:space:]]*[,}]' \
                || fail "$label content"
        else
            printf '%s\n' "$body" | grep -Eq '^[[:space:]]*\[.*\][[:space:]]*$' \
                || fail "$label content"
            # DataSourceSummary is flat. Keep all three markers in the same entry;
            # these checks are smoke markers, not a complete JSON/DTO validator.
            printf '%s\n' "$body" | tr '}' '\n' \
                | grep -E '"pluginId"[[:space:]]*:[[:space:]]*"tushare_pro"[[:space:]]*(,|$)' \
                | grep -E '"credentialConfigured"[[:space:]]*:[[:space:]]*(true|false)[[:space:]]*(,|$)' \
                | grep -Eq '"downloadAvailable"[[:space:]]*:[[:space:]]*(true|false)[[:space:]]*(,|$)' \
                || fail "$label content"
        fi
    fi
}

probe health /actuator/health health
probe downloads /downloads html
probe datasets /datasets html
probe data-sources /api/v1/data-sources json
printf '%s\n' 'Tensor smoke test passed (4 probes).'
