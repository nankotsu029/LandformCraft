#!/usr/bin/env bash
# V2-12-03 live smoke runner for the official v2 command path.
#
# Profile: Paper 1.21.11 + WorldEdit 7.3.19 only (FAWE must not be present), 64x64 placement —
# the WorldEdit-runtime measured ceiling (PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM).
#
# Unlike scripts/smoke/v2-6-14-run.sh, the Release fixture is produced by the *production* export
# path (`lfc v2 export`, V2-12-02) rather than a test-only fixture exporter, and every server-side
# step is issued through the official `/lfc v2 <verb>` grammar (V2-12-03). One deprecated
# `/lfc r2 status` call is issued deliberately to capture the alias warning.
#
# Evidence is written under build/smoke/v2-12-03/evidence/ (worlds and secrets are never committed).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SMOKE_ROOT="$ROOT/build/smoke/v2-12-03"
EXPORT_DIR="$SMOKE_ROOT/fixture"
EVIDENCE_DIR="$SMOKE_ROOT/evidence"
DATA_DIR="$ROOT/run/plugins/LandformCraft/data"
RELEASES_V2="$DATA_DIR/releases-v2"
CONFIRM_DIR="$DATA_DIR/confirmations"
RELEASE_NAME="harbor-cove-64"
REQUEST="examples/v2/diagnostic/harbor-cove-64.request-v2.json"
INTENT="examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json"
# Baseline for cells no coastal feature owns; V2-12-02 requires it to be explicit.
BASELINE_KIND="water"
BASELINE_LAND_Y=54
BASELINE_WATER_Y=46
RCON_PASSWORD="v2616-smoke-local"
RCON_PORT=25575
SERVER_LOG="$ROOT/run/logs/latest.log"
GRADLE_PID=""

