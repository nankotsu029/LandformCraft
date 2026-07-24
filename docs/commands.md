# コマンドリファレンス

## v2 production command（V2-12-06）

V2-12-06でv1 production commandと暫定aliasを削除し、v2を唯一のproduction commandにしました。

* Paper: `/lfc <verb>` は恒久的な明示形 `/lfc v2 <verb>` と同じです。
* CLI: `lfc <verb>` は恒久的な明示形 `lfc v2 <verb>` と同じです。
* `/lfc v2 <verb>`（Paper）／`lfc v2 <verb>`（CLI）は明示形として恒久的に使えます。

version中立の維持verbは version を問わず使えます: `help`／`version`／`doctor`／`ops`（Paper）／`asset`／`selection`（Paper）。`asset` は v2 等価物が無いため既定面に維持されます（ADR 0035 D10 / K3）。


`git tag` が存在しないため、D5 deprecation window条件1の代替判定「v1経路にしか存在しない利用可能機能はゼロ」を [再監査](design-v2/audits/v2-12-05-v1-to-v2-coverage-audit-rerun.md) で確認済みです。

inputは`plugins/LandformCraft/data/releases-v2/`（配置）および`plugins/LandformCraft/data/v2/`（offline verb）からの安全な相対pathです。絶対path、`..`、backslash、symlinkは拒否されます。

### Paper

```text
/lfc v2 request validate|info <generation-request-v2.json>
/lfc v2 request list
/lfc v2 request create <request-id>
/lfc v2 request bounds <request-id> <width> <length> <min-y> <max-y> <water-level>
/lfc v2 request selection <request-id>
/lfc v2 request constraint-map <request-id> <source-slug> <file> <sha256> <width> <length>
/lfc v2 request prompt <request-id>
/lfc v2 request constraint-source <request-id> <source-slug> <land-water|height-guide|zone-label> <promotion-dir> <request-relative-file>
/lfc v2 design <import|fixture|openai|anthropic> <request-v2.json> <intent-or-model> [designs-root]
/lfc v2 generate <request> <intent> <exports-root> <release-id> <land|water> <land-y> <water-y>
/lfc v2 export   <request> <intent> <exports-root> <release-id> <land|water> <land-y> <water-y>
/lfc v2 preview  <release-directory-or-zip>

/lfc v2 export plan   <request> <intent> <exports-root> <release-id> <land|water> <land-y> <water-y>
/lfc v2 export create <plan-id> <token>
/lfc v2 job status|cancel <job-id>
/lfc v2 job list
/lfc v2 candidate list <request-id>
/lfc v2 candidate info <job-id>

/lfc v2 place plan <release-path> <world> <x> <y> <z>
/lfc v2 place confirm <placement-id> <token>
/lfc v2 place execute <placement-id>
/lfc v2 status <placement-id>

/lfc v2 undo plan <placement-id>
/lfc v2 undo execute <placement-id> <token>

/lfc v2 recover diagnose <placement-id>
/lfc v2 recover plan <rollback|accept> <placement-id>
/lfc v2 recover execute <rollback|accept> <placement-id> <token>

/lfc v2 retention plan <placement-id>
/lfc v2 retention execute <placement-id> <plan-id> <token>
/lfc v2 retention status <placement-id>
```

### CLI

```text
lfc v2 request validate|info <generation-request-v2.json>
lfc v2 request list
lfc v2 request create <request-id>
lfc v2 request bounds <request-id> <width> <length> <min-y> <max-y> <water-level>
lfc v2 request constraint-map <request-id> <source-slug> <file> <sha256> <width> <length>
lfc v2 request constraint-source <request-id> <source-slug> <land-water|height-guide|zone-label> <promotion-dir> <request-relative-file>
lfc v2 request generation <request-id> <global-seed> <tile-size>
lfc v2 request foundation-base-levels <request-id> <land-surface-y> <water-bed-y>
lfc v2 request foundation-detail <request-id> <land-amplitude> <water-amplitude> <wavelength> <octaves>
lfc v2 request mask-reconcile <request-id> <tolerance-blocks>
lfc v2 request prompt <request-id> <prompt...>
lfc v2 intent bind <request-v2.json> <intent-in> <intent-out> <source-slug> <land-water|height-guide|zone-label> <hard|soft> <nearest|bilinear-fixed> <tolerance-blocks> [weight-millionths]
lfc v2 intent bindings <request-v2.json> <terrain-intent-v2.json>
lfc v2 design <import|fixture|openai|anthropic> <request-v2.json> <intent-or-model> [designs-root]
lfc v2 generate <request> <intent> <exports-root> <release-id> <land|water> <land-y> <water-y>
lfc v2 export   <request> <intent> <exports-root> <release-id> <land|water> <land-y> <water-y>
lfc v2 preview  <release-directory-or-zip>

lfc v2 job status|cancel <job-id>
lfc v2 job list
lfc v2 candidate list <request-id>
lfc v2 candidate info <job-id>

lfc v2 journal-verify <placement-journal-v2.json>
lfc v2 recovery inspect <journal|plan> <placement-journal-or-recovery-plan-v2.json>

lfc v2 migrate inspect <intent|design|release> <v1-source>
lfc v2 migrate apply   <intent|design|release> <v1-source> <output-root> <migration-id> <strict|accept-lossy>
```

