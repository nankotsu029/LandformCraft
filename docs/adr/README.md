# Architecture Decision Records

ADRは重要な判断の背景、採用案、影響を残します。現在の仕様は通常の`docs/`と`schemas/`にも反映し、ADRだけを読まないと実装できない状態にしません。

## 状態

- `Proposed`: 検討中
- `Accepted`: 採用済み
- `Superseded`: 後続ADRで置換
- `Rejected`: 不採用

## 一覧

- [0001: LandformCraftの境界と正式名称](0001-landformcraft-boundaries.md) — Superseded in part
- [0002: 初期実装を単一srcへ集約する](0002-single-source-project.md) — Accepted
- [0003: Phase 1地形データを2次元gridとして生成する](0003-phase1-grid-terrain.md) — Accepted
- [0004: TileをSponge v3へstream出力して検証後に公開する](0004-streaming-sponge-release.md) — Accepted
- [0005: 配置を確認トークン付きタイルcheckpoint transactionにする](0005-confirmed-checkpointed-placement.md) — Accepted
- [0006: AI Providerを構造化Intent境界へ閉じ込める](0006-provider-neutral-structured-intent.md) — Accepted
- [0007: 画像をrole付きの正規化境界でProviderへ渡す](0007-sanitized-role-aware-image-boundary.md) — Accepted
- [0008: 小規模構造物をversion付きassetと決定論的配置へ限定する](0008-versioned-small-structure-assets.md) — Accepted
- [0009: 独自Web UIを削除しCLI／Paper betaへ集中する](0009-remove-web-ui-and-prepare-beta.md) — Accepted
- [0010: version分離したTerrain Generation v2 roadmapを採用する](0010-adopt-versioned-terrain-v2-roadmap.md) — Accepted
- [0011: v2の2次元fieldをLFC_GRID_V1 sidecarへ保存する](0011-compact-field-sidecar.md) — Accepted
- [0012: Release format 2をcapability分離したstrict artifact indexとして導入する](0012-release-format-2-core-index.md) — Accepted
- [0013: `surface-2_5d`をstrictなRelease 2 capabilityとして固定する](0013-surface-2_5d-release-capability.md) — Accepted
- [0014: `hydrology-plan`を`surface-2_5d`依存のRelease 2 capabilityとして固定する](0014-hydrology-plan-release-capability.md) — Accepted
- [0015: 2026-07再設計はv2基盤を維持し、3トラック依存グラフへroadmapを再構築する](0015-adopt-v2-foundation-three-track-roadmap.md) — Accepted
- [0016: scale class契約と3000×3000へ向けた実行計画境界を導入する](0016-scale-classes-and-execution-planning.md) — Accepted
- [0017: 通常画像からの決定論的land-water抽出をdraft境界として導入する](0017-deterministic-image-mask-extraction.md) — Accepted
- [0018: semantic materialをMinecraft 1.21.11 palette adapterへ隔離する](0018-minecraft-palette-adapter-v2.md) — Accepted
- [0019: `environment-fields`を`hydrology-plan`＋`surface-2_5d`依存のRelease 2 capabilityとして固定する](0019-environment-fields-release-capability.md) — Accepted
- [0020: Release 2 placement plan／journal契約をv1と分離して固定する](0020-release-2-placement-contract.md) — Accepted
- [0021: Release 2 mutation／effect envelopeを保守的AABB契約として固定する](0021-release-2-mutation-effect-envelope.md) — Accepted
- [0022: Release 2 region／disk reservationとbound confirmationをv1と分離して固定する](0022-release-2-reservation-bound-confirmation.md) — Accepted
- [0023: Release 2 fluid／gravity／neighbor containment preflightをapply前のhard gateにする](0023-release-2-containment-preflight.md) — Accepted
- [0024: Release 2 applyをcanonical streamのbounded transactionとして実行する](0024-release-2-canonical-apply-orchestration.md) — Accepted
- [0025: Release 2 bounded settleとeffect envelope全体のexact verifyをterminal APPLIED条件にする](0025-release-2-bounded-settle-full-verify.md) — Accepted
- [0026: Release 2 rollbackはsnapshot済みenvelopeの逆順復元とfull verifyだけを成功とする](0026-release-2-reverse-order-rollback.md) — Accepted
- [0027: Release 2 Undoはoperation-bound confirmとworld-drift preflight後の逆順復元だけを成功とする](0027-release-2-operation-bound-undo.md) — Accepted
- [0028: Release 2 Recoveryは保守的分類とconfirmation-bound rollback／acceptだけを許可する](0028-release-2-conservative-recovery.md) — Accepted
- [0029: Provider／manual／image を v2 capability 明示選択と no-fallback で同一 canonical Intent へ接続する](0029-provider-manual-image-v2-capability-integration.md) — Accepted
- [0030: Release 2 capability dependency matrix と placement eligibility を固定する](0030-release-2-cross-capability-hardening.md) — Accepted
- [0031: Release 2 operational metrics／diagnostics／retention を bounded audit 契約で固定する](0031-release-2-operational-metrics-diagnostics-retention.md) — Accepted
- [0032: FAWE 2.15.2 standalone smoke profile を Release 2 実機証拠の正本とする](0032-fawe-2.15.2-standalone-smoke-profile.md) — Accepted
- [0033: Release 2 verified canonical block sourceをcloseable viewで固定する](0033-release-2-verified-canonical-block-source.md) — Accepted
- [0034: Release 2 Paper配置を明示version dispatchのproduction lifecycleとして接続する](0034-release-2-paper-application-lifecycle.md) — Accepted
- [0035: v1退役のgovernanceと互換性policyの段階的解除](0035-v1-retirement-governance.md) — Accepted

## Template

```markdown
# NNNN: タイトル

- Status: Proposed
- Date: YYYY-MM-DD

## Context

## Decision

## Consequences

## Alternatives
```
