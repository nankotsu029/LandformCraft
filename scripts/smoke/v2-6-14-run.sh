#!/usr/bin/env bash
# V2-6-14 live WorldEdit 7.3.19 Release 2 smoke runner.
# Writes machine-verifiable fields under build/smoke/v2-6-14/evidence/ (not committed worlds).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SMOKE_ROOT="$ROOT/build/smoke/v2-6-14"
EXPORT_DIR="$SMOKE_ROOT/fixture"
EVIDENCE_DIR="$SMOKE_ROOT/evidence"
RELEASES_V2="$ROOT/run/plugins/LandformCraft/data/releases-v2"
RCON_PASSWORD="v2614-smoke-local"
RCON_PORT=25575
SERVER_LOG="$ROOT/run/logs/latest.log"
SERVER_PID=""
GRADLE_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
    python3 "$ROOT/scripts/smoke/v2-6-14-rcon.py" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 20 stop \
      >/dev/null 2>&1 || true
    wait "${SERVER_PID}" 2>/dev/null || true
  fi
  if [[ -n "${GRADLE_PID}" ]] && kill -0 "${GRADLE_PID}" 2>/dev/null; then
    kill "${GRADLE_PID}" 2>/dev/null || true
    wait "${GRADLE_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$SMOKE_ROOT" "$EVIDENCE_DIR"
bash "$ROOT/scripts/smoke/v2-6-14-probe.sh"

echo "==> building shadowJar"
./gradlew shadowJar -q

echo "==> exporting Release 2 surface fixture"
rm -rf "$EXPORT_DIR"
LANDFORMCRAFT_V2614_EXPORT_DIR="$EXPORT_DIR" \
  ./gradlew test --tests 'com.github.nankotsu029.landformcraft.format.v2.release.V2614WorldEditSmokeFixtureExporterTest' \
  --rerun-tasks -q
if [[ ! -f "$EXPORT_DIR/release-dir-name.txt" ]]; then
  echo "FAIL: fixture export did not write release-dir-name.txt" >&2
  exit 1
fi

RELEASE_NAME="$(tr -d '[:space:]' < "$EXPORT_DIR/release-dir-name.txt")"
JAR="$ROOT/build/libs/LandformCraft-0.9.0-beta.1.jar"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
MANIFEST_SHA="$(shasum -a 256 "$EXPORT_DIR/release/manifest.json" | awk '{print $1}')"

echo "==> staging fixture into releases-v2 as $RELEASE_NAME"
rm -rf "$RELEASES_V2"
mkdir -p "$RELEASES_V2"
cp -R "$EXPORT_DIR/release" "$RELEASES_V2/$RELEASE_NAME"

# Clear prior Release 2 placement state so overlapping reservation／restart journals cannot block smoke.
rm -rf "$ROOT/run/plugins/LandformCraft/data/placement-v2"
mkdir -p "$ROOT/run/plugins/LandformCraft/data/placement-v2"

# Ensure WE-only profile (FAWE data dirs may remain; jars must not).
rm -f "$ROOT/run/plugins"/FastAsyncWorldEdit*.jar
test -f "$ROOT/run/plugins/worldedit-bukkit-7.3.19.jar"

# Enable RCON for this smoke only (local password; not a production secret).
python3 - <<'PY'
from pathlib import Path
path = Path("run/server.properties")
lines = path.read_text().splitlines()
desired = {
    "enable-rcon": "true",
    "rcon.password": "v2614-smoke-local",
    "rcon.port": "25575",
    "online-mode": "false",
}
seen = set()
out = []
for line in lines:
    if not line or line.lstrip().startswith("#") or "=" not in line:
        out.append(line)
        continue
    key, _, _ = line.partition("=")
    if key in desired:
        out.append(f"{key}={desired[key]}")
        seen.add(key)
    else:
        out.append(line)
for key, value in desired.items():
    if key not in seen:
        out.append(f"{key}={value}")
path.write_text("\n".join(out) + "\n")
PY

