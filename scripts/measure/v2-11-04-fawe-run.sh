#!/usr/bin/env bash
# V2-11-04 FAWE 2.15.2 standalone Paper JVM measurement (500×500).
# Does NOT use ./gradlew runServer — Paper is launched as its own JVM via paperclip.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=v2-11-04-common.sh
source "$SCRIPT_DIR/v2-11-04-common.sh"
measure_common_init
cd "$ROOT"

PROFILE="fawe"
MEASURE_ROOT="$ROOT/build/measure/v2-11-04"
EXPORT_DIR="$MEASURE_ROOT/fixture"
EVIDENCE_DIR="$MEASURE_ROOT/evidence/${PROFILE}"
RUN_DIR="$ROOT/run-fawe"
RELEASES_V2="$RUN_DIR/plugins/LandformCraft/data/releases-v2"
PLACEMENT_V2="$RUN_DIR/plugins/LandformCraft/data/placement-v2"
CONFIRM_DIR="$RUN_DIR/plugins/LandformCraft/data/confirmations"
RCON_PASSWORD="v21104-measure-local"
RCON_PORT=25576
GAME_PORT=25566
WORLD_NAME="world"
XMX="${LANDFORMCRAFT_V21104_XMX:-8G}"
SERVER_PID=""
TELEMETRY_PID=""
PAPER_LAUNCH_PID=""

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

echo "==> exporting 500x500 solid surface Release"
rm -rf "$EXPORT_DIR"
LANDFORMCRAFT_V21104_EXPORT_DIR="$EXPORT_DIR" \
  ./gradlew test --tests 'com.github.nankotsu029.landformcraft.format.v2.release.V21104MeasurementFixtureExporterTest' \
  --rerun-tasks -q
RELEASE_NAME="$(tr -d '[:space:]' < "$EXPORT_DIR/release-dir-name.txt")"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
MANIFEST_SHA="$(tr -d '[:space:]' < "$EXPORT_DIR/manifest-sha256.txt")"

echo "==> preparing FAWE-only run-fawe/ (standalone paperclip, WorldEdit jar forbidden)"
test -f "$RUN_DIR/paper-1.21.11-132.jar"
rm -f "$RUN_DIR/plugins"/worldedit*.jar "$RUN_DIR/plugins"/WorldEdit*.jar
rm -f "$RUN_DIR/plugins"/LandformCraft*.jar
FAWE_JAR="$(ls -1 "$RUN_DIR/plugins"/FastAsyncWorldEdit*.jar | head -n 1)"
test -n "$FAWE_JAR"
test -f "$FAWE_JAR"
write_eula "$RUN_DIR"
enable_rcon "$RUN_DIR/server.properties" "$RCON_PASSWORD" "$RCON_PORT" "$GAME_PORT"
install_plugin_and_measurement_config "$RUN_DIR" "$WORLD_NAME" 500 500
# Re-assert FAWE-only after plugin install (no WorldEdit jar may reappear).
rm -f "$RUN_DIR/plugins"/worldedit*.jar "$RUN_DIR/plugins"/WorldEdit*.jar
test ! -f "$RUN_DIR/plugins"/worldedit*.jar
test -f "$FAWE_JAR"

rm -rf "$RELEASES_V2" "$PLACEMENT_V2" "$CONFIRM_DIR"
mkdir -p "$RELEASES_V2" "$PLACEMENT_V2" "$CONFIRM_DIR"
cp -R "$EXPORT_DIR/release" "$RELEASES_V2/$RELEASE_NAME"

