#!/usr/bin/env bash
# smartgym_api_bugbounty.sh
# Full negative / fuzz / concurrency test for SmartGym API
# Requirements: bash, curl, jq (optional)
# Usage:
#   chmod +x smartgym_api_bugbounty.sh
#   ./smartgym_api_bugbounty.sh

set -o errexit
set -o nounset
set -o pipefail

BASE="http://localhost:8080/api/v1"
JQ_EXISTS=$(command -v jq || true)
USE_JQ=true
if [[ -z "$JQ_EXISTS" ]]; then
  USE_JQ=false
  echo "Warning: jq not found. Output will be raw JSON."
fi

# Helpers
curl_raw() {
  local METHOD="$1"; shift
  local URL="$1"; shift
  local DATA="${1:-}"
  if [[ -n "$DATA" ]]; then
    curl -sS -w "\nHTTP %{http_code}\n" -X "$METHOD" -H "Content-Type: application/json" "$URL" -d "$DATA"
  else
    curl -sS -w "\nHTTP %{http_code}\n" -X "$METHOD" "$URL"
  fi
}

print_resp() {
  local RAW="$1"
  local STATUS=$(echo "$RAW" | tail -n1 | awk '{print $2}')
  local BODY=$(echo "$RAW" | sed '$d')
  echo "----- HTTP $STATUS -----"
  if $USE_JQ; then
    echo "$BODY" | jq .
  else
    echo "$BODY"
  fi
  echo "message: $(echo "$BODY" | (jq -r '.message // .error.message // .message' 2>/dev/null || sed -n '1p'))"
  echo
}

# Test identities used
TEST_EMAIL="badguy@example.com"
TEST_EMAIL2="alice@example.com"
TEST_TRAIN="mike@smartgym.com"
TEST_DNI="99999998"   # likely unlinked
GOOD_DNI="11111111"   # linked in normal tests

echo "=== START: negative & stress tests ==="
echo "Base URL: $BASE"
echo

# 1) Malformed JSON (syntax error)
echo "[1] Malformed JSON -> POST /customers"
R=$(curl -sS -w "\nHTTP %{http_code}\n" -X POST "$BASE/customers" -H "Content-Type: application/json" -d '{"email": "x',) || true
echo "$R"
echo

# 2) Missing required fields (empty body)
echo "[2] Missing required fields -> POST /customers (empty body)"
R=$(curl_raw POST "$BASE/customers" "{}") || true
print_resp "$R"

# 3) Invalid email format
echo "[3] Invalid email format -> POST /customers"
R=$(curl_raw POST "$BASE/customers" '{"email":"not-an-email","name":"X","age":25}') || true
print_resp "$R"

# 4) SQL / NoSQL injection attempts in fields
echo "[4] Injection payloads -> POST /trainers"
INJ='"; DROP TABLE users; --'
R=$(curl_raw POST "$BASE/trainers" "$(jq -nc --arg e "$INJ@example.com" --arg n "$INJ" --argjson age 40 '{email:$e,name:$n,age:$age,specialty:"Strength"}')") || true
print_resp "$R"

# 5) Extremely large payload
echo "[5] Extremely large payload -> POST /customers"
BIG_NAME=$(printf 'A%.0s' $(seq 1 20000))
R=$(curl_raw POST "$BASE/customers" "$(jq -nc --arg email "huge@example.com" --arg name "$BIG_NAME" --argjson age 30 '{email:$email,name:$name,age:$age}')") || true
print_resp "$R"

# 6) XSS attempt in name
echo "[6] XSS payload -> POST /customers"
XSS='<script>alert("x")</script>'
R=$(curl_raw POST "$BASE/customers" "{\"email\":\"$TEST_EMAIL\",\"name\":\"$XSS\",\"age\":30}") || true
print_resp "$R"

# 7) Duplicate creation (idempotency / conflict)
echo "[7] Create same customer twice -> POST /customers"
R1=$(curl_raw POST "$BASE/customers" "{\"email\":\"$TEST_EMAIL2\",\"name\":\"Alice\",\"age\":28}") || true
print_resp "$R1"
R2=$(curl_raw POST "$BASE/customers" "{\"email\":\"$TEST_EMAIL2\",\"name\":\"Alice\",\"age\":28}") || true
print_resp "$R2"

# 8) Link DNI with missing fields / invalid DNI formats
echo "[8] Link DNI invalid -> POST /identity/customer"
R=$(curl_raw POST "$BASE/identity/customer" '{"dni":"","email":""}') || true
print_resp "$R"
R=$(curl_raw POST "$BASE/identity/customer" '{"dni":"abc-123","email":"nope"}') || true
print_resp "$R"

# 9) Access with unlinked DNI (should produce 422)
echo "[9] Access with unlinked DNI -> POST /access"
R=$(curl_raw POST "$BASE/access" "{\"dni\":\"$TEST_DNI\"}") || true
print_resp "$R"

# 10) Access with extremely long DNI
echo "[10] Access with very long DNI"
LONG_DNI=$(printf '9%.0s' $(seq 1 1024))
R=$(curl_raw POST "$BASE/access" "{\"dni\":\"$LONG_DNI\"}") || true
print_resp "$R"

# 11) Routine assign with missing DNI
echo "[11] Routine assign missing dni"
R=$(curl_raw POST "$BASE/routines/assign" '{}') || true
print_resp "$R"

# 12) Routine active with invalid day value (bad enum)
echo "[12] Routine active invalid day"
R=$(curl_raw GET "$BASE/routines/active/$GOOD_DNI?day=FUNDAY") || true
print_resp "$R"

