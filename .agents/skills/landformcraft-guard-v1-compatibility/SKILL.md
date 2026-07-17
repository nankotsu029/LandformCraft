---
name: landformcraft-guard-v1-compatibility
description: v1互換、golden checksum、generator 3.0.0-phase6、Release format 1、placement、Undoの変更・回帰監査で使う。v2変更がv1へ漏れていないかも検査する。新しいv2 feature実装やPhase gate判定そのものには使わず、Task実行は専用Skillへ渡す。
---

# LandformCraft v1互換性ガード

## 使用条件

- v1 Schema、model、codec、generator、Release 1、placement、Undo、Recoveryへ触れる変更に適用せよ。
- v2 contract、format、query、palette、Paper経路がv1へ影響し得る変更に適用せよ。
- 「互換性を確認」「goldenを守る」「Release 1回帰」の依頼に適用せよ。

## 使用しない条件

- v2 featureの新規実装だけを進める主workflowとして使うな。
- Release 2のtampering matrixだけなら `landformcraft-verify-release-artifacts` を使え。
- 単なる通常test実行に自動適用するな。

## 必ず最初に読む正本

1. `AGENTS.md` のv1／v2互換境界
2. `README.md`、`docs/architecture.md`、`docs/roadmap.md`
3. `docs/design-v2/migration-plan.md`
4. `docs/adr/0003-phase1-grid-terrain.md`〜`0008-versioned-small-structure-assets.md` と、変更に関係する後続ADR
5. v1の `schemas/`、`examples/`、対象production code、現在のcompatibility／format／placement tests
6. V2 Taskに属する変更なら `docs/design-v2/task-index.md` の対象Task節
7. 作業前の `git status --short`

## 入力

- 監査するdiffまたは変更予定。
- 影響が疑われるv1契約面。
- 対象V2 Task ID。該当しなければ「V2 Taskなし」と明記せよ。

## 手順

1. diffをSchema、canonicalization、generator、block stream、Release、placement journal、Undo／Recoveryへ分類せよ。
2. v1とv2のversion dispatch、package、publisher／verifier、journal経路が分離されているか追跡せよ。
3. 現在のtest名とfixtureを `rg` で再発見し、固定されたclass名だけに依存するな。
4. v1 Schemaとexamplesのstrict parse、未知field/version拒否、Java enum同期を検査せよ。
5. 同じv1 Blueprint／seed／generator versionのterrain checksumと全block streamをgoldenで比較せよ。
6. Release 1のstrict allowlist、directory／ZIP、tile coverage、artifact／asset checksum、Sponge read-backを検査せよ。
7. plan→confirm→snapshot→apply→verify、failure rollback、Undo、Recovery、reservation、disk bindingの回帰を検査せよ。
8. v2型やartifactがv1 Release／placementへ接続されていないことをarchitecture testとcode searchで確認せよ。
9. 対象test、互換test群、`./gradlew test`、`./gradlew build`、`git diff --check`を実行せよ。

## 検査項目

- v1 Schemaの意味、default、range、strictnessを変えていないか。
- generator `3.0.0-phase6` のseed派生、tile seam、checksum、materialized block streamを変えていないか。
- Release 1 allowlistを共有mutable catalogやv2 artifactのために緩めていないか。
- existing Release／journal／snapshotをv2として補完推測していないか。
- placement token、actor、world、origin、bounds、reservation、disk、snapshotのbindingを弱めていないか。
- Undo／Recoveryが改変・欠損artifactや曖昧状態を成功扱いしていないか。
- CLI／Paperのv1既定挙動が暗黙にv2へ切り替わっていないか。

## 成功条件

- v1 golden checksumと全block streamが一致すること。
- Release 1 strict verifier、placement、rollback、Undo、Recoveryの対象回帰が通ること。
- v2追加がversion分離され、v1の意味を変更していないこと。

## 停止条件

- 互換性維持と要求実装を同時に満たせないなら停止せよ。
- golden更新が必要に見えても、仕様変更の明示承認なしに期待値を更新するな。
- legacy artifactの欠落値を推測してv2 hard constraintへ昇格する必要があれば停止せよ。

## 出力／報告形式

- 監査したv1 surface一覧
- diffから特定した互換risk
- 実行したgolden／Release／placement／Undo tests
- checksumまたはblock stream比較結果
- version分離の証拠
- 未解決回帰と停止判断

## 関連Skillとの境界

- V2 Task全体は `landformcraft-execute-v2-task` を主Skillにせよ。
- 順序・thread・memory matrixは `landformcraft-audit-determinism-resources` に任せよ。
- Release 2のstrict artifact検証は `landformcraft-verify-release-artifacts` に任せよ。
- V2-6配置の新しい安全契約は `landformcraft-review-v2-placement-safety` に任せ、ここではv1不変だけを監査せよ。
