# User Guide

> CLI／Paperはv2のみです。`lfc <verb>`／`/lfc <verb>`は恒久的な明示形`v2 <verb>`と同じ経路です。既存v1 artifactはread-only verifierまたは`migrate`で扱います。

## 方法A: CLI＋手動JSON

v2の`generation-request-v2.json`は範囲、prompt、constraint map source、seed、tile設定を持ちます。`terrain-intent-v2.json`はAIまたは人が作る設計意図です。同梱fixtureを使う最短経路は次です。

```bash
./gradlew run --args="request validate examples/v2/diagnostic/harbor-cove-64.request-v2.json"
./gradlew run --args="export examples/v2/diagnostic/harbor-cove-64.request-v2.json examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json build/exports harbor-cove-64 water 54 46"
./gradlew run --args="preview build/exports/harbor-cove-64"
```

`export`末尾の`<land|water> <land-y> <water-y>`は、coastal featureが所有しないcellのbaselineを推測しないための明示値です。自分でrequestを作る場合は、strict schema検証とatomic publishを行うauthoring verbを使います。

```bash
./gradlew run --args="--data-dir build/data request create coast-01"
./gradlew run --args="--data-dir build/data request bounds coast-01 256 256 -32 160 62"
./gradlew run --args="--data-dir build/data request constraint-map coast-01 coast-mask maps/coast-u8.png <sha256> 256 256"
./gradlew run --args="--data-dir build/data request prompt coast-01 岩礁と砂浜を持つ海岸"
./gradlew run --args="--data-dir build/data request list --json"
```

CLI管理用には`--data-dir <directory>`、自動処理には`--json`、正常出力を抑えるには`--quiet`、stack traceを必要時だけ出すには`--verbose`を使います。errorはstderr、正常結果はstdoutです。`job status|cancel|list`と`candidate list|info`は同じv2 job storeを参照します。`journal-verify`と`recovery inspect <journal|plan> <artifact>`はread-onlyで、world変更はPaperだけが行います。

## 方法B: ブラウザ版ChatGPT／Claude＋JSON import

これはLandformCraft独自Web UIではありません。`schemas/terrain-intent-v2.schema.json`、v2 requestのpromptと必要最小限の画像だけを外部AIへ渡し、返答をJSONだけで保存します。サーバーファイル、API key、Cookie、player情報を渡してはいけません。

テンプレート:

```text
あなたはMinecraft地形の設計者です。添付したTerrainIntent JSON Schemaへ厳密に適合する
JSON objectだけを返してください。Javaコードやblock座標一覧は返さないでください。
intent-idはrequest-idと一致させ、geometryは0〜1のnormalized座標で明示してください。
HARD relationやconstraintを推測で補完せず、不明な画像情報はUNCONFIRMEDのままにしてください。
block座標一覧や実行可能コードは返さないでください。
```

保存後は`design import <request-v2.json> <terrain-intent-v2.json> [designs-root]`でstrict Design Packageを作成します。v1資産を変換する場合は`migrate inspect`で損失を確認し、`migrate apply ... <strict|accept-lossy>`を明示してください。元資産は上書きされません。

## 方法C: OpenAI API

```bash
export OPENAI_API_KEY='...'
./gradlew run --args="design openai <request-v2.json> <explicit-model-id> <designs-root>"
```

Provider pathはcapabilityを明示し、未対応model／capabilityをv1へfallbackしません。Design Packageのstrict read-back後、出力された`terrain-intent-v2.json`を`generate`／`export`へ渡します。OpenAIには相関用`X-Client-Request-Id`を送りますが、曖昧なnetwork failure後のProvider側exactly-once／非課金を保証するものではありません。

## 方法D: Anthropic API

```bash
export ANTHROPIC_API_KEY='...'
./gradlew run --args="design anthropic <request-v2.json> <explicit-model-id> <designs-root>"
```

OpenAIはResponses APIの`text.format`、AnthropicはMessages APIの`output_config.format`を使います。認証header、画像payload、usage fieldは異なりますが、どちらも同じTerrainIntent再検証、Design Package、generation pipelineへ合流します。Anthropic MessagesのProvider-side idempotencyは保証せず、成功済みDesign Packageを再利用してください。

## 方法E: Paper内workflow