`place`／`status`／`undo`／`recover` はliveなworldを伴うためPaper専用です。CLIから呼ぶと安定code `V2_PAPER_ONLY` で拒否されます。`migrate` は逆にoperator workstation上のlegacy asset pathを読むためCLI専用で、Paperから呼ぶと安定code `V2_CLI_ONLY` で拒否されます（tab completionにも現れません）。既存v1 artifactは`migrate`またはread-only legacy verifierからだけ扱います。

### v2 request authoring（V2-12-08）

`create`／`bounds`／`selection`／`constraint-map`／`constraint-source`／`generation`／`foundation-base-levels`／`foundation-detail`／`mask-reconcile`／`prompt`／`list` はv2 requestを**作成・編集**します。`V2-12-05`のカバレッジ監査で、v1にはあった request authoring がv2に無い（F1）ことが判明したため追加しました（[ADR 0035](adr/0035-v1-retirement-governance.md) D11）。`validate`／`info` の意味は従来どおりで、**pathを引数に取ります**。

保存先はPaperが`plugins/LandformCraft/data/v2/requests/<request-id>.request-v2.json`、CLIが`<--data-dir>/v2/requests/<request-id>.request-v2.json`です。`request-id` はlowercaseのportable slug（`[a-z0-9][a-z0-9._-]{0,63}`）で、pathは受け取りません。作成・編集は毎回strict schemaで検証してからatomicに publish するため、途中状態のrequestは残りません。

| verb | 動作 |
|---|---|
| `create <id>` | 既定bounds（128×128、y -32..160、water 62）、既定prompt、seed 0、tileSize 64、constraint map 0件でrequestを作成します。同じIDが既にあれば**上書きせず失敗**します |
| `bounds <id> <w> <l> <min-y> <max-y> <water>` | 範囲を置換します。`water` は新しい範囲へclampされます |
| `selection <id>` | **Paper専用**。operatorの現在のWorldEdit選択範囲からwidth／length／min-y／max-yを取り込みます。保存済みwater levelは維持（新範囲へclamp）されます。CLIから呼ぶと`V2_PAPER_ONLY`です |
| `constraint-map <id> <slug> <file> <sha256> <w> <l>` | `surface-2_5d` exportが要求する land/water constraint map source を1件宣言します（既存の宣言は置換）。形式はU8 grayscale・north-west原点・east/south軸・pixel中心・回転／反転なし・全面crop・`0=water` `1=land`・no-data禁止に固定です。これ以外の形式は手書きJSONのままとし、推測しません |
| `constraint-source <id> <slug> <land-water｜height-guide｜zone-label> <promotion-dir> <file>` | `promote` が封印したrecordからrole・encoding・寸法・digestを読み、constraint map sourceを1件**追加または更新**します（他の宣言は保持）。3 role共通で、height guideのvalue meaning／scale／sample範囲やzoneのlabel legendはrecordの値をそのまま使い、command引数からは推測しません。map file自体はコピーせず、`<file>`（request相対path）へ置くのはoperatorの作業です（`constraint-map`と同じ） |
| `generation <id> <global-seed> <tile-size>` | 生成settings（seedとtile size）を置換します。`V2-18-09`以降maskは生成へ解決され合成形状と一致する必要があるため、**maskはそれを作ったseedに束縛されます**。authoringがmask側のseedを再現できるよう公開しています（`V2-18-10`） |
| `foundation-base-levels <id> <land-surface-y> <water-bed-y>` | macro foundationのmedium別provisional base elevationを宣言します（[ADR 0038](adr/0038-macro-foundation-contract.md) D2-2(b)）。HARD `LAND_WATER_MASK`と併せて**明示foundation入力**を構成し、`V2-18-10`のowner gateを通過するために必須です。値はrequest boundsの範囲内である必要があり、推測はしません |
| `foundation-detail <id> <land-amplitude> <water-amplitude> <wavelength> <octaves>` | macro foundation背景の平坦なbase levelを、空間的に連続なmulti-scale detailで置換します（[ADR 0041](adr/0041-coherent-detail-kernel.md)、任意）。振幅はblock単位のhard bound（0..32、両medium同時0は不可）、`wavelength`は2冪（8..1024）、`octaves`は1..6です。`foundation-base-levels`が必須で、振幅がwater levelを跨ぐ／vertical extentを外れる場合は宣言時点で拒否します（clampしません）。detailは**背景base level cellの標高のみ**を動かし、`HEIGHT_GUIDE`・producer・coastal modifierが所有するcellやland-water mediumには一切触れません |
| `mask-reconcile <id> <tolerance-blocks>` | mask ⇔ feature reconcile pre-passを宣言します（[ADR 0043](adr/0043-mask-feature-reconcile-pre-pass.md)、任意）。export時、Blueprint compileの前に宣言feature geometry**全体**をHARD `LAND_WATER_MASK`へ1回の剛体平行移動（各軸±`tolerance-blocks`、1..8）で整列します。**maskは動かさず**、既存のfail-closed gateも一切緩めません。既にmaskと一致しているgeometryは動かしません（同点時は移動量最小が勝つため）。`foundation-base-levels`が必須で、`width × length × (2×tolerance+1)²`が評価予算（128,000,000）を超える宣言は拒否します（clampしません） |
| `prompt <id>` / `prompt <id> <text...>` | Paperは**次のchat 1件**をpromptとして保存します（5分で失効、`cancel`で取消）。CLIは引数のtextを保存します。いずれもcredentialに見えるtextは拒否します |
| `list` | 保存済みrequest IDを決定的な順序で列挙します |

