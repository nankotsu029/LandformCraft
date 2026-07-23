# V2-19-04 Generic constraint source ＋ Intent binding authoring

> Status: **PASS（2026-07-23）**。[横断監査 rev.2](../../audits/cross-cutting-audit-2026-07-23.md) §2.7が確定した「constraint source→Intent bindingの公開authoring経路が存在せず、`HEIGHT_GUIDE`／`ZONE_LABEL_MAP`はrequest宣言verbすら無い」欠陥を解消し、source→binding→compile→consumerを公開command surfaceだけで通した。**新しいSchema・新capability・新artifact formatは追加していない**（request／intent契約は3 roleと複数mapを既に表現できていた）。terrain field／tile／blockのsemantic checksum、Release format、v1資産は不変。

## 1. 欠陥（修正前）

| 経路 | 修正前 |
|---|---|
| request source宣言（`LAND_WATER_MASK`） | `request constraint-map` のみ。U8 land/water固定で、既存宣言を**置換**する（複数map不可） |
| request source宣言（`HEIGHT_GUIDE`／`ZONE_LABEL_MAP`） | **verbが存在しない**（Schemaは表現できるのに公開authoringが作れない） |
| Intent binding（`mapReferences`） | **verbが存在しない**。canonical `artifactId` は `constraint:<semantic>:sha256-<宣言digest>` で、公開Releaseで`SurfaceReleaseCapabilityVerifierV2.verifyIntentBindings`が照合する（V2-18-07）。手書きJSONでSHA-256を書き写すしかなく、既存fixtureはdigest部を`0`で埋めてexportの書き換えに依存していた |
| E2E | image fidelity chain testは source宣言までCLIで行い、exportには**最初からmapReferencesを持つsealed intent fixture**を渡していた |

## 2. 修正

### 2.1 `request constraint-source`（CLI／Paper両面）

```text
v2 request constraint-source <request-id> <source-slug> <land-water|height-guide|zone-label> <promotion-dir> <request-relative-file>
```

- **追加・更新**（置換ではない）: 同じsource idは差し替え、他の宣言は保持する。複数mapのrequestが初めてauthoringから作れる。
- role・encoding・寸法・digestは`promote`が封印した**promotion record**から読む。height guideのvalue meaning／scale／offset／valid sample範囲、zoneのlabel legendはcommand引数に載らない値であり、`PromotedConstraintSourceFactoryV2`がrecordの値をそのまま使う（推測しない）。roleは操作者が明示し、recordのroleと一致しなければ読み込みが失敗する。
- map file自体はコピーしない（`constraint-map`と同じ責務境界）。Paper側は promotion directoryをplugin workspace内へ解決する（外部pathは拒否）。

### 2.2 `intent bind` ／ `intent bindings`（CLI専用）

```text
v2 intent bind <request> <intent-in> <intent-out> <source-slug> <role> <hard|soft> <nearest|bilinear-fixed> <tolerance-blocks> [weight-millionths]
v2 intent bindings <request> <intent>
```

- `IntentConstraintBindingServiceV2.bind`が`artifactPrefix(role) + source.expectedSha256()`を組み立てる。**digestは宣言から計算され、authorもproviderも入力しない**（providerは宣言digestを知り得ないので、発明させない）。binding idはsource slugで、同一id／同一sourceIdの既存行は置換する。
- roleと宣言encodingの矛盾（categoricalをHEIGHT_GUIDEに、water/land以外のlegendをLAND_WATER_MASKに）を拒否する。strength／sampling／tolerance／weightの組合せ規則は`TerrainIntentV2.ConstraintMapBinding`が従来どおり検査する。
- `verify`は同じ規則を再計算し、さらにmap実体を`SecureConstraintMapSourceLoader`＋IHDR寸法で再解決する（HARD preflight gateと同じ経路。V2-18-03の`dimensionMismatch`は`ConstraintMapPngHeaderV2`へ切り出して共有した）。1件でも不一致があればCLIは`LFC-REQUEST-INVALID`でfail closedする。
- 出力の`generatorConsumer`は、そのroleを読む生成側の有無を示す（`LAND_WATER_MASK`＝`MACRO_FOUNDATION`、他は`NONE`）。**宣言・binding・検証はできるが読むものは無い**という現状を、能力があるかのように見せずに表示する。

## 3. 検証記録（2026-07-23）

| test | 内容 |
|---|---|
| `ConstraintSourceAndBindingAuthoringV2Test`（5） | 3 role全ての宣言が**累積**すること（置換でない）、height/zone encodingがrecord由来であること、strict re-read一致、同一slugの更新、`artifactId`＝宣言digest、再bindの置換、role×encoding矛盾・未宣言slug・requestId不一致の拒否、consistent判定とunbound source報告、hand-edit された`artifactId`とmap byte差し替えの検出 |
| `LandformCraftCliV2Test`（新規2、実CLI E2E） | extract→promote→`request create/bounds/generation/foundation-base-levels`→`request constraint-source`→`intent bind`→`intent bindings`→`v2 export`（`placementEligible: true`）の全経路をCLIだけで通過。map差し替え後の`intent bindings`は exit 1＋`LFC-REQUEST-INVALID` |
| `PaperV2ConstraintSourceAuthoringV2Test`（新規2） | Paper adapter（`JavaPlugin`非生成）でworkspace内promotion recordからの宣言が成功し、workspace外のpromotion directoryは拒否 |
| suite | `./gradlew test` PASS（224 class／1283 test／10 skip／0 failure）、`./gradlew build` PASS |

## 4. Scope外／不変

- `HEIGHT_GUIDE`の生成側consumer（`V2-19-06`）と「constraint mapは正確に1枚」制約の緩和。現状のsurface exportは land/water binding ちょうど1件を要求するため、他roleを含むintentはexportできない。本Taskはその状態を隠さず`generatorConsumer: NONE`として表示する。
- `MANUAL_CONSTRAINT`／`REFERENCE_IMAGE_DRAFT` design pathのCLI公開（`designPathKinds`は4種のまま）。`MANUAL_CONSTRAINT`経路の`artifactId`は**canonical field checksum**を使う別規則で、本Taskが扱うproduction surface path（宣言input digest）とは意味が異なる（V2-18-07で分離済み）。混同しないよう本文書に明記する。
- 既存`request constraint-map`の互換は維持（land/water 1件を置換する従来の意味のまま）。
- Schema・example・artifact・terrain semantic checksum・Release format・v1 goldenへの変更なし。`V2-19-04`より前のfixture intentが持つ`artifactId`のゼロ埋めdigestは、exportが公開時に書き換える従来動作のまま残している（`intent bindings`はそれを不一致として正しく報告する）。
