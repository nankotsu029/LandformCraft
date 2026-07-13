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

OpenAIの現行公式ガイドではResponses APIのStructured Outputsは`text.format`へJSON Schemaを指定し、画像入力は`input_image`としてdata URLを渡せます。LandformCraftはmodel IDを既定選択せず、利用者がCLIで明示します。選んだmodelが画像入力とStructured Outputsの両方に対応することは運用時に確認してください。

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
