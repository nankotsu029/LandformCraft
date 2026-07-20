#!/usr/bin/env bash
# One-shot RCON probe: FAWE //set vs vanilla chunk fill for stone-seal.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN_DIR="$ROOT/run-fawe"
RCON_PY="$ROOT/scripts/smoke/v2-6-14-rcon.py"
PW="v21104-measure-local"
PORT=25576
OUT="/tmp/v21104-probe.out"
PID_FILE="/tmp/v21104-probe.pid"

cd "$RUN_DIR"
if [[ -f "$PID_FILE" ]]; then
  old="$(cat "$PID_FILE" || true)"
  if [[ -n "$old" ]] && kill -0 "$old" 2>/dev/null; then
    kill "$old" 2>/dev/null || true
    sleep 2
    kill -9 "$old" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
fi

: >"$OUT"
: >"$RUN_DIR/logs/latest.log"
java -Xms2G -Xmx4G -Dcom.mojang.eula.agree=true -jar paper-1.21.11-132.jar --nogui >"$OUT" 2>&1 &
echo $! >"$PID_FILE"
SERVER_PID="$(cat "$PID_FILE")"
echo "server_pid=$SERVER_PID"

for i in $(seq 1 120); do
  if rg -q 'Done \(.*\)! For help' "$RUN_DIR/logs/latest.log" 2>/dev/null; then
    echo "ready after ${i}s"
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "server died during boot" >&2
    tail -n 40 "$OUT" >&2 || true
    exit 1
  fi
  sleep 2
done

rcon() {
  python3 "$RCON_PY" --password "$PW" --port "$PORT" --timeout 60 "$@"
}

echo "== forceload query =="
rcon "forceload query" || true
echo "== forceload remove rectangle =="
rcon "forceload remove 0 0 31 31" || true
echo "== FAWE //pos1/pos2/set =="
rcon "//pos1 0,64,0" || true
rcon "//pos2 31,65,31" || true
rcon "//set stone" || true
echo "== block-coord forceload @ 80 80 (expect chunk [5,5]) =="
rcon "forceload add 80 80" || true
sleep 3
rcon "setblock 80 64 80 minecraft:stone" || true
rcon "fill 80 64 80 95 65 95 minecraft:stone" || true
echo "== strip forceload 0 0 31 499 =="
rcon "forceload add 0 0 31 499" || true
sleep 3
rcon "fill 0 64 0 31 65 499 minecraft:stone" || true
echo "== stop =="
rcon stop || true
wait "$SERVER_PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "probe done"