permissionは`landformcraft.v2.request.create`（作成）と`landformcraft.v2.request.edit`（`bounds`／`selection`／`constraint-map`／`constraint-source`／`generation`／`foundation-base-levels`／`foundation-detail`／`mask-reconcile`／`prompt`／`intent bind`）、read系（`validate`／`info`／`list`）は従来どおり`landformcraft.v2.request`です。

authoringからexportまでの最短経路は次のとおりです。`export`はrequestの`requestId`と intentの`intentId`が一致することを要求します。

```text
lfc v2 request create harbor-cove-64
lfc v2 request bounds harbor-cove-64 64 64 32 72 50
lfc v2 request generation harbor-cove-64 827413 64
lfc v2 request constraint-map harbor-cove-64 coast-mask maps/coast-u8.png <sha256> 64 64
lfc v2 request foundation-base-levels harbor-cove-64 54 46
lfc v2 request prompt harbor-cove-64 A sheltered cove with a stone breakwater.
lfc v2 export <上記request path> <intent path> <exports-root> <release-id> water 54 46
```

`generation`と`foundation-base-levels`は`V2-18-10`以降の必須手順です。maskは`seed`が決める合成coastal形状と一致している必要があり、`foundationBaseLevels`が無いrequestはowner gateで拒否されます。

`V2-18-03`以降、`export`／`generate`は生成前にHARD preflight gateを通ります。宣言したHARD `LAND_WATER_MASK`は実体（request directory相対のPNG）が存在し、`<sha256>`と宣言寸法に一致する必要があります。評価器を持たないHARD constraint（現状は`METRIC_RANGE`）やconsumerを持たないHARD relation（contract-only／未接続kind宛）は`v2.preflight.*` ruleで拒否されます。override flagはありません。

`V2-18-04`以降、HARD `EDGE_CLASSIFICATION`はpreflightを通過し、生成後にtarget-driven validatorが該当world edge（外周band）のland／water share を実測します。HARD違反（例: 北端land率が`minimumShare01`未満）はexport拒否になります。

`V2-18-10`以降、surface exportは**foundation owner被覆100%**を必須とします（[ADR 0038](adr/0038-macro-foundation-contract.md) D7-2）。明示foundation入力（HARD `LAND_WATER_MASK`＋`foundationBaseLevels`）を持たないrequestは、feature非所有cellにfoundation ownerが存在しないため、生成後・publish前に安定rule `v2.export.foundation-owner-coverage-incomplete` でfail closedに拒否されます。**override flagはなく、baseline引数でも回避できません**（D8-2）。gateの対象はsurface foundation ownerだけで、modifier（active contributor）被覆・volume／material被覆・request domain外は対象外です。coverage分母はrequest domainの全cell（width×length）であり、no-data cellを分母から除外することはありません。HARD maskのno-dataはbinding（`INVALID_NO_DATA`）が生成前に、ownerを持たないcellはkernel不変条件（`OWNERLESS_CELL`）が生成時に拒否するため、gateへ到達するfieldに未所有cellが「covered扱い」で混入することはできません。owner候補（candidate）は0..N個許容されますが、merge後のeffective ownerは各cellちょうど1つで、未解決tie／undeclared overlapはmerge kernelがgate到達前に拒否します（ADR 0038 D1）。

`V2-19-06`以降、requestは任意で`HEIGHT_GUIDE` source（`request constraint-source … height-guide …`＋`intent bind … height-guide …`）を1件宣言でき、macro foundationがそれをbackground elevation sourceとして読みます。**優先順位はcellごとにguide＞`foundation-base-levels`**で、guideがno-dataを宣言したcellだけがmedium別base levelへ戻ります。guideが宣言するvalue rangeがrequestのvertical extentを外れる場合はrequest宣言時点で拒否されます（clampしません）。coastal featureが所有するcellの高さはfeature側のものであり、HARD guideが同じcellをtolerance超で指定した場合は`v2.foundation.height-guide-modifier-conflict`でexport拒否、SOFT guideの差は公開Releaseの`constraint.coast.height.residual` sidecarへ記録されます。

