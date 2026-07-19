#!/usr/bin/env bash
# V2-6-15 prerequisite probe — does NOT run placement smoke and must not be treated as success.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "V2-6-15 FAWE 2.15.2 standalone smoke prerequisite probe"
echo "repo=$ROOT"
echo

fail=0
FAWE_DIR="${LFC_FAWE_SERVER_DIR:-$ROOT/run-fawe}"

if ! command -v java >/dev/null 2>&1; then
  echo "FAIL: java not found"
  fail=1
else
  echo "OK: java=$(java -version 2>&1 | head -n 1)"
fi

if [[ ! -d "$FAWE_DIR" ]]; then
  echo "FAIL: FAWE server directory missing: $FAWE_DIR"
  fail=1
else
  echo "OK: FAWE server directory=$FAWE_DIR"
fi

fawe_jar="$(ls -1 "$FAWE_DIR/plugins"/FastAsyncWorldEdit*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${fawe_jar}" ]]; then
  echo "FAIL: FastAsyncWorldEdit jar not found in $FAWE_DIR/plugins"
  fail=1
elif ! echo "$fawe_jar" | grep -q '2.15.2'; then
  echo "FAIL: FAWE jar is not 2.15.2: $fawe_jar"
  fail=1
else
  echo "OK: FAWE jar=$fawe_jar"
fi

we_jar="$(ls -1 "$FAWE_DIR/plugins"/worldedit*.jar "$FAWE_DIR/plugins"/WorldEdit*.jar 2>/dev/null | head -n 1 || true)"
if [[ -n "${we_jar}" ]]; then
  echo "FAIL: WorldEdit plugin present (must be FAWE-only): $we_jar"
  fail=1
else
  echo "OK: no WorldEdit plugin jar alongside FAWE"
fi

lfc_jar="$(ls -1 "$FAWE_DIR/plugins"/LandformCraft*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${lfc_jar}" ]]; then
  echo "FAIL: LandformCraft jar missing in $FAWE_DIR/plugins"
  fail=1
else
  echo "OK: LandformCraft jar=$lfc_jar"
fi

paper_jar="$(ls -1 "$FAWE_DIR/versions"/*/paper*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${paper_jar}" ]]; then
  echo "FAIL: paper jar missing under $FAWE_DIR/versions"
  fail=1
else
  echo "OK: paper jar=$paper_jar"
fi

# Port bind probe (does not start Minecraft): try binding the configured server-port.
port="$(grep -E '^server-port=' "$FAWE_DIR/server.properties" 2>/dev/null | cut -d= -f2 || echo 25566)"
if python3 - "$port" <<'PY'
import socket, sys
port = int(sys.argv[1])
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
try:
    s.bind(("127.0.0.1", port))
except OSError as e:
    print(f"FAIL: cannot bind 127.0.0.1:{port}: {e}")
    sys.exit(2)
else:
    print(f"OK: bind probe succeeded on 127.0.0.1:{port}")
    sys.exit(0)
finally:
    s.close()
PY
then
  :
else
  echo "FAIL: TCP bind probe failed — full Paper smoke cannot run in this environment"
  fail=1
fi

# Dedicated FAWE boot path (run-paper runDirectory), not versions/*.jar direct launch.
if ./gradlew help --task runFaweServer >/dev/null 2>&1; then
  echo "OK: Gradle task runFaweServer is registered"
else
  echo "FAIL: Gradle task runFaweServer missing (FAWE runDirectory boot path)"
  fail=1
fi

echo
if [[ "$fail" -ne 0 ]]; then
  echo "RESULT: BLOCKED_EXTERNAL — do not mark V2-6-15 complete"
  echo "See docs/design-v2/audits/v2-6-15-fawe-smoke.md and ADR 0032"
  exit 2
fi

echo "RESULT: prerequisites present — continue with scripts/smoke/v2-6-15-run.sh"
exit 0
