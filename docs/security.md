# セキュリティ

## 保護対象

- OpenAI／Anthropic APIキーと課金quota
- ユーザーのprompt、参考画像、生成物
- Minecraft worldとsnapshot
- サーバーfilesystem
- job履歴、監査log、provider usage

## Secret管理

優先順位:

1. OS環境変数
2. Docker／Kubernetes等のSecret
3. Minecraftサーバー外の権限制限ファイル
4. 最終手段としてplugin data内の権限制限されたsecret file

既定の環境変数名:

```text
OPENAI_API_KEY
ANTHROPIC_API_KEY
```

禁止事項:

- Gitへのcommit
- `request.yml`、Intent、Blueprint、manifest、snapshotへの保存
- Minecraft chat／command argumentへの入力
- Authorization headerやquery parameterのlog出力
- exception objectを無加工で監査logへserialize

## AIへ送るデータ

- requestに添付されたpromptと許可済み画像だけを送る
- サーバーdirectory、plugin config、log、他ユーザーデータを自動添付しない
- image role、MIME type、pixel count、byte sizeを検証する
- EXIF orientationをpixelへ反映後、元metadataを引き継がずPNGへ再符号化する
- provider、model、送信時刻、usage、response IDは記録できるがsecretを除く
- 保存期間はconfigで管理し、明示的なdry-run plan、actor-bound token、実行時identity再検査を通るcleanupだけを許可する。RECOVERY_REQUIREDとjournalなしsnapshotは自動削除しない

画像やprompt内の命令は非信頼データです。「schemaを無視」「キーを表示」「コードを実行」などのprompt injectionに従わず、Provider出力をschemaとsemantic validatorで制限します。

## File入力

- request rootからの相対pathだけを許可する
- path normalization後にroot内であることを確認する
- symbolic link、device file、FIFOを拒否する
- allowlistされた画像形式とmagic bytesを検証する
- byte size、pixel数、解凍後size、枚数へ上限を設ける
- ZIP slip、ZIP bomb、重複entry、大文字小文字collisionを拒否する
- JSON/YAMLはdepth、文字数、collection数、unknown fieldを制限する
- `.schem`も非信頼入力としてNBT depth、palette、bounds、block countを検査する

Phase 2 verifierはNBTを最大depth 16、tag 20,000、palette 4,096、展開48 MiBに制限し、schematic寸法とmanifestを照合します。ZIPは2,100 entry、展開2 GiBまでとし、重複entry、大小文字collision、absolute／backslash／`..` pathを拒否します。

Phase 6のbuilt-in catalogはMinecraft 1.21.11へ固定します。custom importはSponge v3、DataVersion 4671、寸法／block count／NBT上限、vanilla palette allowlist、slug ID、anchor／rotation metadataを満たすものだけをatomic catalogへ保存し、entity、block entity、biome、symlink、built-in ID衝突を拒否します。Release読込時にasset semantic SHA-256、standalone schematic artifact SHA-256、type、version、寸法、placement bounds／重複を再検証し、未知assetやcatalogの改変を拒否します。TerrainIntent v1からcustom IDを選んで生成へ組み込む経路は未実装です。

Phase 5画像readerはPNG／JPEGだけを許可し、拡張子とmagicを一致させます。request rootと各path segmentのsymlink、非regular file、重複path、同一file keyのalias、multi-frame、decode不能を拒否します。fileは`NOFOLLOW_LINKS`でopenし、読込前後のsize／更新時刻／file keyも比較します。hard limitは1枚8 MiB／source合計32 MiB、1枚4,000,000 pixel／合計16,000,000 pixel、各辺4096、縦横比32:1、正規化PNG 1枚16 MiB／Provider送信合計16 MiBです。headerの寸法検査をfull decodeより先に行い、decode／edge解析はbounded CPU executorでcancelを確認します。

Provider payloadはraw fileではなく、向きを補正してcanonical ARGBへcopyしmetadataを除去した正規化PNGのin-memory defensive copyだけを含めます。alphaは保持します。roleは必須で、roleなしrequestをSchemaで拒否します。Provider向けrole説明には元の相対pathも含めません。監査はchecksum、寸法、role、検証状態、Provider送信有無、変換履歴、上面図観測だけを`image-evidence.json`へ保存し、画像binaryとabsolute pathを保存しません。

同じrequest内のhard link aliasはfile keyで拒否します。request root外で事前に作られたhard linkを単独で渡した場合に元inodeの所在をportableに判定することはできないため、request rootは信頼された管理領域とし、一般ユーザーが任意のhard linkを作成できない権限で運用します。

