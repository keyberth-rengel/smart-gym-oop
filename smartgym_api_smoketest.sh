#!/usr/bin/env bash
# smartgym_api_smoketest.sh
# Suite de pruebas: casos negativos, límites, fuzz ligero y concurrencia de la API SmartGym.
# Requisitos: bash, curl, jq (opcional)
# Uso:
#   chmod +x smartgym_api_smoketest.sh
#   ./smartgym_api_smoketest.sh

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

# Códigos esperados por prueba (separados por '|').
# Compatibilidad con bash 3.2 (macOS) sin arrays asociativos.
get_expect() {
  case "$1" in
    1) echo "400" ;;
    2) echo "400" ;;
    3) echo "400" ;;
    4) echo "201|409" ;;
    5) echo "201|400" ;;
    6) echo "400|409" ;;
    7a) echo "409" ;;
    7b) echo "409" ;;
    8a) echo "400" ;;
    8b) echo "400" ;;
    9) echo "422" ;;
    10) echo "400|422" ;;
    11) echo "400" ;;
    12) echo "422" ;;
    13) echo "400|422" ;;
    14) echo "400|422" ;;                # Bean validation or domain range
    15) echo "400|422" ;;
    16) echo "422" ;;
    17) echo "422" ;;
    18) echo "MIX:201+409" ;;
    19) echo "201" ;;
    20) echo "200" ;;
    21) echo "405" ;;
    22) echo "404" ;;
    23) echo "200" ;;
    24) echo "415" ;;
    25a) echo "201" ;;
    25b) echo "204" ;;               # DELETE now returns 204 No Content
    25c) echo "422" ;;
    26) echo "201|400" ;;
    27) echo "201" ;;              # Unique trainer creation (positive)
    28) echo "200" ;;              # Access with linked DNI (positive)
    29) echo "201" ;;              # Progress valid metrics (should create successfully)
    30a) echo "201" ;;             # Routine assign valid
    30b) echo "200" ;;             # Routine active valid day
    31a) echo "201" ;;             # Booking create valid
    31b) echo "200" ;;             # Booking list all
    32) echo "200" ;;              # Health endpoint
    33) echo "200" ;;              # Identity resolve by DNI
    34) echo "200" ;;              # Customer by DNI lookup
    *) echo "" ;;
  esac
}

# Descripción legible por etiqueta de prueba
get_desc() {
  case "$1" in
    1) echo "Malformed JSON" ;;
    2) echo "Missing required fields" ;;
    3) echo "Invalid email format" ;;
    4) echo "Injection payload (trainer create)" ;;
    5) echo "Extremely large name field" ;;
    6) echo "XSS payload in name" ;;
    7a) echo "Duplicate customer (first attempt conflict)" ;;
    7b) echo "Duplicate customer (second attempt)" ;;
    8a) echo "Link DNI empty fields" ;;
    8b) echo "Link DNI invalid pattern" ;;
    9) echo "Access unlinked DNI" ;;
    10) echo "Access very long DNI" ;;
    11) echo "Routine assign missing dni" ;;
    12) echo "Routine active invalid day enum" ;;
    13) echo "Progress wrong JSON types" ;;
    14) echo "Progress negative/huge values" ;;
    15) echo "Booking missing data" ;;
    16) echo "Booking in the past" ;;
    17) echo "Cancel non-existent booking" ;;
    18) echo "Concurrent booking race" ;;
    19) echo "Mass create customers" ;;
    20) echo "Long header retrieval" ;;
    21) echo "Unsupported HTTP method" ;;
    22) echo "Invalid route 404" ;;
    23) echo "Header injection attempt" ;;
    24) echo "Wrong content-type (XML)" ;;
    25a) echo "Create booking for delete test" ;;
    25b) echo "First delete booking" ;;
    25c) echo "Second delete booking (not found)" ;;
    26) echo "Random bytes in email" ;;
    27) echo "Trainer create unique" ;;
    28) echo "Access linked DNI success" ;;
    29) echo "Progress valid metrics" ;;
    30a) echo "Routine assign valid" ;;
    30b) echo "Routine active valid day" ;;
    31a) echo "Booking create valid slot" ;;
    31b) echo "Booking list all" ;;
    32) echo "Health endpoint" ;;
    33) echo "Identity resolve" ;;
    34) echo "Customer by DNI" ;;
    *) echo "" ;;
  esac
}

FAILED=0

