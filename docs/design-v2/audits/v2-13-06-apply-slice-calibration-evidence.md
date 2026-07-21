# V2-13-06 Apply slice calibration — measured evidence

- Status: **COMPLETE**（2026-07-22、1024 production変更を人間確認・承認済み）
- Audit: [v2-13-06-apply-slice-calibration.md](v2-13-06-apply-slice-calibration.md)
- Runbook: [v2-13-06-apply-slice-calibration-runbook.md](../../smoke/v2-13-06-apply-slice-calibration-runbook.md)
- Raw evidence (not committed): `build/measure/v2-13-06/`
- Calibration bounded-summary semantic checksum:
  `da5438fc0a5846eb92fb649ea5e00467bdb89dbdf4f4effac8d37df289b3f145`
- Calibration summary file SHA-256:
  `394e89777a6a2dafcfe7b2bd2d6befae1494a9dc75767fad61458c32625ee372`
- Selected-run bounded-summary semantic checksum:
  `357872f36f7c344cfee2965296bb2510d2dd21f22b9f7fe0b5d016b1bea4b20f`
- Selected summary file SHA-256:
  `16c2fb44cbac610d875613f176a1343fbb46921d25e33dacc3668d727cccf3d8`

## Host and fixture

```text
Paper: 1.21.11 build 132, standalone paperclip
Adapter: FastAsyncWorldEdit-Paper 2.15.2 only; WorldEdit plugin absent
JVM: Java 21, Xmx 8G, Xms 2G, G1 MaxGCPauseMillis=200
Calibration fixture: v2-13-04-measure-1024, 1024×1024, 2,097,152 mutations, 64 tiles
Calibration build JAR SHA-256: fd59c3bb04f6e1d445f9abb9b8b3d3e00191f61482bf5b1921ac3e84a4135e82
Calibration manifest SHA-256: 40b0281f2595114aa1ab2dcc14e6d79ea796240b684dc6530b08f86a8216b2fc
Selected build JAR SHA-256: d1a96dec7dd3357dee197df8107e232dd42a7c04b5c4a01afdb774d5dc60b783
Watchdog: 60,000 ms
```

## Closed candidate calibration (1024², two pass each)

Stage durations exclude stone-seal/setup gaps. Health values use the APPLY window after a 10-second
warm-up; maximum tick is the five-second maximum reported by Paper. RSS/heap are PID-only.

| Slice | APPLY s p1/p2 | blocks/s p1/p2 | lifecycle s p1/p2 | max tick ms | max 1m MSPT ms | peak RSS bytes | peak used heap bytes | max GC pause ms |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 32 | 1647 / 1648 | 1273 / 1273 | 2072 / 2072 | 35.2 | 4.9 | 5,573,095,424 | 4,082,776,064 | 42.355 |
| 128 | 418 / 417 | 5017 / 5029 | 841 / 842 | 25.1 | 6.5 | 5,489,242,112 | 3,830,127,616 | 44.259 |
| 256 | 215 / 215 | 9754 / 9754 | 636 / 640 | 32.7 | 6.6 | 5,283,459,072 | 3,899,954,176 | 42.132 |
| 512 | 130 / 139 | 16132 / 15087 | 554 / 563 | 18.4 | 6.1 | 5,268,537,344 | 3,681,845,248 | 48.447 |
| **1024** | **109 / 109** | **19240 / 19240** | **534 / 534** | **16.6** | **5.4** | **5,498,265,600** | **3,572,608,000** | **43.473** |

All candidates recorded queue-depth upper bound 1. All ten passes reached `APPLIED` only after full
exact effect-envelope verification, then reached `UNDONE`. Every candidate also completed
plan→SIGKILL before confirmation→restart→doctor Recovery observation. No watchdog signature or disk
admission failure occurred. The cancel response upper bound is the maximum observed accepted-slice
tick (1024: 16.6 ms); the deterministic unit boundary additionally proves cancel is checked after
receipt and before another submission for every candidate.