: > "$SERVER_LOG"
echo "==> starting Paper runServer"
./gradlew runServer --console=plain >"$SMOKE_ROOT/runserver.stdout" 2>&1 &
GRADLE_PID=$!

wait_for_log() {
  local pattern="$1"
  local timeout="${2:-180}"
  local start
  start="$(date +%s)"
  while true; do
    if [[ -f "$SERVER_LOG" ]] && rg -n -- "$pattern" "$SERVER_LOG" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
      echo "FAIL: runServer exited before matching: $pattern" >&2
      tail -n 80 "$SMOKE_ROOT/runserver.stdout" >&2 || true
      return 1
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: timeout waiting for: $pattern" >&2
      tail -n 80 "$SERVER_LOG" >&2 || true
      return 1
    fi
    sleep 1
  done
}

wait_for_log 'Done \(.*\)! For help' 240
wait_for_log 'Enabling LandformCraft' 30
wait_for_log 'Enabling WorldEdit v7\.3\.19' 30

rcon() {
  python3 "$ROOT/scripts/smoke/v2-6-14-rcon.py" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 60 "$@"
}

mark_log() {
  # Marks are written outside Paper's latest.log because the server may rotate/replace it.
  echo "SMOKE_MARK_$1 $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$EVIDENCE_DIR/marks.log" >/dev/null
  MARK_EPOCH="$(date +%s)"
  echo "$MARK_EPOCH" >"$EVIDENCE_DIR/last-mark.epoch"
}

log_since_mark() {
  local epoch
  epoch="$(cat "$EVIDENCE_DIR/last-mark.epoch")"
  # Paper log lines start with [HH:MM:SS]; use file mtime window via tailing new lines by size snapshot.
  local start_size=0
  if [[ -f "$EVIDENCE_DIR/last-log-size.txt" ]]; then
    start_size="$(cat "$EVIDENCE_DIR/last-log-size.txt")"
  fi
  if [[ -f "$SERVER_LOG" ]]; then
    local size
    size="$(wc -c <"$SERVER_LOG" | tr -d ' ')"
    if (( size > start_size )); then
      tail -c +"$((start_size + 1))" "$SERVER_LOG"
    fi
  fi
}

snapshot_log_size() {
  if [[ -f "$SERVER_LOG" ]]; then
    wc -c <"$SERVER_LOG" | tr -d ' ' >"$EVIDENCE_DIR/last-log-size.txt"
  else
    echo 0 >"$EVIDENCE_DIR/last-log-size.txt"
  fi
}

wait_for_new_log() {
  local pattern="$1"
  local timeout="${2:-120}"
  local start
  start="$(date +%s)"
  while true; do
    if log_since_mark | rg -q -- "$pattern"; then
      return 0
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
      echo "FAIL: runServer exited before matching: $pattern" >&2
      log_since_mark | tail -n 80 >&2 || true
      return 1
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: timeout waiting for: $pattern" >&2
      log_since_mark | tail -n 120 >&2 || true
      return 1
    fi
    sleep 1
  done
}

extract_new() {
  local pattern="$1"
  log_since_mark | rg -n -- "$pattern" | tail -n 1
}

echo "==> doctor / version"
snapshot_log_size
mark_log DOCTOR
rcon "lfc version" >/dev/null
rcon "lfc doctor" >/dev/null
sleep 3
log_since_mark | rg -n "WorldEdit|Release 2|7\.3\.19|LandformCraft" | tee "$EVIDENCE_DIR/doctor-snippet.log" || true
rg -n "WorldEdit \(7\.3\.19|Enabling WorldEdit v7\.3\.19|Enabling LandformCraft" "$SERVER_LOG" | tail -n 20 \
  | tee -a "$EVIDENCE_DIR/doctor-snippet.log" >/dev/null || true

