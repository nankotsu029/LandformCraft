#!/usr/bin/env bash
# V2-11-04 WorldEdit 7.3.19 standalone Paper JVM measurement (500×500).
# Does NOT use ./gradlew runServer — Paper is launched as its own JVM via paperclip.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=v2-11-04-common.sh
source "$SCRIPT_DIR/v2-11-04-common.sh"
measure_common_init

PROFILE="worldedit"
MEASURE_ROOT="$ROOT/build/measure/v2-11-04"
EXPORT_DIR="$MEASURE_ROOT/fixture"
EVIDENCE_DIR="$MEASURE_ROOT/evidence/${PROFILE}"
RUN_DIR="$ROOT/run"
RELEASES_V2="$RUN_DIR/plugins/LandformCraft/data/releases-v2"
PLACEMENT_V2="$RUN_DIR/plugins/LandformCraft/data/placement-v2"
CONFIRM_DIR="$RUN_DIR/plugins/LandformCraft/data/confirmations"
RCON_PASSWORD="v21104-measure-local"
RCON_PORT=25575
GAME_PORT=25565
WORLD_NAME="world"
XMX="${LANDFORMCRAFT_V21104_XMX:-10G}"
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

echo "==> preparing WE-only run/ (standalone paperclip)"
test -f "$RUN_DIR/paper-1.21.11-132.jar"
rm -f "$RUN_DIR/plugins"/FastAsyncWorldEdit*.jar
test -f "$RUN_DIR/plugins/worldedit-bukkit-7.3.19.jar"
write_eula "$RUN_DIR"
enable_rcon "$RUN_DIR/server.properties" "$RCON_PASSWORD" "$RCON_PORT" "$GAME_PORT"
install_plugin_and_measurement_config "$RUN_DIR" "$WORLD_NAME" 500 500

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

  # 500×500 → chunks 0..31 inclusive at anchor (0,64,0)
  echo "==> [$label] forceload measurement envelope chunks"
  rcon "forceload add 0 0 31 31" | tee "$life_dir/forceload.txt" >/dev/null || true
  sleep 5

  echo "==> [$label] R2 plan"
  rcon "lfc r2 plan ${RELEASE_NAME} ${WORLD_NAME} 0 64 0" >/dev/null || true
  local confirm_cmd placement_id
  confirm_cmd="$(read_confirmation_command "$CONFIRM_DIR" "" 5 2>/dev/null || true)"
  # Key is r2-confirm-<uuid>; discover the newest file.
  local confirm_file
  confirm_file="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
  if [[ -z "$confirm_file" ]]; then
    # Wait for async plan completion to write the confirmation file.
    local waited=0
    while [[ -z "$confirm_file" && $waited -lt 180 ]]; do
      sleep 2
      waited=$((waited + 2))
      confirm_file="$(ls -1t "$CONFIRM_DIR"/r2-confirm-*.command 2>/dev/null | head -n 1 || true)"
    done
  fi
  if [[ -z "$confirm_file" ]]; then
    echo "FAIL: no r2-confirm confirmation file" >&2
    rg -n "Release 2|r2 |ERROR|Exception" "$RUN_DIR/logs/latest.log" | tail -n 80 >&2 || true
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
  wait_for_log "$RUN_DIR/logs/latest.log" 'outcome: APPLIED|state: APPLIED|outcome: ROLLED_BACK|RECOVERY_REQUIRED' 3600 "$SERVER_PID"
  rg -n "APPLIED|ROLLED_BACK|RECOVERY_REQUIRED|exact verify|settle|outcome" "$RUN_DIR/logs/latest.log" \
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
  wait_for_log "$RUN_DIR/logs/latest.log" 'state: UNDONE|UNDONE' 3600 "$SERVER_PID"
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
wait_for_log "$RUN_DIR/logs/latest.log" 'Enabling WorldEdit v7\.3\.19' 120 "$SERVER_PID"
if ! rg -n 'measurement profile is ENABLED|measurement-profile=enabled' "$RUN_DIR/logs/latest.log" >/dev/null; then
  echo "WARN: measurement profile startup warning not found; checking config" >&2
  rg -n 'measurement-profile' "$RUN_DIR/plugins/LandformCraft/config.yml" || true
fi
test ! -f "$RUN_DIR/plugins"/FastAsyncWorldEdit*.jar

# Two full lifecycles for determinism evidence.
run_lifecycle "pass1"
# Clear placement state between passes but keep the same Release bytes.
rm -rf "$PLACEMENT_V2" "$CONFIRM_DIR"
mkdir -p "$PLACEMENT_V2" "$CONFIRM_DIR"
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
  echo "profile=worldedit-7.3.19"
} | tee "$EVIDENCE_DIR/v1-regression.txt"

DISK_BYTES=0
if [[ -d "$PLACEMENT_V2/snapshots" ]]; then
  DISK_BYTES="$(du -sk "$PLACEMENT_V2/snapshots" | awk '{print $1 * 1024}')"
fi

python3 - <<PY
from pathlib import Path
import json
evidence = Path(r"""$EVIDENCE_DIR""")
telemetry = {}
tel_path = evidence / "telemetry.json"
if tel_path.exists():
    telemetry = json.loads(tel_path.read_text())
peak_rss = telemetry.get("peak_rss_bytes", "unknown")
pass1 = (evidence / "pass1" / "summary.txt").read_text() if (evidence / "pass1" / "summary.txt").exists() else "missing"
pass2 = (evidence / "pass2" / "summary.txt").read_text() if (evidence / "pass2" / "summary.txt").exists() else "missing"
text = "\\n".join([
    "Profile: WorldEdit 7.3.19 standalone",
    "Boot: java -jar paper-1.21.11-132.jar (no Gradle runServer)",
    f"Build JAR SHA-256: $JAR_SHA",
    f"Manifest canonical SHA-256: $MANIFEST_SHA",
    f"Release: $RELEASE_NAME / surface-2_5d / 500x500 solid y=0..1",
    f"Measurement profile world: $WORLD_NAME ceiling=500x500",
    f"Server PID: $SERVER_PID",
    f"Peak RSS (bytes): {peak_rss}",
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