# Ayudante de aserción: etiqueta y código real
assert_status() {
  local LABEL="$1"; shift
  local ACTUAL="$1"; shift
  local EXP="$(get_expect "$LABEL")"
  if [[ -z "$EXP" ]]; then
    echo "[WARN] No expected status configured for $LABEL (actual=$ACTUAL)"; return
  fi
  # Split acceptable codes
  IFS='|' read -r -a ALLOWED <<< "$EXP"
  local MATCH=0
  for code in "${ALLOWED[@]}"; do
    if [[ "$code" == "$ACTUAL" || "$code" == MIX:* ]]; then
      if [[ "$code" == MIX:* ]]; then
        # Just informational for concurrency race
        MATCH=1
      else
        MATCH=1
      fi
    fi
  done
  local DESC="$(get_desc "$LABEL")"
  if [[ $MATCH -eq 1 ]]; then
    echo "[PASS] Test $LABEL ($DESC) status=$ACTUAL expected=$EXP"
  else
    echo "[FAIL] Test $LABEL ($DESC) status=$ACTUAL expected=$EXP"
    FAILED=$((FAILED+1))
  fi
}

# Funciones auxiliares
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
  if [[ -n "$BODY" ]]; then
    if $USE_JQ; then
      echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
    else
      echo "$BODY"
    fi
    echo "message: $(echo "$BODY" | (jq -r '.message // .error.message // .message' 2>/dev/null || sed -n '1p'))"
  else
    echo "(no body)"
  fi
  echo
}

# Identidades base usadas en las pruebas
TEST_EMAIL="badguy@example.com"
TEST_EMAIL2="alice@example.com"
TEST_TRAIN="mike@smartgym.com"
TEST_DNI="99999998"   # likely unlinked
GOOD_DNI="11111111"   # linked in normal tests

echo "=== START: setup + negative & stress tests ==="
echo "Base URL: $BASE"
echo

# --- Preparación: crear entrenador y cliente base y vincular DNI ---
echo "[SETUP] Creating baseline trainer, customer, and linking DNI for positive flows"
BASELINE_TRAINER_PAYLOAD='{"email":"'"$TEST_TRAIN"'","name":"Mike","age":35,"specialty":"Strength"}'
BASELINE_CUSTOMER_PAYLOAD='{"email":"'"$TEST_EMAIL2"'","name":"Alice","age":28}'

TB=$(curl_raw POST "$BASE/trainers" "$BASELINE_TRAINER_PAYLOAD" || true)
print_resp "$TB"
CB=$(curl_raw POST "$BASE/customers" "$BASELINE_CUSTOMER_PAYLOAD" || true)
print_resp "$CB"
LINK=$(curl_raw POST "$BASE/identity/customer" '{"dni":"'"$GOOD_DNI"'","email":"'"$TEST_EMAIL2"'"}' || true)
print_resp "$LINK"

echo "[SETUP] Completed baseline entities (ignore conflicts if they already existed)."
echo

# 1) JSON malformado (error de sintaxis)
echo "[1] Malformed JSON -> POST /customers"
# Enviar JSON truncado intencionalmente sin coma extra que rompía la línea
R=$(curl -sS -w "\nHTTP %{http_code}\n" -X POST "$BASE/customers" -H "Content-Type: application/json" -d '{"email":"x"') || true
echo "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 1 "$STATUS"
echo