cleanup() {
  if [[ -n "${GRADLE_PID}" ]] && kill -0 "${GRADLE_PID}" 2>/dev/null; then
    python3 "$ROOT/scripts/smoke/v2-6-14-rcon.py" --password "$RCON_PASSWORD" --port "$RCON_PORT" \
      --timeout 20 stop >/dev/null 2>&1 || true
    wait "${GRADLE_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$SMOKE_ROOT" "$EVIDENCE_DIR"

echo "==> building shadowJar"
./gradlew shadowJar -q

echo "==> exporting the 64x64 fixture through the production v2 export path"
rm -rf "$EXPORT_DIR"
mkdir -p "$EXPORT_DIR"
./gradlew -q run --args="v2 export $REQUEST $INTENT $EXPORT_DIR $RELEASE_NAME \
$BASELINE_KIND $BASELINE_LAND_Y $BASELINE_WATER_Y" | tee "$EVIDENCE_DIR/cli-export.txt"
if ! rg -q 'placementEligible: true' "$EVIDENCE_DIR/cli-export.txt"; then
  echo "FAIL: v2 export did not produce a placement-eligible Release" >&2
  exit 1
fi
MANIFEST_SHA="$(shasum -a 256 "$EXPORT_DIR/$RELEASE_NAME/manifest.json" | awk '{print $1}')"
JAR="$ROOT/build/libs/LandformCraft-0.9.0-beta.1.jar"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"

echo "==> staging the Release into releases-v2 as $RELEASE_NAME"
rm -rf "$RELEASES_V2"
mkdir -p "$RELEASES_V2"
cp -R "$EXPORT_DIR/$RELEASE_NAME" "$RELEASES_V2/$RELEASE_NAME"

# Clear prior Release 2 placement state so stale reservations/journals cannot block the smoke.
rm -rf "$DATA_DIR/placement-v2"
mkdir -p "$DATA_DIR/placement-v2"
rm -rf "$CONFIRM_DIR"
mkdir -p "$CONFIRM_DIR"

# WorldEdit-only profile: FAWE data directories may remain, its jars must not.
rm -f "$ROOT/run/plugins"/FastAsyncWorldEdit*.jar
test -f "$ROOT/run/plugins/worldedit-bukkit-7.3.19.jar"

# Pin measured-candidate to the WorldEdit runtime ceiling (64). A leftover FAWE measurement
# config (500/1000) would fail onEnable under WE-only and leave the plugin disabled.
CONFIG_YML="$ROOT/run/plugins/LandformCraft/config.yml"
mkdir -p "$(dirname "$CONFIG_YML")"
if [[ ! -f "$CONFIG_YML" ]]; then
  unzip -p "$JAR" config.yml >"$CONFIG_YML"
fi
python3 - "$CONFIG_YML" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
keys = ("measured-candidate-max-width", "measured-candidate-max-length")
lines = path.read_text().splitlines()
out, seen = [], set()
for line in lines:
    stripped = line.lstrip(" ")
    key = stripped.split(":", 1)[0].strip() if ":" in stripped else ""
    if key in keys:
        indent = line[: len(line) - len(stripped)]
        out.append(f"{indent}{key}: 64")
        seen.add(key)
    else:
        out.append(line)
missing = [k for k in keys if k not in seen]
if missing:
    release2_idx = next(
        (i for i, line in enumerate(out) if line.lstrip(" ") == "release2:"), None
    )
    if release2_idx is None:
        placement_idx = next(
            (i for i, line in enumerate(out) if line.lstrip(" ") == "placement:"), None
        )
        block = ["  release2:"] + [f"    {k}: 64" for k in missing]
        if placement_idx is None:
            out.extend(["placement:"] + block)
        else:
            for offset, row in enumerate(block):
                out.insert(placement_idx + 1 + offset, row)
    else:
        indent = out[release2_idx][: len(out[release2_idx]) - len(out[release2_idx].lstrip(" "))] + "  "
        for offset, key in enumerate(missing):
            out.insert(release2_idx + 1 + offset, f"{indent}{key}: 64")
path.write_text("\n".join(out) + "\n")
PY

# Enable RCON for this smoke only (local password; not a production secret).
python3 - <<'PY'
from pathlib import Path
path = Path("run/server.properties")
desired = {
    "enable-rcon": "true",
    "rcon.password": "v2616-smoke-local",
    "rcon.port": "25575",
    "online-mode": "false",
}
seen, out = set(), []
for line in path.read_text().splitlines():
    if not line or line.lstrip().startswith("#") or "=" not in line:
        out.append(line)
        continue
    key, _, _ = line.partition("=")
    if key in desired:
        out.append(f"{key}={desired[key]}")
        seen.add(key)
    else:
        out.append(line)
out.extend(f"{k}={v}" for k, v in desired.items() if k not in seen)
path.write_text("\n".join(out) + "\n")
PY

: > "$SERVER_LOG"
echo "==> starting Paper runServer"
# Coastal 64×64 with GRAVITY effect (~495k blocks) OOMs the default 2G runServer heap mid-undo.
export LANDFORMCRAFT_RUNSERVER_XMX="${LANDFORMCRAFT_RUNSERVER_XMX:-4G}"
# --no-daemon: a crashed Gradle daemon otherwise tears down the Paper child mid-smoke.
./gradlew runServer --no-daemon --console=plain >"$SMOKE_ROOT/runserver.stdout" 2>&1 &
GRADLE_PID=$!

wait_for_log() {
  local pattern="$1" timeout="${2:-180}" start
  start="$(date +%s)"
  while true; do
    if [[ -f "$SERVER_LOG" ]] && rg -n -- "$pattern" "$SERVER_LOG" >/dev/null 2>&1; then return 0; fi
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
wait_for_log 'Enabling WorldEdit v7\.3\.19' 30
wait_for_log 'Enabling LandformCraft' 30
# "Enabling …" also appears on a failed enable; require the success log line and reject disable-on-error.
if rg -q 'Error occurred while enabling LandformCraft' "$SERVER_LOG"; then
  echo "FAIL: LandformCraft failed to enable (often measured-candidate-max-* above the WE ceiling of 64)" >&2
  rg -n 'Error occurred while enabling LandformCraft|measured-candidate|IllegalArgumentException' \
    "$SERVER_LOG" | tail -n 40 >&2 || true
  exit 1
fi
wait_for_log 'LandformCraft beta services initialized' 30

rcon() {
  python3 "$ROOT/scripts/smoke/v2-6-14-rcon.py" --password "$RCON_PASSWORD" --port "$RCON_PORT" \
    --timeout 60 "$@"
}

snapshot_log_size() {
  if [[ -f "$SERVER_LOG" ]]; then wc -c <"$SERVER_LOG" | tr -d ' ' >"$EVIDENCE_DIR/last-log-size.txt"
  else echo 0 >"$EVIDENCE_DIR/last-log-size.txt"; fi
}

log_since_mark() {
  local start_size=0
  [[ -f "$EVIDENCE_DIR/last-log-size.txt" ]] && start_size="$(cat "$EVIDENCE_DIR/last-log-size.txt")"
  if [[ -f "$SERVER_LOG" ]]; then
    local size; size="$(wc -c <"$SERVER_LOG" | tr -d ' ')"
    if (( size > start_size )); then tail -c +"$((start_size + 1))" "$SERVER_LOG"; fi
  fi
}

wait_for_new_log() {
  local pattern="$1" timeout="${2:-120}" start
  start="$(date +%s)"
  while true; do
    if log_since_mark | rg -q -- "$pattern"; then return 0; fi
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

# Count-based wait: avoids byte-mark races when async RCON replies land in the same second
# as snapshot_log_size (seen on the post-APPLIED status step).
count_log_matches() {
  local pattern="$1" count
  if [[ -f "$SERVER_LOG" ]]; then
    count="$(rg -c -- "$pattern" "$SERVER_LOG" 2>/dev/null || true)"
    count="$(printf '%s' "$count" | tr -d '[:space:]')"
    [[ -n "$count" ]] || count=0
    printf '%s\n' "$count"
  else
    echo 0
  fi
}

wait_for_log_count_increase() {
  local pattern="$1" before="$2" timeout="${3:-120}" start after
  before="$(printf '%s' "$before" | tr -d '[:space:]')"
  [[ -n "$before" ]] || before=0
  start="$(date +%s)"
  while true; do
    after="$(count_log_matches "$pattern")"
    if (( after > before )); then return 0; fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
      sleep 2
      after="$(count_log_matches "$pattern")"
      if (( after > before )); then return 0; fi
      echo "FAIL: runServer exited before count increase for: $pattern (before=$before after=$after)" >&2
      tail -n 40 "$SERVER_LOG" >&2 || true
      return 1
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: timeout waiting for new log match: $pattern (before=$before after=$after)" >&2
      tail -n 40 "$SERVER_LOG" >&2 || true
      return 1
    fi
    sleep 1
  done
}

extract_new() { log_since_mark | rg -n -- "$1" | tail -n 1; }

# V2-11-03: CONSOLE/RCON confirmation tokens are written to owner-only files, not the server log.
wait_for_confirmation_file() {
  local key="$1" timeout="${2:-120}" start file
  file="$CONFIRM_DIR/${key}.command"
  start="$(date +%s)"
  while true; do
    if [[ -f "$file" ]]; then
      local cmd
      cmd="$(tr -d '\r' <"$file" | head -n 1)"
      cmd="${cmd#/}"
      printf '%s\n' "$cmd"
      return 0
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
      sleep 2
      if [[ -f "$file" ]]; then
        local cmd
        cmd="$(tr -d '\r' <"$file" | head -n 1)"
        cmd="${cmd#/}"
        printf '%s\n' "$cmd"
        return 0
      fi
      echo "FAIL: runServer exited before confirmation file: $file" >&2
      ls -la "$CONFIRM_DIR" >&2 || true
      return 1
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: confirmation file missing: $file" >&2
      ls -la "$CONFIRM_DIR" >&2 || true
      log_since_mark | tail -n 80 >&2 || true
      return 1
    fi
    sleep 1
  done
}

echo "==> version / doctor / v2 help"
snapshot_log_size
# RCON command replies return over the RCON channel and are not echoed into latest.log, so the
# response text itself is the evidence; the log only adds the async LandformCraft lines.
{
  echo "--- lfc version"; rcon "lfc version" || true
  echo "--- lfc doctor";  rcon "lfc doctor" || true
  echo "--- lfc help";    rcon "lfc help" || true
} | tee "$EVIDENCE_DIR/doctor-rcon.txt" >/dev/null
sleep 3
log_since_mark | rg -n "WorldEdit|Release 2|7\.3\.19|LandformCraft|v2 " \
  | tee "$EVIDENCE_DIR/doctor-snippet.log" || true
if ! rg -q 'v2 (export|place|status)' "$EVIDENCE_DIR/doctor-rcon.txt"; then
  echo "FAIL: /lfc help does not advertise the official v2 verbs" >&2
  exit 1
fi

# The Release is 64x64 with local Y span 32..72 (40 inclusive). Place at Y=64 so the GRAVITY
# effect radius (64) and FLUID support (2) stay inside world minHeight -64 — the same anchor the
# offline E2E uses. Anchor Y=-60 (V2-6-14 solid-stone smoke) overflows for coastal sand.
#
# Paper's /fill cap is 32768 blocks. Effect footprint with fluid radius 2 is 68×68 (=4624/layer),
# so Y slices must be at most 7 layers (32368 < 32768).
#
# Paper 1.21 forceload takes *block* column coordinates (not chunk indices). Passing chunk
# indices is interpreted as a tiny block range and leaves most of the envelope unloaded.
ANCHOR_X=0
ANCHOR_Y=64
ANCHOR_Z=0
MUTATION_SPAN_Y=40
FLUID_RADIUS=2
GRAVITY_FALL=64
FILL_MIN_X=$((ANCHOR_X - FLUID_RADIUS))
FILL_MAX_X=$((ANCHOR_X + 64 - 1 + FLUID_RADIUS))
FILL_MIN_Z=$((ANCHOR_Z - FLUID_RADIUS))
FILL_MAX_Z=$((ANCHOR_Z + 64 - 1 + FLUID_RADIUS))
FILL_MIN_Y=$((ANCHOR_Y - GRAVITY_FALL))
FILL_MAX_Y=$((ANCHOR_Y + MUTATION_SPAN_Y + FLUID_RADIUS))
FILL_SLICE_Y=7
echo "==> forceload blocks (${FILL_MIN_X},${FILL_MIN_Z})..(${FILL_MAX_X},${FILL_MAX_Z}) + sliced stone fill"
snapshot_log_size
rcon "forceload add ${FILL_MIN_X} ${FILL_MIN_Z} ${FILL_MAX_X} ${FILL_MAX_Z}" \
  | tee "$EVIDENCE_DIR/forceload.txt" >/dev/null || true
# Force-load marks are async; wait until the first fill slice can see loaded chunks.
sleep 5
: > "$EVIDENCE_DIR/fill.txt"
for (( y = FILL_MIN_Y; y <= FILL_MAX_Y; y += FILL_SLICE_Y )); do
  top=$(( y + FILL_SLICE_Y - 1 ))
  (( top > FILL_MAX_Y )) && top=$FILL_MAX_Y
  OUT="$(rcon "fill ${FILL_MIN_X} ${y} ${FILL_MIN_Z} ${FILL_MAX_X} ${top} ${FILL_MAX_Z} minecraft:stone" || true)"
  echo "y=${y}..${top}: $OUT" >>"$EVIDENCE_DIR/fill.txt"
  # "No blocks were filled" also contains "blocks filled"; reject it explicitly. An empty fill
  # means the stone baseline was never established, so the snapshot would not be the intended one.
  if echo "$OUT" | rg -qi 'No blocks were filled|ブロックは配置されませんでした'; then
    echo "FAIL: fill slice y=${y}..${top} filled nothing; the stone baseline was not established." >&2
    echo "       Clear the area (or a previous smoke already left it as stone) and re-run: $OUT" >&2
    exit 1
  fi
  if ! echo "$OUT" | rg -qi 'Filled [0-9]+|[0-9]+ ブロック'; then
    echo "FAIL: envelope fill slice y=${y}..${top} did not succeed: $OUT" >&2
    echo "---- forceload ----" >&2
    cat "$EVIDENCE_DIR/forceload.txt" >&2 || true
    exit 1
  fi
  sleep 1
done

echo "==> v2 place plan"
snapshot_log_size
rcon "lfc v2 place plan ${RELEASE_NAME} world ${ANCHOR_X} ${ANCHOR_Y} ${ANCHOR_Z}" >/dev/null
wait_for_new_log 'CONFIRMATION_ISSUED|Release 2 Placement ID' 180
PLAN_LINE="$(extract_new 'Release 2 Placement ID' || true)"
echo "$PLAN_LINE" | tee "$EVIDENCE_DIR/plan-line.txt"
PLACEMENT_ID="$(echo "$PLAN_LINE" | sed -n 's/.*Release 2 Placement ID: \([0-9a-fA-F-]\{36\}\).*/\1/p' | tail -n 1)"
if [[ -z "$PLACEMENT_ID" ]]; then
  echo "FAIL: could not parse placement id from server log" >&2
  log_since_mark | tail -n 120 >&2
  exit 1
fi
CONFIRM_CMD="$(wait_for_confirmation_file "r2-confirm-${PLACEMENT_ID}" 60)"
echo "$CONFIRM_CMD" | tee "$EVIDENCE_DIR/confirm-command.txt"
echo "placement=$PLACEMENT_ID" | tee "$EVIDENCE_DIR/placement-id.txt"

echo "==> v2 place confirm"
snapshot_log_size
rcon "$CONFIRM_CMD" >/dev/null
wait_for_new_log 'SNAPSHOT_COMPLETE|v2 place execute |state: SNAPSHOT' 600
log_since_mark | rg -n "SNAPSHOT_COMPLETE|containment|v2 place execute|state:" \
  | tee "$EVIDENCE_DIR/confirm-snippet.log" || true

echo "==> v2 place execute"
snapshot_log_size
# Prefer outcome: — state: APPLIED can race with a later status mirror in the same second.
BEFORE_OUTCOME="$(count_log_matches 'outcome: APPLIED')"
rcon "lfc v2 place execute ${PLACEMENT_ID}" >/dev/null
wait_for_log_count_increase 'outcome: APPLIED' "$BEFORE_OUTCOME" 900
log_since_mark | rg -n "APPLIED|ROLLED_BACK|RECOVERY_REQUIRED|exact verify|settle|outcome" \
  | tee "$EVIDENCE_DIR/execute-snippet.log" || true
# Let the server reclaim snapshot memory before the next RCON round-trip.
sleep 5

echo "==> v2 status (canonical) and r2 status (deprecated alias)"
BEFORE_STATUS="$(count_log_matches "Release 2 Placement ID: ${PLACEMENT_ID}")"
rcon "lfc v2 status ${PLACEMENT_ID}" >/dev/null
wait_for_log_count_increase "Release 2 Placement ID: ${PLACEMENT_ID}" "$BEFORE_STATUS" 120
rg -n "Release 2 Placement ID: ${PLACEMENT_ID}|state: APPLIED" "$SERVER_LOG" | tail -n 6 \
  | tee "$EVIDENCE_DIR/status-line.txt" || true
BEFORE_ALIAS_STATUS="$(count_log_matches "Release 2 Placement ID: ${PLACEMENT_ID}")"
# Deprecation warning is sync on the RCON reply (not mirrored into latest.log).
ALIAS_OUT="$(rcon "lfc r2 status ${PLACEMENT_ID}" || true)"
printf '%s\n' "$ALIAS_OUT" | tee "$EVIDENCE_DIR/alias-rcon.txt" >/dev/null
if ! printf '%s\n' "$ALIAS_OUT" | rg -q 'deprecated alias'; then
  echo "FAIL: /lfc r2 status did not emit the deprecated-alias warning on RCON" >&2
  printf '%s\n' "$ALIAS_OUT" >&2
  exit 1
fi
wait_for_log_count_increase "Release 2 Placement ID: ${PLACEMENT_ID}" "$BEFORE_ALIAS_STATUS" 120
{
  printf '%s\n' "$ALIAS_OUT"
  rg -n "deprecated alias|Release 2 Placement ID: ${PLACEMENT_ID}|state: APPLIED" "$SERVER_LOG" | tail -n 8 || true
} | tee "$EVIDENCE_DIR/alias-snippet.log" >/dev/null

echo "==> v2 undo"
BEFORE_UNDO="$(count_log_matches 'state: UNDONE')"
rcon "lfc v2 undo plan ${PLACEMENT_ID}" >/dev/null
UNDO_CMD="$(wait_for_confirmation_file "r2-undo-${PLACEMENT_ID}" 180)"
echo "$UNDO_CMD" | tee "$EVIDENCE_DIR/undo-command.txt"
rcon "$UNDO_CMD" >/dev/null
wait_for_log_count_increase 'state: UNDONE' "$BEFORE_UNDO" 900
rg -n "UNDONE|undo" "$SERVER_LOG" | tail -n 20 | tee "$EVIDENCE_DIR/undo-snippet.log" || true

echo "==> v1 command regression (must be unchanged by V2-12-03)"
snapshot_log_size
{
  echo "--- lfc help"; rcon "lfc help" || true
  echo "--- lfc doctor"; rcon "lfc doctor" || true
  echo "--- lfc apply status <unknown-id>"
  rcon "lfc apply status 00000000-0000-0000-0000-000000000000" || true
} | tee "$EVIDENCE_DIR/v1-regression-rcon.txt" >/dev/null
sleep 3
log_since_mark | rg -n "apply|help|doctor" | tee "$EVIDENCE_DIR/v1-regression.log" || true
# v1 verbs must still be routed by the v1 handler (not swallowed by the v2 router).
if rg -q 'V2_UNKNOWN_VERB|V2_PAPER_ONLY' "$EVIDENCE_DIR/v1-regression-rcon.txt"; then
  echo "FAIL: a v1 command was routed through the v2 router" >&2
  cat "$EVIDENCE_DIR/v1-regression-rcon.txt" >&2
  exit 1
fi

echo "==> collecting checksums"
mkdir -p "$EVIDENCE_DIR/checksums"
for dir in journals operations snapshots; do
  target="$DATA_DIR/placement-v2/$dir"
  [[ -d "$target" ]] || continue
  find "$target" -type f -print0 | while IFS= read -r -d '' file; do
    shasum -a 256 "$file" >>"$EVIDENCE_DIR/checksums/$dir.sha256"
  done
done
{
  echo "jar_sha256=$JAR_SHA"
  echo "release_manifest_sha256=$MANIFEST_SHA"
  echo "release_name=$RELEASE_NAME"
  echo "placement_id=$PLACEMENT_ID"
  echo "anchor=${ANCHOR_X},${ANCHOR_Y},${ANCHOR_Z}"
  echo "dimensions=64x64"
} | tee "$EVIDENCE_DIR/summary.txt"

echo "==> smoke sequence issued; review $EVIDENCE_DIR before recording the audit"
