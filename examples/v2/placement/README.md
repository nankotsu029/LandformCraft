# Placement prerequisite, apply, settle/verify, rollback, Undo, and recovery examples (V2-6-01〜V2-6-10)

These persisted fixtures freeze the Release 2 placement contract through containment preflight,
canonical apply prerequisites, settle／full-verify policy／evidence, and operation-bound Undo.

- `placement-plan-v2.json` — sealed plan with unbound envelope／reservation slots (`V2-6-01`)
- `placement-journal-v2.json` — initial `PLANNED` journal (`V2-6-01`)
- `placement-envelope-plan-v2.json` — SOLID-only mutation＝effect envelope (`V2-6-02`)
- `placement-reservation-plan-v2.json` — effect-region／disk reservation bound to envelope (`V2-6-03`)
- `placement-safety-state-v2.json` — atomic safety ledger after reserve (`V2-6-03`)
- `placement-snapshot-plan-v2.json` — snapshot-all index sealed after strict read-back of every
  effect-envelope snapshot file; binds plan／envelope／reservation／confirmation checksums (`V2-6-04`)
- `placement-containment-policy-v2.json` — version-frozen containment radii／catalog／budget (`V2-6-05`)
- `placement-containment-evidence-v2.json` — sealed `CONTAINED` evidence for a closed water pocket
  after snapshot-all; binds plan／envelope／snapshot checksums (`V2-6-05`)
- `placement-settle-verify-policy-v2.json` — version-frozen settle／verify limits／budget (`V2-6-07`)
- `placement-verify-evidence-v2.json` — sealed `VERIFIED` evidence after bounded settle and exact
  effect-envelope stream match; binds plan／envelope／snapshot／apply-complete journal checksums
  (`V2-6-07`)
- `placement-undo-plan-v2.json` — sealed operation-bound Undo plan with UNDO confirmation and
  `KEEP_SNAPSHOTS_FOR_CLEANUP` retention (`V2-6-09`)
- `placement-recovery-plan-v2.json` — sealed confirmation-bound recovery plan (`SAFE_TO_ACCEPT`
  with `RECOVERY_ACCEPT`) carrying the baseline／observed-world／expected-applied evidence
  checksums the classification was derived from (`V2-6-10`)

Read via `LandformV2DataCodec.readPlacementPlan`／`readPlacementJournal`／`readPlacementEnvelopePlan`／
`readPlacementReservationPlan`／`readPlacementSafetyStateV2`／`readPlacementSnapshotPlan`／
`readPlacementContainmentPolicy`／`readPlacementContainmentEvidence`／
`readPlacementSettleVerifyPolicy`／`readPlacementVerifyEvidence`／`readPlacementUndoPlan`／
`readPlacementRecoveryPlan`.

The V2-6-06 stream example exercised by `PlacementApplyTransactionServiceV2Test` is one exact
X-fastest→Z→Y block per mutation coordinate, tagged with an explicit owner tile and overlay
ordinal. It is replayed in this fixed order:

```text
canonical tile index
  → SOLID, overlay ordinal ascending
  → AIR_CARVE, overlay ordinal ascending
  → FLUID, overlay ordinal ascending
```

Surface stone, cave air, sky solid, waterfall water, and underground lava use this one stream;
there is no FeatureKind-specific Paper example or adapter. After apply the journal remains
`APPLYING` with a canonical `APPLIED` tile prefix — that checkpoint alone is never terminal
success.

V2-6-07 advances only from that apply-complete journal through bounded settle and a full effect
envelope exact verify:

```text
APPLYING (all tiles APPLIED)
  → SETTLING
  → VERIFYING
  → APPLIED (all tiles VERIFIED) + sealed PlacementVerifyEvidenceV2
```

Sampled verify is rejected by policy. Failure (timeout, out-of-envelope update, mismatch, cancel,
shutdown, slice／queue／resource budget) leaves `RECOVERY_REQUIRED`.

V2-6-08 rollback adds no persisted shape: the existing journal schema already fixes
`ROLLING_BACK`／`ROLLED_BACK` and the `RESTORED` tile state. `PlacementRollbackServiceV2` starts
only from a persisted `RECOVERY_REQUIRED` journal (every tile `SNAPSHOTTED` or `APPLIED`) and
advances:

```text
RECOVERY_REQUIRED
  → ROLLING_BACK (reverse canonical tile order; canonical RESTORED tile suffix checkpoints)
  → ROLLED_BACK (all tiles RESTORED) + reservation lease released
```

Restore slices (`release-2-placement-restore-slice-v1`) replay the snapshot baseline verbatim in
X-fastest→Z→Y order per effect AABB; missing or tampered snapshot evidence is rejected with zero
world mutation, and terminal `ROLLED_BACK` is sealed only after a bounded rollback settle plus an
exact full-envelope stream match against the snapshot baseline. Published snapshot files are kept
as recovery evidence.

V2-6-09 Undo adds `PlacementUndoPlanV2` without rewriting the sealed apply plan. Prepare issues an
UNDO reservation／confirmation while the journal stays `APPLIED`. Execute rejects world drift
against the expected applied stream (force overwrite forbidden), then:

```text
APPLIED (all tiles VERIFIED) + sealed PlacementUndoPlanV2
  → UNDOING (VERIFIED prefix + RESTORED suffix; reverse-order restore)
  → UNDONE (all tiles RESTORED) + reservation released; snapshots kept for cleanup
```

V2-6-10 recovery classifies persisted journal／artifact／world evidence conservatively and only
offers confirmation-bound actions. `PlacementRecoveryServiceV2.diagnose` is read-only; prepare
seals `PlacementRecoveryPlanV2` with a one-time `RECOVERY_ROLLBACK`／`RECOVERY_ACCEPT`
confirmation. Rollback execute reconciles an interrupted journal deterministically
(VERIFIED→APPLIED, RESTORED→SNAPSHOTTED) back into `RECOVERY_REQUIRED` and delegates to the
V2-6-08 rollback transaction; accept execute seals terminal `APPLIED` only after a fresh full
exact world scan again matches the expected applied stream. Ambiguous evidence is
`MANUAL_INTERVENTION_REQUIRED` with no automatic action, and snapshot cleanup is a dry-run-bound
deletion for terminal `ROLLED_BACK`／`UNDONE` journals only.
