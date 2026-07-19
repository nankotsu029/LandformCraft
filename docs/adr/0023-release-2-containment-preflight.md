# 0023: Release 2 fluid／gravity／neighbor containment preflightをapply前のhard gateにする

- Status: Accepted
- Date: 2026-07-18
- Decision scope: V2-6-05

## Context

ADR 0021はmutation／effect envelopeのAABB上限を、ADR 0022／snapshot-allは予約と全envelope snapshotを固定した。しかしfluid／gravity／neighbor updateがeffect envelope外へ波及する可能性は、AABB expansionだけでは証明できない。事後rollbackやsnapshot外検出へ委ねると、AGENTS.mdの配置不変条件とgeneration-pipelineのhard validation要求を破る。

## Decision

V2-6-05で次を追加する。

- `format.v2.minecraft.PlacementBlockPhysicsCatalogV2`（`release-2-placement-block-physics-catalog-v1`）— identifier単位の閉じたSOLID／AIR／FLUID／GRAVITY／NEIGHBOR／UNSUPPORTED分類。unknownは拒否。
- `PlacementContainmentPolicyV2`（`release-2-placement-containment-policy-v1`）— envelope physics policyと一致必須のclosure／support／scan budget。
- `core.v2.placement.safety.PlacementContainmentPreflightV2` — `SNAPSHOT_COMPLETE`後にpost-apply predicted worldをcanonical X→Z→Y順で走査し、fluid closure、gravity support、neighbor radius、boundary seal、physics-class宣言を検査する。
- `PlacementContainmentEvidenceV2`（`release-2-placement-containment-v1`）— `CONTAINED`だけをsealし、plan／envelope／snapshotへchecksum bindingする。失敗時はevidenceを発行せずhard rejectする。

apply、settle simulation、事後rollbackによる代替は対象外である。journalに新stateは追加しない。

## Consequences

- apply orchestration（V2-6-06）はcontainment evidenceを必須前提にできる。
- 上限を証明できないwater／lava／sand／gravel／neighbor／unsupported stateは配置eligibleにならない。
- v1 placement／Undo意味は変更しない。

## Alternatives considered

### snapshot外副作用をsettle後に検出してrollbackする

rollback不能な外部影響を許容するため不採用。

### physics分類を呼び出し側の自由リストにする

決定性とdenylist境界が崩れるため、version固定catalogのみを採用。
