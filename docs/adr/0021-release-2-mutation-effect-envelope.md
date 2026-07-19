# 0021: Release 2 mutation／effect envelopeを保守的AABB契約として固定する

- Status: Accepted
- Date: 2026-07-18
- Decision scope: V2-6-02

## Context

ADR 0020はRelease 2 placement plan／journal契約を固定したが、world mutation前に必要なmutation／effect envelope算出は未実装だった。3D volumeとfluid／gravity／neighbor updateはtile境界外へ影響し得るため、tile AABBだけではsnapshot範囲を安全に上限化できない。

## Decision

V2-6-02で`model.v2.placement.PlacementEnvelopePlanV2`と`core.v2.placement.envelope.PlacementEnvelopeCompilerV2`を追加する。

- per-tile mutation AABBはtile core XZ内のcontent AABBとする。
- effect AABBはversion固定のphysics policy（`release-2-placement-physics-policy-v1`）でFLUID／GRAVITY／NEIGHBOR support radiusを保守的に加算する。SOLID／AIRはexpansionしない。
- union mutation／effect、allowed world bounds containment、int overflow拒否、effect volume／disk estimate admissionをseal時に検査する。
- `mutationEnvelopeChecksum`と`canonicalChecksum`を分け、`PlacementPlanV2.EnvelopeReferencesV2.bound(...)`へbindingする。
- unknown physics class、tile order mismatch、world border／Y overflow、budget超過、under-approximationはhard rejectする。

reservation、snapshot、apply、settleは対象外である。

## Consequences

- snapshot-all（V2-6-04）はunion effect envelopeを正本にできる。
- 副作用上限を定義できないcontentは配置eligibleにならない。
- v1 placement journal／disk estimate意味は変更しない。

## Alternatives considered

### tileごとのsnapshot→apply交互方式

未snapshot隣接領域をfluidが変更し得るため不採用（volumetric-terrain §11）。

### physics radiusをcaller可変にする

決定性と安全上限が崩れるため、version固定policyのみを採用。
