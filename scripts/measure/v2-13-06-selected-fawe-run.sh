#!/usr/bin/env bash
# V2-13-06 final two-pass re-measurement for the selected production slice. This reuses the
# accepted 1000x1000 and 1024x1024 lifecycle runners and adds identical Paper MSPT sampling.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SELECTED_ROOT="$ROOT/build/measure/v2-13-06/selected"
HEALTH_SCRIPT="$SCRIPT_DIR/v2-13-06-server-health.py"
RCON_SCRIPT="$ROOT/scripts/smoke/v2-6-14-rcon.py"
SLICE_BLOCKS=1024
RCON_PORT=25576
HEALTH_PID=""
STOP_FILE=""

stop_health_sampler() {
  if [[ -n "$STOP_FILE" ]]; then
    touch "$STOP_FILE"
  fi
  if [[ -n "$HEALTH_PID" ]]; then
    wait "$HEALTH_PID" || true
  fi
  HEALTH_PID=""
  STOP_FILE=""
}
trap stop_health_sampler EXIT

run_dimension() {
  local dimension="$1"
  local password="$2"
  local runner="$3"
  local source_evidence="$4"
  local target="$SELECTED_ROOT/$dimension"
  STOP_FILE="$SELECTED_ROOT/.health-stop-$dimension"
  rm -f "$STOP_FILE"
  mkdir -p "$target"

  python3 "$HEALTH_SCRIPT" \
    --rcon "$RCON_SCRIPT" \
    --password "$password" \
    --port "$RCON_PORT" \
    --out "$target/paper-health.json" \
    --stop-file "$STOP_FILE" \
    --interval-seconds 10 &
  HEALTH_PID=$!

  local run_ok=false
  if LANDFORMCRAFT_V21306_APPLY_SLICE_BLOCKS="$SLICE_BLOCKS" \
      LANDFORMCRAFT_V21301_XMX="${LANDFORMCRAFT_V21306_XMX:-8G}" \
      LANDFORMCRAFT_V21301_XMS="${LANDFORMCRAFT_V21306_XMS:-2G}" \
      LANDFORMCRAFT_V21304_XMX="${LANDFORMCRAFT_V21306_XMX:-8G}" \
      LANDFORMCRAFT_V21304_XMS="${LANDFORMCRAFT_V21306_XMS:-2G}" \
      bash "$runner"; then
    run_ok=true
  fi
  stop_health_sampler
  if [[ "$run_ok" != true ]]; then
    echo "FAIL: selected-slice $dimension measurement did not complete" >&2
    exit 1
  fi

  cp -R "$source_evidence/." "$target/"
  cp "$ROOT/run-fawe/logs/latest.log" "$target/final-server.log"
  if ! grep -n "apply slice calibration is ENABLED ($SLICE_BLOCKS mutations/slice)" \
      "$target/final-server.log" >/dev/null; then
    echo "FAIL: $dimension server did not acknowledge selected slice $SLICE_BLOCKS" >&2
    exit 1
  fi
}

rm -rf "$SELECTED_ROOT"
mkdir -p "$SELECTED_ROOT"

echo "==> V2-13-06 selected slice: 1000x1000 two-pass re-measurement"
run_dimension \
  "1000x1000" \
  "v21301-measure-local" \
  "$SCRIPT_DIR/v2-13-01-stage-instrumentation-run.sh" \
  "$ROOT/build/measure/v2-13-01/evidence/fawe"

echo "==> V2-13-06 selected slice: 1024x1024 two-pass re-measurement + Recovery drill"
run_dimension \
  "1024x1024" \
  "v21304-measure-local" \
  "$SCRIPT_DIR/v2-13-04-fawe-run.sh" \
  "$ROOT/build/measure/v2-13-04/evidence/fawe"

JAR_1000="$(sed -n 's/^build_jar_sha256=//p' "$SELECTED_ROOT/1000x1000/summary.txt")"
JAR_1024="$(sed -n 's/^build_jar_sha256=//p' "$SELECTED_ROOT/1024x1024/summary.txt")"
if [[ -z "$JAR_1000" || "$JAR_1000" != "$JAR_1024" ]]; then
  echo "FAIL: selected measurements did not use the same plugin JAR" >&2
  exit 1
fi

{
  echo "selected_slice_blocks=$SLICE_BLOCKS"
  echo "build_jar_sha256=$JAR_1000"
  echo "dimensions=1000x1000,1024x1024"
  echo "passes_per_dimension=2"
  echo "profile=Paper 1.21.11 + FAWE 2.15.2 standalone"
} | tee "$SELECTED_ROOT/summary.txt"
echo "RESULT: selected-slice evidence at build/measure/v2-13-06/selected"