# 13) Progress: invalid numeric types (strings instead of numbers)
echo "[13] Progress with wrong types -> POST /progress"
R=$(curl_raw POST "$BASE/progress" '{"dni":"'"$GOOD_DNI"'","weightKg":"heavy","bodyFatPct":"low","musclePct":"much"}') || true
print_resp "$R"

# 14) Progress: negative numbers, extremely large floats
echo "[14] Progress negative & huge values -> POST /progress"
R=$(curl_raw POST "$BASE/progress" '{"dni":"'"$GOOD_DNI"'","weightKg":-100,"bodyFatPct":5000,"musclePct":-10}') || true
print_resp "$R"

# 15) Booking create: missing required, wrong formats
echo "[15] Booking missing data -> POST /bookings"
R=$(curl_raw POST "$BASE/bookings" '{"customer_email":"","trainer_email":"","date":"bad","time":"xx"}') || true
print_resp "$R"

# 16) Booking in the past (business rule)
PAST="2000-01-01"
echo "[16] Booking in the past -> POST /bookings"
R=$(curl_raw POST "$BASE/bookings" "{\"customer_email\":\"$TEST_EMAIL2\",\"trainer_email\":\"$TEST_TRAIN\",\"date\":\"$PAST\",\"time\":\"09:00\"}") || true
print_resp "$R"

# 17) Create then cancel invalid ID
echo "[17] Cancel non-existent booking -> DELETE /bookings/999999"
R=$(curl_raw DELETE "$BASE/bookings/999999") || true
print_resp "$R"

# 18) Concurrent booking race (N parallel attempts for same slot)
echo "[18] Concurrency race -> multiple POST /bookings same slot"
SLOT_DATE="$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d '1 day' +%Y-%m-%d)"
SLOT_TIME="12:00"
CONC=6
echo "Launching $CONC concurrent booking attempts for $SLOT_DATE $SLOT_TIME"
for i in $(seq 1 $CONC); do
  (
    curl -sS -X POST "$BASE/bookings" -H "Content-Type: application/json" \
      -d "{\"customer_email\":\"user$i@example.com\",\"trainer_email\":\"$TEST_TRAIN\",\"date\":\"$SLOT_DATE\",\"time\":\"$SLOT_TIME\",\"note\":\"concurrent-$i\"}" \
      -w "\nHTTP %{http_code}\n"
  ) &
done
wait
echo "Concurrent booking attempts finished."
echo

# 19) Mass create to test memory limits (small loop—increase with caution)
echo "[19] Mass create customers (50) — adjust count if needed"
for i in $(seq 1 50); do
  curl -sS -X POST "$BASE/customers" -H "Content-Type: application/json" \
    -d "{\"email\":\"mass$i@example.com\",\"name\":\"Mass $i\",\"age\":20}" -o /dev/null -w "mass$i: HTTP %{http_code}\n"
done
echo

# 20) Large header / very long URL
echo "[20] Long header"
LONG_HEADER=$(printf 'H%.0s' $(seq 1 8000))
curl -sS -X GET "$BASE/customers/$TEST_EMAIL2" -H "X-Long: $LONG_HEADER" -w "\nHTTP %{http_code}\n" || true
echo

# 21) Malformed method (unsupported)
echo "[21] Unsupported HTTP method"
R=$(curl_raw PATCH "$BASE/customers" '{"email":"x@y.com"}' || true)
print_resp "$R"

# 22) Test invalid endpoints (404)
echo "[22] Invalid route 404"
R=$(curl_raw GET "$BASE/this-route-does-not-exist") || true
print_resp "$R"

# 23) Attempt to inject header-breaking strings
echo "[23] Header injection attempt"
curl -sS -X GET "$BASE/customers/$TEST_EMAIL2" -H $'X-Test: line1\r\nline2: bad' -w "\nHTTP %{http_code}\n" || true
echo

# 24) Test content-type mismatch (send XML)
echo "[24] Wrong content-type (XML payload)"
curl -sS -X POST "$BASE/customers" -H "Content-Type: application/xml" -d "<customer><email>x@x.com</email></customer>" -w "\nHTTP %{http_code}\n" || true
echo

# 25) Repeated delete (idempotency)
echo "[25] Repeated delete same id"
# create a booking first
CB=$(curl_raw POST "$BASE/bookings" "{\"customer_email\":\"$TEST_EMAIL2\",\"trainer_email\":\"$TEST_TRAIN\",\"date\":\"$SLOT_DATE\",\"time\":\"13:00\"}") || true
print_resp "$CB"
BID=$(echo "$CB" | sed '$d' | jq -r '.data.id // empty' 2>/dev/null || echo "")
if [[ -n "$BID" ]]; then
  echo "Deleting booking id $BID twice:"
  curl -sS -X DELETE "$BASE/bookings/$BID" -w "\nHTTP %{http_code}\n" || true
  curl -sS -X DELETE "$BASE/bookings/$BID" -w "\nHTTP %{http_code}\n" || true
else
  echo "Booking id not found; skipping repeated delete."
fi
echo

# 26) Fuzz-ish: random bytes in strings (non-printables)
echo "[26] Random bytes in JSON strings (portable)"
RAND=$(head -c 100 /dev/urandom | xxd -p | tr -d '\n' | head -c 200)
R=$(curl_raw POST "$BASE/customers" "{\"email\":\"rand_$RAND@example.com\",\"name\":\"rand\",\"age\":30}" || true)
print_resp "$R"

echo "=== DONE: bugbounty tests ==="
echo "Review responses above for unexpected 2xx on invalid inputs, 500s, or state corruption."