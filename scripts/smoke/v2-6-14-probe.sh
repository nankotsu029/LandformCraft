#!/usr/bin/env bash
# V2-6-14 prerequisite probe — does NOT complete smoke by itself.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
echo "V2-6-14 WorldEdit 7.3.19 smoke prerequisite probe"
fail=0

if ! command -v java >/dev/null 2>&1; then
  echo "FAIL: java missing"; fail=1
else
  echo "OK: java present"
fi

if [[ ! -f ./gradlew ]]; then
  echo "FAIL: gradlew missing"; fail=1
else
  echo "OK: gradlew present"
fi

if [[ ! -f src/main/java/com/github/nankotsu029/landformcraft/core/v2/placement/apply/VerifiedReleaseCanonicalBlockSourceV2.java ]]; then
  echo "FAIL: verified Release 2 canonical block source is missing (V2-6-20)"
  fail=1
else
  echo "OK: verified Release 2 canonical block source is present (V2-6-20)"
fi

if ! rg -n 'Release2PlacementApplicationServiceV2|PaperRelease2PlacementServiceV2' \
    src/main/java/com/github/nankotsu029/landformcraft/Landformcraft.java >/dev/null 2>&1; then
  echo "FAIL: Release 2 Paper application service is not assembled in Landformcraft.java (V2-6-21)"
  fail=1
else
  echo "OK: Release 2 Paper application service assembly is present (V2-6-21)"
fi

if ! rg -n 'args\[0\]\.equalsIgnoreCase\("r2"\)|handleRelease2' \
    src/main/java/com/github/nankotsu029/landformcraft/paper/LandformCraftCommand.java >/dev/null 2>&1; then
  echo "FAIL: explicit /lfc r2 command routing is missing"
  fail=1
else
  echo "OK: explicit /lfc r2 command routing is present"
fi

if rg -n 'placements, null, null, release2Operations' \
    src/main/java/com/github/nankotsu029/landformcraft/Landformcraft.java >/dev/null 2>&1; then
  echo "FAIL: Landformcraft still constructs commands without Release 2 placements"
  fail=1
fi

we_jar="$(ls -1 run/plugins/worldedit*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${we_jar}" ]]; then
  echo "WARN: no worldedit jar under run/plugins (./gradlew runServer may still download/use cached WE)"
else
  echo "OK: $we_jar"
  if ! echo "$we_jar" | grep -q '7.3.19'; then
    echo "FAIL: WorldEdit jar does not look like 7.3.19"; fail=1
  fi
fi

fawe_jar="$(ls -1 run/plugins/FastAsyncWorldEdit*.jar 2>/dev/null | head -n 1 || true)"
if [[ -n "${fawe_jar}" ]]; then
  echo "FAIL: FAWE jar present in WE profile run/plugins: $fawe_jar"; fail=1
else
  echo "OK: no FAWE jar in run/plugins"
fi

echo
if [[ "$fail" -ne 0 ]]; then
  echo "RESULT: STOPPED — fix prerequisites before live smoke"
  echo "See docs/design-v2/audits/v2-6-14-v2-6-15-reaudit.md"
  exit 2
fi
echo "RESULT: static prerequisites look OK — still require live R2 E2E evidence"
exit 0