`V2-19-12`以降、requestは任意で`foundation-detail`（[ADR 0041](adr/0041-coherent-detail-kernel.md)、`coherent-detail-fixed-v1`）を宣言でき、macro foundation背景の平坦な`foundation-base-levels`を空間的に連続なmulti-scale detailへ置換します。適用対象は**背景base level cellの標高だけ**で、`HEIGHT_GUIDE`が値を指定したcell・producer（`PLAIN`等）所有cell・coastal modifier所有cellには適用せず、land-water mediumも変えません（優先順位表に行を増やしません）。振幅はhard boundで、隣接cell段差の上限（`maximumAdjacentStepMillionths`）が契約として公開されるため、`PlainGeneratorV2`のcell-hash micro reliefのような空間的に非連続なノイズとは機械的に区別できます（cell-hashの流用ではありません）。整数演算のみ・closed formのためhalo／support radiusは0で、whole生成とtile生成は一致します。erosion等の近傍filterはScope外です。

`V2-19-14`以降、requestは任意で`mask-reconcile`（[ADR 0043](adr/0043-mask-feature-reconcile-pre-pass.md)、`mask-feature-reconcile-v1`）を宣言でき、export spineがBlueprint compileの前に宣言feature geometry**全体**を1回の剛体平行移動（各軸±tolerance、整数block）でHARD `LAND_WATER_MASK`へ整列します。これは「maskを先に固定し、geometryを後から寄せる」運用のための機構で、`V2-18-13`以降の実務手順（geometryやseedを触るたびにmaskを再生成する）を数block分の位置ずれについて不要にします。**補正は常に一方向**（geometry→mask）で、maskの値・digest・bindingは変更しません。**per-featureのoffset・回転・拡大縮小・形状変形は行わない**ため、宣言済みrelation（`ENCLOSES`／`ADJACENT_TO`／`OVERLAPS`）の相対geometryは厳密に保存されます。既にmaskと一致しているgeometryは動きません（同点は移動量最小が勝つ）。**新しい拒否ruleは追加していません**: toleranceの内側に一致する配置が無いrunは、従来どおり`v2.coastal-transition-hard-conflict`等の既存HARD gateが拒否します。適用したoffsetは`v2 export`／`/lfc v2 export`のsummaryへ`maskFeatureReconcile`として表示され、公開Releaseの`source/terrain-intent.json`には**整列後のgeometry**が封印されます（Releaseが実際に含む地形を記述するため）。任意形状への追従（手描きmaskへのcell単位の追従）はScope外です。

`V2-18-09`以降、requestがHARD `LAND_WATER_MASK`と`foundationBaseLevels`（medium別のprovisional base elevation宣言）の両方を持つ場合、macro foundation stageがmaskを解決して全cellのland-water＋provisional elevationを確定し、coastal featureはその基礎の上のsurface modifierとして合成されます（ADR 0038）。この**foundation経路**では末尾のbaseline引数（`<land|water> <land-y> <water-y>`）は受理されますが**無視**され、CLI summaryへNON_GATING警告 `v2.cli.surface-baseline-deprecated` が表示されます。maskとfeature形状が矛盾するcell（HARD maskをmodifierが上書きしようとする等）はexport拒否です。maskの大域構図はEDGE実測を充足できるため、edge意図をHARDで宣言できます。

### v2 intent binding（V2-19-04）

`intent bind`／`intent bindings` は、requestが宣言したconstraint sourceとTerrainIntentの`mapReferences`を接続する**CLI専用**verbです（operator workstation上のintent artifactを読み書きするため、`migrate`／`extract`／`promote`と同じ扱いです）。

bindingのcanonicalな`artifactId`は`constraint:<semantic>:sha256-<宣言digest>`で、digest部分は**requestが宣言した入力mapのdigestと一致していなければなりません**（公開surface Releaseで`SurfaceReleaseCapabilityVerifierV2`が検査する規則、V2-18-07）。手書きでは64桁のSHA-256をJSONへ書き写すことになり、誤りはexport時まで表面化しませんでした。`intent bind` はこれをrequestから計算します。providerにこの値を発明させることはできず、させません。

| verb | 動作 |
|---|---|
| `intent bind <request> <intent-in> <intent-out> <slug> <role> <hard｜soft> <nearest｜bilinear-fixed> <tolerance> [weight]` | 宣言済みsourceのbindingを1件追加または更新し、`<intent-out>`へstrictに書き出します（binding idはslug、既存の同一id／同一sourceIdの行は置換）。roleが宣言済みencodingと矛盾する場合（categorical mapをHEIGHT_GUIDEにする、water/land以外のlegendをLAND_WATER_MASKにする等）は拒否します。strength／sampling／tolerance／weightの組み合わせ規則は`TerrainIntentV2`が検査します |
| `intent bindings <request> <intent>` | 各bindingについて、宣言済みsourceの存在、`artifactId`の再計算一致、roleとencodingの整合、map実体のsecure resolve・digest・IHDR寸法を照合します。read-onlyで、1件でも不一致があれば`LFC-REQUEST-INVALID`でfail closedします。bindingが無い宣言済みsourceは`unboundSources`として報告します |

