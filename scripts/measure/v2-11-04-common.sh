#!/usr/bin/env bash
# Shared helpers for V2-11-04 standalone Paper measurement (no Gradle daemon relay).
# shellcheck disable=SC2034

measure_common_init() {
  ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  RCON_PY="$ROOT/scripts/smoke/v2-6-14-rcon.py"
  TELEMETRY_PY="$ROOT/scripts/measure/v2-11-04-telemetry.py"
  JAR="$ROOT/build/libs/LandformCraft-0.9.0-beta.1.jar"
}

enable_rcon() {
  local server_properties="$1"
  local password="$2"
  local port="$3"
  local game_port="$4"
  python3 - <<PY
from pathlib import Path
path = Path(r"""$server_properties""")
path.parent.mkdir(parents=True, exist_ok=True)
if not path.exists():
    path.write_text("online-mode=false\n")
lines = path.read_text().splitlines()
desired = {
    "enable-rcon": "true",
    "rcon.password": """$password""",
    "rcon.port": """$port""",
    "online-mode": "false",
    "server-port": """$game_port""",
    "max-tick-time": "-1",
    "spawn-protection": "0",
    "view-distance": "8",
    "simulation-distance": "6",
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
}

enable_measurement_watchdog() {
  local server_properties="$1"
  python3 - "$server_properties" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
lines = path.read_text().splitlines()
out = []
found = False
for line in lines:
    if line.startswith("max-tick-time="):
        out.append("max-tick-time=60000")
        found = True
    else:
        out.append(line)
if not found:
    out.append("max-tick-time=60000")
path.write_text("\n".join(out) + "\n")
PY
}

write_eula() {
  printf 'eula=true\n' >"$1/eula.txt"
}

install_plugin_and_measurement_config() {
  local run_dir="$1"
  local world_name="$2"
  local max_width="$3"
  local max_length="$4"
  local apply_slice_blocks="${5:-0}"
  mkdir -p "$run_dir/plugins"
  rm -f "$run_dir/plugins"/LandformCraft*.jar
  cp "$JAR" "$run_dir/plugins/LandformCraft-0.9.0-beta.1.jar"
  local cfg_dir="$run_dir/plugins/LandformCraft"
  mkdir -p "$cfg_dir"
  if [[ ! -f "$cfg_dir/config.yml" ]]; then
    unzip -p "$JAR" config.yml >"$cfg_dir/config.yml"
  fi
  python3 - "$cfg_dir/config.yml" "$world_name" "$max_width" "$max_length" "$apply_slice_blocks" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
world, width, length, apply_slice_blocks = sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
lines = path.read_text().splitlines()
out = []
i = 0
replaced = False
while i < len(lines):
    line = lines[i]
    stripped = line.lstrip(" ")
    indent = line[: len(line) - len(stripped)]
    if stripped == "measurement-profile:" and not replaced:
        child = indent + "  "
        out.append(line)
        out.append(f"{child}enabled: true")
        out.append(f'{child}isolated-world: "{world}"')
        out.append(f"{child}max-width: {width}")
        out.append(f"{child}max-length: {length}")
        out.append(f"{child}apply-slice-blocks: {apply_slice_blocks}")
        i += 1
        while i < len(lines):
            nxt = lines[i]
            if not nxt.strip():
                break
            nxt_stripped = nxt.lstrip(" ")
            nxt_indent = nxt[: len(nxt) - len(nxt_stripped)]
            if len(nxt_indent) <= len(indent):
                break
            i += 1
        replaced = True
        continue
    out.append(line)
    i += 1
if not replaced:
    raise SystemExit("config.yml missing measurement-profile block")
path.write_text("\n".join(out) + "\n")
PY
}

wait_for_log() {
  local log_file="$1"
  local pattern="$2"
  local timeout="${3:-300}"
  local pid="$4"
  local start
  start="$(date +%s)"
  while true; do
    if [[ -f "$log_file" ]] && grep -nE -- "$pattern" "$log_file" >/dev/null 2>&1; then
      return 0
    fi
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      echo "FAIL: server PID $pid exited before matching: $pattern" >&2
      return 1
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: timeout waiting for: $pattern" >&2
      tail -n 80 "$log_file" >&2 || true
      return 1
    fi
    sleep 1
  done
}

read_confirmation_command() {
  local confirmations_dir="$1"
  local key="$2"
  local file="$confirmations_dir/${key}.command"
  local timeout="${3:-120}"
  local start
  start="$(date +%s)"
  while true; do
    if [[ -f "$file" ]]; then
      # Stored as "/lfc ...."; RCON wants the body without the leading slash.
      local cmd
      cmd="$(tr -d '\r' <"$file" | head -n 1)"
      cmd="${cmd#/}"
      printf '%s\n' "$cmd"
      return 0
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "FAIL: confirmation file missing: $file" >&2
      ls -la "$confirmations_dir" >&2 || true
      return 1
    fi
    sleep 1
  done
}

start_standalone_paper() {
  local run_dir="$1"
  local xmx="$2"
  local stdout_log="$3"
  # Xms defaults to Xmx (V2-11-04 behaviour). Callers that need RSS to track the
  # live set instead of the committed heap pass a smaller Xms explicitly.
  local xms="${4:-$xmx}"
  local gc_log="${5:-}"
  local jvm_args=(
    "-Xms${xms}"
    "-Xmx${xmx}"
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
  )
  if [[ -n "$gc_log" ]]; then
    jvm_args+=("-Xlog:gc*:file=${gc_log}:time,uptime,level,tags")
  fi
  mkdir -p "$run_dir/logs"
  : >"$run_dir/logs/latest.log"
  (
    cd "$run_dir"
    exec java \
      "${jvm_args[@]}" \
      -Dcom.mojang.eula.agree=true \
      -jar paper-1.21.11-132.jar \
      --nogui
  ) >"$stdout_log" 2>&1 &
  echo $!
}

find_server_pid() {
  local run_dir="$1"
  local launcher_pid="$2"
  # Prefer the child JVM that owns the paperclip classpath; fall back to launcher.
  local child
  child="$(pgrep -P "$launcher_pid" -a 2>/dev/null | grep -m1 -E 'paper-1\.21\.11-132\.jar|Paperclip|net.minecraft' | awk '{print $1}' || true)"
  if [[ -n "$child" ]]; then
    echo "$child"
    return 0
  fi
  # paperclip may exec itself in-place
  if kill -0 "$launcher_pid" 2>/dev/null; then
    echo "$launcher_pid"
    return 0
  fi
  return 1
}
