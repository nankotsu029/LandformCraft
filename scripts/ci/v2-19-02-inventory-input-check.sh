#!/usr/bin/env bash
# V2-19-02: prove that the filesystem inventory tests cannot be hidden by an up-to-date or cached
# Gradle test result, and that the verdict is the same with the build cache enabled and disabled.
#
# The 2026-07-23 cross-cutting audit measured the opposite: the same working tree passed when Gradle
# reused the test result and failed when the task actually ran, because the roots the tests walk were
# not declared as task inputs. This script re-runs that measurement as a pass/fail check.
#
# Usage: scripts/ci/v2-19-02-inventory-input-check.sh
# Exit 0 = the inventory roots are tracked correctly.

set -uo pipefail

cd "$(dirname "$0")/../.."

EXAMPLE_PROBE="examples/v2/diagnostic/scenarios/v2-19-02-input-tracking-probe.terrain-intent-v2.json"
DOCS_PROBE="docs/design-v2/v2-19-02-input-tracking-probe.md"
FILTER=(--tests '*SchemaContractTest' --tests '*DocsLinkConsistencyTest')
failures=0

cleanup() {
    rm -f "$EXAMPLE_PROBE" "$DOCS_PROBE"
}
trap cleanup EXIT

for probe in "$EXAMPLE_PROBE" "$DOCS_PROBE"; do
    if [ -e "$probe" ]; then
        echo "refusing to run: probe path already exists: $probe" >&2
        exit 2
    fi
done

run_inventory_tests() {
    # $1: --build-cache | --no-build-cache
    ./gradlew test "${FILTER[@]}" "$1" >/dev/null 2>&1
}

expect() {
    # $1: label, $2: expected verdict (pass|fail), $3: cache flag
    local label="$1" expected="$2" cache="$3" actual
    if run_inventory_tests "$cache"; then actual=pass; else actual=fail; fi
    if [ "$actual" = "$expected" ]; then
        echo "OK   ${label} ${cache}: ${actual}"
    else
        echo "FAIL ${label} ${cache}: expected ${expected}, got ${actual}"
        failures=$((failures + 1))
    fi
}

echo "== baseline: clean working tree =="
expect "clean" pass --build-cache
expect "clean" pass --no-build-cache

echo "== untracked orphan example must be seen even by a warm cache =="
printf '{ "note": "V2-19-02 input tracking probe" }\n' > "$EXAMPLE_PROBE"
expect "orphan-example" fail --build-cache
expect "orphan-example" fail --no-build-cache
rm -f "$EXAMPLE_PROBE"

echo "== untracked broken docs link must be seen even by a warm cache =="
printf '# V2-19-02 probe\n\n[broken](./v2-19-02-does-not-exist.md)\n' > "$DOCS_PROBE"
expect "broken-docs-link" fail --build-cache
expect "broken-docs-link" fail --no-build-cache
rm -f "$DOCS_PROBE"

echo "== restored working tree =="
expect "restored" pass --build-cache
expect "restored" pass --no-build-cache

if [ "$failures" -ne 0 ]; then
    echo "V2-19-02 inventory input check FAILED (${failures} mismatched verdicts)" >&2
    exit 1
fi
echo "V2-19-02 inventory input check PASSED"
