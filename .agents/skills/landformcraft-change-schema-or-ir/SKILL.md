---
name: landformcraft-change-schema-or-ir
description: schemas/配下のJSON Schema、TerrainIntent v2／WorldBlueprint v2などの中間表現、record契約、canonical JSON、checksumへ影響する変更で使う。Schemaへ触れない通常実装には使わず、v1 Schemaの変更は禁止のため停止する。
---

# LandformCraft Schema／中間表現変更

## 使用条件

- `schemas/*.schema.json`の追加・変更、model recordの契約変更、canonicalization・checksum・version dispatchへの影響がある変更に適用せよ。

## 使用しない条件

- Schemaへ触れない実装・テスト追加には使うな。
- v1 Schema・保存形式・Release 1の変更は禁止である。必要になったら停止せよ。

## 必ず最初に読む正本

1. `AGENTS.md` §5〜§9
2. 対象Schemaと対応するJava record／codec
3. `docs/design-v2/terrain-intent-v2.md`、`world-blueprint-v2.md`のうち関係する節
4. 関連ADR（0011〜0017）
5. 対象Taskの`docs/design-v2/task-index.md`節

## 手順

1. 変更が追加的（新しいoptional無しのstrict union追加等）か破壊的かを最初に判定し、破壊的なら停止して新versionを提案せよ。
2. Java record不変条件とSchema制約を同時に書き、どちらか一方だけで検証される値を作るな。
3. strict round-trip（parse→canonical JSON→parse）テストを追加せよ。unknown field／future version拒否を必ず含めよ。
4. 影響するexample（`examples/`）を同じ変更で更新し、`SchemaContractTest`系を実行せよ。
5. checksumへ影響する場合は、影響範囲（golden、capability verifier）を列挙し、意図した変更だけかを確認せよ。
6. `./gradlew test`と`build`で閉じよ。

## 成果物

- Schema、record、codec、round-tripテスト、example、関連docsの同期した変更一式。

## 停止条件

- v1契約・既存checksumの意味を変える必要がある場合。
- 同一情報の正本が2箇所に生まれる場合（どちらを正本にするか報告して停止）。
