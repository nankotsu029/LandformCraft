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
