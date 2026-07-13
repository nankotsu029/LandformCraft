# 0007: 画像をrole付きの正規化境界でProviderへ渡す

- Status: Accepted
- Date: 2026-07-13

## Context

参考画像は大きさ、形式、metadata、path、内容のすべてが非信頼です。raw fileをProvider adapterへ直接渡すと、symlinkによるrequest root外読込、画像bomb、EXIF位置情報の送信、roleの曖昧な解釈、Providerごとの前処理差が生じます。一方、画像なしrequestと画像ありrequestを別pipelineにすると、manual importやfixtureだけ検証を迂回する危険があります。

## Decision

- `GenerationRequest.images`の`role`を必須とし、欠落時はSchemaでrequestを拒否する
- file読込はadmission制限付きI/O executor、decode／解析はbounded CPU executorで行う
- request root内の非symlink regular fileだけを許可し、PNG／JPEGのmagic、拡張子、byte、寸法、pixel、frame数をdecode前後で検査する
- EXIF orientationをpixelへ反映し、canonical ARGBへcopyしてalphaを保持し、metadataを継承せずPNGへ再符号化する
- Provider SPIへはdefensive copyを持つ`PreparedReferenceImage`だけを渡す
- roleごとの解釈をProvider promptに明示し、`TOP_DOWN_SKETCH`だけをnorth-upの`+X/EAST`、`+Z/SOUTH`へ正規化する
- 明示的な文章と上面図、複数上面図の強い海／陸矛盾はProvider呼出前に拒否する
- checksum、検証状態、Provider送信有無、provider／response／prompt version、変換履歴、座標対応、観測を`image-evidence.json`へ保存し、画像binaryはDesign Packageへ保存しない
- 画像なしrequestも空の証跡を作り、同じApplication Service、Provider SPI、publisherを通す

## Consequences

Provider adapterはfilesystemへ触れず、OpenAI／Anthropicで同じ検証済みcontentを使えます。raw metadataやabsolute pathを外部へ送らず、Design Packageから前処理を監査できます。再符号化のCPU／memory costが増えるためhard limitとbounded executorが必要です。edge colorによる矛盾検査は意味理解ではないため、確信できない場合は`INCONCLUSIVE`として続行し、強い反対だけを拒否します。

## Alternatives

- raw fileを各Provider adapterへ渡す案は、検証差と秘密漏えい面を増やすため不採用
- 画像全体をDesign Packageへ保存する案は、容量とprivacy負担を増やすため不採用
- roleを省略可能にしてAIへ推測させる案は、同じ画像を上面図と雰囲気画像で誤解するため不採用
