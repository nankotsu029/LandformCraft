#!/usr/bin/env bash
# V2-11-05 FAWE 2.15.2 standalone Paper JVM measurement (1000×1000).
# Same methodology as V2-11-04: paperclip in run-fawe/, WorldEdit plugin forbidden.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=v2-11-04-common.sh
source "$SCRIPT_DIR/v2-11-04-common.sh"
measure_common_init
cd "$ROOT"

PROFILE="fawe"
MEASURE_ROOT="$ROOT/build/measure/v2-11-05"
EXPORT_DIR="$MEASURE_ROOT/fixture"
EVIDENCE_DIR="$MEASURE_ROOT/evidence/${PROFILE}"
RUN_DIR="$ROOT/run-fawe"
RELEASES_V2="$RUN_DIR/plugins/LandformCraft/data/releases-v2"
PLACEMENT_V2="$RUN_DIR/plugins/LandformCraft/data/placement-v2"
CONFIRM_DIR="$RUN_DIR/plugins/LandformCraft/data/confirmations"
RCON_PASSWORD="v21105-measure-local"
RCON_PORT=25576
GAME_PORT=25566
WORLD_NAME="world"
# 1000×1000 needs more heap than 500. The first attempt ran Xms=Xmx=8G and RSS
# tracked the committed heap (8.0 GiB) while the live set stayed near 3 GiB, so
# Xms is kept small and Xmx carries the ceiling.
XMX="${LANDFORMCRAFT_V21105_XMX:-8G}"
XMS="${LANDFORMCRAFT_V21105_XMS:-2G}"
DIM=999
SERVER_PID=""
TELEMETRY_PID=""
PAPER_LAUNCH_PID=""
# Long-run budgets: 4× area of 500 → allow several hours per phase.
PLAN_WAIT_SEC="${LANDFORMCRAFT_V21105_PLAN_WAIT_SEC:-1800}"
EXEC_TIMEOUT_SEC="${LANDFORMCRAFT_V21105_EXEC_TIMEOUT_SEC:-10800}"
UNDO_TIMEOUT_SEC="${LANDFORMCRAFT_V21105_UNDO_TIMEOUT_SEC:-10800}"

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

echo "==> building shadowJar"
./gradlew shadowJar -q

echo "==> exporting 1000x1000 solid surface Release"
rm -rf "$EXPORT_DIR"
LANDFORMCRAFT_V21105_EXPORT_DIR="$EXPORT_DIR" \
  ./gradlew test --tests 'com.github.nankotsu029.landformcraft.format.v2.release.V21105MeasurementFixtureExporterTest' \
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
test -n "$FAWE_JAR"
test -f "$FAWE_JAR"
write_eula "$RUN_DIR"
enable_rcon "$RUN_DIR/server.properties" "$RCON_PASSWORD" "$RCON_PORT" "$GAME_PORT"
install_plugin_and_measurement_config "$RUN_DIR" "$WORLD_NAME" 1000 1000
rm -f "$RUN_DIR/plugins"/worldedit*.jar "$RUN_DIR/plugins"/WorldEdit*.jar
test ! -f "$RUN_DIR/plugins"/worldedit*.jar
test -f "$FAWE_JAR"

rm -rf "$RELEASES_V2" "$PLACEMENT_V2" "$CONFIRM_DIR"
mkdir -p "$RELEASES_V2" "$PLACEMENT_V2" "$CONFIRM_DIR"
cp -R "$EXPORT_DIR/release" "$RELEASES_V2/$RELEASE_NAME"

rcon() {
  python3 "$RCON_PY" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 180 "$@"
}

