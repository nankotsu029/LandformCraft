#!/usr/bin/env bash
# V2-13-04 1024×1024 FAWE placement measurement (2 pass + Recovery drill).
#
# Same real-host methodology as V2-11-05 / V2-13-01 (standalone Paper JVM in run-fawe/, FAWE 2.15.2
# only, WorldEdit plugin forbidden, measurement profile, RCON, PID-only telemetry). The only
# differences from the V2-13-01 stage-instrumentation runner are:
#   1. dimensions 1024×1024 (DIM=1023) instead of 1000×1000 — the MEDIUM horizontal ceiling proven
#      offline-viable E2E by V2-13-03 (cell count = MEDIUM_MAXIMUM_CELLS = 1024²);
#   2. an explicit failure/recovery drill after the two passes (plan → SIGKILL before confirm →
#      restart → doctor / status), matching V2-11-05.
# It records per-stage wall-clock marks (PLAN / SNAPSHOT / APPLY / SETTLE / VERIFY / UNDO) and emits
# committed per-stage duration evidence via v2-13-01-stage-durations.py.
#
# It changes NO safety ordering and NO verify scope, and it does NOT promote PlacementDimensionLimitV2
# or any catalog dimension (catalog promotion is a separate approval Task after V2-13-04). It is
# real-machine dependent: without a dedicated high-memory measurement host it cannot run (Task status
# BLOCKED_EXTERNAL). See docs/smoke/v2-13-04-fawe-1024-measurement-runbook.md.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=v2-11-04-common.sh
source "$SCRIPT_DIR/v2-11-04-common.sh"
measure_common_init
cd "$ROOT"

PROFILE="fawe"
MEASURE_ROOT="$ROOT/build/measure/v2-13-04"
EXPORT_DIR="$MEASURE_ROOT/fixture"
EVIDENCE_DIR="$MEASURE_ROOT/evidence/${PROFILE}"
RUN_DIR="$ROOT/run-fawe"
RELEASES_V2="$RUN_DIR/plugins/LandformCraft/data/releases-v2"
PLACEMENT_V2="$RUN_DIR/plugins/LandformCraft/data/placement-v2"
CONFIRM_DIR="$RUN_DIR/plugins/LandformCraft/data/confirmations"
STAGE_EMITTER="$SCRIPT_DIR/v2-13-01-stage-durations.py"
RCON_PASSWORD="v21304-measure-local"
RCON_PORT=25576
GAME_PORT=25566
WORLD_NAME="world"
XMX="${LANDFORMCRAFT_V21304_XMX:-8G}"
XMS="${LANDFORMCRAFT_V21304_XMS:-2G}"
DIM=1023
SERVER_PID=""
TELEMETRY_PID=""
PAPER_LAUNCH_PID=""
PLAN_WAIT_SEC="${LANDFORMCRAFT_V21304_PLAN_WAIT_SEC:-1800}"
EXEC_TIMEOUT_SEC="${LANDFORMCRAFT_V21304_EXEC_TIMEOUT_SEC:-10800}"
UNDO_TIMEOUT_SEC="${LANDFORMCRAFT_V21304_UNDO_TIMEOUT_SEC:-10800}"