```text
/lfc request create coast-01
/lfc request selection coast-01
/lfc request prompt coast-01
/lfc request validate requests/coast-01.request-v2.json
/lfc design openai requests/coast-01.request-v2.json <model-id>
/lfc export plan <request-v2.json> <intent-v2.json> <exports-root> <release-id> <land|water> <land-y> <water-y>
/lfc export create <plan-id> <token>
/lfc job status <job-id>
/lfc job cancel <job-id>
/lfc candidate list <request-id>
/lfc candidate info <job-id>
/lfc place plan <release-path> <world> <x> <y> <z>
/lfc place confirm <placement-id> <token>
/lfc place execute <placement-id>
/lfc status <placement-id>
/lfc undo plan <placement-id>
/lfc undo execute <placement-id> <token>
/lfc recover diagnose <placement-id>
/lfc retention status <placement-id>
```

Paper Provider呼出し、画像decode、generation、artifact I/Oはメインスレッド外です。job IDは開始直後に返ります。prompt chat sessionは5分、一回用で、`cancel`を送ると保存しません。

## 既存v1 artifactの移行

v1 production commandは削除済みです。v1 intent／design package／Release 1はread-only legacy verifierと`migrate inspect|apply`からだけ扱います。migrationは元artifactを上書きせず、未対応要素をreportへ明記します。

## 方法F: 画像入力

画像からの生成手順、指定への一致度を上げる組み合わせ、対応地形の境界は [地形・構造物・画像入力ガイド](terrain-design-guide.md) を参照してください。

| role | 用途 |
|---|---|
| `MOOD_REFERENCE` | 雰囲気、色、粗さ。地理の正本ではない |
| `TOP_DOWN_SKETCH` | north-upの上面図。右が`+X/EAST`、下が`+Z/SOUTH` |
| `HEIGHT_REFERENCE` | 高低差の参考 |
| `ZONE_REFERENCE` | zone境界の参考 |
| `MATERIAL_REFERENCE` | surface materialの参考 |
| `STRUCTURE_REFERENCE` | 小規模構造物の雰囲気参考 |

PNG／JPEGだけを受け付け、magic、拡張子、byte数、寸法、pixel数、frame数、symlink／hardlink alias、読込中の差替えを検査します。EXIF orientationを補正し、metadataなしPNGへ再符号化します。上面図間、またはpromptとの強い東西南北の海陸矛盾は拒否し、確信できない意味矛盾は`INCONCLUSIVE`として人がpreviewを確認します。

## 方法G: 建築物

`terrain-intent.json`での定義例と各typeの配置条件は [地形・構造物・画像入力ガイド](terrain-design-guide.md) を参照してください。

built-inは桟橋、橋、小屋、遺跡、道、石積み、階段、柵です。type、preferred zone、seedからanchorとrotationを決め、水、崖、傾斜、bounds、他structureとの間隔を検査します。preferred zoneへ置けなければ全域を安全探索し、配置不能ならterrain-only warningになります。同じ入力、seed、generator versionなら同じ結果です。

custom assetは `--data-dir`配下の`imports/`へSponge v3 schematicとmetadataを置きます。

```bash
./gradlew run --args="asset validate custom.schem metadata.json --data-dir <data>"
./gradlew run --args="asset import custom.schem metadata.json --data-dir <data>"
./gradlew run --args="asset list --data-dir <data>"
./gradlew run --args="asset info <asset-id> --data-dir <data>"
./gradlew run --args="asset remove <asset-id> --data-dir <data>"
```

v3／1.21.11、最大64³、最大32768 block、beta vanilla palette、entity／block entity／biomeなし、anchor範囲、rotation、slope／water metadataを検査します。built-inとのID衝突、使用中Releaseのasset削除、absolute path、symlinkを拒否します。

## 方法H: Releaseの移動

directoryとZIPは同じ論理Releaseです。Release 2はmanifestのartifact allowlist、各checksum、semantic checksum、strict read-backを検証します。移動後、別serverでもCLI `preview <release-directory-or-zip>`（strict verify込み）を実行してください。Release 1は`migrate inspect release <source>`がpackaged legacy verifierでstrict検証します。Minecraft versionは1.21.11固定で、対応外DataVersionを推測変換しません。WorldEditまたはFAWEはどちらか一方だけを使います。