出力の`generatorConsumer`は、そのroleを**実際に読む生成側があるか**を示します。`V2-19-06`以降、`LAND_WATER_MASK`と`HEIGHT_GUIDE`が`MACRO_FOUNDATION`（前者はmedium、後者はbackground elevation）、`ZONE_LABEL_MAP`は`NONE`です。surface exportのconstraint map要求もrole別になり、**`LAND_WATER_MASK`ちょうど1件＋`HEIGHT_GUIDE`任意1件**を受理します。consumerを持たない`ZONE_LABEL_MAP` bindingを含むintentは、無視ではなく拒否されます。

`V2-19-04`より前に書かれたfixture intentは`artifactId`のdigest部分を`0`で埋めています（exportが公開時に書き換えるため）。そうしたintentを`intent bindings`にかけると不一致として報告されます — 期待どおりの結果で、`intent bind` で作り直せば解消します。

### v2 design のreference image（V2-19-03）

`design`はrequestの`referenceImages`宣言をそのまま入力に取ります。宣言があるrequestは、以前はprovider呼出し前に必ず失敗していました（宣言数と送信数の不一致）。現在は宣言順（id昇順）に1件ずつ準備してproviderへ渡します。CLIとPaperの`import`／`fixture`／`openai`／`anthropic`の4 pathすべてが同じ準備を通ります。

- **path解決**: requestファイルのdirectory配下の相対pathだけを受理します。絶対path、`..`、backslash、symlink、同一fileへのhard link別名、重複宣言は拒否します。
- **受理条件**: PNG／JPEGのmagicと拡張子が一致すること、single frame（APNG不可）、source byte／寸法／aspect比／pixel数／decode working setが各上限内であること。読み取り中にfileが変化した場合も拒否します。
- **送信内容**: EXIF orientationを適用したrasterから**PNGを再符号化**したものだけを送ります。EXIF・XMP・comment等のmetadataとfilesystem pathはprovider payloadにもログにも出ません。送信byteはper-image／合計16MiBが上限です。
- **`expectedSha256`（任意）**: 宣言するとfile bytesのSHA-256と照合し、不一致（authoring後の差し替え等）をprovider呼出し前に拒否します。省略時はdigest検査を行いません。
- **失敗**: いずれもartifactを一切作らずfail closedで、CLIは`LFC-REQUEST-INVALID`（transport／不正responseは`LFC-PROVIDER-FAILED`）と`Design failed safely (<code>).`を返します。`<code>`は`PATH_SECURITY`／`BUDGET_EXCEEDED`／`INVALID_REQUEST`等の安定codeです。

reference imageはAI提案のためのSOFT cueであり、`mapReferences`（HARD constraint map）を生成しません。決定論的な形状入力はrequestの`constraintMaps`経路です（[Direct Constraint Maps v2](design-v2/image-constraint-maps.md)）。

### v2 retention／read-onlyなplacement state確認（V2-12-10）

`V2-12-05`のカバレッジ監査で、v1にあった`cleanup`／`journal-verify`／`recovery`（read-only）に相当するv2運用機能がcommandへ未接続だった（F2）ため接続しました（[ADR 0035](adr/0035-v1-retirement-governance.md) D11）。retention policyそのものは変更していません。

| verb | surface | 動作 |
|---|---|---|
| `retention plan <placement-id>` | Paper | そのplacementのapplied journalからplan＋journalを読み、recovery plannerが**retention window内**と判定したsnapshotのbyte総量を示す1回限りの確認token（10分失効、actor束縛）を発行します。v1 `cleanup plan`相当 |
| `retention execute <placement-id> <plan-id> <token>` | Paper | tokenを消費して該当snapshotを削除します。削除対象はrecovery cleanup planが列挙したwindow外terminal snapshotだけです |
| `retention status <placement-id>` | Paper | journal stateとsnapshot byte使用量を表示します |
| `journal-verify <placement-journal-v2.json>` | CLI | v2 placement journalをstrict検証します（v1 `journal-verify`相当）。read-only |
| `recovery inspect <journal\|plan> <artifact.json>` | CLI | journal state（`RECOVERY_REQUIRED`含む）またはrecovery planのclassificationをread-onlyで表示します（v1 `recovery status\|diagnose`のread-only視点） |