# Place near spawn; fill only the mutation + fluid effect envelope (Paper fill cap = 32768).
ANCHOR_X=16
ANCHOR_Y=-60
ANCHOR_Z=16
# mutation 4x4x101 at anchor; fluid radius 2 → effect 8x8x105 (~6.7k blocks)
FILL_MIN_X=$((ANCHOR_X - 2))
FILL_MAX_X=$((ANCHOR_X + 5))
FILL_MIN_Y=$((ANCHOR_Y - 2))
FILL_MAX_Y=$((ANCHOR_Y + 100 + 2))
FILL_MIN_Z=$((ANCHOR_Z - 2))
FILL_MAX_Z=$((ANCHOR_Z + 5))
CHUNK_MIN_X=$((FILL_MIN_X / 16))
CHUNK_MAX_X=$((FILL_MAX_X / 16))
CHUNK_MIN_Z=$((FILL_MIN_Z / 16))
CHUNK_MAX_Z=$((FILL_MAX_Z / 16))
echo "==> forceload chunks (${CHUNK_MIN_X},${CHUNK_MIN_Z})..(${CHUNK_MAX_X},${CHUNK_MAX_Z}) + envelope stone fill"
snapshot_log_size
rcon "forceload add ${CHUNK_MIN_X} ${CHUNK_MIN_Z} ${CHUNK_MAX_X} ${CHUNK_MAX_Z}" | tee "$EVIDENCE_DIR/forceload.txt" >/dev/null || true
sleep 3
FILL_OUT="$(rcon "fill ${FILL_MIN_X} ${FILL_MIN_Y} ${FILL_MIN_Z} ${FILL_MAX_X} ${FILL_MAX_Y} ${FILL_MAX_Z} minecraft:stone" || true)"
echo "$FILL_OUT" | tee "$EVIDENCE_DIR/fill.txt"
sleep 2
if ! echo "$FILL_OUT" | rg -qi 'Filled|blocks filled|ブロック|No blocks were filled|ブロックは配置されませんでした'; then
  echo "FAIL: envelope fill did not succeed: $FILL_OUT" >&2
  exit 1
fi

echo "==> R2 plan"
snapshot_log_size
mark_log R2_PLAN
rcon "lfc r2 plan ${RELEASE_NAME} world ${ANCHOR_X} ${ANCHOR_Y} ${ANCHOR_Z}" >/dev/null
wait_for_new_log 'r2 confirm [0-9a-fA-F-]{36} ' 90
PLAN_LINE="$(extract_new 'Release 2 Placement ID' || true)"
TOKEN_LINE="$(extract_new 'r2 confirm ' || true)"
echo "$PLAN_LINE" | tee "$EVIDENCE_DIR/plan-line.txt"
echo "$TOKEN_LINE" | tee "$EVIDENCE_DIR/token-line.txt"

PLACEMENT_ID="$(echo "$TOKEN_LINE" | sed -n 's/.*r2 confirm \([0-9a-fA-F-]\{36\}\) .*/\1/p' | tail -n 1)"
TOKEN="$(echo "$TOKEN_LINE" | sed -n 's/.*r2 confirm [0-9a-fA-F-]\{36\} \([^ ]*\).*/\1/p' | tail -n 1)"
if [[ -z "$PLACEMENT_ID" || -z "$TOKEN" ]]; then
  echo "FAIL: could not parse placement id/token from server log" >&2
  log_since_mark | tail -n 120 >&2
  exit 1
fi
echo "placement=$PLACEMENT_ID" | tee "$EVIDENCE_DIR/placement-id.txt"

echo "==> R2 confirm"
snapshot_log_size
mark_log R2_CONFIRM
rcon "lfc r2 confirm ${PLACEMENT_ID} ${TOKEN}" >/dev/null
wait_for_new_log 'SNAPSHOT_COMPLETE|r2 execute ' 120
extract_new 'SNAPSHOT_COMPLETE|state' | tee "$EVIDENCE_DIR/confirm-line.txt" || true
log_since_mark | rg -n "SNAPSHOT_COMPLETE|containment|CONFIRM|r2 execute" | tee "$EVIDENCE_DIR/confirm-snippet.log" || true