cleanup() {
  if [[ -n "${TELEMETRY_PID}" ]] && kill -0 "${TELEMETRY_PID}" 2>/dev/null; then
    kill "${TELEMETRY_PID}" 2>/dev/null || true
    wait "${TELEMETRY_PID}" 2>/dev/null || true
  fi
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
    python3 "$RCON_PY" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 30 stop \
      >/dev/null 2>&1 || true
    wait "${PAPER_LAUNCH_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$MEASURE_ROOT" "$EVIDENCE_DIR"
rm -rf "$EVIDENCE_DIR"
mkdir -p "$EVIDENCE_DIR"

# --- additive stage-mark capture (identical to V2-13-01) --------------------------------------
MARKS_FILE=""
mark_init() { MARKS_FILE="$1"; : >"$MARKS_FILE"; }
record_mark() {
  # Idempotent per name: first observation wins (journal states are monotonic).
  local name="$1"
  if ! grep -qE "^${name} " "$MARKS_FILE" 2>/dev/null; then
    echo "${name} $(date +%s)" >>"$MARKS_FILE"
  fi
}
journal_state() {
  local pid="$1"
  local file="$PLACEMENT_V2/journals/${pid}.json"
  [[ -f "$file" ]] || return 0
  python3 - "$file" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
print(json.load(p.open()).get("state", "") if p.exists() else "")
PY
}

echo "==> building shadowJar"
./gradlew shadowJar -q

echo "==> exporting 1024x1024 solid surface Release (V2-13-04 measurement fixture)"
rm -rf "$EXPORT_DIR"
LANDFORMCRAFT_V21304_EXPORT_DIR="$EXPORT_DIR" \
  ./gradlew test --tests 'com.github.nankotsu029.landformcraft.format.v2.release.V21304MeasurementFixtureExporterTest' \
  --rerun-tasks -q
RELEASE_NAME="$(tr -d '[:space:]' < "$EXPORT_DIR/release-dir-name.txt")"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
MANIFEST_SHA="$(tr -d '[:space:]' < "$EXPORT_DIR/manifest-sha256.txt")"
TILE_COUNT="$(tr -d '[:space:]' < "$EXPORT_DIR/tile-count.txt")"

echo "==> preparing FAWE-only run-fawe/ (standalone paperclip, WorldEdit jar forbidden)"
test -f "$RUN_DIR/paper-1.21.11-132.jar"
rm -f "$RUN_DIR/plugins"/worldedit*.jar "$RUN_DIR/plugins"/WorldEdit*.jar
rm -f "$RUN_DIR/plugins"/LandformCraft*.jar
FAWE_JAR="$(ls -1 "$RUN_DIR/plugins"/FastAsyncWorldEdit*.jar | head -n 1)"
test -n "$FAWE_JAR"; test -f "$FAWE_JAR"
write_eula "$RUN_DIR"
enable_rcon "$RUN_DIR/server.properties" "$RCON_PASSWORD" "$RCON_PORT" "$GAME_PORT"
install_plugin_and_measurement_config "$RUN_DIR" "$WORLD_NAME" 1024 1024
rm -f "$RUN_DIR/plugins"/worldedit*.jar "$RUN_DIR/plugins"/WorldEdit*.jar
test -f "$FAWE_JAR"

rm -rf "$RELEASES_V2" "$PLACEMENT_V2" "$CONFIRM_DIR"
mkdir -p "$RELEASES_V2" "$PLACEMENT_V2" "$CONFIRM_DIR"
cp -R "$EXPORT_DIR/release" "$RELEASES_V2/$RELEASE_NAME"

rcon() { python3 "$RCON_PY" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 180 "$@"; }

server_alive() {
  local i
  for i in 1 2 3; do
    if kill -0 "$SERVER_PID" 2>/dev/null; then return 0; fi
    sleep 2
  done
  return 1
}

# Reuse the V2-11-05 / V2-13-01 stone-seal + keep-load envelope helper.
# Paper 1.21's single `forceload add` caps at 256 chunks; the 64-block (4-chunk) × Z-span
# (64-chunk) keep-load strips are exactly 256 chunks at DIM=1023, which is within the cap.
seal_and_forceload() {
  local life_dir="$1"
  : >"$life_dir/forceload.txt"; : >"$life_dir/seal.txt"
  local rm_x0 rm_x1
  for rm_x0 in $(seq 0 64 "$DIM"); do
    rm_x1=$((rm_x0 + 63)); if (( rm_x1 > DIM )); then rm_x1=$DIM; fi
    rcon "forceload remove ${rm_x0} 0 ${rm_x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
  done
  local x0 x1
  for x0 in $(seq 0 16 "$DIM"); do
    x1=$((x0 + 15)); if (( x1 > DIM )); then x1=$DIM; fi
    rcon "forceload add ${x0} 0 ${x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
    sleep 2
    rcon "setblock ${x0} 63 0 minecraft:bedrock" >>"$life_dir/seal.txt" 2>&1 || true
    rcon "setblock ${x1} 63 ${DIM} minecraft:bedrock" >>"$life_dir/seal.txt" 2>&1 || true
    sleep 1
    rcon "fill ${x0} 64 0 ${x1} 65 ${DIM} minecraft:stone" >>"$life_dir/seal.txt" 2>&1 || true
    rcon "forceload remove ${x0} 0 ${x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
  done
  if ! grep -qiE 'successfully filled|no blocks were filled' "$life_dir/seal.txt"; then
    echo "FAIL: stone-seal produced no recognizable fill responses" >&2; return 1
  fi
  local fl_x0 fl_x1
  for fl_x0 in $(seq 0 64 "$DIM"); do
    fl_x1=$((fl_x0 + 63)); if (( fl_x1 > DIM )); then fl_x1=$DIM; fi
    rcon "forceload add ${fl_x0} 0 ${fl_x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
  done
  sleep 5
}

run_lifecycle() {
  local label="$1"
  local life_dir="$EVIDENCE_DIR/${label}"
  mkdir -p "$life_dir"
  mark_init "$life_dir/marks.tsv"
  local start_epoch end_epoch
  start_epoch="$(date +%s)"
  rcon "lfc version" | tee "$life_dir/version.txt" >/dev/null || true
  rcon "lfc doctor" | tee "$life_dir/doctor.txt" >/dev/null || true

  echo "==> [$label] stone-seal + keep-load envelope"
  seal_and_forceload "$life_dir"

  echo "==> [$label] v2 place plan"
  record_mark PLAN_ISSUED
  rcon "lfc v2 place plan ${RELEASE_NAME} ${WORLD_NAME} 0 64 0" | tee "$life_dir/plan-rcon.txt" >/dev/null || true
  local confirm_file="" waited=0
  while [[ -z "$confirm_file" && $waited -lt $PLAN_WAIT_SEC ]]; do
    sleep 2; waited=$((waited + 2))
    confirm_file="$(ls -1t "$CONFIRM_DIR"/v2-confirm-*.command 2>/dev/null | head -n 1 || true)"
  done
  [[ -n "$confirm_file" ]] || { echo "FAIL: no v2-confirm file" >&2; return 1; }
  record_mark CONFIRMATION_ISSUED
  local confirm_cmd placement_id
  confirm_cmd="$(tr -d '\r' <"$confirm_file" | head -n 1)"; confirm_cmd="${confirm_cmd#/}"
  placement_id="$(basename "$confirm_file" .command)"; placement_id="${placement_id#v2-confirm-}"
  echo "placement=$placement_id" | tee "$life_dir/placement-id.txt"

  echo "==> [$label] R2 confirm (snapshot-all + containment + Undo baseline)"
  record_mark CONFIRM_SUBMITTED
  rcon "$confirm_cmd" >/dev/null || true
  local confirm_start; confirm_start="$(date +%s)"
  while true; do
    local st; st="$(journal_state "$placement_id")"
    if [[ "$st" == "SNAPSHOT_COMPLETE" ]]; then record_mark SNAPSHOT_COMPLETE; break; fi
    if [[ "$st" == "RECOVERY_REQUIRED" || "$st" == "ROLLED_BACK" ]]; then
      echo "FAIL: confirm entered $st" >&2; return 1; fi
    server_alive || { echo "FAIL: server died during confirm" >&2; return 1; }
    (( $(date +%s) - confirm_start < 7200 )) || { echo "FAIL: confirm timeout" >&2; return 1; }
    sleep 3
  done

  echo "==> [$label] v2 place execute (apply→settle→full verify)"
  record_mark EXECUTE_SUBMITTED
  rcon "lfc v2 place execute ${placement_id}" >/dev/null || true
  local exec_start; exec_start="$(date +%s)"
  while true; do
    local st; st="$(journal_state "$placement_id")"
    case "$st" in
      APPLYING) : ;;
      SETTLING) record_mark SETTLING ;;
      VERIFYING) record_mark SETTLING; record_mark VERIFYING ;;
      APPLIED) record_mark SETTLING; record_mark VERIFYING; record_mark APPLIED; break ;;
      ROLLED_BACK|RECOVERY_REQUIRED) echo "FAIL: execute entered $st" >&2; return 1 ;;
    esac
    server_alive || { echo "FAIL: server died during execute" >&2; return 1; }
    (( $(date +%s) - exec_start < EXEC_TIMEOUT_SEC )) || { echo "FAIL: execute timeout" >&2; return 1; }
    sleep 3
  done
  grep -nE "APPLIED|settle|exact verify|DISK_" "$RUN_DIR/logs/latest.log" \
    | tee "$life_dir/execute-snippet.log" >/dev/null || true
  if grep -qE 'DISK_BUDGET_EXCEEDED|DISK_SHORTAGE' "$RUN_DIR/logs/latest.log"; then
    echo "FAIL: disk admission exceeded" >&2; return 1; fi

  echo "==> [$label] v2 undo plan"
  rcon "lfc v2 undo plan ${placement_id}" >/dev/null || true
  local undo_file="" ; waited=0
  while [[ -z "$undo_file" && $waited -lt 300 ]]; do
    undo_file="$(ls -1t "$CONFIRM_DIR"/v2-undo-*.command 2>/dev/null | head -n 1 || true)"
    sleep 2; waited=$((waited + 2))
  done
  [[ -n "$undo_file" ]] || { echo "FAIL: no undo file" >&2; return 1; }
  local undo_cmd; undo_cmd="$(tr -d '\r' <"$undo_file" | head -n 1)"; undo_cmd="${undo_cmd#/}"
  record_mark UNDO_SUBMITTED
  rcon "$undo_cmd" >/dev/null || true
  local undo_start; undo_start="$(date +%s)"
  while true; do
    local st; st="$(journal_state "$placement_id")"
    if [[ "$st" == "UNDONE" ]]; then record_mark UNDONE; break; fi
    if [[ "$st" == "RECOVERY_REQUIRED" ]]; then echo "FAIL: undo entered recovery" >&2; return 1; fi
    server_alive || { echo "FAIL: server died during undo" >&2; return 1; }
    (( $(date +%s) - undo_start < UNDO_TIMEOUT_SEC )) || { echo "FAIL: undo timeout" >&2; return 1; }
    sleep 3
  done
  grep -nE "UNDONE|rollback|restore" "$RUN_DIR/logs/latest.log" \
    | tee "$life_dir/undo-snippet.log" >/dev/null || true

  end_epoch="$(date +%s)"
  { echo "label=$label"; echo "placement_id=$placement_id";
    echo "wall_seconds=$((end_epoch - start_epoch))"; echo "dimensions=1024x1024"; } \
    | tee "$life_dir/summary.txt"

  echo "==> [$label] emit committed per-stage duration evidence"
  python3 "$STAGE_EMITTER" --marks "$life_dir/marks.tsv" \
    --pass-id "$label" --dimensions 1024x1024 --profile "fawe-2.15.2" \
    --captured-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --out-json "$life_dir/stage-durations.json" --out-txt "$life_dir/stage-durations.txt"
  cat "$life_dir/stage-durations.txt"
}