`retention`は実行中serverのplacement stateを読み、snapshot state（world blockではありません）を削除するためPaper専用です（`landformcraft.v2.retention`権限）。`journal-verify`／`recovery inspect`は明示したartifact fileだけを読みworldを変更しないためCLI専用で、Paperからは安定code`V2_CLI_ONLY`になります。**v2 recoveryの実行系**（`recover diagnose|plan|execute`）は従来どおりPaper専用で、`recovery inspect`（CLI read-only）とはverb token（`recover`／`recovery`）で区別されます。

retentionが実際に削除できるのはplacement統合が有効なruntimeだけです（統合が無ければretention対象のsnapshotも存在しません）。CLIには`recovery list`（全placement列挙）はありません。列挙はoffline store走査を要し、v2の「明示path・非推測」方針（`v2 verify`／`v2 preview`と同じく明示artifactを取る）に反するため、CLIは明示artifactのinspectだけを提供します。Paper起動時のrestart-state点検が列挙相当を担います。

### v2 job／candidate／二段階export（V2-12-09）

`V2-12-05`のカバレッジ監査で、v1にあった非同期job→candidate→確認付きexportの運用がv2に無い（F3）ことが判明したため追加しました（[ADR 0035](adr/0035-v1-retirement-governance.md) D11）。**既存の単発`v2 export`／`v2 generate`は同期実行のまま変更していません。**

| verb | 動作 |
|---|---|
| `export plan <request> <intent> <exports-root> <release-id> <land\|water> <land-y> <water-y>` | **Paper専用**。exportを予約し、staging用のfree diskをadmitしてから**1回限りの確認token**を発行します。tokenはactorとplan、そしてrequest／intent／release-id／ZIP有無のdigestに束縛されます |
| `export create <plan-id> <token>` | **Paper専用**。tokenを消費して非同期job をqueueし、job IDを返します。plan作成後にrequestかintentが編集されていればdigestが一致せず拒否されます |
| `job status <job-id>` | job の現在の状態。状態は遷移ごとに永続化されるため、再起動後も読めます |
| `job cancel <job-id>` | job を取り消します。cancel token・Future cancel・interrupt・job stateが連動し、publisherのatomic move（commit point）より前に観測されるため、**取り消されたjobはReleaseを公開しません** |
| `job list` | 保存済みjobをjob ID順で列挙します |
| `candidate list <request-id>` | そのrequestの**公開済み**job（＝v2のcandidate）をjob ID順で列挙します |
| `candidate info <job-id>` | 1件のjob snapshotを表示します |

state は `QUEUED` → `RUNNING` → `PUBLISHED`／`CANCELLED`／`FAILED` で、後ろ3つは終端です。終端jobへの再cancelは状態を返すだけで、新しい遷移を作りません。

`export plan`／`create` がPaper専用なのは、planが実行中のserver processに存在し、確認したjobも同じprocessで走るためです。CLIから呼ぶと安定code `V2_PAPER_ONLY` になります。`job`／`candidate` はjob storeが永続的なため両surfaceから読めますが、CLIの`job cancel`は**別processで走っているworkerには届かず**、durableなsnapshotを`CANCELLED`にするだけです（v1 CLIの`job cancel`と同じ性質です）。

job storeはPaperが`plugins/LandformCraft/data/v2/jobs/`、CLIが`<--data-dir>/v2/jobs/`です。job IDはcanonicalなlowercase UUIDのみで、短縮形・大文字・traversalは拒否されます。保持上限は4096件で、超過するとjobをqueueせずに拒否します。未確認planの上限は256件、tokenの有効期間は10分です。permissionは`landformcraft.v2.job`と`landformcraft.v2.candidate`です。

新しいSchemaは`generation-job-v2.schema.json`（example: `examples/v2/job/export-job-v2.json`）です。v1の`generation-job.schema.json`は凍結されたままで、拡張も再利用もしていません。

`generate` は `export` のZIP無し版（strict Release directoryのみ）です。`<land|water> <land-y> <water-y>` は引数としては必須のまま受理されますが（`V2-12-02`）、`V2-18-10`でfoundation owner gateがfail closedになったため、**この引数だけで成立するlegacy exportはもうありません**。明示foundation入力を持つrequestでは無視され`v2.cli.surface-baseline-deprecated`警告が出ます（`V2-18-09`）。引数自体の削除はV2-18-12後の独立Task＋ADR amendmentです（ADR 0038 D8-3）。

### v1→v2 migration（V2-12-04）

既存v1資産をv2 artifactへ変換する明示操作です。自動一括migrationは行わず、1回につき1資産だけを、operatorが種別・source・出力先・loss方針を明示して変換します。

| source kind | 読むもの |
|---|---|
| `intent` | v1 `terrain-intent.json` |
| `design` | v1 design package directory（`<designs-root>/<request-id>/<job-id>`） |
| `release` | Release format 1 のdirectoryまたは`.zip` |

