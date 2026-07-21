# V2-14-02 checksum影響監査: 斜視／multi-view reference role

- Task: `V2-14-02`（Track B、V2-14 Image fidelity wiring follow-up）
- Date: 2026-07-21
- 目的: `GenerationRequestV2.ReferenceImageRole` へ斜視地形reference用roleと同一地点multi-view用roleを追加する前に、追加がcanonical checksum影響識別子に該当しないこと、および既存artifactのchecksumを変えないことを確認する。
- 前提: 本監査を実装前に完了することがTask gateである（[task-index.md](../task-index.md) `V2-14-02`）。監査で互換性が確認できない場合は実装せず停止する。

## 1. 追加するrole

| enum値 | 用途 | 契約 |
|---|---|---|
| `OBLIQUE_TERRAIN_REFERENCE` | 斜視（perspective）で撮影・描画された地形reference | AI提案入力専用。座標constraint・HARD geometryを生成しない |
| `MULTI_VIEW_REFERENCE` | 同一地点を複数視点から撮影・描画した参照群 | AI提案入力専用。座標constraint・HARD geometryを生成しない |

いずれもAIへ渡すprompt入力としての意味付けだけを持ち、pixel→座標対応、height guide、mask、curve等の決定論的constraintを生成しない。斜視→top-down自動変換や未確認地下地形の推定は本Taskの非Scopeであり、role契約としても禁止する。

## 2. canonical checksum影響識別子への該当確認

[ADR 0035](../../adr/0035-v1-retirement-governance.md) D4がchecksum影響として凍結する識別子と、本追加の関係は次の通り。**いずれにも該当しない。**

| 凍結識別子カテゴリ | 本追加が変更するか | 根拠 |
|---|---|---|
| Schema `$id` | しない | `generation-request-v2.schema.json` の `$id` は不変。enum配列へ値を追記するのみ |
| schemaファイル名 | しない | ファイル名不変 |
| capability名 | しない | Release capability・feature capabilityの識別子に触れない |
| artifact type／version | しない | `generation-request-v2` type / version 1 は不変 |
| module／stage ID | しない | 追加は`model.v2`のenumで、moduleやstage IDではない |
| named seed | しない | seed契約に無関係 |
| contract ID | しない | requestVersion・intentContractVersion不変 |
| generator version | しない | generator `3.0.0-phase6` および v2 generatorへ無関係 |
| error code | しない | 安定code（`V2_CLI_ONLY` 等）に触れない |
| metric label | しない | operational metric labelに触れない |

enum**値名**はcanonical checksum影響識別子ではない。追加はadditiveであり、既存の値名・順序・serialize表現を変更しない。

## 3. 既存artifact checksumへの非影響

`GenerationRequestV2` はRelease surface artifact `source/generation-request.json`（type `generation-request-v2`）としてcanonical JSON化され、`LandformV2DataCodec.generationRequestChecksum` でSHA-256が計算される。role値は `reference.role().name()` として直列化される。

- 既存のrequest／fixture／goldenはすべて既存role（`MOOD_REFERENCE` 等）または空 `referenceImages: []` を用いる。enum値の追加は既存値のserialize結果を変えないため、**既存artifactのcanonical JSONとchecksumは不変**である。
- リポジトリ内にrequest checksumのハードコードliteralは存在しない。`generationRequestChecksum` を参照する全testは、既存roleを含むfixtureから動的に計算・比較しており、新role未使用のため影響を受けない（`GenerationRequestV2CodecTest`、`MigrationPhaseGateV2Test`、`V2RequestStoreV2Test`、`*ReleaseFixtureV2` 他）。
- 新roleを実際に使うartifactだけが新roleを含むchecksumを持つ。これは新規入力に対する新規checksumであり、既存artifactの回帰ではない。

## 4. determinism／v1互換

- reference image roleは決定論的な Blueprint→field→block/metric/artifact 生成へ入らない。roleが流れる先は (1) `TerrainIntentPromptV2.imageRoleText` のAI prompt文、(2) request codecのserialize、(3) `TerrainDesignRequestV2` の宣言／prepared一致検証、の3経路のみで、canonical block／field／metric checksumへは寄与しない。よって同一 Blueprint・seed・generator version からの生成物checksumは不変。
- v1 `model.ReferenceImageRole`（legacy schema `generation-request.schema.json`）は本Taskで一切変更しない。v2の別enumへの追加であり、v1凍結（v1 Schema・generator `3.0.0-phase6`・Release format 1・golden）を破らない。

## 5. example

[examples/v2/diagnostic/oblique-multi-view.request-v2.json](../../../examples/v2/diagnostic/oblique-multi-view.request-v2.json) が両roleを含むrequestの正例である（`constraintMaps: []`。reference imageはAI提案入力専用で決定論的constraintを生成しない）。

## 6. 結論

**PASS（互換性確認）。** 斜視／multi-view roleの追加はadditiveであり、canonical checksum影響識別子に該当せず、既存artifactのchecksum・v1互換・determinismを破らない。Task gateを満たすため実装へ進む。