echo "==> starting standalone Paper JVM (Xmx=$XMX)"
PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper.stdout" "$XMS")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
echo "paper_launch_pid=$PAPER_LAUNCH_PID server_pid=$SERVER_PID" | tee "$EVIDENCE_DIR/pids.txt"
python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry.json" --interval-seconds 20 &
TELEMETRY_PID=$!
wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'FastAsyncWorldEdit|Enabling FastAsyncWorldEdit' 120 "$SERVER_PID"
if grep -nE 'Enabling WorldEdit v' "$RUN_DIR/logs/latest.log" >/dev/null; then
  echo "FAIL: WorldEdit plugin enabled on FAWE-only profile" >&2; exit 1; fi

# cold pass (pass1) then warm pass (pass2) — the two passes give cold/warm baseline per stage.
run_lifecycle "pass1"

echo "==> restarting Paper between pass1 and pass2 (clear placement store safely)"
kill "${TELEMETRY_PID}" 2>/dev/null || true; wait "${TELEMETRY_PID}" 2>/dev/null || true; TELEMETRY_PID=""
rcon stop >/dev/null 2>&1 || true
wait "${PAPER_LAUNCH_PID}" 2>/dev/null || true; SERVER_PID=""; PAPER_LAUNCH_PID=""
sleep 3
rm -rf "$PLACEMENT_V2" "$CONFIRM_DIR"; mkdir -p "$PLACEMENT_V2" "$CONFIRM_DIR"
test -d "$RELEASES_V2/$RELEASE_NAME"

PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper-pass2.stdout" "$XMS")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry-pass2.json" --interval-seconds 20 &
TELEMETRY_PID=$!
wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'FastAsyncWorldEdit|Enabling FastAsyncWorldEdit' 120 "$SERVER_PID"

run_lifecycle "pass2"

echo "==> failure/recovery drill: plan then SIGKILL before confirm"
rm -rf "$PLACEMENT_V2" "$CONFIRM_DIR"; mkdir -p "$PLACEMENT_V2" "$CONFIRM_DIR"
rcon "lfc v2 place plan ${RELEASE_NAME} ${WORLD_NAME} 0 64 0" >/dev/null || true
sleep 15
CONFIRM_BEFORE_KILL="$(ls -1t "$CONFIRM_DIR"/v2-confirm-*.command 2>/dev/null | head -n 1 || true)"
echo "confirm_before_kill=${CONFIRM_BEFORE_KILL:-none}" | tee "$EVIDENCE_DIR/recovery-plan.txt"
kill -9 "$SERVER_PID" 2>/dev/null || true
wait "$PAPER_LAUNCH_PID" 2>/dev/null || true
SERVER_PID=""; PAPER_LAUNCH_PID=""
if [[ -n "${TELEMETRY_PID}" ]]; then
  kill "${TELEMETRY_PID}" 2>/dev/null || true; wait "${TELEMETRY_PID}" 2>/dev/null || true; TELEMETRY_PID=""
