# AI Provider連携

Phase 4／5では、OpenAI Responses API、Anthropic Messages API、manual JSON import、fixtureを同じ`TerrainDesignProvider`境界へ接続します。Providerは`TerrainIntent`だけを返し、地形生成、schematic出力、world操作は行いません。

## 共通契約

- Providerへ渡すのはprompt、Provider向けsubset JSON Schema、role説明、正規化済み画像bytesだけ
- 返却JSONはProvider側のStructured Outputだけを信用せず、完全なローカルSchemaとJava record不変条件で再検証
- request timeout 60秒、最大3 attempts、429／5xxだけを`Retry-After`または指数backoffで再試行
- JSON requestは24 MiB、responseは4 MiB、正規化画像合計は16 MiBに制限
- process内20 RPM、累積100,000 tokens、requestごと最大4096 output tokensを既定guardにする
- APIキー、Authorization header、prompt本文、画像binary、Provider error本文はDesign Packageへ保存しない
- provider／model／prompt version／response ID／usage／attemptsは`audit.json`へ保存
- v2 design packageの`audit-v2.json`は任意で`supportLint`（V2-19-08のreport-only design lint）も保存する。移行bundleのauditは持たない
- 画像の正規化version、checksum、送信有無、provider／response／prompt versionは`image-evidence.json`へ保存

ローカルguardはProvider側の課金上限の代替ではありません。OpenAIへjob／request fingerprintから決定した`X-Client-Request-Id`を送り、Design auditへjob ID、request checksum、provider、model、prompt version、attempt、response IDを保存します。作成済みDesign Packageは後続generateで再利用できますが、新しいdesign jobはProviderを再呼出しします。成功応答のdurable cacheはまだありません。Anthropic Messagesを含め、response生成後・client受信前のnetwork断に対するProvider側exactly-once／非課金は保証しません。token budgetはprocess再起動を跨がず、通貨換算とcircuit breakerも対象外です。

## OpenAI Responses API

現在のadapterはResponses APIへ次を送ります。

- `POST /v1/responses`
- `Authorization: Bearer ...`
- `input`内の`input_text`と、画像がある場合のdata URL形式`input_image`
- `text.format.type = json_schema`、`strict = true`、subset `schema`
- `store = false`
- `X-Client-Request-Id`（相関用。exactly-once保証ではない）

OpenAIの現行公式ガイドではResponses APIのStructured Outputsは`text.format`へJSON Schemaを指定し、画像入力は`input_image`としてdata URLを渡せます。ローカルSchemaの`const`はProvider送信時だけ、対応subsetで等価な1要素`enum`へ変換し、型のない`enum`には値から推論できる`type`を付けます。対応外の制約はSchemaノードからだけ除去し、`minimum`や`maximum`という同名のデータプロパティは保持します。完全なローカルSchemaは変更せず、応答時に再検証します。LandformCraftはmodel IDを既定選択せず、利用者がCLIで明示します。選んだmodelが画像入力とStructured Outputsの両方に対応することは運用時に確認してください。

HTTP 4xx／5xxではProviderのerror本文やpromptを表示せず、英数字からなる`type`、`code`、`param`だけを診断情報として表示します。たとえば`code=invalid_json_schema; param=text.format.schema`なら送信Schemaの互換性エラーです。