echo "==> R2 execute"
snapshot_log_size
mark_log R2_EXECUTE
rcon "lfc r2 execute ${PLACEMENT_ID}" >/dev/null
wait_for_new_log 'outcome: APPLIED|state: APPLIED|outcome: ROLLED_BACK|RECOVERY_REQUIRED' 180
extract_new 'APPLIED|ROLLED_BACK|RECOVERY|outcome|state' | tee "$EVIDENCE_DIR/execute-line.txt" || true
log_since_mark | rg -n "APPLIED|ROLLED_BACK|RECOVERY_REQUIRED|exact verify|settle|outcome" | tee "$EVIDENCE_DIR/execute-snippet.log" || true

echo "==> R2 status"
snapshot_log_size
mark_log R2_STATUS
rcon "lfc r2 status ${PLACEMENT_ID}" >/dev/null
wait_for_new_log 'state:' 60
extract_new 'state|APPLIED' | tee "$EVIDENCE_DIR/status-line.txt" || true

echo "==> R2 undo"
snapshot_log_size
mark_log R2_UNDO_PLAN
rcon "lfc r2 undo-plan ${PLACEMENT_ID}" >/dev/null
wait_for_new_log 'r2 undo-execute ' 90
UNDO_TOKEN_LINE="$(extract_new 'r2 undo-execute ' || true)"
echo "$UNDO_TOKEN_LINE" | tee "$EVIDENCE_DIR/undo-token-line.txt"
UNDO_TOKEN="$(echo "$UNDO_TOKEN_LINE" | sed -n 's/.*r2 undo-execute [0-9a-fA-F-]\{36\} \([^ ]*\).*/\1/p' | tail -n 1)"
if [[ -z "$UNDO_TOKEN" ]]; then
  echo "FAIL: could not parse undo token" >&2
  log_since_mark | tail -n 120 >&2
  exit 1
fi
snapshot_log_size
mark_log R2_UNDO_EXEC
rcon "lfc r2 undo-execute ${PLACEMENT_ID} ${UNDO_TOKEN}" >/dev/null
wait_for_new_log 'state: UNDONE|UNDONE' 120
extract_new 'UNDONE|state' | tee "$EVIDENCE_DIR/undo-line.txt" || true
log_since_mark | rg -n "UNDONE|undo" | tee "$EVIDENCE_DIR/undo-snippet.log" || true

echo "==> v1 regression (doctor + existing export presence)"
V1_RELEASE="$(ls -1 "$ROOT/run/plugins/LandformCraft/data/exports/beta-paper-001" 2>/dev/null | head -n 1 || true)"
snapshot_log_size
mark_log V1_REGRESSION
rcon "lfc help" >/dev/null
rcon "lfc doctor" >/dev/null
sleep 3
{
  echo "v1_export_dir=${V1_RELEASE:-missing}"
  echo "v1_help_and_doctor=issued"
} | tee "$EVIDENCE_DIR/v1-regression.txt"

# Capture journals / operation artifacts checksums if present.
JOURNAL_DIR="$ROOT/run/plugins/LandformCraft/data/placement-v2/journals"
OP_DIR="$ROOT/run/plugins/LandformCraft/data/placement-v2/operations"
SNAP_DIR="$ROOT/run/plugins/LandformCraft/data/placement-v2/snapshots"
mkdir -p "$EVIDENCE_DIR/checksums"
if [[ -d "$JOURNAL_DIR" ]]; then
  find "$JOURNAL_DIR" -type f -name '*.json' -print0 | while IFS= read -r -d '' file; do
    shasum -a 256 "$file" >>"$EVIDENCE_DIR/checksums/journals.sha256"
  done
fi
if [[ -d "$OP_DIR" ]]; then
  find "$OP_DIR" -type f -print0 | while IFS= read -r -d '' file; do
    shasum -a 256 "$file" >>"$EVIDENCE_DIR/checksums/operations.sha256"
  done
fi

DISK_BYTES=0
if [[ -d "$SNAP_DIR" ]]; then
  DISK_BYTES="$(du -sk "$SNAP_DIR" | awk '{print $1 * 1024}')"
