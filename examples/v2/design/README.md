# Release 2 design package examples

Examples for the V2-6-11 design artifact contract:

- `design-audit-v2.json` — secret-free audit metadata for one published design job (`audit-v2.json` in a design package). `supportLint` is absent here, which is what a v1→v2 migration bundle writes.
- `design-audit-with-support-lint-v2.json` — the same audit with the V2-19-08 design-time support lint. `surface` is the reachable kind and capability set presented before the provider call, projected from `production-dispatch-registry-v2` (not from the Feature Support Catalog), and `findings` are the dispatch dry-run of the returned intent. Every finding is `NON_GATING`: this example's design declares `PLAIN` alone, so it is published and reported as `NOT_SELECTABLE` rather than rejected. The two checksums are the built-in values as of V2-19-08 and move whenever the registry or the reachability projection does.
- `image-draft-evidence-v2.json` — soft reference-image draft summary without pixel arrays (`image-draft-evidence-v2.json` when a draft is present).

A complete design package also contains `terrain-intent-v2.json` and `checksums.sha256`. Soft draft evidence never implies HARD constraint promotion.
