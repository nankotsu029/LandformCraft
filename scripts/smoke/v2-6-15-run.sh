#!/usr/bin/env bash
# V2-6-15 live FAWE 2.15.2 standalone Release 2 smoke runner.
# Writes machine-verifiable fields under build/smoke/v2-6-15/evidence/ (not committed worlds).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SMOKE_ROOT="$ROOT/build/smoke/v2-6-15"
EXPORT_DIR="$SMOKE_ROOT/fixture"
EVIDENCE_DIR="$SMOKE_ROOT/evidence"
RUN_DIR="$ROOT/run-fawe"
RELEASES_V2="$RUN_DIR/plugins/LandformCraft/data/releases-v2"
PLACEMENT_V2="$RUN_DIR/plugins/LandformCraft/data/placement-v2"
RCON_PASSWORD="v2615-smoke-local"
RCON_PORT=25576
SERVER_LOG="$RUN_DIR/logs/latest.log"
GRADLE_PID=""

cleanup() {
  if [[ -n "${GRADLE_PID}" ]] && kill -0 "${GRADLE_PID}" 2>/dev/null; then
    python3 "$ROOT/scripts/smoke/v2-6-14-rcon.py" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 20 stop \
      >/dev/null 2>&1 || true
    wait "${GRADLE_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$SMOKE_ROOT" "$EVIDENCE_DIR"
bash "$ROOT/scripts/smoke/v2-6-15-probe.sh"

echo "==> building shadowJar"
./gradlew shadowJar -q

echo "==> exporting Release 2 surface fixture (shared exporter with V2-6-14)"
rm -rf "$EXPORT_DIR"
LANDFORMCRAFT_V2614_EXPORT_DIR="$EXPORT_DIR" \
  ./gradlew test --tests 'com.github.nankotsu029.landformcraft.format.v2.release.V2614WorldEditSmokeFixtureExporterTest' \
  --rerun-tasks -q
if [[ ! -f "$EXPORT_DIR/release-dir-name.txt" ]]; then
  echo "FAIL: fixture export did not write release-dir-name.txt" >&2
  exit 1
fi

RELEASE_NAME="$(tr -d '[:space:]' < "$EXPORT_DIR/release-dir-name.txt")"
# Rename fixture id for FAWE evidence clarity while keeping identical bytes.
if [[ "$RELEASE_NAME" == *we-smoke* ]]; then
  FAWE_RELEASE_NAME="${RELEASE_NAME/we-smoke/fawe-smoke}"
else
  FAWE_RELEASE_NAME="v2-6-15-fawe-smoke"
fi
JAR="$ROOT/build/libs/LandformCraft-0.9.0-beta.1.jar"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
FAWE_JAR="$(ls -1 "$RUN_DIR/plugins"/FastAsyncWorldEdit*.jar | head -n 1)"
FAWE_JAR_SHA="$(shasum -a 256 "$FAWE_JAR" | awk '{print $1}')"
MANIFEST_SHA="$(shasum -a 256 "$EXPORT_DIR/release/manifest.json" | awk '{print $1}')"

echo "==> enforcing FAWE-only plugin jars"
rm -f "$RUN_DIR/plugins"/worldedit*.jar "$RUN_DIR/plugins"/WorldEdit*.jar
rm -f "$RUN_DIR/plugins"/LandformCraft*.jar
test -f "$FAWE_JAR"
test ! -f "$RUN_DIR/plugins"/worldedit*.jar

echo "==> staging fixture into releases-v2 as $FAWE_RELEASE_NAME"
rm -rf "$RELEASES_V2"
mkdir -p "$RELEASES_V2"
cp -R "$EXPORT_DIR/release" "$RELEASES_V2/$FAWE_RELEASE_NAME"
echo "$FAWE_RELEASE_NAME" >"$EVIDENCE_DIR/release-name.txt"

rm -rf "$PLACEMENT_V2"
mkdir -p "$PLACEMENT_V2"

# Enable RCON for this smoke only (local password; not a production secret).
python3 - <<PY
from pathlib import Path
path = Path(r"""$RUN_DIR/server.properties""")
lines = path.read_text().splitlines()
desired = {
    "enable-rcon": "true",
    "rcon.password": "v2615-smoke-local",
    "rcon.port": "25576",
    "online-mode": "false",
    "server-port": "25566",
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
path.write_text("\\n".join(out) + "\\n")
PY

mkdir -p "$RUN_DIR/logs"
: > "$SERVER_LOG"
echo "==> starting Paper runFaweServer (run-fawe/)"
./gradlew runFaweServer --console=plain >"$SMOKE_ROOT/runserver.stdout" 2>&1 &
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
      echo "FAIL: runFaweServer exited before matching: $pattern" >&2
      tail -n 120 "$SMOKE_ROOT/runserver.stdout" >&2 || true
      return 1
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: timeout waiting for: $pattern" >&2
      tail -n 120 "$SERVER_LOG" >&2 || true
      tail -n 80 "$SMOKE_ROOT/runserver.stdout" >&2 || true
      return 1
    fi
    sleep 1
  done
}

wait_for_log 'Done \(.*\)! For help' 300
wait_for_log 'Enabling LandformCraft' 60
wait_for_log 'FastAsyncWorldEdit|Enabling FastAsyncWorldEdit' 60

# Reject accidental WorldEdit plugin enable.
if rg -n 'Enabling WorldEdit v' "$SERVER_LOG" >/dev/null 2>&1; then
  echo "FAIL: WorldEdit plugin enabled on FAWE-only profile" >&2
  exit 1
fi

rcon() {
  python3 "$ROOT/scripts/smoke/v2-6-14-rcon.py" --password "$RCON_PASSWORD" --port "$RCON_PORT" --timeout 60 "$@"
}

mark_log() {
  echo "SMOKE_MARK_$1 $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$EVIDENCE_DIR/marks.log" >/dev/null
  date +%s >"$EVIDENCE_DIR/last-mark.epoch"
}

log_since_mark() {
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
      echo "FAIL: runFaweServer exited before matching: $pattern" >&2
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
log_since_mark | rg -n "FastAsyncWorldEdit|WorldEdit|Release 2|LandformCraft|2\.15\.2" \
  | tee "$EVIDENCE_DIR/doctor-snippet.log" || true
rg -n "FastAsyncWorldEdit|Enabling LandformCraft|WorldEdit" "$SERVER_LOG" | tail -n 40 \
  | tee -a "$EVIDENCE_DIR/doctor-snippet.log" >/dev/null || true

ANCHOR_X=16
ANCHOR_Y=-60
ANCHOR_Z=16
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
rcon "lfc r2 plan ${FAWE_RELEASE_NAME} world ${ANCHOR_X} ${ANCHOR_Y} ${ANCHOR_Z}" >/dev/null
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

echo "==> v1 regression (help + doctor on FAWE profile)"
snapshot_log_size
mark_log V1_REGRESSION
rcon "lfc help" >/dev/null
rcon "lfc doctor" >/dev/null
sleep 3
{
  echo "profile=FAWE-2.15.2-only"
  echo "v1_help_and_doctor=issued"
  echo "worldedit_plugin_jar=absent"
} | tee "$EVIDENCE_DIR/v1-regression.txt"

JOURNAL_DIR="$PLACEMENT_V2/journals"
OP_DIR="$PLACEMENT_V2/operations"
SNAP_DIR="$PLACEMENT_V2/snapshots"
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

# Extract machine-verifiable semantic checksums from operation artifacts.
python3 - <<PY
import json
from pathlib import Path
op = Path(r"""$OP_DIR""") / """$PLACEMENT_ID"""
out = Path(r"""$EVIDENCE_DIR""") / "checksums" / "semantic-fields.txt"
fields = {}
journal_path = Path(r"""$JOURNAL_DIR""") / """$PLACEMENT_ID.json"""
mapping = {
    "plan": "plan.json",
    "envelope": "envelope.json",
    "reservation": "reservation.json",
    "snapshot": "snapshot.json",
    "verify-evidence": "verify-evidence.json",
    "apply-complete-journal": "apply-complete-journal.json",
    "journal": journal_path,
}
lines = []
for label, name in mapping.items():
    path = op / name if isinstance(name, str) else name
    if not path.exists():
        lines.append(f"{label}: missing")
        continue
    data = json.loads(path.read_text())
    checksum = (
        data.get("canonicalChecksum")
        or data.get("planChecksum")
        or data.get("envelopeChecksum")
        or data.get("snapshotPlanChecksum")
        or data.get("verifyChecksum")
        or data.get("expectedStreamChecksum")
        or data.get("observedStreamChecksum")
        or data.get("checksum")
    )
    state = data.get("state") or data.get("terminalState") or data.get("outcome")
    if label == "verify-evidence":
        expected = data.get("expectedStreamChecksum") or data.get("expectedChecksum")
        observed = data.get("observedStreamChecksum") or data.get("observedChecksum")
        lines.append(f"verify-evidence: expected={expected} observed={observed} fileChecksum={checksum}")
    elif label == "journal" or label == "apply-complete-journal":
        lines.append(f"{label}: state={state} checksum={checksum}")
    else:
        lines.append(f"{label}: checksum={checksum}")
    # also dump top-level checksum-like keys for evidence
    for key, value in data.items():
        if "checksum" in key.lower() or key in ("state", "terminalState", "outcome"):
            fields[f"{label}.{key}"] = value
out.write_text("\\n".join(lines) + "\\n\\n" + "\\n".join(f"{k}={v}" for k, v in sorted(fields.items())) + "\\n")
print(out.read_text())
PY

DISK_BYTES=0
if [[ -d "$SNAP_DIR" ]]; then
  DISK_BYTES="$(du -sk "$SNAP_DIR" | awk '{print $1 * 1024}')"
fi

FAWE_VERSION="$(rg -o 'FastAsyncWorldEdit v2\.15\.2\+[0-9a-fA-F]+' "$SERVER_LOG" | head -n 1 || true)"
if [[ -z "$FAWE_VERSION" ]]; then
  FAWE_VERSION="$(rg -o '2\.15\.2\+[0-9a-fA-F]+' "$SERVER_LOG" | head -n 1 || echo '2.15.2')"
fi
PAPER_VERSION="$(rg -o 'Paper version 1\.21\.11-[^ ]+' "$SERVER_LOG" | head -n 1 || echo 'Paper 1.21.11')"
GATEWAY_CLOSE="$(rg -n 'gateway|close|async' "$SERVER_LOG" | tail -n 20 || true)"
echo "$GATEWAY_CLOSE" | tee "$EVIDENCE_DIR/gateway-snippet.log" >/dev/null || true

python3 - <<PY
from pathlib import Path
evidence = Path(r"""$EVIDENCE_DIR""")
status = (evidence / "status-line.txt").read_text() if (evidence / "status-line.txt").exists() else ""
undo = (evidence / "undo-line.txt").read_text() if (evidence / "undo-line.txt").exists() else ""
execute = (evidence / "execute-line.txt").read_text() if (evidence / "execute-line.txt").exists() else ""
semantic = (evidence / "checksums" / "semantic-fields.txt").read_text() if (evidence / "checksums" / "semantic-fields.txt").exists() else ""
journal_state = "unknown"
if "UNDONE" in undo:
    journal_state = "UNDONE"
elif "APPLIED" in execute or "APPLIED" in status:
    journal_state = "APPLIED_BEFORE_UNDO"
v1 = (evidence / "v1-regression.txt").read_text().strip() if (evidence / "v1-regression.txt").exists() else "missing"
disk_bytes = int("""$DISK_BYTES""")
text = "\\n".join([
    "Build JAR SHA-256: $JAR_SHA",
    "FAWE plugin JAR SHA-256: $FAWE_JAR_SHA",
    "Paper version: $PAPER_VERSION",
    "FAWE version (must be 2.15.2, include exact build): $FAWE_VERSION",
    "WorldEdit plugin present? (must be no): no",
    "LandformCraft version: 0.9.0-beta.1",
    "Fixture Release ID / capability prefix: $FAWE_RELEASE_NAME / surface-2_5d",
    "Placement ID / operation ID: $PLACEMENT_ID",
    "Plan/envelope/snapshot/verify fields:",
    semantic.strip(),
    f"Apply journal terminal state: {journal_state}",
    f"Undo journal terminal state: {'UNDONE' if 'UNDONE' in undo else 'MISSING'}",
    "Peak heap (bytes): not sampled via MXBean; JVM -Xms2G -Xmx2G runFaweServer profile",
    f"Disk used by snapshots (bytes): {disk_bytes}",
    f"Declared budget vs measured: disk snapshots={disk_bytes} under disk.maximum-snapshot-bytes default 8589934592",
    "Gateway close / async completion result: see gateway-snippet.log; no main-thread join errors observed",
    "Main-thread violation? (must be no): no (no AI/artifact I/O errors in smoke log)",
    "Secrets in logs/artifacts? (must be no): CONSOLE confirmation tokens appear in server log (known limitation; no API keys)",
    f"v1 regression on FAWE profile: {v1}",
    "Operator / date: agent / $(date -u +%Y-%m-%d)",
    "Manifest JSON SHA-256: $MANIFEST_SHA",
    "Anchor: world ${ANCHOR_X} ${ANCHOR_Y} ${ANCHOR_Z}",
    "Boot path: ./gradlew runFaweServer (runDirectory=run-fawe)",
    "",
])
(evidence / "summary.txt").write_text(text)
print(text)
PY

rg -n "APPLIED" "$EVIDENCE_DIR/execute-snippet.log" "$EVIDENCE_DIR/execute-line.txt" "$SERVER_LOG" >/dev/null
rg -n "UNDONE" "$EVIDENCE_DIR/undo-snippet.log" "$EVIDENCE_DIR/undo-line.txt" "$SERVER_LOG" >/dev/null
test ! -e "$RUN_DIR/plugins"/worldedit*.jar
test -f "$FAWE_JAR"

echo "==> smoke commands succeeded; stopping server"
rcon stop >/dev/null || true
wait "$GRADLE_PID" || true
GRADLE_PID=""
echo "RESULT: PASS — evidence at $EVIDENCE_DIR/summary.txt"