出力は`<output-root>/<migration-id>/`のmigration bundleで、`migration-report-v2.json`と、Release 2 design package（`designs/<request-id>/<job-id>/`）、bundle全体の`checksums.sha256`からなります。publishはstaging→strict read-back→atomic move→published read-backで、途中失敗時に中途半端な成果物は残りません。sourceは読むだけで一切変更されず、既存の`<migration-id>`があれば上書きせず失敗します。同じsourceからは常に同じbundleが出ます（job UUIDとtimestampをsource digestとv1 auditから導出するため）。

`inspect` はdry-runで、reportを表示するだけで出力先には何も書きません。

**変換されないもの**（loss policy）: v1 intentは`theme`以外に位置・形状を持たず、v2 Featureはnormalized geometryを要求します。したがってtopology、seaSides、landRatio、relief、coastline、water、zone、structureは**v2 intentへ移されず、reportへ1件ずつ理由付きで列挙**されます。推測してFeatureを作ることはしません。Release 1のtile schematic、structures、assets、previewは、v2 Blueprintのmodule／stage／field descriptorを持たないため変換対象外で、移行後のintentから`lfc v2 export`で作り直します。

このためほぼ全てのv1資産はlossyです。`apply` の最終引数で `strict`（1件でも非対応があれば拒否）か `accept-lossy`（reportの内容を承知のうえ実行）を明示する必要があり、既定でlossy変換が通ることはありません。

移行されたintentは`provenance.source = UPGRADED_V1`／`confirmationState = UNCONFIRMED`、design packageは`pathKind = IMPORT`を持ちます。geometryは通常のv2 design stageで追加してください。

失敗は安定code（`V2_UNKNOWN_VERB`／`V2_UNKNOWN_OPERATION`／`V2_USAGE`／`V2_PAPER_ONLY`／`V2_CLI_ONLY`／`V2_PERMISSION_DENIED`／`V2_UNAVAILABLE`）と `v2CorrelationId` を伴います。

### v1退役後の互換command

