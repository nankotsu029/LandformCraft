---
name: landformcraft-sync-docs-roadmap
description: Task完了時・実装変更後のREADME、docs、roadmap、Task Index、CHANGELOG、AGENTSの同期で使う。文書だけを先行させる新規設計の書き起こしには使わない。
---

# LandformCraft docs／roadmap同期

## 使用条件

- Task完了報告の直前、または実装とdocsの乖離を修正する場合に適用せよ。

## 使用しない条件

- 実装を伴わない将来設計の書き起こしには使うな（設計はADR＋design-v2文書の担当Taskで行う）。

## 正本の役割分担（重複記載を作るな）

| 情報 | 正本 |
|---|---|
| 進捗・次Task | `docs/roadmap.md` |
| Task Scope・Acceptance | `docs/design-v2/task-index.md` |
| モデル割当 | `docs/design-v2/model-assignment.md` |
| 設計判断の理由 | `docs/adr/` |
| 実装状態の対外説明 | `README.md`（実装済み／`EXPERIMENTAL`／未実装を明確に） |
| 契約詳細 | `schemas/`と各design-v2文書 |

## 手順

1. 今回の変更が上記のどの正本へ影響するかを列挙せよ。
2. roadmapへは短い完了記録（何を実装し、何を検証し、何が未接続か、次Task）だけを書き、詳細はテストとdesign文書へ置け。
3. READMEは「できること／できないこと」を実態と一致させよ。`EXPERIMENTAL`・未接続を実装済みと書くな。
4. 矛盾する古い記述は更新・統合・Deprecated明示・archiveのいずれかで解消し、矛盾したまま残すな。
5. リンク切れを`rg`で確認せよ。

## 停止条件

- 実装とAcceptanceが一致しないままdocsだけ完了へ書き換える指示になっている場合は停止して報告せよ。