rcon() {
  python3 "$RCON_PY" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 120 "$@"
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

  # Seal the mutation envelope with stone before plan so snapshot／Undo baseline is stable.
  # Paper 1.21 forceload arguments are BLOCK column X/Z (not chunk indices).
  # `forceload add 5 5` marks chunk [0,0]; use block ranges covering the envelope.
  echo "==> [$label] stone-seal mutation envelope (stable undo baseline, strip-wise)"
  : >"$life_dir/forceload.txt"
  : >"$life_dir/touchload.txt"
  : >"$life_dir/seal.txt"
  rcon "forceload remove 0 0 499 499" >>"$life_dir/forceload.txt" 2>&1 || true
  local x0 x1
  for x0 in $(seq 0 32 499); do
    x1=$((x0 + 31))
    if (( x1 > 499 )); then x1=499; fi
    # Force-load this strip's block columns (covers ~2×32 chunks).
    rcon "forceload add ${x0} 0 ${x1} 499" >>"$life_dir/forceload.txt" 2>&1 || true
    sleep 2
    # Touch corners to ensure residency before fill.
    rcon "setblock ${x0} 63 0 minecraft:bedrock" >>"$life_dir/touchload.txt" 2>&1 || true
    rcon "setblock ${x1} 63 499 minecraft:bedrock" >>"$life_dir/touchload.txt" 2>&1 || true
    sleep 1
    rcon "fill ${x0} 64 0 ${x1} 65 499 minecraft:stone" >>"$life_dir/seal.txt" 2>&1 || true
    rcon "forceload remove ${x0} 0 ${x1} 499" >>"$life_dir/forceload.txt" 2>&1 || true
  done
  if rg -q 'That position is not loaded' "$life_dir/seal.txt"; then
    echo "FAIL: stone-seal could not load positions" >&2
    rg -n 'That position is not loaded' "$life_dir/seal.txt" | head -n 20 >&2 || true
    head -n 40 "$life_dir/forceload.txt" >&2 || true
    return 1
  fi
  # Paper: "Successfully filled N block(s)" or "No blocks were filled" (already stone — OK).
  if ! rg -qi 'successfully filled|no blocks were filled' "$life_dir/seal.txt"; then
    echo "FAIL: stone-seal produced no recognizable fill responses" >&2
    cat "$life_dir/seal.txt" >&2 || true
    return 1
  fi
  echo "==> [$label] stone-seal OK ($(rg -c 'Successfully filled' "$life_dir/seal.txt" || echo 0) strips changed, $(rg -c 'No blocks were filled' "$life_dir/seal.txt" || echo 0) already stone)"

  # Keep full envelope force-loaded for placement (block-column range).
  echo "==> [$label] re-forceload full envelope for placement"
  rcon "forceload add 0 0 499 499" >>"$life_dir/forceload.txt" 2>&1 || true
  sleep 3

  echo "==> [$label] R2 plan"
  rcon "lfc r2 plan ${RELEASE_NAME} ${WORLD_NAME} 0 64 0" | tee "$life_dir/plan-rcon.txt" >/dev/null || true
  local confirm_cmd placement_id
  confirm_cmd="$(read_confirmation_command "$CONFIRM_DIR" "" 5 2>/dev/null || true)"
  # Key is r2-confirm-<uuid>; discover the newest file.
  local confirm_file
  confirm_file="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
  if [[ -z "$confirm_file" ]]; then
    # Wait for async plan completion to write the confirmation file.
    # 500×500 envelope planning can take several minutes under FAWE memory pressure.
    local waited=0
    while [[ -z "$confirm_file" && $waited -lt 900 ]]; do
      sleep 2
      waited=$((waited + 2))
      confirm_file="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
      if (( waited % 60 == 0 )); then
        echo "… waiting for r2-confirm (${waited}s)" >&2
      fi
    done
  fi
  if [[ -z "$confirm_file" ]]; then
    echo "FAIL: no r2-confirm confirmation file" >&2
    rg -n "Release 2|r2 |ERROR|Exception|plan" "$RUN_DIR/logs/latest.log" | tail -n 120 >&2 || true
    return 1
  fi
  confirm_cmd="$(tr -d '\r' <"$confirm_file" | head -n 1)"
  confirm_cmd="${confirm_cmd#/}"
  placement_id="$(basename "$confirm_file" .command)"
  placement_id="${placement_id#r2-confirm-}"
  echo "placement=$placement_id" | tee "$life_dir/placement-id.txt"
  echo "$confirm_cmd" | tee "$life_dir/confirm-command.txt"

  echo "==> [$label] R2 confirm (snapshot-all + containment)"
  rcon "$confirm_cmd" >/dev/null || true
  wait_for_log "$RUN_DIR/logs/latest.log" 'SNAPSHOT_COMPLETE|r2 execute |state: SNAPSHOT' 600 "$SERVER_PID"
  rg -n "SNAPSHOT_COMPLETE|CONFIRM|containment|r2 execute" "$RUN_DIR/logs/latest.log" \
    | tee "$life_dir/confirm-snippet.log" >/dev/null || true

  echo "==> [$label] R2 execute (apply→settle→full verify) — may take many minutes"
  rcon "lfc r2 execute ${placement_id}" >/dev/null || true
  # Poll both the live log and journal terminal; apply of 500k blocks is slice-bounded.
  local exec_start
  exec_start="$(date +%s)"
  while true; do
    if rg -q 'outcome: APPLIED|state: APPLIED' "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      break
    fi
    if rg -q 'outcome: ROLLED_BACK|RECOVERY_REQUIRED|state: ROLLED_BACK|state: RECOVERY_REQUIRED' \
         "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "FAIL: server died during execute" >&2
      return 1
    fi
    if (( $(date +%s) - exec_start >= 3600 )); then
      echo "FAIL: execute timeout" >&2
      rg -n "APPLIED|ROLLED_BACK|RECOVERY|execute|settle|verify" "$RUN_DIR/logs/latest.log" | tail -n 80 >&2 || true
      return 1
    fi
    # Progress breadcrumb every ~60s
    if (( ($(date +%s) - exec_start) % 60 < 3 )); then
      echo "… still executing ($(( $(date +%s) - exec_start ))s)" >&2
      rcon "lfc r2 status ${placement_id}" >/dev/null || true
    fi
    sleep 3
  done
  rg -n "APPLIED|ROLLED_BACK|RECOVERY_REQUIRED|exact verify|settle|outcome|state:" "$RUN_DIR/logs/latest.log" \
    | tee "$life_dir/execute-snippet.log" >/dev/null || true
  if ! rg -q "APPLIED" "$life_dir/execute-snippet.log" "$RUN_DIR/logs/latest.log"; then
    echo "FAIL: execute did not reach APPLIED" >&2
    return 1
  fi

  echo "==> [$label] R2 undo"
  rcon "lfc r2 undo-plan ${placement_id}" >/dev/null || true
  local undo_file=""
  local waited=0
  while [[ -z "$undo_file" && $waited -lt 180 ]]; do
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
  # Undo may take as long as apply; also fail fast on verify mismatch / recovery.
  local undo_start
  undo_start="$(date +%s)"
  while true; do
    if rg -q 'state: UNDONE|outcome: UNDONE' "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      break
    fi
    if rg -q 'undo exact verify mismatch|VERIFY_MISMATCH|state: RECOVERY_REQUIRED|outcome: RECOVERY_REQUIRED' \
         "$RUN_DIR/logs/latest.log" 2>/dev/null; then
      echo "FAIL: undo entered recovery / verify mismatch" >&2
      rg -n "undo exact verify|RECOVERY|UNDONE|VERIFY" "$RUN_DIR/logs/latest.log" | tail -n 40 >&2 || true
      return 1
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "FAIL: server died during undo" >&2
      return 1
    fi
    if (( $(date +%s) - undo_start >= 3600 )); then
      echo "FAIL: undo timeout" >&2
      return 1
    fi
    if (( ($(date +%s) - undo_start) % 60 < 3 )); then
      echo "… still undoing ($(( $(date +%s) - undo_start ))s)" >&2
    fi
    sleep 3
  done
  rg -n "UNDONE|undo" "$RUN_DIR/logs/latest.log" | tee "$life_dir/undo-snippet.log" >/dev/null || true
  if ! rg -q "UNDONE" "$life_dir/undo-snippet.log" "$RUN_DIR/logs/latest.log"; then
    echo "FAIL: undo did not reach UNDONE" >&2
    return 1
  fi

  end_epoch="$(date +%s)"
  {
    echo "label=$label"
    echo "placement_id=$placement_id"
    echo "wall_seconds=$((end_epoch - start_epoch))"
    echo "release=$RELEASE_NAME"
    echo "manifest_sha256=$MANIFEST_SHA"
  } | tee "$life_dir/summary.txt"

  # Capture operation checksums for determinism comparison.
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
PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper.stdout")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
echo "paper_launch_pid=$PAPER_LAUNCH_PID server_pid=$SERVER_PID" | tee "$EVIDENCE_DIR/pids.txt"

python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry.json" \
  --interval-seconds 20 &
TELEMETRY_PID=$!

wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'FastAsyncWorldEdit|Enabling FastAsyncWorldEdit' 120 "$SERVER_PID"
if ! rg -n 'measurement profile is ENABLED|measurement-profile=enabled' "$RUN_DIR/logs/latest.log" >/dev/null; then
  echo "WARN: measurement profile startup warning not found; checking config" >&2
  rg -n 'measurement-profile' "$RUN_DIR/plugins/LandformCraft/config.yml" || true
fi
if rg -n 'Enabling WorldEdit v' "$RUN_DIR/logs/latest.log" >/dev/null; then
  echo "FAIL: WorldEdit plugin enabled on FAWE-only profile" >&2
  exit 1
fi
test ! -f "$RUN_DIR/plugins"/worldedit*.jar

# Two full lifecycles for determinism evidence.
run_lifecycle "pass1"

# Never wipe placement-v2 while the JVM still holds the store — plan hung on pass2
# when files were deleted under a live server. Restart cleanly between passes.
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
# Keep the same Release bytes for determinism comparison.
test -d "$RELEASES_V2/$RELEASE_NAME"

PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper-pass2.stdout")"
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
sleep 8
CONFIRM_BEFORE_KILL="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
echo "confirm_before_kill=${CONFIRM_BEFORE_KILL:-none}" | tee "$EVIDENCE_DIR/recovery-plan.txt"
# Hard-stop the JVM to simulate crash after CONFIRMATION_ISSUED.
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
PAPER_LAUNCH_PID="$(start_standalone_paper "$RUN_DIR" "$XMX" "$EVIDENCE_DIR/paper-restart.stdout")"
sleep 2
SERVER_PID="$(find_server_pid "$RUN_DIR" "$PAPER_LAUNCH_PID")"
python3 "$TELEMETRY_PY" --pid "$SERVER_PID" --out "$EVIDENCE_DIR/telemetry-restart.json" \
  --interval-seconds 20 &
TELEMETRY_PID=$!
wait_for_log "$RUN_DIR/logs/latest.log" 'Done \(.*\)! For help' 600 "$SERVER_PID"
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling LandformCraft' 120 "$SERVER_PID"
rcon "lfc doctor" | tee "$EVIDENCE_DIR/recovery-doctor.txt" >/dev/null || true
rcon "lfc r2 status" >/dev/null 2>&1 || true
rg -n "RECOVERY|CONFIRMATION|placement|journal" "$RUN_DIR/logs/latest.log" \
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
    f"Build JAR SHA-256: $JAR_SHA",
    f"Manifest canonical SHA-256: $MANIFEST_SHA",
    f"Release: $RELEASE_NAME / surface-2_5d / 500x500 solid y=0..1",
    f"Measurement profile world: $WORLD_NAME ceiling=500x500",
    f"Server PID (final): $SERVER_PID",
    f"Peak RSS (bytes): {peak if peak else 'unknown'}",
    f"Disk snapshots (bytes): $DISK_BYTES",
    "Pass1:",
    pass1.strip(),
    "Pass2:",
    pass2.strip(),
    "Failure/recovery: plan then SIGKILL; restart + doctor recorded",
    "Catalog promotion: NOT performed (V2-11-06)",
    f"Operator / date: agent / $(date -u +%Y-%m-%d)",
    "",
])
(evidence / "summary.txt").write_text(text)
print(text)
PY

# Success gates
rg -n "APPLIED" "$EVIDENCE_DIR/pass1/execute-snippet.log" >/dev/null
rg -n "UNDONE" "$EVIDENCE_DIR/pass1/undo-snippet.log" >/dev/null
rg -n "APPLIED" "$EVIDENCE_DIR/pass2/execute-snippet.log" >/dev/null
rg -n "UNDONE" "$EVIDENCE_DIR/pass2/undo-snippet.log" >/dev/null

echo "==> stopping server"
rcon stop >/dev/null || true
wait "$PAPER_LAUNCH_PID" 2>/dev/null || true
SERVER_PID=""
PAPER_LAUNCH_PID=""
echo "RESULT: PASS — evidence at $EVIDENCE_DIR/summary.txt"