v1 production commandと暫定aliasはV2-12-06で削除しました。既存v1 intent／design package／Release 1は`migrate inspect|apply`が内部のread-only legacy verifierでstrict検証します。`asset`（K3）、`doctor`／`job`／`recovery`／`version`／`help`（K4）、`ops`／`selection`（version中立）も維持しています。退役時の1対1対応表は [V2-12-06 evidence](design-v2/audits/v2-12-06-v1-retirement-evidence.md#command-replacement-inventory) にあります。

## Release 2明示配置（V2-6-21）

`plan`はstrict Release verify、tile/physics分類、effect envelope、region/disk reservation、actor-bound token発行までで、worldを変更しません。`confirm`がtokenを消費してsnapshot-allとcontainmentを完了します。`execute`はcanonical apply→bounded settle→full exact verifyを実行し、失敗時は逆順rollbackを試みます。曖昧な状態は`RECOVERY_REQUIRED`のままで、`recover-diagnose`が許可しないactionを実行できません。prepared／confirmed／appliedのoperation contextはstage commit markerを最後にfsync＋atomic publishし、main journalより先にdurable化します。

permissionは`landformcraft.v2.plan`、`.confirm`、`.execute`、`.status`、`.undo`、`.recovery`（offline verbは`.request`／`.request.create`／`.request.edit`／`.design`／`.export`／`.preview`／`.job`／`.candidate`／`.retention`／`.recovery`）です。WorldEdit 7.3.19／FAWE 2.15.2実機smoke（64×64）とV2-6 Phase gate（`V2-6-19`）まで検証済みで、`V2-11-01`によりcatalog上の`paper_apply`（および`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`）は`surface-2_5d` capabilityのSANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPEだけが`SUPPORTED`です。寸法は`V2-11-06`で実測evidenceの範囲まで昇格し、FAWE 2.15.2では1000×1000以内（`V2-11-04`／`V2-11-05`実測）、WorldEdit 7.3.19では64×64以内です。他featureのPaper適用は`EXPERIMENTAL`または`UNSUPPORTED`であり、runtime上限を超える寸法は未測定のため拒否されます。既定configの上限は保守的に64×64で、引き上げは運用者の明示設定です（[admin-guide.md](admin-guide.md)）。

v2／r2の配置commandはpermissionに加えてserver operatorであるPlayer、CONSOLE、RCONだけを受理します。command block／proxied・unknown sender／非operatorは拒否し、plan対象worldは`world-policy.allowed`／`denied`を実行時に再検査します。deny worldと存在しないworldはworld mutation前にstable errorで失敗します。Tab補完はpermissionとworld policyを反映し、confirmation tokenを一切候補へ出しません。

## Paper

rootは `/landformcraft`、aliasは `/lfc` です。tokenはTab補完しません。token付きclick eventは入力欄へcopyするだけで自動実行しません。
`/lfc help` は「確認」「設計・生成」「配置・復旧」に分けて表示します。CLIの `--help` も「設計・生成」「Release・検証」「管理」に分け、各commandの用途と実行例を表示します。

| Command | Permission | 説明 |
|---|---|---|
| `help` | `landformcraft.help` | help |
| `version` | `landformcraft.version` | plugin／generator／Schema／Minecraft／Paper／WE検出 |
| `doctor` | `landformcraft.doctor` | secret非表示のread-only診断 |
| `ops metrics` | `landformcraft.doctor` | Release 2 closed-label運用metrics（V2-6-13） |
| `ops diagnose <correlation-id>` | `landformcraft.doctor` | audit correlationからredacted診断 |
| `request create <id>` | `request.create` | request作成 |
| `request bounds selection <id>` | `request.edit` | WorldEdit選択をboundsへ設定 |
| `request bounds <id> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | `request.edit` | inclusive bounds設定 |
| `request prompt <id>` | `request.edit` | 5分のchat prompt session |
| `request validate/info <id>` | `request.validate` | Schema／record検証、表示 |
| `request list` | `request.list` | 一覧 |
| `design create <request> <openai\|anthropic> <model>` | `design.create` | 非同期Provider設計 |
| `design import/fixture <request> <relative-json>` | `design.import` | imports rootから設計 |
| `design status <job>` | `job.status` | 設計job状態 |
| `design info/verify <design>` | `design.verify` | Design Package検証 |
| `generate <design>` | `generate` | candidate生成job開始 |
| `job status/cancel <job>` | `job.status` / `job.cancel` | 永続job操作 |
| `candidate list <request-id>` | `candidate.read` | request別candidate一覧 |
| `candidate info/preview/validate <id>` | `candidate.read` | preview directory、file、checksum |
| `export plan <candidate>` | `export.plan` | Release生成計画、token発行 |
| `export create <plan> <token>` | `export.execute` | token確認後のRelease job |
| `export status <job>` | `job.status` | export job状態 |
| `export verify/info <request/release>` | `export.verify` | strict verification |
| `export list` | `export.verify` | Release一覧 |
| `selection` | `selection` | WorldEdit選択表示 |
| `apply plan <release> <world> <x> <y> <z>` | `apply.plan` | verify、予約、disk見積、token |
| `apply execute <placement> <token>` | `apply.execute` | snapshot付き配置 |
| `apply status <placement>` | `apply.status` | journal状態 |
| `apply undo <placement>` | `undo.plan` | world drift検査前のUndo計画（v1、または注入済みRelease 2 path） |
| `undo execute <placement> <token>` | `undo.execute` | snapshot逆順復元（v1、または注入済みRelease 2 path） |

Release 2 Undo（V2-6-09）は`PaperPlacementUndoServiceV2`／`PlacementUndoApplicationServiceV2.isRelease2Path()`でv1と分離します。world drift時はmutationせず拒否し、force overwriteはありません。
| `apply recover status/diagnose <placement>` | `recovery` | 復旧状態／保守的診断 |
| `apply recover rollback/accept <placement> <token>` | `recovery` | 明示復旧。acceptは全一致時だけ |
| `asset validate/import <schem> <metadata>` | `asset.read` / `asset.manage` | 制限付きasset |
| `asset list/info <id>` | `asset.read` | catalog読取 |
| `asset remove <id>` | `asset.manage` | 使用中は拒否 |
| `cleanup plan/status` | `cleanup` | retention dry-run／状態 |
| `cleanup execute <plan> <token>` | `cleanup` | 再検査して削除、audit |

permissionの完全名はすべて `landformcraft.` prefix付きです。`landformcraft.admin`は全childを持ちます。

## CLI

```text
landformcraft [--data-dir <dir>] [--json] [--quiet] [--verbose] <command>
```

実行名はGradleでは `./gradlew run --args="..."` です。

```text
validate <request.yml> <intent.json>
generate|preview <request.yml> <intent.json> [output] [candidate-index]
export <request.yml> <intent.json> [exports-root] [candidate-index]
verify <release-directory-or-zip>
journal-verify <placement-journal.json>
design <import|fixture> <request.yml> <intent.json> [designs] [jobs]
design <openai|anthropic> <request.yml> <model-id> [designs] [jobs]
design-verify <design-directory>
version
doctor
request <create|bounds|prompt|validate|info|list> ...
job <status|cancel> <job-id>
candidate list <request-id>
candidate <info|preview|validate> <candidate-id>
asset <validate|import|list|info|remove> ...
recovery <list|status|diagnose> [placement-id]
```

`--json` error契約は `errorCode`、`safeMessage`、`correlationId`、`operation`、`resourceId`、`stage`、`suggestedAction`、`exitCode`です。usage errorは2、処理失敗は1、成功は0です。CLIからworld mutationはしません。

`job status`は永続snapshotを読めます。`job cancel`はcancel intentを永続化しますが、別processで実行中のCLI taskへ通知するIPCはこのrelease candidateにありません。foreground commandはCtrl+C shutdown hookで実Futureへcancelを伝播し、Paper jobは同じplugin process内の所有Futureをcancelします。cross-process cancelが必要な運用ではbeta公開しないでください。