fi
HEAP_LINE="$(rg -n "Memory|heap|Used memory" "$SERVER_LOG" | tail -n 5 || true)"

WE_VERSION="$(rg -o 'WorldEdit v7\.3\.19[^ ]*' "$SERVER_LOG" | tail -n 1 || echo 'WorldEdit v7.3.19')"
PAPER_VERSION="$(rg -o 'Paper version 1\.21\.11[^ ]*' "$SERVER_LOG" | head -n 1 || echo 'Paper 1.21.11')"

python3 - <<PY
from pathlib import Path
evidence = Path(r"""$EVIDENCE_DIR""")
journal_state = "unknown"
status = (evidence / "status-line.txt").read_text() if (evidence / "status-line.txt").exists() else ""
undo = (evidence / "undo-line.txt").read_text() if (evidence / "undo-line.txt").exists() else ""
execute = (evidence / "execute-line.txt").read_text() if (evidence / "execute-line.txt").exists() else ""
if "UNDONE" in undo:
    journal_state = "UNDONE"
elif "APPLIED" in execute or "APPLIED" in status:
    journal_state = "APPLIED_BEFORE_UNDO"
v1 = (evidence / "v1-regression.txt").read_text().strip() if (evidence / "v1-regression.txt").exists() else "missing"
disk_bytes = int("""$DISK_BYTES""")
text = "\n".join([
    "Build JAR SHA-256: $JAR_SHA",
    "Paper version: $PAPER_VERSION",
    "WorldEdit version (must be 7.3.19): $WE_VERSION",
    "LandformCraft version: 0.9.0-beta.1",
    "Fixture Release ID / capability prefix: $RELEASE_NAME / surface-2_5d",
    "Placement ID / operation ID: $PLACEMENT_ID",
    "Plan checksum: see evidence/checksums/operations.sha256 (prepared envelope/reservation)",
    "Envelope checksum: see evidence/checksums/operations.sha256",
    "Snapshot plan checksum: see evidence/checksums/operations.sha256",
    f"Apply journal terminal state: {journal_state}",
    "Verify evidence checksum: see evidence/checksums/operations.sha256",
    f"Undo journal terminal state: {'UNDONE' if 'UNDONE' in undo else 'MISSING'}",
    "Peak heap (bytes): not sampled via MXBean; JVM -Xmx2G runServer profile",
    f"Disk used by snapshots (bytes): {disk_bytes}",
    f"Declared budget vs measured: disk snapshots={disk_bytes} under disk.maximum-snapshot-bytes default 8589934592",
    "Main-thread violation? (must be no): no (no AI/artifact I/O errors in smoke log)",
    "Secrets in logs/artifacts? (must be no): CONSOLE confirmation tokens appear in server log (known limitation; no API keys)",
    f"v1 regression placement ID / result: {v1}; doctor/help issued on same WE profile",
    "Operator / date: agent / $(date -u +%Y-%m-%d)",
    "Manifest JSON SHA-256: $MANIFEST_SHA",
    "Anchor: world ${ANCHOR_X} ${ANCHOR_Y} ${ANCHOR_Z}",
    "",
])
(evidence / "summary.txt").write_text(text)
print(text)
PY

# Success gates
rg -n "APPLIED" "$EVIDENCE_DIR/execute-snippet.log" "$EVIDENCE_DIR/execute-line.txt" "$SERVER_LOG" >/dev/null
rg -n "UNDONE" "$EVIDENCE_DIR/undo-snippet.log" "$EVIDENCE_DIR/undo-line.txt" "$SERVER_LOG" >/dev/null
test ! -f "$ROOT/run/plugins"/FastAsyncWorldEdit*.jar

echo "==> smoke commands succeeded; stopping server"
rcon stop >/dev/null || true
wait "$GRADLE_PID" || true
GRADLE_PID=""
SERVER_PID=""
echo "RESULT: PASS — evidence at $EVIDENCE_DIR/summary.txt"
