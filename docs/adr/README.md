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