## World保護

- default permissionはoperatorのみ
- help／version／doctor、request、design、generate、job、candidate、export、selection、apply plan／execute／status、undo plan／execute、recovery、asset read／manage、cleanupを個別permissionへ分割する
- applyはRelease verify、target bounds、world UUID／name、world border、heightを事前検査する
- preview／dry-run／明示confirmなしで変更しない
- snapshot作成失敗時は配置しない
- tileごとの適用記録と逆順rollbackを永続化する
- WorldEdit／FAWE sessionを必ずcloseし、完了確認前にsnapshotを削除しない

placementはworld UUID＋inclusive boundsをstrict atomic stateへ予約し、別IDのoverlapをplan時とexecute直前に拒否します。snapshotはRelease working space、全tile worst case、journal、temporary、rollback、safety marginを見積もり、実FileStoreのusable bytesから他placement予約を差し引きます。確認tokenは10分期限の一回用hashとして保存し、operation、plan／Release checksum、world、origin／bounds、Player UUID／CONSOLE actor、時刻、nonceへbindingします。

LandformCraftのdata artifactはtoken hashだけを保存します。一方、PaperのCONSOLE senderへ表示したcommand候補は標準server logへも出力されます。今回のWorldEdit smokeでこの平文記録を実確認したため、CONSOLE tokenの非永続化は未達です。Player向けclick候補を自動実行しないことだけではこのCONSOLE問題を解決しません。

execute／undoのworld mutation Futureはcallerからcancelできず、plugin disableは先に新規mutation受理を停止します。停止budgetを超えた中断はjournalの`RECOVERY_REQUIRED`で明示し、完了を推測して自動再実行しません。

## Availabilityと課金

次は最終運用上の目標です。Phase 4で実装済みの範囲は後段に分けて記載します。

- provider timeout、retry上限、exponential backoff＋jitter
- request単位・ユーザー単位・server単位のquota
- candidates、画像、boundsにhard limit
- retryで同一課金処理を重複させないidempotency／response記録
- circuit breakerと手動JSON importへのfallback
- CPU、memory、disk、job queueへbudgetを設定

Phase 4ではHTTPをadmission制限付きVirtual Threadで実行し、60秒timeout、最大3 attempts、429／5xxのexponential backoff、`Retry-After`、Future cancelからinterruptへの伝播を実装しています。process内のRPMと累積token budget、requestごとの最大output tokenも強制します。価格をコードへ固定せずusage tokenを監査し、通貨上限はprovider側billing limitで補完します。API responseのerror message本文は監査へ保存せず、statusとallowlistされたerror typeだけをdomain errorへ変換します。

ユーザー別の永続quota、通貨budget、circuit breakerは非対応です。process内quotaとProvider billing limitを併用し、manual JSON importをAPI障害時の明示的fallbackとして利用します。OpenAI client request IDは相関用で、Provider側exactly-onceを保証しません。

JSON request bodyは24 MiB、HTTP response bodyは4 MiBをhard limitとします。responseは`InputStream`から上限+1 byteまで読み、過大応答を全量保持せず拒否します。429／5xxの再試行は同じ論理requestですが、Provider側のidempotency keyはまだ付与していないため、結果不明時の重複課金防止はProvider監査と運用上の課金上限で補完します。

Structured Outputへ送るSchemaはprovider共通subsetへ簡略化しますが、応答は必ず完全なローカルSchemaとrecord不変条件で再検証します。したがってproviderがpattern、minimum、maximum等を強制できない場合でも、範囲外IntentはDesign Package公開前に拒否されます。

## Logging

記録するもの:

- job／request ID、stage、duration、結果
- provider／model、usage、response ID
- validation issue、artifact checksum、placement ID
- 操作者UUID、confirm、undo結果

記録しないもの:

- APIキー、header、Cookie
- prompt／画像の全文（明示した監査mode以外）
- server filesystemの不必要なabsolute path
- snapshotのblock内容

redactionはlog出力直前だけでなく、exceptionをdomain errorへ変換するadapter境界でも行います。

## Dependency方針

- Gradle dependencyをversion固定し、意図せずlatestへ追従しない
- Paper、WorldEdit、FAWEは公式repository／releaseを使う
- WorldEdit／FAWEを同時導入しない
- WorldEdit／Paperをshaded plugin JARへ含めない
- 定期的に脆弱性と対応Minecraft versionを確認する
