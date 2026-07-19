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

### V2-1 numeric constraint map

V2-1のdirect constraint mapはoffline専用の別経路です。AI向け`ReferenceImageProcessor`のARGB／EXIF正規化を再利用せず、filesystemの安全原則だけを共有し、`SecureConstraintMapSourceLoader`と`NumericPngDecoder`で数値sampleを保持します。受理する入力は、宣言と一致するnon-interlaced grayscale PNGのU8またはU16、single frameだけです。RGB、alpha、palette、bit depth違い、APNG、未知critical chunk、chunk順／CRC不正、truncated／trailing data、拡張子とmagicの不一致を拒否します。ICC、EXIF、text、gamma、filenameを数値意味へ利用しません。

Requestが指定するbudgetは上限を狭めることだけができ、次のtrusted ceilingを拡張できません。

| 対象 | trusted ceiling |
|---|---:|
| map数 | 32 |
| source 1枚 | 8 MiB |
| source合計 | 32 MiB |
| 1辺 | 4096 pixel |
| 縦横比 | 32:1 |
| 1枚 | 4,000,000 pixel |
| Request合計 | 16,000,000 pixel |
| decoded sample 1枚 | 8 MiB |
| decoded sample合計 | 32 MiB |
| decoder working set | 32 MiB |
| canonical bundle artifact | 64 MiB |
| constraint stage resident | 96 MiB |

sourceは全件についてpath、symlink、regular file、file identity、size、合計byteをpreflightし、`retained total + largest source + 64 KiB read buffer`がresident admissionを通るまで1 byteも読みません。各fileは`NOFOLLOW_LINKS`でopenし、read前後のsize、mtime、file keyを比較します。同一Request内で同じfile identityを別pathから参照するhardlink aliasを拒否します。request root外で作られた単独hardlinkの所在をportableに検出できない点はPhase 5画像と同じ運用境界です。

decode前にheader寸法、aspect、pixel、sample byteを検査し、working setは保持済みsource、`MemoryCacheImageInputStream`のsource copy、ImageIO raster、compact sample copy、固定overheadを含めて admissionします。Request宣言のSHA-256、寸法、sample type、label辞書、no-data、height意味と一致しない入力はcanonical化しません。hard constraintのno-data、未知label、future encoding、checksum不一致、hard同士の競合も公開前に拒否します。

canonical fieldとindexはstaging内で全fileのartifact／semantic checksum、strict Schema、descriptor、label辞書、source Request／Intent checksumをread-backします。cancelの最終観測点はbundleのatomic move直前です。`ATOMIC_MOVE`成功をcommit pointとし、その後に届いたcancelを理由に公開済みbundleを削除しません。move前のcancel／failureではstagingだけを削除し、canonical targetを作りません。atomic moveを提供しないfilesystemでは失敗します。

### V2-2 offline tile schematic

V2-2 tile exportは既存v1 writer／Release verifierを変更せず、V2専用pathへ隔離する。targetはplan由来のcanonical filename、non-symbolic directory、新規fileだけを許可する。writerは全block Listを持たず、許可済みcoastal block stateのcount、辞書順palette、固定digestだけを保持する。二走査間でstateまたはsemantic checksumが変われば公開しない。palette 16384、Data 40 MiB、artifact 64 MiB、decompressed read-back 48 MiB、NBT depth 16、tag 20000をhard ceilingとする。

staging read-backはSponge version／DataVersion、dimension、zero offset、連続palette ID、canonical allowlist、VarInt entry数、block entity／entity／biome不在、semantic checksumを検証する。最後のcancel観測後の`.schem` atomic moveをcommit pointとし、cancel／failure／unknown state／truncated dataではtargetやtemporaryを残さない。WorldEdit adapterはmetadata pathをroot内へnormalizeし、symlink、byte長、artifact checksumを検査した同じimmutable bytesをbounded inspectorとWorldEdit 7.3.19へ渡す。Release 2／Paperへの信頼昇格はまだ行わない。

### V2-2-10 Release format 2 core

Release format 2 coreはv1 Release reader／writerを共有しない。manifestは256 KiB以下のnon-symbolic regular fileで、strict Schema、duplicate JSON key拒否、unknown property拒否、canonical exclude-self checksumを通る必要がある。directoryは最多257 regular file、case-insensitive duplicateなし、総量128 MiB以下、artifact単体64 MiB以下に制限し、`manifest.json`以外の全fileを`artifacts[]`へexactに対応させる。

ZIPはnon-symbolic regular `.zip`だけを受け付け、圧縮／展開とも128 MiB以下、最多257 entry、64 KiBのbounded copy bufferで処理する。directory entry、absolute path、backslash、`..`、duplicate／case collision、index外fileを拒否し、stagingへだけ展開してstrict directory verifierへ渡す。V2-2-10 core-only releaseは`requiredCapabilities[]`と`artifacts[]`が空でなければならない。未知capability、unknown type/versionは拒否する。

