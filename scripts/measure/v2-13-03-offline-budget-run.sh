#!/usr/bin/env bash
# V2-13-03: run the 1024×1024 offline generation budget probe and write committed evidence.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EVIDENCE_DIR="${LANDFORMCRAFT_V21303_EVIDENCE_DIR:-$ROOT/docs/design-v2/audits/v2-13-03-evidence}"
mkdir -p "$EVIDENCE_DIR"

export LANDFORMCRAFT_V21303_EVIDENCE_DIR="$EVIDENCE_DIR"
cd "$ROOT"

echo "V2-13-03 offline budget measurement"
echo "evidence: $EVIDENCE_DIR"

./gradlew test --tests 'com.github.nankotsu029.landformcraft.format.v2.release.Medium1024OfflineBudgetMeasurementV2Test' \
  -Dlandformcraft.v21303.evidenceDir="$EVIDENCE_DIR"

echo "wrote:"
ls -la "$EVIDENCE_DIR"
