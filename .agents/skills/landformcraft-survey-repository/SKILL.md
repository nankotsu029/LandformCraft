---
name: landformcraft-survey-repository
description: Task着手前・再設計前・引き継ぎ時のリポジトリ調査で使う。実装済み／未実装／文書だけの機能を、コード・テスト・Schemaを根拠に分類し、調査記録を残す。実装やdocs修正そのものには使わず、Task実行は専用Skillへ渡す。
---

# LandformCraft リポジトリ調査

## 使用条件

- 新しいPhase・大規模Task・再設計の前に現状を確定する場合に適用せよ。
- roadmapや監査文書の完了主張とコードの一致を検証する場合に適用せよ。

## 使用しない条件

- 単一Taskの実装前確認だけならTask capsule作成で足りる。使うな。
- 調査結果に基づく実装・docs修正はこのSkillで行うな。

## 入力

- 調査目的（何を判断するための調査か）。
- 対象範囲（全体／特定subsystem）。

## 手順

1. `git status --short`と直近logを記録し、未コミット変更を保護対象として明記せよ。
2. `docs/roadmap.md`、`docs/design-v2/task-index.md`、監査文書の完了主張を列挙せよ。
3. 各主張へ対応するproduction code・テスト・Schemaを`rg`で発見し、file pathとclass名で紐づけよ。
4. `./gradlew build -x test`と対象テスト（必要ならfull `test`）を実行し、緑／赤を事実として記録せよ。
5. 実装済み／`EXPERIMENTAL`／文書のみ／矛盾、の4分類で表へまとめよ。
6. 技術的負債・再利用資産・拡張を妨げる構造を、根拠file付きで列挙せよ。

## 成果物

- `docs/audits/`配下の調査記録（例: `redesign-2026-07-current-state.md`の形式）。実際のファイルパス、クラス、Schema、テスト名を根拠として含めること。

## 停止条件

- コードと正本文書の矛盾を発見した場合は、調査記録に矛盾として残し、修正を勝手に行わず報告して停止せよ。
