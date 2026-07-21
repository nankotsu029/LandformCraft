#!/usr/bin/env bash
# V2-13-06 closed apply-slice calibration on the same isolated 1024x1024 FAWE host/profile as
# V2-13-04. Each candidate runs the full two-pass lifecycle plus Recovery drill. Paper MSPT/TPS is
# sampled read-only; the one-in-flight-slice service invariant supplies the scheduler queue bound.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CALIBRATION_ROOT="$ROOT/build/measure/v2-13-06/calibration"
HEALTH_SCRIPT="$SCRIPT_DIR/v2-13-06-server-health.py"
SUMMARY_SCRIPT="$SCRIPT_DIR/v2-13-06-calibration-summary.py"
RCON_SCRIPT="$ROOT/scripts/smoke/v2-6-14-rcon.py"
RCON_PASSWORD="v21304-measure-local"
RCON_PORT=25576
CANDIDATES=(32 128 256 512 1024)

mkdir -p "$ROOT/build/measure/v2-13-06"
rm -rf "$CALIBRATION_ROOT"
mkdir -p "$CALIBRATION_ROOT"

for slice in "${CANDIDATES[@]}"; do
  candidate_dir="$CALIBRATION_ROOT/slice-$slice"
  stop_file="$CALIBRATION_ROOT/.health-stop-$slice"
  rm -f "$stop_file"
  mkdir -p "$candidate_dir"
  echo "==> V2-13-06 calibration slice=$slice"
  python3 "$HEALTH_SCRIPT" \
    --rcon "$RCON_SCRIPT" \
    --password "$RCON_PASSWORD" \
    --port "$RCON_PORT" \
    --out "$candidate_dir/paper-health.json" \
    --stop-file "$stop_file" \
    --interval-seconds 10 &
  health_pid=$!
  candidate_ok=false
  if LANDFORMCRAFT_V21306_APPLY_SLICE_BLOCKS="$slice" \
      LANDFORMCRAFT_V21304_XMX="${LANDFORMCRAFT_V21306_XMX:-8G}" \
      LANDFORMCRAFT_V21304_XMS="${LANDFORMCRAFT_V21306_XMS:-2G}" \
      bash "$SCRIPT_DIR/v2-13-04-fawe-run.sh"; then
    candidate_ok=true
  fi
  touch "$stop_file"
  wait "$health_pid" || true
  if [[ "$candidate_ok" != true ]]; then
    echo "FAIL: calibration candidate $slice did not complete" >&2
    exit 1
  fi
  cp -R "$ROOT/build/measure/v2-13-04/evidence/fawe/." "$candidate_dir/"
  cp "$ROOT/run-fawe/logs/latest.log" "$candidate_dir/final-server.log"
  if ! grep -n "apply slice calibration is ENABLED ($slice mutations/slice)" \
      "$candidate_dir/final-server.log" >/dev/null; then
    echo "FAIL: server did not acknowledge apply slice $slice" >&2
    exit 1
  fi
done

python3 "$SUMMARY_SCRIPT" \
  --root "$CALIBRATION_ROOT" \
  --out "$ROOT/build/measure/v2-13-06/calibration-summary.json"
echo "RESULT: calibration evidence at build/measure/v2-13-06/calibration-summary.json"