capture_disk_and_queue_evidence() {
  local life_dir="$1"
  local tag="$2"
  mkdir -p "$life_dir/admission"
  {
    echo "tag=$tag"
    echo "ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    if [[ -d "$PLACEMENT_V2" ]]; then
      du -sk "$PLACEMENT_V2"/* 2>/dev/null || true
      echo "--- snapshots ---"
      du -sk "$PLACEMENT_V2/snapshots" 2>/dev/null || echo "snapshots=absent"
      find "$PLACEMENT_V2" -name 'reservation.json' -o -name 'undo-reservation.json' 2>/dev/null \
        | while read -r f; do
            echo "FILE $f"
            # Keep only size / numeric budget fields — no secrets expected, but stay small.
            python3 - "$f" <<'PY' || true
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
try:
    data = json.loads(p.read_text())
except Exception as exc:
    print(f"read_failed={exc}")
    raise SystemExit(0)
def walk(obj, prefix=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = f"{prefix}.{k}" if prefix else k
            lk = k.lower()
            if any(s in lk for s in ("byte", "size", "budget", "reserv", "disk", "floor", "lease", "block")):
                if isinstance(v, (int, float, str, bool)) or v is None:
                    print(f"{key}={v}")
                else:
                    walk(v, key)
            else:
                walk(v, key)
    elif isinstance(obj, list):
        for i, v in enumerate(obj[:20]):
            walk(v, f"{prefix}[{i}]")
walk(data)
PY
          done
    fi
    echo "--- log admission / disk / slice ---"
    grep -nE "DISK_BUDGET|DISK_SHORTAGE|admission|queued|slice|scheduler|reservation floor|snapshot bytes|maximumQueued|VERIFY|APPLIED|UNDONE" \
      "$RUN_DIR/logs/latest.log" 2>/dev/null | tail -n 120 || true
  } | tee "$life_dir/admission/${tag}.txt" >/dev/null
}

# The 1000×1000 run is long enough that a stalled phase is indistinguishable from
# a slow one by log tailing alone. Capture a JVM thread dump so a stall names the
# blocking frame instead of only a wall-clock number.
capture_thread_dump() {
  local life_dir="$1"
  local tag="$2"
  mkdir -p "$life_dir/threads"
  jstack "$SERVER_PID" >"$life_dir/threads/${tag}.txt" 2>&1 || true
}

# `kill -0` can fail transiently while the JVM is under heavy GC pressure. The
# first attempt aborted a still-live server on a single failed probe, so require
# consecutive failures before declaring the server dead.
server_alive() {
  local i
  for i in 1 2 3; do
    if kill -0 "$SERVER_PID" 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}

run_lifecycle() {
  local label="$1"
  local life_dir="$EVIDENCE_DIR/${label}"
  mkdir -p "$life_dir"
  local start_epoch end_epoch
  start_epoch="$(date +%s)"

  echo "==> [$label] doctor / version"
  rcon "lfc version" | tee "$life_dir/version.txt" >/dev/null || true
  rcon "lfc doctor" | tee "$life_dir/doctor.txt" >/dev/null || true
  sleep 2

  # Seal mutation envelope with stone before plan (stable Undo baseline).
  # Paper 1.21 forceload args are BLOCK column X/Z.
  # Paper vanilla fill rejects >32768 blocks. Height y=64..65 ⇒ 2 layers, so
  # strip_width * length <= 16384. With length=1000 use width 16 (32000 blocks).
  echo "==> [$label] stone-seal mutation envelope (strip-wise, width 16)"
  : >"$life_dir/forceload.txt"
  : >"$life_dir/touchload.txt"
  : >"$life_dir/seal.txt"
  # Clear leftover forceloads in ≤256-chunk strips (full-area remove hits the same cap).
  local rm_x0 rm_x1
  for rm_x0 in $(seq 0 64 "$DIM"); do
    rm_x1=$((rm_x0 + 63))
    if (( rm_x1 > DIM )); then rm_x1=$DIM; fi
    rcon "forceload remove ${rm_x0} 0 ${rm_x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
  done
  local x0 x1
  for x0 in $(seq 0 16 "$DIM"); do
    x1=$((x0 + 15))
    if (( x1 > DIM )); then x1=$DIM; fi
    rcon "forceload add ${x0} 0 ${x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
    sleep 2
    rcon "setblock ${x0} 63 0 minecraft:bedrock" >>"$life_dir/touchload.txt" 2>&1 || true
    rcon "setblock ${x1} 63 ${DIM} minecraft:bedrock" >>"$life_dir/touchload.txt" 2>&1 || true
    sleep 1
    rcon "fill ${x0} 64 0 ${x1} 65 ${DIM} minecraft:stone" >>"$life_dir/seal.txt" 2>&1 || true
    rcon "forceload remove ${x0} 0 ${x1} ${DIM}" >>"$life_dir/forceload.txt" 2>&1 || true
  done
  if grep -qE 'That position is not loaded' "$life_dir/seal.txt"; then
    echo "FAIL: stone-seal could not load positions" >&2
    grep -nE 'That position is not loaded' "$life_dir/seal.txt" | head -n 20 >&2 || true
    return 1
  fi
  if ! grep -qiE 'successfully filled|no blocks were filled' "$life_dir/seal.txt"; then
    echo "FAIL: stone-seal produced no recognizable fill responses" >&2
    cat "$life_dir/seal.txt" >&2 || true
    return 1
  fi
  echo "==> [$label] stone-seal OK"

  # Paper caps a single forceload add at 256 chunks. 1000×1000 is 63×63=3969 chunks,
  # so keep the envelope loaded via ≤252-chunk strips (4 chunks × 63 = 252) and do NOT
  # remove them until the lifecycle finishes.
  echo "==> [$label] re-forceload full envelope in ≤256-chunk strips (keep loaded)"
  : >"$life_dir/forceload-keep.txt"
  local fl_x0 fl_x1
  for fl_x0 in $(seq 0 64 "$DIM"); do
    fl_x1=$((fl_x0 + 63))
    if (( fl_x1 > DIM )); then fl_x1=$DIM; fi
    rcon "forceload add ${fl_x0} 0 ${fl_x1} ${DIM}" >>"$life_dir/forceload-keep.txt" 2>&1 || true
  done
  if grep -qE 'Too many chunks in the specified area' "$life_dir/forceload-keep.txt"; then
    echo "FAIL: forceload strip exceeded Paper 256-chunk cap" >&2
    grep -nE 'Too many chunks' "$life_dir/forceload-keep.txt" | head -n 10 >&2 || true
    return 1
  fi
  cat "$life_dir/forceload-keep.txt" >>"$life_dir/forceload.txt"
  rcon "forceload query" >>"$life_dir/forceload.txt" 2>&1 || true
  sleep 5

  echo "==> [$label] R2 plan"
  rcon "lfc r2 plan ${RELEASE_NAME} ${WORLD_NAME} 0 64 0" | tee "$life_dir/plan-rcon.txt" >/dev/null || true
  local confirm_file=""
  local waited=0
  while [[ -z "$confirm_file" && $waited -lt $PLAN_WAIT_SEC ]]; do
    sleep 2
    waited=$((waited + 2))
    confirm_file="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
    if (( waited % 60 == 0 )); then
      echo "… waiting for r2-confirm (${waited}s)" >&2
      capture_disk_and_queue_evidence "$life_dir" "plan-wait-${waited}s"
    fi
  done
  if [[ -z "$confirm_file" ]]; then
    echo "FAIL: no r2-confirm confirmation file" >&2
    grep -nE "Release 2|r2 |ERROR|Exception|plan|DISK_|admission" "$RUN_DIR/logs/latest.log" | tail -n 120 >&2 || true
    return 1
  fi
  local confirm_cmd placement_id
  confirm_cmd="$(tr -d '\r' <"$confirm_file" | head -n 1)"
  confirm_cmd="${confirm_cmd#/}"
  placement_id="$(basename "$confirm_file" .command)"
  placement_id="${placement_id#r2-confirm-}"
  echo "placement=$placement_id" | tee "$life_dir/placement-id.txt"
  echo "$confirm_cmd" | tee "$life_dir/confirm-command.txt"
  capture_disk_and_queue_evidence "$life_dir" "after-plan"

  echo "==> [$label] R2 confirm (snapshot-all + containment)"
  # Confirm runs async on landformcraft-io virtual threads. For 1000×1000 the
  # PlacementExpectedBlockResolverV2 Map.copyOf of ~2M mutation overrides alone can
  # take many minutes after snapshot files are published — do NOT treat SNAPSHOTTING
  # (prefix of SNAPSHOT_COMPLETE) as done. Poll the durable journal state instead.
  rcon "$confirm_cmd" >/dev/null || true
  local confirm_start journal_state
  confirm_start="$(date +%s)"
  journal_state=""
  while true; do
    if [[ -f "$PLACEMENT_V2/journals/${placement_id}.json" ]]; then
      journal_state="$(python3 - "$PLACEMENT_V2/journals/${placement_id}.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1])).get("state", ""))
PY
)"
    fi
    if [[ "$journal_state" == "SNAPSHOT_COMPLETE" ]]; then
      break
    fi
    if [[ "$journal_state" == "RECOVERY_REQUIRED" || "$journal_state" == "ROLLED_BACK" ]]; then
      echo "FAIL: confirm entered $journal_state" >&2
      return 1
    fi
    if ! server_alive; then
      echo "FAIL: server died during confirm/snapshot" >&2
      return 1
    fi
    if (( $(date +%s) - confirm_start >= 7200 )); then
      echo "FAIL: confirm/snapshot timeout (7200s); journal_state=${journal_state:-missing}" >&2
      grep -nE "SNAPSHOT|CONFIRM|containment|ERROR|Exception" "$RUN_DIR/logs/latest.log" \
        | tail -n 80 >&2 || true
      return 1
    fi
    if (( ($(date +%s) - confirm_start) % 60 < 3 )); then
      echo "… still confirming/snapshotting ($(( $(date +%s) - confirm_start ))s) state=${journal_state:-unknown}" >&2
      capture_disk_and_queue_evidence "$life_dir" "confirm-$(( $(date +%s) - confirm_start ))s"
      capture_thread_dump "$life_dir" "confirm-$(( $(date +%s) - confirm_start ))s"
    fi
    sleep 3
  done
  grep -nE "SNAPSHOT_COMPLETE|CONFIRM|containment|DISK_|admission|snapshot" "$RUN_DIR/logs/latest.log" \
    | tee "$life_dir/confirm-snippet.log" >/dev/null || true
  echo "journal_state=$journal_state" | tee "$life_dir/confirm-journal-state.txt"
  capture_disk_and_queue_evidence "$life_dir" "after-confirm"

  echo "==> [$label] R2 execute (apply→settle→full verify) — long-running"
  rcon "lfc r2 execute ${placement_id}" >/dev/null || true
  local exec_start
  exec_start="$(date +%s)"
  while true; do
    # Prefer durable journal — log lines like "state: APPLIED" can be ambiguous.
    local exec_journal=""
    if [[ -f "$PLACEMENT_V2/journals/${placement_id}.json" ]]; then
      exec_journal="$(python3 - "$PLACEMENT_V2/journals/${placement_id}.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1])).get("state", ""))
PY
)"
    fi
    if [[ "$exec_journal" == "APPLIED" ]] \
         || grep -qE 'outcome: APPLIED|  state: APPLIED$' "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      break
    fi
    if [[ "$exec_journal" == "ROLLED_BACK" || "$exec_journal" == "RECOVERY_REQUIRED" ]] \
         || grep -qE 'outcome: ROLLED_BACK|RECOVERY_REQUIRED|  state: ROLLED_BACK$|  state: RECOVERY_REQUIRED$|DISK_BUDGET_EXCEEDED|DISK_SHORTAGE' \
              "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      break
    fi
    if ! server_alive; then
      echo "FAIL: server died during execute" >&2
      return 1
    fi
    if (( $(date +%s) - exec_start >= EXEC_TIMEOUT_SEC )); then
      echo "FAIL: execute timeout (${EXEC_TIMEOUT_SEC}s) journal=${exec_journal:-missing}" >&2
      grep -nE "APPLIED|ROLLED_BACK|RECOVERY|execute|settle|verify|DISK_|admission" "$RUN_DIR/logs/latest.log" \
        | tail -n 120 >&2 || true
      return 1
    fi
    if (( ($(date +%s) - exec_start) % 60 < 3 )); then
      echo "… still executing ($(( $(date +%s) - exec_start ))s) state=${exec_journal:-unknown}" >&2
      rcon "lfc r2 status ${placement_id}" | tee -a "$life_dir/status-poll.log" >/dev/null || true
      capture_disk_and_queue_evidence "$life_dir" "exec-$(( $(date +%s) - exec_start ))s"
      capture_thread_dump "$life_dir" "exec-$(( $(date +%s) - exec_start ))s"
    fi
    sleep 3
  done
  grep -nE "APPLIED|ROLLED_BACK|RECOVERY_REQUIRED|exact verify|settle|outcome|state:|DISK_|admission|slice|queued" \
    "$RUN_DIR/logs/latest.log" | tee "$life_dir/execute-snippet.log" >/dev/null || true
  if grep -qE 'DISK_BUDGET_EXCEEDED|DISK_SHORTAGE' "$life_dir/execute-snippet.log" "$RUN_DIR/logs/latest.log"; then
    echo "FAIL: disk admission exceeded during execute" >&2
    return 1
  fi
  exec_journal="$(python3 - "$PLACEMENT_V2/journals/${placement_id}.json" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
print(json.load(p.open()).get("state", "") if p.exists() else "")
PY
)"
  if [[ "$exec_journal" != "APPLIED" ]] && ! grep -qE "  state: APPLIED$|outcome: APPLIED" "$life_dir/execute-snippet.log" "$RUN_DIR/logs/latest.log"; then
    echo "FAIL: execute did not reach APPLIED (journal=${exec_journal:-missing})" >&2
    return 1
  fi
  capture_disk_and_queue_evidence "$life_dir" "after-applied"

  echo "==> [$label] R2 undo"
  rcon "lfc r2 undo-plan ${placement_id}" >/dev/null || true
  local undo_file=""
  waited=0
  while [[ -z "$undo_file" && $waited -lt 300 ]]; do
    undo_file="$(ls -1t "$CONFIRM_DIR"/r2-undo-*.command 2>/dev/null | head -n 1 || true)"
    sleep 2
    waited=$((waited + 2))
  done
  if [[ -z "$undo_file" ]]; then
    echo "FAIL: no undo confirmation file" >&2
    return 1
  fi
  local undo_cmd
  undo_cmd="$(tr -d '\r' <"$undo_file" | head -n 1)"
  undo_cmd="${undo_cmd#/}"
  echo "$undo_cmd" | tee "$life_dir/undo-command.txt"
  rcon "$undo_cmd" >/dev/null || true
  local undo_start
  undo_start="$(date +%s)"
  while true; do
    local undo_journal=""
    if [[ -f "$PLACEMENT_V2/journals/${placement_id}.json" ]]; then
      undo_journal="$(python3 - "$PLACEMENT_V2/journals/${placement_id}.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1])).get("state", ""))
PY
)"
    fi
    if [[ "$undo_journal" == "UNDONE" ]] \
         || grep -qE 'outcome: UNDONE|  state: UNDONE$' "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      break
    fi
    if [[ "$undo_journal" == "RECOVERY_REQUIRED" ]] \
         || grep -qE 'undo exact verify mismatch|VERIFY_MISMATCH|  state: RECOVERY_REQUIRED$|outcome: RECOVERY_REQUIRED' \
              "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      echo "FAIL: undo entered recovery / verify mismatch" >&2
      grep -nE "undo exact verify|RECOVERY|UNDONE|VERIFY" "$RUN_DIR/logs/latest.log" | tail -n 40 >&2 || true
      return 1
    fi
    if ! server_alive; then
      echo "FAIL: server died during undo" >&2
      return 1
    fi
    if (( $(date +%s) - undo_start >= UNDO_TIMEOUT_SEC )); then
      echo "FAIL: undo timeout journal=${undo_journal:-missing}" >&2
      return 1
    fi
    if (( ($(date +%s) - undo_start) % 60 < 3 )); then
      echo "… still undoing ($(( $(date +%s) - undo_start ))s) state=${undo_journal:-unknown}" >&2
      capture_disk_and_queue_evidence "$life_dir" "undo-$(( $(date +%s) - undo_start ))s"
      capture_thread_dump "$life_dir" "undo-$(( $(date +%s) - undo_start ))s"
    fi
    sleep 3
  done
  grep -nE "UNDONE|undo" "$RUN_DIR/logs/latest.log" | tee "$life_dir/undo-snippet.log" >/dev/null || true
  undo_journal="$(python3 - "$PLACEMENT_V2/journals/${placement_id}.json" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
print(json.load(p.open()).get("state", "") if p.exists() else "")
PY
)"
  if [[ "$undo_journal" != "UNDONE" ]] && ! grep -qE "  state: UNDONE$|outcome: UNDONE" "$life_dir/undo-snippet.log" "$RUN_DIR/logs/latest.log"; then
    echo "FAIL: undo did not reach UNDONE (journal=${undo_journal:-missing})" >&2
    return 1
  fi
  capture_disk_and_queue_evidence "$life_dir" "after-undone"

  end_epoch="$(date +%s)"
  {
    echo "label=$label"
    echo "placement_id=$placement_id"
    echo "wall_seconds=$((end_epoch - start_epoch))"
    echo "release=$RELEASE_NAME"
    echo "manifest_sha256=$MANIFEST_SHA"
    echo "tile_count=$TILE_COUNT"
    echo "dimensions=1000x1000"
  } | tee "$life_dir/summary.txt"

  mkdir -p "$life_dir/checksums"
  if [[ -d "$PLACEMENT_V2/operations" ]]; then
    find "$PLACEMENT_V2/operations" -type f -print0 \
      | sort -z \
      | xargs -0 shasum -a 256 >"$life_dir/checksums/operations.sha256" || true
  fi
  if [[ -d "$PLACEMENT_V2/journals" ]]; then
    find "$PLACEMENT_V2/journals" -type f -name '*.json' -print0 \
      | sort -z \
      | xargs -0 shasum -a 256 >"$life_dir/checksums/journals.sha256" || true
  fi
}

echo "==> starting standalone Paper JVM (Xmx=$XMX)"
PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper.stdout" "$XMS")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
echo "paper_launch_pid=$PAPER_LAUNCH_PID server_pid=$SERVER_PID" | tee "$EVIDENCE_DIR/pids.txt"

python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry.json" \
  --interval-seconds 20 &
TELEMETRY_PID=$!

wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'FastAsyncWorldEdit|Enabling FastAsyncWorldEdit' 120 "$SERVER_PID"
if ! grep -nE 'measurement profile is ENABLED|measurement-profile=enabled' "$RUN_DIR/logs/latest.log" >/dev/null; then
  echo "WARN: measurement profile startup warning not found; checking config" >&2
  grep -nE 'measurement-profile' "$RUN_DIR/plugins/LandformCraft/config.yml" || true
fi
if grep -nE 'Enabling WorldEdit v' "$RUN_DIR/logs/latest.log" >/dev/null; then
  echo "FAIL: WorldEdit plugin enabled on FAWE-only profile" >&2
  exit 1
fi
test ! -f "$RUN_DIR/plugins"/worldedit*.jar

run_lifecycle "pass1"

echo "==> restarting Paper between pass1 and pass2 (clear placement store safely)"
if [[ -n "${TELEMETRY_PID}" ]] && kill -0 "${TELEMETRY_PID}" 2>/dev/null; then
  kill "${TELEMETRY_PID}" 2>/dev/null || true
  wait "${TELEMETRY_PID}" 2>/dev/null || true
  TELEMETRY_PID=""
fi
rcon stop >/dev/null 2>&1 || true
wait "${PAPER_LAUNCH_PID}" 2>/dev/null || true
SERVER_PID=""
PAPER_LAUNCH_PID=""
sleep 3
rm -rf "$PLACEMENT_V2" "$CONFIRM_DIR"
mkdir -p "$PLACEMENT_V2" "$CONFIRM_DIR"
test -d "$RELEASES_V2/$RELEASE_NAME"

PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper-pass2.stdout" "$XMS")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
echo "pass2_paper_launch_pid=$PAPER_LAUNCH_PID server_pid=$SERVER_PID" | tee -a "$EVIDENCE_DIR/pids.txt"
python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry-pass2.json" \
  --interval-seconds 20 &
TELEMETRY_PID=$!
wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'FastAsyncWorldEdit|Enabling FastAsyncWorldEdit' 120 "$SERVER_PID"

run_lifecycle "pass2"

echo "==> failure/recovery drill: plan then kill server before confirm"
rcon "lfc r2 plan ${RELEASE_NAME} ${WORLD_NAME} 0 64 0" >/dev/null || true
sleep 15
CONFIRM_BEFORE_KILL="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
echo "confirm_before_kill=${CONFIRM_BEFORE_KILL:-none}" | tee "$EVIDENCE_DIR/recovery-plan.txt"
kill -9 "$SERVER_PID" 2>/dev/null || true
wait "$PAPER_LAUNCH_PID" 2>/dev/null || true
SERVER_PID=""
PAPER_LAUNCH_PID=""
if [[ -n "${TELEMETRY_PID}" ]]; then
  kill "${TELEMETRY_PID}" 2>/dev/null || true
  wait "${TELEMETRY_PID}" 2>/dev/null || true
  TELEMETRY_PID=""
fi

echo "==> restart after crash for recovery observation"
PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper-restart.stdout" "$XMS")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry-restart.json" \
  --interval-seconds 20 &
TELEMETRY_PID=$!
wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
rcon "lfc doctor" | tee "$EVIDENCE_DIR/recovery-doctor.txt" >/dev/null || true
rcon "lfc r2 status" >/dev/null 2>&1 || true
grep -nE "RECOVERY|CONFIRMATION|placement|journal" "$RUN_DIR/logs/latest.log" \
  | tee "$EVIDENCE_DIR/recovery-snippet.log" >/dev/null || true

echo "==> v1 regression smoke (doctor/help on same profile)"
rcon "lfc help" >/dev/null || true
rcon "lfc doctor" >/dev/null || true
{
  echo "v1_help_and_doctor=issued"
  echo "profile=fawe-2.15.2"
} | tee "$EVIDENCE_DIR/v1-regression.txt"

DISK_BYTES=0
if [[ -d "$PLACEMENT_V2/snapshots" ]]; then
  DISK_BYTES="$(du -sk "$PLACEMENT_V2/snapshots" | awk '{print $1 * 1024}')"
fi

# Aggregate admission evidence: no DISK failures across passes.
{
  echo "disk_budget_exceeded_hits=$(cat "$EVIDENCE_DIR"/pass*/execute-snippet.log "$RUN_DIR/logs/latest.log" 2>/dev/null | grep -cE 'DISK_BUDGET_EXCEEDED|DISK_SHORTAGE' || true)"
  echo "admission_notes=see pass*/admission/"
  echo "scheduler_status_polls=see pass*/status-poll.log"
} | tee "$EVIDENCE_DIR/admission-summary.txt"