### V2-2-11 `surface-2_5d` payload

`surface-2_5d`は空coreと異なるstrict dispatchである。manifestの全non-manifest fileをindexし、field indexが列挙しない`.lfgrid`、preview indexが列挙しないPNG、tile metadataに対応しない`.schem`を拒否する。readerはrequest／intent／Blueprint、field、validation、preview、tileのschema versionとcanonical／semantic checksumを相互に照合し、hard validation error、tile overlap／hole、future capability/type/versionを拒否する。

publisherはraw source artifactをportable manifestへ記録せず、non-symbolic sourceをstagingへcopyし、staged directoryとZIPをstrict read-backしてからatomic moveする。copy中のsource fingerprint変化、publish前cancel／failureはtargetを公開しない。ZIP側でもdirectoryと同じsemantic verifierを再実行するため、manifestだけを書換えてchecksumを再計算してもfield／preview／tileの相互bindingを偽装できない。

publisherはstaging self-verify、disk budget確認、fsync、atomic moveを必須とし、non-atomic fallbackを使わない。cancelはpublish前・staging verify中・atomic move直前に観測し、commit前のcancel／failureではtargetを残さない。Release 2 apply application serviceとPaper／WorldEdit gatewayはV2-6-06、明示評価commandはV2-6-21で接続したが、production capabilityのsupport昇格とは分離する。`V2-6-02`のmutation／effect envelope契約（ADR 0021）だけをworld変更の権限として扱わず、V2-6-01〜05の全証拠とV2-6-06 strict gateを必須にする。

V2-6-20のverified canonical source（ADR 0033）は、source作成時にRelease 2 exact file set／capability prefix／directory・ZIP budget／semantic bindingを既存strict verifierで再検査する。ZIPは検証済みprivate stagingをsource lifecycle中だけ保持し、tile cursor開始時にも対象schematicのbyte lengthとSHA-256を再検査する。全Release blockの常駐、未検証ZIP entryの直接解釈、unknown block stateのfallback、close後のreopenを許さない。

V2-6-21のPaper lifecycle（ADR 0034）は、`releases-v2/`配下の安全な相対containerだけを受理し、plan時とapply直前にstrict verifyする。confirmationはactor／Release／target／envelope／reservation／operation／expiryへbindingし、平文tokenをjournalへ保存しない。全snapshotのstrict publishとcontainment evidenceなしにscheduler submissionへ進まず、post-commit failureは`RECOVERY_REQUIRED`＋逆順rollbackへ分類する。operation contextはstage commit markerを最後にfsync＋atomic publishし、全write pointのbefore／after failureをrestart-safe testで固定する。Undoは同placement leaseだけを安全に置換し、第三者overlapを拒否する。R2 commandはoperator Player／CONSOLE／RCON限定で、command block／非operator、actor mismatch、deny／missing worldをworld mutation前にstable domain errorで拒否し、tokenを補完しない。startup inspectionはartifactやworldを推測修復せず、manual interventionを成功へ昇格しない。`/lfc r2`をv1 commandと明示分離し、version fallbackを行わない。

### V2-6-02 mutation／effect envelope

envelope compilerはversion固定physics policy以外の半径を受理せず、unknown physics class、world border／Y overflow、effect volume／disk budget超過、tile order mismatch、under-approximationをfallbackせず拒否する。raw pathやsecretはartifactへ保存しない。

### V2-6-05 fluid／gravity／neighbor containment

containment preflightは`SNAPSHOT_COMPLETE`後・最初のapply前にだけ実行する。閉じた`PlacementBlockPhysicsCatalogV2`以外のblock state、UNSUPPORTED denylist、envelope外へ逃げ得るfluid／gravity／neighbor、physics-class understatement、envelope gap、unknown policy／catalog versionをhard rejectし、`CONTAINED` evidenceだけをsealする。snapshot外検出や事後rollbackをcontainmentの代替にしない。apply権限やworld mutationはまだ付与しない。

### V2-6-06 canonical apply transaction

apply前にRelease directory、unbound→envelope-bound→confirmed plan checksum chain、reservation ownership、consumed confirmation、published snapshot、containment evidence、source manifest／capability／fingerprintを再検証する。canonical sourceはmutation AABBをexact coverageし、閉じたphysics catalogとeffect-envelope宣言にないstateを拒否する。executor、queue、block数、overlay数、scheduler sliceはallocation／dispatch前に上限検査する。journalはschema-valid canonical JSONをstagingへ書き、strict read-back、fsync、必須atomic move、directory fsync、published read-back後だけ更新する。scheduler submissionを試みた後はworld mutationの有無を楽観推測せず、失敗を`RECOVERY_REQUIRED`へ分類する。observer timeout／cancelをscheduler operationのcancel証拠にせず、late completionを必ずreconcileする。

