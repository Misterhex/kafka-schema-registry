#!/usr/bin/env bash
set -euo pipefail

SR_URL="${SR_URL:-http://localhost:8081}"
PASS=0
FAIL=0
TOTAL=0
CT="Content-Type: application/vnd.schemaregistry.v1+json"

red()   { printf '\033[0;31m%s\033[0m' "$*"; }
green() { printf '\033[0;32m%s\033[0m' "$*"; }

pass() {
  TOTAL=$((TOTAL + 1))
  PASS=$((PASS + 1))
  echo "  $(green PASS) $1"
}

fail() {
  TOTAL=$((TOTAL + 1))
  FAIL=$((FAIL + 1))
  echo "  $(red FAIL) $1"
  echo "        Expected: $2"
  echo "        Got:      $3"
}

# Assert response body equals expected string exactly
assert_eq() {
  local test_name="$1" expected="$2" url="$3"
  local body
  body=$(curl -s "$url")
  if [ "$body" = "$expected" ]; then
    pass "$test_name"
  else
    fail "$test_name" "$expected" "$body"
  fi
}

# Assert response body contains expected substring
assert_contains() {
  local test_name="$1" expected="$2" url="$3"
  local body
  body=$(curl -s "$url")
  if echo "$body" | grep -qF "$expected"; then
    pass "$test_name"
  else
    fail "$test_name" "contains '$expected'" "$body"
  fi
}

# Assert POST response body equals expected string
assert_post_eq() {
  local test_name="$1" expected="$2" url="$3" data="$4"
  local body
  body=$(curl -s -X POST -H "$CT" -d "$data" "$url")
  if [ "$body" = "$expected" ]; then
    pass "$test_name"
  else
    fail "$test_name" "$expected" "$body"
  fi
}

# Assert POST response body contains expected substring
assert_post_contains() {
  local test_name="$1" expected="$2" url="$3" data="$4"
  local body
  body=$(curl -s -X POST -H "$CT" -d "$data" "$url")
  if echo "$body" | grep -qF "$expected"; then
    pass "$test_name"
  else
    fail "$test_name" "contains '$expected'" "$body"
  fi
}

# Assert PUT response body equals expected string
assert_put_eq() {
  local test_name="$1" expected="$2" url="$3" data="$4"
  local body
  body=$(curl -s -X PUT -H "$CT" -d "$data" "$url")
  if [ "$body" = "$expected" ]; then
    pass "$test_name"
  else
    fail "$test_name" "$expected" "$body"
  fi
}

# Assert DELETE response body equals expected string
assert_delete_eq() {
  local test_name="$1" expected="$2" url="$3"
  local body
  body=$(curl -s -X DELETE "$url")
  if [ "$body" = "$expected" ]; then
    pass "$test_name"
  else
    fail "$test_name" "$expected" "$body"
  fi
}

# Assert DELETE response body contains expected substring
assert_delete_contains() {
  local test_name="$1" expected="$2" url="$3"
  local body
  body=$(curl -s -X DELETE "$url")
  if echo "$body" | grep -qF "$expected"; then
    pass "$test_name"
  else
    fail "$test_name" "contains '$expected'" "$body"
  fi
}

# Assert HTTP status code
assert_status() {
  local test_name="$1" expected_status="$2" url="$3"
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' "$url")
  if [ "$status" = "$expected_status" ]; then
    pass "$test_name"
  else
    fail "$test_name" "HTTP $expected_status" "HTTP $status"
  fi
}

# Assert Content-Type header contains expected string
assert_content_type() {
  local test_name="$1" expected="$2" url="$3"
  local headers
  headers=$(curl -sI "$url")
  if echo "$headers" | grep -iqF "$expected"; then
    pass "$test_name"
  else
    fail "$test_name" "Content-Type contains '$expected'" "$headers"
  fi
}

# ── Wait for service ──────────────────────────────────────────────
echo "Waiting for schema registry at $SR_URL ..."
for i in $(seq 1 60); do
  if curl -sf "$SR_URL/" > /dev/null 2>&1; then
    echo "Schema registry is ready."
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "ERROR: Schema registry not available after 60 attempts."
    exit 1
  fi
  sleep 2
done

echo ""
echo "Running tests..."
echo ""

# ── 1. Root ───────────────────────────────────────────────────────
echo "== Root =="
assert_eq "1. Root returns empty object" '{}' "$SR_URL/"

# ── 2. Empty subjects ────────────────────────────────────────────
echo "== Subjects =="
assert_eq "2. Empty subjects list" '[]' "$SR_URL/subjects"

# ── 3. Global config ─────────────────────────────────────────────
echo "== Config =="
assert_eq "3. Global config" '{"compatibilityLevel":"BACKWARD"}' "$SR_URL/config"

# ── 4. Global mode ───────────────────────────────────────────────
echo "== Mode =="
assert_eq "4. Global mode" '{"mode":"READWRITE"}' "$SR_URL/mode"

# ── 5. Schema types ──────────────────────────────────────────────
echo "== Schemas =="
assert_eq "5. Schema types" '["AVRO","JSON","PROTOBUF"]' "$SR_URL/schemas/types"

# ── 6. Contexts ──────────────────────────────────────────────────
echo "== Contexts =="
assert_eq "6. Contexts" '["."]' "$SR_URL/contexts"

# ── 7. Register Avro schema ──────────────────────────────────────
echo "== Register & Query =="
AVRO_V1='{"schema":"{\"type\":\"record\",\"name\":\"Test\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}"}'
assert_post_eq "7. Register Avro schema" '{"id":1}' "$SR_URL/subjects/test-value/versions" "$AVRO_V1"

# ── 8. Subjects now populated ────────────────────────────────────
assert_eq "8. Subjects now populated" '["test-value"]' "$SR_URL/subjects"