fi

echo "==> restart after crash for recovery observation"
PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper-restart.stdout" "$XMS")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry-restart.json" --interval-seconds 20 &
TELEMETRY_PID=$!
wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
rcon "lfc doctor" | tee "$EVIDENCE_DIR/recovery-doctor.txt" >/dev/null || true
rcon "lfc v2 status" >/dev/null 2>&1 || true
grep -nE "RECOVERY|CONFIRMATION|placement|journal" "$RUN_DIR/logs/latest.log" \
  | tee "$EVIDENCE_DIR/recovery-snippet.log" >/dev/null || true

echo "==> v1 regression smoke (doctor/help on same profile)"
rcon "lfc help" >/dev/null || true
rcon "lfc doctor" >/dev/null || true
{ echo "v1_help_and_doctor=issued"; echo "profile=fawe-2.15.2"; } | tee "$EVIDENCE_DIR/v1-regression.txt"

echo "==> stopping server"
rcon stop >/dev/null 2>&1 || true
wait "$PAPER_LAUNCH_PID" 2>/dev/null || true; SERVER_PID=""; PAPER_LAUNCH_PID=""

DISK_BYTES=0
if [[ -d "$PLACEMENT_V2/snapshots" ]]; then
  DISK_BYTES="$(du -sk "$PLACEMENT_V2/snapshots" | awk '{print $1 * 1024}')"
fi

{
  echo "profile=fawe-2.15.2"; echo "build_jar_sha256=$JAR_SHA"; echo "manifest_sha256=$MANIFEST_SHA"
  echo "release=$RELEASE_NAME"; echo "tile_count=$TILE_COUNT"; echo "dimensions=1024x1024"
  echo "disk_snapshot_bytes=$DISK_BYTES"
  echo "pass1_bottleneck=$(grep '^bottleneck_stage=' "$EVIDENCE_DIR/pass1/stage-durations.txt" | cut -d= -f2)"
  echo "pass2_bottleneck=$(grep '^bottleneck_stage=' "$EVIDENCE_DIR/pass2/stage-durations.txt" | cut -d= -f2)"
  echo "recovery=plan then SIGKILL; restart + doctor recorded"
  echo "operator_date=agent / $(date -u +%Y-%m-%d)"
} | tee "$EVIDENCE_DIR/summary.txt"
echo "RESULT: PASS — per-stage evidence at $EVIDENCE_DIR/pass*/stage-durations.{json,txt}"
