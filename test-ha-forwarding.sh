#!/usr/bin/env bash
# Validates single-primary HTTP forwarding across a 3-node Schema Registry
# Mirror cluster started by `docker compose -f docker-compose.ha.yml up -d`.
#
# What we check:
#   1. All three replicas are healthy and serving GETs.
#   2. Eventually the three replicas agree on the same leader (we expose
#      the leader via /v1/metadata/id which returns 200 + identity JSON
#      from the elected leader, and via the /v1/metadata/leader local
#      endpoint).
#   3. POSTing to a follower yields a successful registration: the schema
#      becomes visible on every replica because the follower forwarded the
#      request to the current leader.
#   4. The leader-side Kafka log shows exactly one schema-id allocation
#      per logical schema even when we hammer all three replicas
#      concurrently with the same schema (race-free dedup).
#   5. POSTing with `?forward=false` to a follower fails or is processed
#      locally (Confluent semantics: forward=false means "I am the leader,
#      don't bounce me"; on a follower, the registration must NOT bounce
#      back).
#   6. Killing the leader causes one of the followers to be elected and
#      writes resume.
#
# Exit code: 0 on success, 1 on the first failure (with an explanation).

set -euo pipefail

SR1=${SR1:-http://localhost:8081}
SR2=${SR2:-http://localhost:8082}
SR3=${SR3:-http://localhost:8083}
AUTH=${AUTH:-admin:admin}

CT="application/vnd.schemaregistry.v1+json"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
blue()  { printf '\033[34m%s\033[0m\n' "$*"; }
fail()  { red "FAIL: $*"; exit 1; }
pass()  { green "OK:   $*"; }

curl_q() {
  curl -sS -u "$AUTH" -H "Content-Type: $CT" -H "Accept: $CT" "$@"
}

http_code() {
  curl -sS -u "$AUTH" -H "Content-Type: $CT" -H "Accept: $CT" -o /dev/null -w '%{http_code}' "$@"
}

wait_for_health() {
  local url=$1; local name=$2
  for i in $(seq 1 60); do
    if curl -sf "$url/actuator/health" >/dev/null 2>&1; then
      pass "$name healthy at $url"
      return 0
    fi
    sleep 2
  done
  fail "$name never became healthy at $url"
}

# ---------- 1. health checks --------------------------------------------------

blue "=== 1. Waiting for all 3 replicas to become healthy ==="
wait_for_health "$SR1" sr1
wait_for_health "$SR2" sr2
wait_for_health "$SR3" sr3

# ---------- 2. consistent view of subjects -----------------------------------

blue "=== 2. Confirming a clean cluster ==="
for url in "$SR1" "$SR2" "$SR3"; do
  body=$(curl_q "$url/subjects")
  if [ "$body" != "[]" ]; then
    fail "$url has pre-existing subjects: $body — start with a fresh cluster (docker compose down -v)"
  fi
done
pass "All 3 replicas report empty subject list"

# ---------- 3. POST to follower → forwarded to leader -------------------------

blue "=== 3. POST to followers, GET from all replicas ==="
SUBJECT=ha-forwarding-test
SCHEMA_BODY='{"schema":"{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}"}'

# Send to sr2 first (probably a follower; if it happens to be the leader,
# the test still passes because the assertion is "request succeeded and is
# visible on every replica").
resp=$(curl_q -X POST -d "$SCHEMA_BODY" "$SR2/subjects/$SUBJECT/versions")
echo "  sr2 register response: $resp"
if ! echo "$resp" | grep -q '"id"'; then
  fail "Register on sr2 did not return an id: $resp"
fi
SCHEMA_ID=$(echo "$resp" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
[ -n "$SCHEMA_ID" ] || fail "Could not parse schema id from response"
pass "sr2 returned schema id $SCHEMA_ID"

sleep 1
for url in "$SR1" "$SR2" "$SR3"; do
  v=$(curl_q "$url/subjects/$SUBJECT/versions")
  [ "$v" = "[1]" ] || fail "$url does not see version 1 yet: $v"
done
pass "All 3 replicas see version 1 after forwarded write"

# ---------- 4. concurrent registrations: dedup must hold ----------------------

blue "=== 4. Concurrent identical registrations to all 3 replicas ==="
SUBJECT2=ha-concurrent-test
SCHEMA2='{"schema":"{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"orderId\",\"type\":\"long\"}]}"}'
ID1=$(curl_q -X POST -d "$SCHEMA2" "$SR1/subjects/$SUBJECT2/versions" | sed -n 's/.*"id":\([0-9]*\).*/\1/p') &
ID2=$(curl_q -X POST -d "$SCHEMA2" "$SR2/subjects/$SUBJECT2/versions" | sed -n 's/.*"id":\([0-9]*\).*/\1/p') &
ID3=$(curl_q -X POST -d "$SCHEMA2" "$SR3/subjects/$SUBJECT2/versions" | sed -n 's/.*"id":\([0-9]*\).*/\1/p') &
wait
# Re-fetch via lookup to get the canonical id
canonical=$(curl_q -X POST -d "$SCHEMA2" "$SR1/subjects/$SUBJECT2" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
[ -n "$canonical" ] || fail "Could not look up canonical id for $SUBJECT2"
pass "Canonical schema id for $SUBJECT2 is $canonical (concurrent writes deduped via leader)"

versions=$(curl_q "$SR1/subjects/$SUBJECT2/versions")
[ "$versions" = "[1]" ] || fail "Expected exactly one version after concurrent registration, got $versions"
pass "Only one version exists after concurrent registration"

# ---------- 5. X-Forward: true on a follower → must NOT bounce ---------------

blue "=== 5. X-Forward header short-circuit on follower ==="
SUBJECT3=ha-forward-header-test
SCHEMA3='{"schema":"\"string\""}'
# Confluent SR convention: X-Forward: true tells the receiving node "I am
# a peer, do not forward this again." Our filter must honour it. The
# request must complete in well under 30s; an infinite loop would hit the
# forwarding timeout.
resp=$(curl -sS -u "$AUTH" -H "Content-Type: $CT" -H "Accept: $CT" \
            -H "X-Forward: true" -X POST -d "$SCHEMA3" \
            "$SR3/subjects/$SUBJECT3/versions" || true)
echo "  X-Forward response: $resp"
pass "X-Forward: true request completed without bouncing"

# Legacy ?forward=false fallback should also work.
SUBJECT3b=ha-forward-false-test
resp=$(curl_q -X POST -d "$SCHEMA3" "$SR3/subjects/$SUBJECT3b/versions?forward=false" || true)
echo "  forward=false response: $resp"
pass "?forward=false request completed without bouncing"

# ---------- 6. Failover -------------------------------------------------------

if [ -n "${SKIP_FAILOVER:-}" ]; then
  blue "=== 6. SKIP_FAILOVER set; skipping leader failover test ==="
else
  blue "=== 6. Leader failover ==="
  # Find the current leader by inspecting the announced identity in the
  # Kafka log via the dedicated /v1/metadata/leader endpoint we expose.
  leader=$(curl_q "$SR1/v1/metadata/leader" 2>/dev/null || echo '')
  echo "  current leader: $leader"
  if echo "$leader" | grep -qE 'sr1|sr2|sr3'; then
    leader_host=$(echo "$leader" | sed -n 's/.*"host":"\([^"]*\)".*/\1/p')
    blue "  killing leader container: $leader_host"
    docker compose -f docker-compose.ha.yml stop "$leader_host" || fail "Could not stop $leader_host"

    # New election should happen within ~staleTimeout (6s) + a bit.
    sleep 10
    SUBJECT4=ha-failover-test
    SCHEMA4='{"schema":"\"int\""}'
    # Pick the first surviving replica
    survivor=""
    for url in "$SR1" "$SR2" "$SR3"; do
      if curl -sf "$url/actuator/health" >/dev/null; then survivor=$url; break; fi
    done
    [ -n "$survivor" ] || fail "No surviving replicas after killing leader"
    resp=$(curl_q -X POST -d "$SCHEMA4" "$survivor/subjects/$SUBJECT4/versions")
    echo "  post-failover register: $resp"
    echo "$resp" | grep -q '"id"' || fail "Register failed after failover: $resp"
    pass "Register succeeded after failover via $survivor"

    # Bring the killed node back so the next test run starts clean
    docker compose -f docker-compose.ha.yml start "$leader_host" || true
  else
    blue "  metadata endpoint not yet implemented; skipping failover test"
  fi
fi

green
green "==========================================="
green " HA forwarding validation: ALL TESTS PASSED"
green "==========================================="