# ── 9. List versions ─────────────────────────────────────────────
assert_eq "9. List versions" '[1]' "$SR_URL/subjects/test-value/versions"

# ── 10. Get version 1 ────────────────────────────────────────────
assert_contains "10. Get version 1" '"subject":"test-value"' "$SR_URL/subjects/test-value/versions/1"

# ── 11. Get schema by ID ─────────────────────────────────────────
assert_contains "11. Get schema by ID" '"schema":' "$SR_URL/schemas/ids/1"

# ── 12. Schema dedup ─────────────────────────────────────────────
assert_post_eq "12. Schema dedup (same schema returns same id)" '{"id":1}' "$SR_URL/subjects/test-value/versions" "$AVRO_V1"

# ── 13. Register v2 (backward compatible) ────────────────────────
AVRO_V2='{"schema":"{\"type\":\"record\",\"name\":\"Test\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"},{\"name\":\"f2\",\"type\":[\"null\",\"string\"],\"default\":null}]}"}'
assert_post_eq "13. Register v2 (backward compat)" '{"id":2}' "$SR_URL/subjects/test-value/versions" "$AVRO_V2"

# ── 14. Get latest version ───────────────────────────────────────
assert_contains "14. Get latest version" '"version":2' "$SR_URL/subjects/test-value/versions/latest"

# ── 15. Lookup schema by content ─────────────────────────────────
assert_post_contains "15. Lookup schema by content" '"version":1' "$SR_URL/subjects/test-value" "$AVRO_V1"

# ── 16. Compatibility check (compatible) ─────────────────────────
echo "== Compatibility =="
COMPAT_SCHEMA='{"schema":"{\"type\":\"record\",\"name\":\"Test\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"},{\"name\":\"f2\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"f3\",\"type\":[\"null\",\"int\"],\"default\":null}]}"}'
assert_post_contains "16. Compatibility check (compatible)" '"is_compatible":true' "$SR_URL/compatibility/subjects/test-value/versions/latest" "$COMPAT_SCHEMA"

# ── 17. Compatibility check (incompatible) ───────────────────────
INCOMPAT_SCHEMA='{"schema":"{\"type\":\"record\",\"name\":\"Test\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"f1\",\"type\":\"int\"}]}"}'
assert_post_contains "17. Compatibility check (incompatible)" '"is_compatible":false' "$SR_URL/compatibility/subjects/test-value/versions/latest" "$INCOMPAT_SCHEMA"

# ── 18. Set subject config ───────────────────────────────────────
echo "== Subject Config =="
assert_put_eq "18. Set subject config" '{"compatibility":"FULL"}' "$SR_URL/config/test-value" '{"compatibility":"FULL"}'

# ── 19. Get subject config ───────────────────────────────────────
assert_eq "19. Get subject config" '{"compatibilityLevel":"FULL"}' "$SR_URL/config/test-value"

# ── 20. Delete subject config ────────────────────────────────────
assert_delete_contains "20. Delete subject config" '"compatibilityLevel":"FULL"' "$SR_URL/config/test-value"

# ── 21. Subjects for schema ID ───────────────────────────────────
echo "== Schema ID Queries =="
assert_contains "21. Subjects for schema ID" '"test-value"' "$SR_URL/schemas/ids/1/subjects"

# ── 22. Versions for schema ID ───────────────────────────────────
assert_contains "22. Versions for schema ID" '"subject":"test-value"' "$SR_URL/schemas/ids/1/versions"

# ── 23. Soft delete version ──────────────────────────────────────
echo "== Delete Versions =="
assert_delete_eq "23. Soft delete version 1" '1' "$SR_URL/subjects/test-value/versions/1"

# ── 24. Versions after soft delete ───────────────────────────────
assert_eq "24. Versions after soft delete" '[2]' "$SR_URL/subjects/test-value/versions"

# ── 25. Hard delete version ──────────────────────────────────────
assert_delete_eq "25. Hard delete version 1" '1' "$SR_URL/subjects/test-value/versions/1?permanent=true"

# ── 26. Register JSON schema ─────────────────────────────────────
echo "== JSON Schema =="
JSON_SCHEMA='{"schema":"{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}","schemaType":"JSON"}'
assert_post_contains "26. Register JSON schema" '"id":' "$SR_URL/subjects/json-test/versions" "$JSON_SCHEMA"

# ── 27. Subject not found (404) ──────────────────────────────────
echo "== Error Cases =="
assert_contains "27. Subject not found (404)" '"error_code":40401' "$SR_URL/subjects/nonexistent/versions"

# ── 28. Schema not found (404) ───────────────────────────────────
assert_contains "28. Schema not found (404)" '"error_code":40403' "$SR_URL/schemas/ids/999"

# ── 29. Content-Type check ───────────────────────────────────────
echo "== Content-Type =="
assert_content_type "29. Content-Type on success" 'application/vnd.schemaregistry.v1+json' "$SR_URL/config"

# ── 30. Error Content-Type check ─────────────────────────────────
assert_content_type "30. Content-Type on error" 'application/vnd.schemaregistry.v1+json' "$SR_URL/schemas/ids/999"

# ── 31. Cluster metadata ─────────────────────────────────────────
echo "== Metadata =="
assert_contains "31. Cluster metadata" '"id":' "$SR_URL/v1/metadata/id"

# ── 32. Server version ───────────────────────────────────────────
assert_contains "32. Server version" '"version":' "$SR_URL/v1/metadata/version"

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "==============================="
echo "  PASSED: $PASS  FAILED: $FAIL  TOTAL: $TOTAL"
echo "==============================="
[ "$FAIL" -eq 0 ] || exit 1
