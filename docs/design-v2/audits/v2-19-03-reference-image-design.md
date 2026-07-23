# V2-19-03 Reference-image public design repair（BROKEN_PUBLIC解消）

> Status: **PASS（2026-07-23）**。[横断監査 rev.2](../../audits/cross-cutting-audit-2026-07-23.md) §2.6が確定した「reference imageを1枚でも宣言したrequestの公開designはprovider呼出し前に必ず失敗する」欠陥を解消し、宣言画像をsecure envelope経由で準備してproviderへ渡す経路を実CLI／Paper E2Eまで通した。**画像からHARD geometryを起こす経路は追加していない**（ADR 0017の禁止は不変）。terrain field／tile／blockのsemantic checksum、Release format、v1資産はいずれも不変。

## 1. 欠陥（修正前）

`TerrainDesignApplicationServiceV2.buildProviderRequest`はpathに関係なく`List.of()`を`TerrainDesignRequestV2`へ渡し、後者は`generationRequest.referenceImages().size() != images.size()`を例外にする。修正後に`buildProviderRequest`を旧形（`List.of()`）へ一時的に戻して実測したところ、本Taskで追加した`aRequestDeclaringReferenceImagesReachesTheProvider`と実CLIの`designWithReferenceImagesPublishesAPackageFromTheCli`はいずれも`every declared reference image must have exactly one verified image handle`で失敗した（旧挙動の再現。probeは即座に戻した）。したがって`referenceImages`が非空のrequestは、import／fixture／openai／anthropicの**全4 path**でprovider到達前に失敗した。`PreparedReferenceImageV2`をmain sourceで構築するcallerは存在せず、provider adapter側の画像payload実装（base64・role text・path非送信検査）は到達不能だった。あわせて4画像を宣言する`oblique-multi-view.request-v2.json`の実画像は1つもrepositoryに存在せず、Schema loadを超える証拠を持たなかった。

## 2. 修正

### 2.1 `ReferenceImagePreparationServiceV2`（`core.v2.design`）

宣言画像をrequestのcanonical順（`referenceImages`はid昇順で正規化される）に1件ずつ準備する。専用decoderは作らず、既存の共有部品`SecureImageExtractionEnvelopeV2`を再利用する。

| 段階 | 内容 |
|---|---|
| resolve | request親directory配下へのrelative resolve、symlink・hard link alias・path traversal・重複path拒否 |
| admission | source byte／総byte／寸法／aspect／pixel／decode working setの各予算、magicと拡張子の一致、single frame（APNG拒否含む）、TOCTOU再stat |
| digest | requestが任意の`expectedSha256`を宣言していれば、envelopeが計算したsource digestと照合し不一致を拒否 |
| sanitize | EXIF orientationを適用したARGB rasterを取得（sourceのmetadataは値として持ち込まない） |
| re-encode | **rasterからPNGを再符号化**し、それを提出bytesとする。EXIF／XMP／comment等は構造的に残らない |
| budget | 提出bytesの per-image 16MiB と合計16MiB（`TerrainDesignRequestV2.MAX_TOTAL_IMAGE_BYTES`）を、record構築前に安定failure codeで判定 |
| cancel | 各画像の前後でcancellation tokenを確認。準備物はメモリ内のみで、拒否・cancel時にartifactは1つも生成されない |

`PreparedReferenceImageV2.checksum`は**提出bytes**のSHA-256（content address）であり、source fileのdigestとは別物である。source digestは`expectedSha256`照合にだけ使う。failure codeの対応は`UNSAFE_PATH`／`HARD_LINK_ALIAS`／`INVALID_PATH_DESCRIPTOR`→`PATH_SECURITY`、各budget系→`BUDGET_EXCEEDED`、`MISSING_FILE`／`SOURCE_CHANGED`／`UNSUPPORTED_FORMAT`／`INVALID_MAGIC`／`CORRUPT_IMAGE`／`MULTI_FRAME`→`INVALID_REQUEST`である。

### 2.2 orchestrator配線

`buildProviderRequest`が上記serviceを呼ぶ。画像を宣言しないrequestは従来どおり空listのままで、挙動もbyteも変わらない。準備はprovider種別に依存せず全pathで走るため、offlineのfixture／import runも同じ宣言入力を検証する。

### 2.3 Request契約: 任意の`expectedSha256`