# 2) Campos requeridos faltantes (cuerpo vacío)
echo "[2] Missing required fields -> POST /customers (empty body)"
R=$(curl_raw POST "$BASE/customers" "{}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 2 "$STATUS"

# 3) Formato de email inválido
echo "[3] Invalid email format -> POST /customers"
R=$(curl_raw POST "$BASE/customers" '{"email":"not-an-email","name":"X","age":25}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 3 "$STATUS"

# 4) Intentos de inyección en campos
echo "[4] Injection payloads -> POST /trainers"
INJ='"; DROP TABLE users; --'
if $USE_JQ; then
  R=$(curl_raw POST "$BASE/trainers" "$(jq -nc --arg e "$INJ@example.com" --arg n "$INJ" --argjson age 40 '{email:$e,name:$n,age:$age,specialty:"Strength"}')") || true
else
  R=$(curl_raw POST "$BASE/trainers" "{\"email\":\"${INJ}@example.com\",\"name\":\"${INJ}\",\"age\":40,\"specialty\":\"Strength\"}") || true

# 5) Payload extremadamente grande
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 4 "$STATUS"
fi
# (block 4 ends)
echo "[5] Extremely large payload -> POST /customers"
if $USE_JQ; then
  BIG_NAME=$(printf 'A%.0s' $(seq 1 20000))
  R=$(curl_raw POST "$BASE/customers" "$(jq -nc --arg email "huge@example.com" --arg name "$BIG_NAME" --argjson age 30 '{email:$email,name:$name,age:$age}')") || true
else
  BIG_NAME=$(printf 'A%.0s' $(seq 1 2048)) # shorter fallback
  R=$(curl_raw POST "$BASE/customers" "{\"email\":\"huge@example.com\",\"name\":\"$BIG_NAME\",\"age\":30}") || true
fi
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 5 "$STATUS"

# 6) Intento XSS en nombre
echo "[6] XSS payload -> POST /customers"
XSS='<script>alert("x")</script>'
# Escape embedded quotes for JSON safety
XSS_ESCAPED=$(printf '%s' "$XSS" | sed 's/"/\\"/g')
R=$(curl_raw POST "$BASE/customers" "{\"email\":\"$TEST_EMAIL\",\"name\":\"$XSS_ESCAPED\",\"age\":30}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 6 "$STATUS"

# 7) Creación duplicada (idempotencia / conflicto)
echo "[7] Create same customer twice -> POST /customers"
R1=$(curl_raw POST "$BASE/customers" "{\"email\":\"$TEST_EMAIL2\",\"name\":\"Alice\",\"age\":28}") || true
print_resp "$R1"
STATUS=$(echo "$R1" | tail -n1 | awk '{print $2}')
assert_status 7a "$STATUS"
R2=$(curl_raw POST "$BASE/customers" "{\"email\":\"$TEST_EMAIL2\",\"name\":\"Alice\",\"age\":28}") || true
print_resp "$R2"
STATUS=$(echo "$R2" | tail -n1 | awk '{print $2}')
assert_status 7b "$STATUS"

# 8) Vincular DNI con campos vacíos / formato inválido
echo "[8] Link DNI invalid -> POST /identity/customer"
R=$(curl_raw POST "$BASE/identity/customer" '{"dni":"","email":""}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 8a "$STATUS"
R=$(curl_raw POST "$BASE/identity/customer" '{"dni":"abc-123","email":"nope"}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 8b "$STATUS"

# 9) Acceso con DNI no vinculado (debe dar 422)
echo "[9] Access with unlinked DNI -> POST /access"
R=$(curl_raw POST "$BASE/access" "{\"dni\":\"$TEST_DNI\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 9 "$STATUS"

# 10) Acceso con DNI extremadamente largo
echo "[10] Access with very long DNI"
LONG_DNI=$(printf '9%.0s' $(seq 1 1024))
R=$(curl_raw POST "$BASE/access" "{\"dni\":\"$LONG_DNI\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 10 "$STATUS"

# 11) Asignar rutina sin DNI
echo "[11] Routine assign missing dni"
R=$(curl_raw POST "$BASE/routines/assign" '{}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 11 "$STATUS"

# 12) Rutina activa con día inválido (enum incorrecto)
echo "[12] Routine active invalid day"
R=$(curl_raw GET "$BASE/routines/active/$GOOD_DNI?day=FUNDAY") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 12 "$STATUS"

# 13) Progreso: tipos numéricos inválidos (cadenas en lugar de números)
echo "[13] Progress with wrong types -> POST /progress"
R=$(curl_raw POST "$BASE/progress" '{"dni":"'"$GOOD_DNI"'","weightKg":"heavy","bodyFatPct":"low","musclePct":"much"}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 13 "$STATUS"

# 14) Progreso: valores negativos / excesivos
echo "[14] Progress negative & huge values -> POST /progress"
R=$(curl_raw POST "$BASE/progress" '{"dni":"'"$GOOD_DNI"'","weightKg":-100,"bodyFatPct":5000,"musclePct":-10}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 14 "$STATUS"

# 15) Reserva: datos faltantes / formatos incorrectos
echo "[15] Booking missing data -> POST /bookings (no date field)"
R=$(curl_raw POST "$BASE/bookings" '{"customer_email":"","trainer_email":"","time":"xx"}') || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 15 "$STATUS"

# 16) Reserva en el pasado (regla de negocio)
echo "[16] Booking time in the past today -> POST /bookings"
# Generar una hora pasada segura: minuto actual - 5 (o 00:01 fallback)
CUR_H=$(date +%H)
CUR_M=$(date +%M)
if [ $CUR_M -ge 5 ]; then
  PAST_MIN=$(printf '%02d' $((10#$CUR_M - 5)))
  PAST_TIME="$CUR_H:$PAST_MIN"
else
  PAST_TIME="00:01"
fi
R=$(curl_raw POST "$BASE/bookings" "{\"customer_email\":\"$TEST_EMAIL2\",\"trainer_email\":\"$TEST_TRAIN\",\"time\":\"$PAST_TIME\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 16 "$STATUS"

# 17) Cancelar ID inexistente
echo "[17] Cancel non-existent booking -> DELETE /bookings/999999"
R=$(curl_raw DELETE "$BASE/bookings/999999") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 17 "$STATUS"

# 18) Carrera concurrente de reservas (N intentos paralelos mismo horario)
echo "[18] Concurrency race -> multiple POST /bookings same slot (today)"
# Seleccionar un horario futuro estable dentro del mismo día
CUR_H=$(date +%H)
CUR_M=$(date +%M)
if [ $CUR_M -ge 55 ]; then
  FUTURE_MIN=00
  FUTURE_H=$(( (10#$CUR_H + 1) % 24 ))
  SLOT_TIME="$(printf '%02d' $FUTURE_H):$FUTURE_MIN"
else
  FUTURE_MIN=$(printf '%02d' $((10#$CUR_M + 5)))
  SLOT_TIME="$CUR_H:$FUTURE_MIN"
fi
CONC=6
echo "Launching $CONC concurrent booking attempts for today $SLOT_TIME"
echo "[18a] Pre-creating $CONC customers for concurrency test"
for i in $(seq 1 $CONC); do
  curl -sS -X POST "$BASE/customers" -H "Content-Type: application/json" \
    -d "{\"email\":\"user$i@example.com\",\"name\":\"User $i\",\"age\":25}" -o /dev/null || true
done
echo "[18b] Launching booking race"
for i in $(seq 1 $CONC); do
  (
    curl -sS -X POST "$BASE/bookings" -H "Content-Type: application/json" \
      -d "{\"customer_email\":\"user$i@example.com\",\"trainer_email\":\"$TEST_TRAIN\",\"time\":\"$SLOT_TIME\",\"note\":\"concurrent-$i\"}" \
      -w "\nHTTP %{http_code}\n"
  ) &
done
wait
echo "Concurrent booking attempts finished."
echo "[18] Evaluating concurrency outcomes (expect mix of 201 & 409)"
CONC_STATUSES=$(curl -sS "$BASE/bookings" | grep -o 'HTTP [0-9][0-9][0-9]' || true)
echo "[INFO] Review individual outputs above."
echo "[NOTE] Expected: $(get_expect 18)"
echo

# 19) Creación masiva para estrés ligero (50 clientes)
echo "[19] Mass create customers (50) — adjust count if needed"
for i in $(seq 1 50); do
  curl -sS -X POST "$BASE/customers" -H "Content-Type: application/json" \
    -d "{\"email\":\"mass$i@example.com\",\"name\":\"Mass $i\",\"age\":20}" -o /dev/null -w "mass$i: HTTP %{http_code}\n"
done
echo

# 20) Header muy largo
echo "[20] Long header"
LONG_HEADER=$(printf 'H%.0s' $(seq 1 8000))
curl -sS -X GET "$BASE/customers/$TEST_EMAIL2" -H "X-Long: $LONG_HEADER" -w "\nHTTP %{http_code}\n" || true
echo

# 21) Método HTTP no soportado
echo "[21] Unsupported HTTP method"
R=$(curl_raw PATCH "$BASE/customers" '{"email":"x@y.com"}' || true)
print_resp "$R"

# 22) Endpoint inválido (404)
echo "[22] Invalid route 404"
R=$(curl_raw GET "$BASE/this-route-does-not-exist") || true
print_resp "$R"

# 23) Intento de inyección en header
echo "[23] Header injection attempt"
curl -sS -X GET "$BASE/customers/$TEST_EMAIL2" -H $'X-Test: line1\r\nline2: bad' -w "\nHTTP %{http_code}\n" || true
echo

# 24) Content-Type incorrecto (XML en vez de JSON)
echo "[24] Wrong content-type (XML payload)"
curl -sS -X POST "$BASE/customers" -H "Content-Type: application/xml" -d "<customer><email>x@x.com</email></customer>" -w "\nHTTP %{http_code}\n" || true
echo

# 25) Eliminación repetida (idempotencia)
echo "[25] Repeated delete same id (future slot)"
# Calcular hora futura para creación que se pueda luego eliminar
CUR_H=$(date +%H)
CUR_M=$(date +%M)
if [ $CUR_M -ge 50 ]; then
  FUTURE_MIN=00
  FUTURE_H=$(( (10#$CUR_H + 1) % 24 ))
  DEL_TIME="$(printf '%02d' $FUTURE_H):$FUTURE_MIN"
else
  FUTURE_MIN=$(printf '%02d' $((10#$CUR_M + 10)))
  DEL_TIME="$CUR_H:$FUTURE_MIN"
fi
# Evitar colisión con SLOT_TIME de test 18 (misma hora:min) si coincide
if [[ -n "${SLOT_TIME:-}" && "$DEL_TIME" == "$SLOT_TIME" ]]; then
  ADJ_H=${DEL_TIME%:*}
  ADJ_M=${DEL_TIME#*:}
  NEW_M=$((10#$ADJ_M + 7))
  if [ $NEW_M -ge 60 ]; then
    ADJ_H=$(( (10#$ADJ_H + 1) % 24 ))
    NEW_M=$(( NEW_M - 60 ))
  fi
  DEL_TIME="$(printf '%02d' $ADJ_H):$(printf '%02d' $NEW_M)"
fi
CB=$(curl_raw POST "$BASE/bookings" "{\"customer_email\":\"$TEST_EMAIL2\",\"trainer_email\":\"$TEST_TRAIN\",\"time\":\"$DEL_TIME\"}") || true
print_resp "$CB"
BID=$(echo "$CB" | sed '$d' | jq -r '.data.id // empty' 2>/dev/null || echo "")
STATUS_CB=$(echo "$CB" | tail -n1 | awk '{print $2}')
assert_status 25a "$STATUS_CB"
if [[ -n "$BID" ]]; then
  echo "Deleting booking id $BID twice:"
  DEL1=$(curl -sS -w "\nHTTP %{http_code}\n" -X DELETE "$BASE/bookings/$BID" || true)
  CODE1=$(echo "$DEL1" | tail -n1 | awk '{print $2}')
  print_resp "$DEL1"
  assert_status 25b "$CODE1"
  DEL2=$(curl -sS -w "\nHTTP %{http_code}\n" -X DELETE "$BASE/bookings/$BID" || true)
  CODE2=$(echo "$DEL2" | tail -n1 | awk '{print $2}')
  print_resp "$DEL2"
  assert_status 25c "$CODE2"
else
  echo "[WARN] Booking id not found; skipping delete checks."
fi
echo

# 26) Fuzz ligero: bytes aleatorios en email
echo "[26] Random bytes in JSON strings (portable)"
RAND=$(head -c 100 /dev/urandom | xxd -p | tr -d '\n' | head -c 200)
R=$(curl_raw POST "$BASE/customers" "{\"email\":\"rand_$RAND@example.com\",\"name\":\"rand\",\"age\":30}" || true)
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 26 "$STATUS"

echo "=== DONE: bugbounty tests ==="
echo "Review responses above for unexpected 2xx on invalid inputs, 500s, or state corruption."
echo
########################################################
# Flujos positivos (27+)
########################################################

# 27) Crear entrenador único
echo "[27] Trainer create unique -> POST /trainers"
UNIQUE_TRAINER="unique_$(date +%s)@smartgym.com"
R=$(curl_raw POST "$BASE/trainers" "{\"email\":\"$UNIQUE_TRAINER\",\"name\":\"Unique T\",\"age\":33,\"specialty\":\"Cardio\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 27 "$STATUS"

# 28) Acceso exitoso con DNI vinculado
echo "[28] Access linked DNI success -> POST /access"
R=$(curl_raw POST "$BASE/access" "{\"dni\":\"$GOOD_DNI\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 28 "$STATUS"

# 29) Progreso válido (cliente/DNI nuevo para evitar duplicado diario)
PROG_EMAIL="progress_$(date +%s)@example.com"
PROG_DNI="22$(printf '%06d' $RANDOM)"  # ensure 8 digits starting with 22
echo "[29] Progress valid metrics (fresh) -> setup customer + identity"
curl -sS -X POST "$BASE/customers" -H "Content-Type: application/json" -d "{\"email\":\"$PROG_EMAIL\",\"name\":\"Prog User\",\"age\":30}" -o /dev/null || true
curl -sS -X POST "$BASE/identity/customer" -H "Content-Type: application/json" -d "{\"dni\":\"$PROG_DNI\",\"email\":\"$PROG_EMAIL\"}" -o /dev/null || true
echo "[29] Progress valid metrics -> POST /progress (dni=$PROG_DNI)"
R=$(curl_raw POST "$BASE/progress" "{\"dni\":\"$PROG_DNI\",\"weightKg\":80.0,\"bodyFatPct\":18.0,\"musclePct\":42.0}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 29 "$STATUS"

# 30) Asignar rutina y consultar bloque activo
echo "[30] Routine assign valid -> POST /routines/assign"
R=$(curl_raw POST "$BASE/routines/assign" "{\"dni\":\"$GOOD_DNI\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 30a "$STATUS"
echo "[30] Routine active valid day (monday) -> GET /routines/active"
R=$(curl_raw GET "$BASE/routines/active/$GOOD_DNI?day=MONDAY") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 30b "$STATUS"

# 31) Crear reserva válida y listar todas
echo "[31] Booking create valid slot (today) -> POST /bookings (next free future minute)"
# Obtener tiempos ocupados existentes (pueden persistir de ejecuciones previas)
USED_TIMES=$(curl -sS "$BASE/bookings" | jq -r '.data[].time' 2>/dev/null || echo "")
CUR_H=$(date +%H)
CUR_M=$(date +%M)
PH=$CUR_H
PM=$((10#$CUR_M + 2))
# Asegurar futuro (>= ahora +2 min)
if [ $PM -ge 60 ]; then
  PH=$(( (10#$PH + 1) % 24 ))
  PM=$(( PM - 60 ))
fi
# Iterar hasta encontrar minuto libre (evita SLOT_TIME, DEL_TIME y cualquier histórico)
while true; do
  CAND="$(printf '%02d' $PH):$(printf '%02d' $PM)"
  if [[ "$CAND" == "${SLOT_TIME:-}" || "$CAND" == "${DEL_TIME:-}" ]]; then
    PM=$(( PM + 1 ))
  elif echo "$USED_TIMES" | grep -qx "$CAND"; then
    PM=$(( PM + 1 ))
  else
    POS_TIME="$CAND"
    break
  fi
  if [ $PM -ge 60 ]; then
    PH=$(( (10#$PH + 1) % 24 ))
    PM=00
  fi
done
echo "[DEBUG31] SLOT_TIME=${SLOT_TIME:-<unset>} DEL_TIME=${DEL_TIME:-<unset>} SELECTED_POS_TIME=$POS_TIME"
R=$(curl_raw POST "$BASE/bookings" "{\"customer_email\":\"$TEST_EMAIL2\",\"trainer_email\":\"$TEST_TRAIN\",\"time\":\"$POS_TIME\"}") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 31a "$STATUS"
echo "[31] Booking list all -> GET /bookings"
R=$(curl_raw GET "$BASE/bookings") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 31b "$STATUS"

echo "=== DONE: full test suite (negative + positive) ==="
echo "Review responses above for unexpected codes or missing positives."
echo
########################################################
# New endpoint verification (32+)
########################################################

# 32) Endpoint de salud
echo "[32] Health endpoint -> GET /health"
R=$(curl_raw GET "$BASE/health") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 32 "$STATUS"

# 33) Resolver identidad por DNI
echo "[33] Identity resolve -> GET /identity/$GOOD_DNI"
R=$(curl_raw GET "$BASE/identity/$GOOD_DNI") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 33 "$STATUS"

# 34) Cliente por DNI
echo "[34] Customer by DNI -> GET /customers/by-dni/$GOOD_DNI"
R=$(curl_raw GET "$BASE/customers/by-dni/$GOOD_DNI") || true
print_resp "$R"
STATUS=$(echo "$R" | tail -n1 | awk '{print $2}')
assert_status 34 "$STATUS"

echo "=== DONE: extended endpoint tests ==="
echo
if [[ $FAILED -eq 0 ]]; then
  echo "ALL TESTS PASS (within expected status ranges)."
else
  echo "FAILED TESTS: $FAILED (see [FAIL] markers above)."
fi