### V2-6-12 cross-capability hardening

Release format 2 の valid capability prefix は`ReleaseCapabilityDependencyMatrixV2`（ADR 0030）だけが正本であり、不完全依存・unknown capability・future format／manifest version を silent repair／forward-read しない。`ReleasePlacementEligibilityVerifierV2`は directory／ZIP 双方で strict verify＋sealed checksum＋valid prefix を placement 前に要求する。missing／extra／duplicate／version／checksum／path traversal／case collision／ZIP bomb／entry・expand budget 超過を拒否する。artifact limits は`ReleaseArtifactLimitsCatalogV2`の trusted ceiling を超えない。Release 1 allowlist（`checksums.sha256` 経路）とは共有せず緩めない。foundation／bathymetry は Feature 別 placement type を追加せず、既存 canonical overlay stream への bind だけを許す。

### V2-6-13 operational metrics／diagnostics／retention

運用監査は`operations-audit-v2.jsonl`へbounded eventだけを追記する（ADR 0031）。absolute path、Authorization、API key、raw payloadをaudit／diagnostics findingへ保存しない。metrics labelはclosed enumに限定し、自由文字列cardinalityを拒否する。Release 2 retention cleanupはactor-bound dry-run／confirmであり、startup自動削除defaultはない。

### V2-3-14 `hydrology-plan` payload

`hydrology-plan`は`surface-2_5d`を必須依存とする別dispatchである。`requiredCapabilities`からsurfaceを外すこと、plan／routing／reconciliation／validation／previewの欠損、routing graph checksum改変、未知type/version、index外fileを拒否する。surface単独Releaseのstrict verifyは維持する。このoffline capabilityをworld変更の権限として扱わない。

### V2-3-02 hydrology routing bundle

Hydrology routingはtrustedなBlueprintから導出したprovisional surfaceでも、範囲、overflow、blocked cell、outlet、resource budgetを再検査する。outletを地形からfallback推測せず、`HARD` outletがblocked、またはroutable componentに宣言outletがない場合は公開前に失敗する。1000×1000を上限に、CPU／working／retained／field artifactをpreflightし、全域working setはdense voxelではなく2D primitive fieldだけに限定する。bounded executorは最大8 worker、queue最大worker数で、cancel／interrupt時に所有executorをshutdownする。

routing bundleのindexは512 KiB以下で、`index.json`と2個の`LFC_GRID_V1`以外のfile／directory、symlinkを拒否する。strict readerはartifact／semantic／graph／routing checksumだけでなく、未知D8 code、global bounds外direction、downstream accumulation非増加、未宣言terminal、basin coverage不一致もrow windowで検査する。publisherはsibling stagingをself-verifyし、atomic move直前を最後のcancel観測点とする。standalone bundleだけではRelease capabilityやworld mutationの権限を与えない。同じstrict形式をV2-3-15でoffline `SUPPORTED`とした`hydrology-plan` capabilityへ収容する場合も、surface依存とcomplete artifact setの検証を省略しない。

V2-4-01 geology bundleは4個のU16 `LFC_GRID_V1`以外のfile、symlink、未知／重複semantic、plan checksumと異なるprovenance、future encoding、artifact／semantic checksum改変を拒否する。cellは4 fieldすべてno-data、または既知province codeに対応するformation／hardness／permeabilityの完全一致だけを受理する。window寸法と4個の`int[]`、writer 64 KiB strict read-back、retained descriptor、CPU、単体／合計artifact byteをallocation前にbudget検査する。stagingの全域read-backと最後のcancel check後だけatomic publishし、standalone bundleをRelease capabilityやfeature supportの根拠にしない。

V2-4-02 lithology catalogはcompile-time built-inのclosed listだけを受理する。unknown／future ID、compact code範囲外、任意class、external preset、path、catalog／plan checksum不一致、source geology checksumまたはprovince／formation／scalar不一致はstrictに拒否する。catalog canonical bytesとplan canonical bytesはbudgetで上限化し、既存sidecarのreaderはbounded windowだけでassignmentを検査する。

V2-4-04 climate planはclosed built-in preset、integer-only kernel、4 fieldのphase／single owner、source `HydrologyPlan`／fixed prior／generator versionとreplacement prior checksumの完全一致だけを受理する。preset欠落、unknown／future contract、version／checksum mismatchをfallbackせず拒否し、1000角でもcoarse gridとbounded windowのCPU／retained／working／canonical byteをallocation前に検査する。descriptor-only fieldをRelease capabilityやworld mutationの権限として扱わない。

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
