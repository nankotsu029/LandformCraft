# User Guide

## 方法A: CLI＋手動JSON

`request.yml`は範囲、prompt、画像role、seed、tile／ZIP設定を持ちます。`terrain-intent.json`はAIまたは人が作る設計意図です。

```bash
./gradlew run --args="validate <request.yml> <terrain-intent.json>"
./gradlew run --args="generate <request.yml> <terrain-intent.json> <candidate-directory> 0"
./gradlew run --args="preview <request.yml> <terrain-intent.json> <candidate-directory> 0"
./gradlew run --args="export <request.yml> <terrain-intent.json> <exports-root> 0"
./gradlew run --args="verify <release-directory-or-zip>"
```

CLI管理用には `--data-dir <directory>`、自動処理には `--json`、正常出力を抑えるには `--quiet`、stack traceを必要時だけ出すには `--verbose` を使います。errorはstderr、正常結果はstdoutです。

```bash
./gradlew run --args="request create coast-01 --data-dir build/data"
./gradlew run --args="request bounds coast-01 256 256 -32 160 62 --data-dir build/data"
./gradlew run --args="request prompt coast-01 岩礁と砂浜を持つ海岸 --data-dir build/data"
./gradlew run --args="request validate coast-01 --data-dir build/data --json"
```

`job status|cancel`、`candidate list <request-id>|info|preview|validate`、`recovery list|status|diagnose`も同じdata dirを参照します。CLI recoveryはread-onlyで、world変更はPaperだけが行います。

## 方法B: ブラウザ版ChatGPT／Claude＋JSON import

これはLandformCraft独自Web UIではありません。`schemas/terrain-intent.schema.json`、`request.yml`のpromptと必要最小限の画像だけを外部AIへ渡し、返答をJSONだけで保存します。サーバーファイル、API key、Cookie、player情報を渡してはいけません。

テンプレート:

```text
あなたはMinecraft地形の設計者です。添付したTerrainIntent JSON Schemaへ厳密に適合する
JSON objectだけを返してください。Javaコードやblock座標一覧は返さないでください。
request-idは <request-id> と一致させ、範囲はrequest.ymlを越えないでください。
preferred-zoneはzones内のidだけを使い、構造物は小規模な8 typeだけにしてください。
不明な画像情報は推測で上書きせず、promptを優先して保守的な値にしてください。
```

保存後は `design import <request> <intent> <designs> <jobs>` と `design-verify`を実行します。

## 方法C: OpenAI API

```bash
export OPENAI_API_KEY='...'
./gradlew run --args="design openai <request.yml> <explicit-model-id> <designs-root> <jobs-root>"
```

画像なし／ありでcommandは同じです。画像はrequest root配下に置き、requestの`images`へ安全な相対pathとroleを記述します。Design Packageを `design-verify`した後、その`terrain-intent.json`でgenerateします。OpenAIには相関用 `X-Client-Request-Id` を送りますが、曖昧なnetwork failure後のProvider側exactly-once／非課金を保証するものではありません。

## 方法D: Anthropic API

```bash
export ANTHROPIC_API_KEY='...'
./gradlew run --args="design anthropic <request.yml> <explicit-model-id> <designs-root> <jobs-root>"
```

OpenAIはResponses APIの`text.format`、AnthropicはMessages APIの`output_config.format`を使います。認証header、画像payload、usage fieldは異なりますが、どちらも同じTerrainIntent再検証、Design Package、generation pipelineへ合流します。Anthropic MessagesのProvider-side idempotencyは保証せず、成功済みDesign Packageを再利用してください。

## 方法E: Paper内workflow

```text
/lfc request create coast-01
/lfc request bounds selection coast-01
/lfc request prompt coast-01
/lfc request validate coast-01
/lfc design create coast-01 openai <model-id>
/lfc design import coast-01 inputs/terrain-intent.json
/lfc design status <job-id>
/lfc design verify <design-id>
/lfc generate <design-id>
/lfc job status <job-id>
/lfc job cancel <job-id>
/lfc candidate list <request-id>
/lfc candidate preview <candidate-id>
/lfc export plan <candidate-id>
/lfc export create <plan-id> <token>
/lfc export status <job-id>
/lfc export verify <request-id>/<release-id>
/lfc apply plan <request-id>/<release-id> <world> <x> <y> <z>
/lfc apply execute <placement-id> <token>
/lfc apply status <placement-id>
/lfc apply undo <placement-id>
/lfc undo execute <placement-id> <token>
/lfc apply recover diagnose <placement-id>
```

Paper Provider呼出し、画像decode、generation、artifact I/Oはメインスレッド外です。job IDは開始直後に返ります。prompt chat sessionは5分、一回用で、`cancel`を送ると保存しません。

## 方法F: 画像入力

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

directoryとZIPは同じ論理Releaseです。`checksums.sha256`は全file、`.zip.sha256`は外部ZIPを検査します。移動後、別serverでもCLI `verify`を実行してください。Minecraft versionは1.21.11固定で、対応外DataVersionを推測変換しません。WorldEditまたはFAWEはどちらか一方だけを使います。