python3 - <<PY
from pathlib import Path
import json
evidence = Path(r"""$EVIDENCE_DIR""")

def peak_rss(path: Path):
    if not path.exists():
        return 0
    data = json.loads(path.read_text())
    value = data.get("peak_rss_bytes", 0)
    return int(value) if isinstance(value, (int, float)) else 0

peak = max(peak_rss(evidence / "telemetry.json"), peak_rss(evidence / "telemetry-pass2.json"),
           peak_rss(evidence / "telemetry-restart.json"))
pass1 = (evidence / "pass1" / "summary.txt").read_text() if (evidence / "pass1" / "summary.txt").exists() else "missing"
pass2 = (evidence / "pass2" / "summary.txt").read_text() if (evidence / "pass2" / "summary.txt").exists() else "missing"
text = "\\n".join([
    "Profile: FAWE 2.15.2 standalone",
    "Boot: java -jar paper-1.21.11-132.jar (no Gradle runServer)",
    f"Xmx: $XMX",
    f"Xms: $XMS",
    f"Build JAR SHA-256: $JAR_SHA",
    f"Manifest canonical SHA-256: $MANIFEST_SHA",
    # Use a plain string so bash expands $TILE_COUNT; an f-string {TILE_COUNT} is a Python NameError.
    "Release: $RELEASE_NAME / surface-2_5d / 1000x1000 solid y=0..1 ($TILE_COUNT tiles, tile size 128)",
    f"Measurement profile world: $WORLD_NAME ceiling=1000x1000",
    f"Server PID (final): $SERVER_PID",
    f"Peak RSS (bytes): {peak if peak else 'unknown'}",
    f"Disk snapshots (bytes): $DISK_BYTES",
    "Pass1:",
    pass1.strip(),
    "Pass2:",
    pass2.strip(),
    "Failure/recovery: plan then SIGKILL; restart + doctor recorded",
    "Long-run admission: no DISK_BUDGET_EXCEEDED/DISK_SHORTAGE; slice/queue polls under pass*/admission/",
    "Catalog promotion: NOT performed (V2-11-06)",
    f"Operator / date: agent / $(date -u +%Y-%m-%d)",
    "",
])
(evidence / "summary.txt").write_text(text)
print(text)
PY

grep -nE "APPLIED" "$EVIDENCE_DIR/pass1/execute-snippet.log" >/dev/null
grep -nE "UNDONE" "$EVIDENCE_DIR/pass1/undo-snippet.log" >/dev/null
grep -nE "APPLIED" "$EVIDENCE_DIR/pass2/execute-snippet.log" >/dev/null
grep -nE "UNDONE" "$EVIDENCE_DIR/pass2/undo-snippet.log" >/dev/null

echo "==> stopping server"
rcon stop >/dev/null || true
wait "$PAPER_LAUNCH_PID" 2>/dev/null || true
SERVER_PID=""
PAPER_LAUNCH_PID=""
echo "RESULT: PASS — evidence at $EVIDENCE_DIR/summary.txt"
