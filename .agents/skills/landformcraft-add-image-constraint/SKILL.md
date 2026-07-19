---
name: landformcraft-add-image-constraint
description: V2-7（画像忠実性）のTask、抽出アルゴリズム追加、constraint map role追加、draft／昇格経路の変更で使う。AI Provider向けreference image処理の変更や、画像と無関係なfield変更には使わない。
---

# LandformCraft 画像制約追加

## 使用条件

- `format.v2.constraint`／`format.v2.constraint.extract`への抽出・decode・binding追加に適用せよ。
- V2-7のTask実行、抽出algorithm versionの追加に適用せよ。

## 使用しない条件

- AIへ送るreference image正規化（`validation/ReferenceImageProcessor`）の変更には使うな。
- 画像と無関係なfield・generator変更には使うな。

## 必ず最初に読む正本

1. `docs/design-v2/image-constraint-maps.md`（特に15節の抽出契約）
2. `docs/adr/0017-deterministic-image-mask-extraction.md`
3. 既存の`NumericPngDecoder`／`SecureConstraintMapSourceLoader`／`ImageLandWaterExtractorV2`とそのテスト
4. 対象Taskの`docs/design-v2/task-index.md`節（V2-7-01〜07）

## 不変条件

- 抽出・decodeはinteger-onlyで、AIを呼ばず、同一入力から同一checksumを再生すること。
- 抽出結果はdraft（SOFT）であり、hard化は明示昇格（V2-7-04契約）だけとすること。
- algorithm・encoding・thresholdの変更は新version文字列として追加し、既存versionの意味を変えないこと。
- trusted ceiling方式のadmissionを割当前に行い、cancelを有界間隔で観測すること。
- source→draft→constraint mapのprovenance連鎖を検証可能に保つこと。

## 手順

1. 対象roleまたはalgorithmの契約（入力・出力・version・上限）を先に記述せよ。
2. 既存パターン（failure code enum＋専用exception＋limits record＋final raster class）へ従って実装せよ。
3. golden分類／変換テスト、決定性（repeat・thread・locale・timezone）、全拒否経路、cancelをテストせよ。
4. `docs/design-v2/image-constraint-maps.md`とroadmap、必要ならSchema・exampleを同期せよ。

## 停止条件

- draftの暗黙hard化、fallback昇格、AI出力の直接field書込みが必要になった場合。
- 反復回数や浮動小数に依存し決定論を保証できないアルゴリズムしか無い場合（設計を報告して停止）。