Selection relative to slice 32:

| Gate | 1024 result | Threshold | Result |
|---|---:|---:|---|
| APPLY reduction | 93.3839% | ≥30% | PASS |
| lifecycle reduction | 74.2278% | ≥20% | PASS |
| maximum observed tick | 16.6 ms | <50 ms and no worse than baseline | PASS |
| maximum window-average MSPT | 5.4 ms | <50 ms | PASS |
| peak RSS / used heap | below slice-32 values | no degradation | PASS |
| maximum GC pause | 43.473 ms | ≤200 ms | PASS |
| watchdog / terminal lifecycle / Recovery | none / invariant | invariant | PASS |

## Selected production-default re-measurement

Both dimensions used the same selected build JAR. Each pass reached `APPLIED` after full exact verify
and then `UNDONE`.

| Dimension | APPLY s p1/p2 | blocks/s p1/p2 | lifecycle s p1/p2 | APPLY reduction | lifecycle reduction | peak RSS bytes | peak used heap bytes | max GC pause ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1000² | 102 / 103 | 19608 / 19417 | 506 / 507 | 93.4776% | 74.3544% | 5,624,307,712 | 3,975,503,872 | 43.940 |
| 1024² | 108 / 108 | 19418 / 19418 | 533 / 533 | 93.4446% | 74.2761% | 5,527,519,232 | 3,946,972,160 | 43.154 |

The final 1024² APPLY window yielded 22 successful direct health samples: maximum tick 40.8 ms and
maximum window-average MSPT 5.4 ms. The final 1000² auxiliary health subprocess returned only RCON
`Operation not permitted`; its tick/MSPT fields are therefore explicitly unavailable and are not
imputed. Its stage timing, journals, GC and PID telemetry are valid. Selection safety is independently
covered by the immediately preceding same-host 1024 calibration (22 successful samples, max tick
16.6 ms) and the final 1024 run. No watchdog signature occurred in either final dimension.

Recovery was repeated after the selected 1024² two-pass run: plan→SIGKILL before confirmation→restart
→doctor. v1 help/doctor smoke was also issued on the same profile.

## Determinism and examples

- All slice candidates preserve canonical ordering, tile checkpoint progression, plan checksum and
  world-state checksum in `PlacementApplyTransactionServiceV2Test`.
- Sealed example regeneration is explicit-only. Plan, journal and all affected downstream envelope／
  reservation／safety／snapshot／containment／Undo／Recovery fixtures were regenerated twice through
  their compiler tests; every byte hash was stable. Key SHA-256 values: plan
  `29a72ec65b43242054bed615e99f396f71becf95453e68502cf17deadd86d5ad`, journal
  `9d634b10d64b6de05b32f1b76850c2130fa6f8e6e208a8b1370e3a30e6bf845b`, envelope
  `11a71447fef29205be63fde814710af4e9259092959935185560e42b49b112be`, reservation
  `69562c9e69195c41c89432d2a072b75d6e00f6b3eb19e8031e2e90e6db8130c7`, safety state
  `87c23e4293a20f94a9ef0afaee5a9f9f415f6c573f3690d014f2f62dbdb8b9b3`, snapshot
  `1a1930a676bbcdd128cb89c98007f4499cbc0240213dd4dfe795ed6d10103552`.
- No JSON Schema changed: apply slice and working bytes are runtime/admission values already present
  in the sealed plan contract, not new fields or enum values.

## Non-claims

- 1024 is not promoted in the placement dimension catalog and is not newly claimed `SUPPORTED` or
  `testedMaximum`.
- WorldEdit standalone, other plugins, SLOs and LARGE are not measured.
- The 1000² final health-sampler gap is not filled from another run.
- The operator explicitly approved the 1024 production change on 2026-07-22 after reviewing this
  evidence. Catalog/dimension promotion and V2-13 parent completion remain separate decisions.