`generation-request-v2.schema.json`の`referenceImage`へ任意の`expectedSha256`（既存`$defs/checksum`）を追加し、`GenerationRequestV2.ReferenceImageSource`と`LandformV2DataCodec`を対応させた。**absent時は書き出さない**ため、既存requestのcanonical byteとchecksumは不変である。constraint mapの`expectedSha256`と同じ意味・同じ命名で、authoring後に差し替えられた画像がproviderへ届くことを防ぐ。

### 2.4 CLI failure分類

`DesignExceptionV2`はCLIの`reportFailure`でどの分岐にも当たらず`LFC-INTERNAL`＋「Operation failed safely.」になっていた。正常な利用者入力（存在しない画像、digest不一致、予算超過）が内部エラーとして出るのは誤りなので、`PROVIDER_TRANSPORT`／`INVALID_RESPONSE`→`LFC-PROVIDER-FAILED`、その他→`LFC-REQUEST-INVALID`へ分類し、safe messageは`Design failed safely (<code>).`とした（安定codeのみ。message本文・path・payloadは出さない）。

### 2.5 examples

- `examples/v2/diagnostic/references/{cove-east,cove-north,mood,oblique-ridge}.png`（各64×64）を新規追加した。整数演算だけで生成した合成画像で、`ReferenceImageExampleFixtureV2`が内容の正本、`ReferenceImageExampleFixtureV2Test`が committed PNGのdecode結果と一致することを検査する（byte比較ではなくpixel比較なのでPNG encoder差に依存しない）。
- `oblique-multi-view.request-v2.json`は`mood`にだけ`expectedSha256`を宣言し、optional fieldの両分岐をexampleとして持つ。
- design E2E用に`examples/v2/diagnostic/oblique-multi-view.terrain-intent-v2.json`（`intentId: oblique-multi-view-64`、`mapReferences: []`）を追加した。

## 3. 検証記録（2026-07-23）

| test | 内容 |
|---|---|
| `ReferenceImagePreparationServiceV2Test`（8） | example 4画像のrequest順・role・path・寸法・checksum＝提出bytes・**pixel一致**、画像なしrequestは空list、JPEG→PNG正規化＋EXIF orientation 6の寸法入替、tEXt metadataのpayload非混入（fixtureが実際にsecretを含むことも検査）、`expectedSha256`のpositive／差し替え拒否、missing／magic spoof／拡張子不一致／symlinkの安定code、tightened limitでのbudget拒否、cancel、繰り返し・locale（tr-TR）・timezone（Pacific/Chatham）決定性 |
| `TerrainDesignApplicationServiceV2Test`（新規2） | 画像宣言requestがfixture pathで公開成功（**修正前は必ず失敗**）、Anthropic stub serverへのhandoffでbase64 PNG 4枚・role text・path非送信・metadata非送信を実測 |
| `LandformCraftCliV2Test`（新規2） | 実CLI `v2 design fixture` が画像付きrequestからDesign Packageを公開、画像欠落時は`LFC-REQUEST-INVALID`＋`Design failed safely (INVALID_REQUEST).`でexit 1、package非生成、絶対path非出力 |
| `PaperV2ReferenceImageDesignV2Test`（新規2） | Paper adapter（`PaperV2WorkflowServiceV2`、`JavaPlugin`非生成）でplugin workspace内requestから公開成功、workspace外へのsymlink画像は`PATH_SECURITY`で拒否しpackage非生成 |
| `ReferenceImageExampleFixtureV2Test`（2＋手動helper） | committed example画像の内容と宣言digestの一致 |
| suite | `./gradlew test` PASS、`./gradlew build` PASS |

## 4. Scope外／不変

- `MANUAL_CONSTRAINT`／`REFERENCE_IMAGE_DRAFT`のCLI／Paper公開（`designPathKinds`は4種のまま）。soft draft pathはpixel入力を直接受けるAPIで、公開verbの追加は本Taskの目的（宣言画像のprovider handoff修理）とは別の機能である。
- constraint source→Intent bindingのauthoring（`V2-19-04`）。reference imageは依然としてSOFT cue専用で、`mapReferences`を生成しない。
- 画像evidence artifactのDesign Packageへの追加（`image-draft-evidence-v2.json`はsoft draft path専用のまま）。
- terrain field／tile／block semantic checksum、Release format／capability、v1 golden、placement経路は一切変更していない。requestのcanonical byteが変わるのは`expectedSha256`を新たに宣言したexample 1件だけである。