JSON Schemaでは`zones[].areaShare`の配列全体の合計を制約できないため、promptでも合計1.0以下を明示します。それでもProviderが、各値は0より大きく1以下なのに合計だけが1.0を超える応答を返した場合は、相対比を保って合計1.0未満へ決定論的に正規化してから完全SchemaとJava recordで再検証します。0、負数、1超過の個別値や、その他のSchema／意味制約違反は修復せず拒否します。manual import／fixtureにはこのProvider限定正規化を適用しません。

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Images and vision](https://developers.openai.com/api/docs/guides/images-vision)

## Anthropic Messages API

現在のadapterはMessages APIへ次を送ります。

- `POST /v1/messages`
- `x-api-key`と`anthropic-version`
- `messages[].content`内のtextと、画像がある場合のbase64 image source
- `output_config.format.type = json_schema`とsubset `schema`

Anthropicの現行公式ガイドではStructured Outputsを`output_config.format`へ指定します。対応modelは変わり得るため、LandformCraftはmodel IDを固定せず利用者に明示させます。

- [Claude Structured Outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)
- [Claude vision](https://docs.anthropic.com/en/docs/build-with-claude/vision)

## CLI

```bash
./gradlew run --args="design openai <request.yml> <model-id> <designs-root> <jobs-root>"
./gradlew run --args="design anthropic <request.yml> <model-id> <designs-root> <jobs-root>"
./gradlew run --args="design import <request.yml> <intent.json> <designs-root> <jobs-root>"
./gradlew run --args="design fixture <request.yml> <intent.json> <designs-root> <jobs-root>"
```

APIキーは`OPENAI_API_KEY`／`ANTHROPIC_API_KEY`環境変数だけから読みます。CLI引数、request、Minecraft chat、manifestには書きません。

## 検証範囲

自動testはlocal fake HTTP serverを使用し、両Providerのrequest shape、画像payload、response mapping、429／5xx retry、4xx非retry、timeout、cancel、過大response、秘密・raw path非送信を検証します。OpenAI fakeは画像付きresponseからgenerator／8 previewまでを通します。Anthropic fakeをDesign Package、generator、Releaseまで通す単一E2Eはrelease checklist上の未完了項目です。実APIへの有料live requestはsecretと課金を必要とするため通常のbuildでは実行せず、live互換性は`IMPLEMENTED_BUT_UNTESTED`として監査します。

Paperは`/lfc design create`から同じadapterを呼びます。`config.yml`はenabled、API key環境変数名、default model、timeout／retry／RPM／token policyだけを持ち、key値は保存しません。

## TerrainIntent v2（V2-6-11）

Release 2／TerrainIntent v2 向けには、v1 SPI と並設した `ai.spi.v2` と `TerrainDesignApplicationServiceV2`（`isRelease2Path()`）を使います。既定の CLI／Paper `design` は **v1 のまま** で、v2 は intent contract version と capability を明示した dispatch だけが受理します。

- Capability: `TERRAIN_INTENT_V2_STRUCTURED`／`MANUAL_CONSTRAINT_BUNDLE`／`REFERENCE_IMAGE_SOFT_DRAFT`
- Path: OpenAI／Anthropic／import／fixture／manual constraint／reference-image soft draft
- Negotiation: `ProviderCapabilityCatalogV2`＋`DesignCapabilityNegotiatorV2`。未知 version・未宣言 model・capability mismatch は hard reject し、v1 へ fallback しない（ADR 0029）
- Provider adapter は TerrainIntent v2 Schema の subset を送り、応答は `LandformV2DataCodec` で完全再検証する
- Design Package v2: `terrain-intent-v2.json`／`audit-v2.json`／任意 `image-draft-evidence-v2.json`／`checksums.sha256`
- Request の `referenceImages` は `ReferenceImagePreparationServiceV2` が request 相対 path から準備し、provider へ渡す（V2-19-03）。共有の secure ファイル封筒（`SecureImageExtractionEnvelopeV2`）で resolve／symlink・hard link 拒否／magic と拡張子の一致／single frame／byte・寸法・aspect・pixel・decode 予算を検査し、EXIF orientation を適用したうえで**sanitized raster から PNG を再符号化**する。したがって EXIF・XMP・comment 等の source metadata と filesystem path は provider payload にもログにも出ない。任意の `expectedSha256` を宣言すると、digest 不一致の差し替え画像は provider 呼出し前に拒否される
- Provider 呼出し前に、現在の production dispatch registry から投影した **reachable kind／capability 集合**（`design-support-lint-v1`）を prompt の独立 section として渡す（V2-19-08）。集合の出所は dispatch registry であり Feature Support Catalog ではない（`export: SUPPORTED` は公開 dispatch 到達性と同義ではない）。これは助言であって制約ではなく、historic 60-kind Schema は狭めない。基盤 guard contract の `promptVersion`（`terrain-intent-v2-structured-guards`）は不変で、advisory は `design-support-lint-v1` と reachability projection checksum（design audit が記録する）で一意に識別できる
- Provider 応答後、publish 前に **dispatch dry-run** を行い、宣言 kind ごとの到達性（route 無し／contract-only／plan-only）と intent 全体の選択可否、pipeline runtime が要求する companion kind の不足を `audit-v2.json` の任意 `supportLint` へ記録する。全 finding は `NON_GATING` で、未到達 kind を宣言した design も従来どおり publish される（fail-closed 化は別途人間承認）。CLI／Paper の `design` サマリは同じ内容を表示する
- reference image soft draft（`REFERENCE_IMAGE_DRAFT` path）は SOFT 提案だけで、HARD `mapReferences` への暗黙昇格は禁止（ADR 0017）。この path 自体は pixel 入力を直接受け取る API であり、CLI／Paper の `design` verb には未公開（公開 path は import／fixture／openai／anthropic の 4 種）
- 実 API credential は通常 test に不要。local fake HTTP の contract test で閉じる

同一 canonical `TerrainIntentV2` なら provider 差は後続 generation checksum に影響しません。
