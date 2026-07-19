---
name: landformcraft-assign-task-model
description: 新Taskの追加時や実行計画の見直し時に、モデル・Effort・レビュー担当を決めるために使う。Taskの実装そのものには使わない。
---

# LandformCraft Taskモデル割当

## 使用条件

- Task Indexへ新Taskを追加した直後、または割当の再検討時に適用せよ。

## 使用しない条件

- 割当表があるTaskの実行時には使うな（`model-assignment.md`の該当行へ従え）。

## 正本

- `docs/design-v2/model-assignment.md`（原則・昇格ラダー・レビューマトリクス・種類別既定割当）

## 手順

1. Taskのリスク種別を判定せよ: 通常実装／計画が難しい通常実装／contract・Schema互換／memory／concurrency／数学的保証／transaction・rollback・recovery／実機・実測／監査。
2. `model-assignment.md` §3の種類別既定割当から主担当とEffortを引け。最初から上位モデルを選ぶな。
3. レビューはレビューマトリクスから実装者と別系統を選べ。
4. 割当表（§2）へ行を追加し、根拠（リスク種別）を1行で書け。
5. Task capsuleへ推奨モデル・Effort・停止条件を記載せよ。

## 停止条件

- どのリスク種別にも当てはまらない、または複数の高リスク種別が同一Taskに混在する場合は、Task分割を提案して停止せよ。
