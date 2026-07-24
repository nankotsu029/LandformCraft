# 0041: coherent detail kernel（macro foundation背景標高のbounded multi-scale detail）

- Status: **Accepted**（2026-07-24 人間承認、**D2 background base-level cellのみ／D3 request level optional を明示採択**）
- Date: 2026-07-24（起草・採択同日）
- Decision scope: `V2-19-12`。適用範囲は macro foundation stage（`MacroFoundationV2`／`MacroFoundationStageV2`）の**background owner標高**、その宣言面である`GenerationRequestV2`（＋Schema／CLI／Paper authoring verb）、および新設kernel package `generator.v2.detail`
- Depends on: [ADR 0038](0038-macro-foundation-contract.md) D1／D2-2／D5-3／D6／D9、`V2-18-09`（macro foundation production spine）、`V2-18-10`（foundation owner gate）、`V2-19-06`（`HEIGHT_GUIDE` consumer）、`V2-19-07`（foundation producer tier）、[2026-07-23横断監査](../audits/cross-cutting-audit-2026-07-23.md) §3.1／提案T-D2
- Blocks: `V2-19-12`の実装（kernel、request宣言、fixture、conformance case、docs同期）

## Context

[横断監査 §3.1](../audits/cross-cutting-audit-2026-07-23.md)（rev.2、独立レビュー照合済み）は、大きな高低差・複雑な局所標高について次を実測で確定した。

1. **表現契約側に不足はない。** `GenerationRequestV2.Bounds`は垂直範囲≤512 block、fixed-point millionths、`HeightEncoding` 3種まで実装済みである。
2. **生成能力が無い。** 公開production surfaceの標高は、`V2-19-06`以降でも「`HEIGHT_GUIDE`が指定したcell」か「medium別base level（land／waterの2定数）」のいずれかであり、guideを宣言しないrequestのbackgroundは**2値flat**である。
3. **既存の唯一のmicro reliefは空間的に非連続である。** `PlainGeneratorV2:99-106`の`microReliefVariationBlocks`は`PlainFixedMathV2.cellHash(seedNamespace, x, z)`＝cellごとに独立なhashで、隣接cell間に相関が無い。監査はrev.1の「noiseが一切ない」をこの表現へ訂正したうえで、**「公開productionにcoherentなmulti-scale detail／erosion kernelは存在しない」**と結論した。

`V2-19-07`がproducer tierを実体化したことで`PLAIN`等のfootprintは自前のbase elevationを持つが、footprint外（＝典型的なintentでは地表の大半）は依然として2定数である。監査の提案T-D2は、この欠落を埋める最小の単位として「空間的に連続なbounded・deterministic・multi-scale detail modifier（seed namespace／frequency／amplitude／halo／support radius／tile seam／CPU・memory budget固定）」を求め、**cell-hash独立ノイズの流用を禁じ、erosionを同時導入しないこと**を条件とした。[Task Index §19.2](../design-v2/task-index.md) `V2-19-12`はこれをADR＋人間承認必須のTaskとして登録している。

本ADRは、その detail kernel の契約（何を、どこへ、どの決定性・境界・予算で適用するか）を確定する。

## 凍結（本ADRが`Accepted`になっても変更しない）

1. **`HEIGHT_GUIDE`優先（ADR 0038 D2-2、`V2-19-06`）。** guideが値を指定したcellの標高はguideのものである。detailはそれを動かさない。
2. **modifier所有cellの標高（ADR 0038 D5-3）。** coastal 4種等のsurface modifierが所有するcellの高さはmodifierのものであり、detailは関与しない。
3. **producer所有cellの標高（`V2-19-07`）。** `PlainGeneratorV2`（`foundation-plain-fixed-v1`）とそのmicro reliefは**不変**とする。producerのcell-hash micro reliefをkernelへ置換することは本ADRのScopeではない（別Task＋amendment）。
4. **land-water medium。** detailは標高だけを動かし、mediumはHARD maskとproducer宣言のみが決める（ADR 0038 D2-3）。
5. **新capability名・新artifact type／version・Release format変更・新Schema `$id`を導入しない**（[task-index §19.1](../design-v2/task-index.md)共通停止、AGENTS.md §9）。
6. **erosion（近傍を読むfilter）を同時導入しない。** Task Index `V2-19-12` gateの明示条件であり、D9で境界を確定する。
7. **本ADRが`Proposed`の間は`V2-19-12`の実装を開始しない。**

## Decision

> **採択（確定）: D2 の適用範囲は background base-level cell のみ（guide／producer／modifier 所有 cell には適用しない）、D3 の宣言面は request level の optional `foundationDetail`（medium別振幅＋wavelength＋octaves）を採用する。** 2026-07-24 の人間承認（ADR 全文レビュー＋D2 適用範囲・D3 宣言面の明示採択）により Status を `Accepted` とする。代替案 A1（intent level 宣言）・A5（taper band 同時採用）は不採用であり、記録として Alternatives 節に残す。`V2-19-12` の実装はこの採択をもって開始可能となった。

### D1. Kernel契約（`coherent-detail-fixed-v1`）

新package `generator.v2.detail`へ`CoherentDetailKernelV2`を新設する。契約は次のとおりで、**整数演算のみ**（浮動小数点を用いない）である。

**入力**

| 入力 | 由来 | 備考 |
|---|---|---|
| `globalSeed` | `GenerationRequestV2.GenerationSettings.globalSeed` | 既存のrequest seed。新しいseed入力を作らない |
| seed namespace | **kernel側の固定定数** | 著者は指定できない（D4） |
| `amplitudeMillionths` | 宣言（D3） | mediumごと |
| `wavelengthBlocks` W | 宣言（D3） | 2冪、8..1024 |
| `octaves` K | 宣言（D3） | 1..6 |

**octave分解**

- octave `k ∈ [0, K)` の格子間隔は `W_k = W >> k`（`W_k ≥ 4` を宣言時に強制）。
- octave振幅は `a_k = floor(A × 2^(K-1-k) / (2^K − 1))` とし、**`Σ a_k ≤ A` を構成上保証する**。すなわち宣言した`amplitude`は「おおよその強さ」ではなく**hard bound**であり、`|detail(x,z)| ≤ A` が全cellで成り立つ。

**格子値とinterpolation**

- 格子点値 `v(k, lx, lz) ∈ [−10⁶, +10⁶]` は、octave seed（D4）と格子座標のtagged integer mixから決まる。
- cell内位置は `u = floorMod(coord, W_k) × 10⁶ / W_k`、重みは smoothstep `s(u) = u²(3·10⁶ − 2u) / 10¹²`（long演算、切り捨て）。
- 4格子点のbilinear補間をX→Zの順に行い、octave寄与を `a_k × value / 10⁶` として加算する。

**coherence（cell-hashとの決定的な差）**

smoothstepは端点で微分0であり、1 cell歩いたときのoctave寄与の変化は `3·a_k / W_k` を超えない（連続版の最大勾配 `1.5 / W_k` × 値域 `2a_k`）。したがってkernelは

```text
maximumAdjacentStepMillionths = Σ_k ( ceil(3 × a_k / W_k) + 1 )   // +1 は整数丸めのslack
```

を**契約として公開**し、4近傍の隣接cell差分がこれを超えないことをtestで固定する。同じ振幅のcell-hash場はこの上限を必ず破る（値域全体へ一様に散る）ため、「cell-hash独立ノイズの流用ではない」ことが**機械的に判定可能**になる。

### D2. 適用範囲＝background owner cellのうち「medium別base levelで決まっていたcell」だけ

detailは、ADR 0038 D1のeffective ownerが**background**であり、かつその標高が`foundationBaseLevels`（medium別の定数）から決まっていたcellにのみ加算する。

| cellの種別 | 標高の決定者 | detail |
|---|---|---|
| background、guide未指定 | `foundationBaseLevels`（2定数） | **加算する** |
| background、`HEIGHT_GUIDE`が値を指定（HARD／SOFT問わず） | guide | 加算しない（凍結1） |
| producer所有（`PLAIN`等） | producer | 加算しない（凍結3） |
| surface modifier所有（coastal 4種等） | modifier | 加算しない（凍結2） |

これは「**detailは2定数を置き換えるものであり、それ以外の宣言済み標高には触れない**」という単一規則である。SOFT guideのcellも除外するのは、guideが宣言された以上そのcellのdesired高さは著者が与えており、そこへnoiseを足すとconformanceの`RasterResidual`（`V2-19-06`が有効化した実測残差）が「著者の意図との差」ではなく「kernelが足したnoise」になり、residualの意味が壊れるためである。

**帰結（隠さずに宣言する）:** background cellとmodifier／producer所有cellの境界では、最大で振幅`A`の段差が生じ得る。これは detail 導入前から存在する段差（flat base levelとmodifier高さの差）が`A`だけ広がるもので、新種の不整合ではない。段差をbandで滑らかにするtaper（modifier footprintからの距離に応じてdetail重みを落とす）は**support radius > 0とdistance fieldを要求する**ため本ADRでは採用せず、Alternatives A5として後続Taskへ分離する。本Taskはこの段差を conformance case で**測定してpin**する。

### D3. 宣言面＝request levelのoptional `foundationDetail`

`foundationBaseLevels`（ADR 0038 D2-2(b)、`V2-18-09`）と同じ位置・同じ方式で宣言する。

```text
GenerationRequestV2.FoundationDetail
- landAmplitudeBlocks   : 0..32   （LAND background cellの振幅）
- waterAmplitudeBlocks  : 0..32   （WATER background cellの振幅）
- wavelengthBlocks      : 2冪、8..1024（frequency = 1 / wavelength。整数のみで表すためwavelengthを正本とする）
- octaves               : 1..6
```

- **optional。** absentのrequestはSchema上もcanonical byte上も従来と完全に同一で、既存Release・既存checksumは1 byteも動かない（D7）。
- **`foundationBaseLevels`必須。** detailは2定数を置き換えるものなので、明示foundation入力を持たないlegacy baseline requestには宣言できない（宣言したら`LFC-REQUEST-INVALID`）。
- **medium別振幅。** `foundationBaseLevels`が既にmedium別であることに合わせる。片方0は正当（陸だけ起伏、海底は平坦）だが、**両方0は拒否する**（`V2-19-01`が禁じた意図的no-opの宣言を作らない）。
- **wavelength／octavesはmedium共通。** パラメータ面を最小に保つ。
- **seed namespaceは宣言しない**（D4）。
- authoring verbは`v2 request foundation-detail <request-id> <land-amplitude> <water-amplitude> <wavelength> <octaves>`をCLIとPaperへ追加する（権限は既存の`request.edit`）。`V2-18-10`が`foundation-base-levels`／`generation`を追加したのと同じ理由で、宣言できてもauthoringから到達できない入力は作らない。

### D4. 決定性、seed namespace、tile seam、halo、support radius

1. **seed namespaceは固定。** octave seedは既存の`NamedSeedDeriverV2`（`sha256-tagged-v1`、enum ordinal・collection順序が入らない）で `derive(globalSeed, module, moduleVersion, "macro-foundation-background", "foundation:detail:<medium>:octave-<k>", "coherent-detail-fixed-v1")` として導出する。著者はnamespaceを選べない — 選べるとseedとnamespaceの2軸で同じ形が再現でき、request seedがreproducibilityの単一正本でなくなる。medium別namespaceにより陸と海底の起伏は相関しない。
2. **closed form。** `detail(x, z)`はglobal X/Zと封印済みパラメータだけの純関数で、他cellのfield値・生成順序・tile分割・thread数・module登録順・locale・timezoneに依存しない。
3. **support radius = 0、halo不要。** kernelは近傍cellの**field値**を読まない（読むのは自身の格子点だけで、それは座標の関数である）。したがってADR 0038 D6が参照する`SurfaceFoundationPlanV2.ResourceBudget.supportRadiusXZ`は0のままでよく、新しいhalo宣言を追加しない。**近傍fieldを読む処理（erosion、thermal slope、hydraulic flow）はsupport radius > 0を要求するため本kernelの契約外**である（D9）。
4. **tile seam。** whole生成とtile生成が同値であることは (2) から自明に従う（同じ座標には同じ値）。conformance caseで再exportと`tr-TR`／`Pacific/Chatham`での再測定を実施し、差分0を実測でpinする。

### D5. Fail-closed規則（override flagを設けない）

**宣言時（`GenerationRequestV2`構築＝CLI／Paper／codec共通の単一gate）**

| 条件 | 拒否理由 |
|---|---|
| `wavelengthBlocks`が2冪でない、8未満、1024超 | 格子整合と決定性のため |
| `octaves`が1..6外、または `W >> (K−1) < 4` | 最細octaveが格子として成立しない |
| `landAmplitudeBlocks`／`waterAmplitudeBlocks`が0..32外 | 振幅上限 |
| 両振幅が0 | 効果ゼロの宣言（`V2-19-01`） |
| `foundationBaseLevels`不在 | detailは2定数を置き換える機構である（D3） |
| `landSurfaceY − landAmplitude < waterLevel` | LAND cellが水面下へ沈む（乾いた窪みができる） |
| `waterBedY + waterAmplitude > waterLevel − 1` | 海底が水面に達し、WATER cellの水柱が消える |
| `landSurfaceY ± amplitude`／`waterBedY ± amplitude`がbounds外 | request垂直範囲を超える（clampしない） |

**生成時（defence in depth）**

per-cellで結果標高が request垂直範囲外、またはmediumのdatumを跨いだ場合は rule `v2.foundation.detail-out-of-contract`（`SurfaceFoundationExceptionV2`／`CONTRACT_VIOLATION`）で拒否する。`|detail| ≤ A`（D1）と宣言時checkにより到達不能な経路だが、`V2-19-06`の`v2.foundation.height-guide-out-of-contract`と同じくdefence in depthとして残す。

**override flagは設けない**（`V2-18-03`／ADR 0040 A2の方針を継承）。

### D6. Resource budget（新しいhard-codeを追加しない、ADR 0038 D6）

- **CPU:** 1 cellあたり `K ≤ 6` octave × 格子点4個のinteger mix、加えてbilinearの数回のlong乗除算だけである。SHA-256はoctaveごとに構築時1回で、cellごとには実行しない。既存のsurface生成は既にcellごとにcompositor・generator sampleを回しており、その定数倍以下に収まる。
- **Memory:** kernelはprofileとoctave seed配列（`K ≤ 6` long）だけを保持する。**格子値cacheを持たず**、per-cell配列も追加しない。`CoastalSurfaceFieldsV2.estimatedResidentBytes`は不変である。
- **admission:** 既存の`ExportBudgetV2`／scale契約（[ADR 0016](0016-scale-classes-and-execution-planning.md)）／`ScaleDimensionPolicyV2` MEDIUM ceilingを再利用し、新しい寸法・tile・予算のhard-codeを追加しない。LARGEは引き続き`SUPPORTED`と表現しない。

### D7. Checksum影響の宣言（`V2-19-12` gateが要求する項目）

| 対象 | 影響 | 根拠 |
|---|---|---|
| `foundationDetail`を宣言しない既存fixture（`harbor-cove-64-honored`／`-plain`／`-guided`／`-river`／`-meander`／`-beach`／`-coastless`／`coastal-honored-400`／`shore-2to1-400`）のterrain field／tile／**block semantic checksum** | **不変** | detailの適用点はoptional値がpresentの場合だけで、absentでは1命令も実行しない |
| 同fixtureの容器byte（request／intent／blueprint canonicalChecksum／manifest） | **不変** | absentのoptionalはJSONへ書き出さない（`foundationBaseLevels`と同一方式） |
| `production-dispatch-registry-v2` registry checksum／`public-dispatch-reachability-v1` projection | **不変** | route集合・support列・materialization分類を変更しない |
| Feature Support Catalog sealed checksum | **不変** | capability昇格を行わない（D8） |
| v1 Schema／generator `3.0.0-phase6`／Release format 1／v1 golden／placement／Undo | **不変** | v2 spineのみを対象とする |
| 新fixture `harbor-cove-64-honored-detail` | 新規追加 | 既存checksumに影響しない |

**「既存fixture完全不変」は努力目標ではなくAcceptance条件**とし、既存の絶対checksum pin（tile semantic checksum `20318e6c…` 等）で機械的に固定する。動いたらD2の解釈違反であり、停止して本ADRへ戻る（D10）。

### D8. 証拠（conformance case）と昇格しないもの

常設conformance caseとして`harbor-cove-64-honored-detail`を追加する。すべて**公開Releaseのartifactからのみ**測る。

1. **入力。** `harbor-cove-64-honored`のintentとland-water maskを**byte同一で再利用**し、requestへ`foundationDetail`だけを足す。detailはland-water分類を一切変えないため（凍結4）、`V2-18-13`のmask再生成規則（`active ? composed : macro-background`）を満たしたままである。
2. **block materialization（`V2-19-01`のgate計測コード`FeatureMaterializationV2`）。** baseline `harbor-cove-64-honored`とのfinal canonical block stream差分が非空であり、実測effect classが宣言と完全一致すること。
3. **coherence実測。** 公開tileから復元したbackground cellのsurface Yについて、4近傍の隣接段差がD1の`maximumAdjacentStepMillionths`以下であること。**同一振幅のcell-hash場が同じ判定を必ず破る**ことを対照testで示し、「cell-hash流用でない」を実測で固定する。
4. **非平坦性。** background land cell・background water cellそれぞれのsurface Yがそれぞれ2値以上を取ること（flat回帰の検出）。
5. **不侵襲性。** guide／producer／modifierが所有するcellの標高がbaselineと一致すること（D2の適用範囲をfieldから実測）。
6. **決定性。** 再export、`tr-TR`／`Pacific/Chatham`で差分0。
7. **境界。** modifier境界の最大段差を測定して記録する（D2の帰結の可視化）。

**昇格しないもの:** capability列（`paper_apply`を含む）、dispatch route集合、`PRODUCTION_CONNECTED`集合、寸法上限。detailはFeatureではなくfoundation標高の決定規則であり、新しいFeatureKindも追加しない。

### D9. erosionと近傍filterの境界（同時導入禁止の明文化）

本kernelは**closed formのdetail**だけを扱う。次は本ADRのScope外であり、実装しない。

- erosion（hydraulic／thermal）、slope-limited smoothing、その他**近傍cellのfield値を読むiterative filter**。これらは support radius > 0、halo宣言、tile seamの追加契約、反復回数の予算固定を要し、決定性の議論も別物になる。
- `PlainGeneratorV2`等producerのcell-hash micro reliefのkernelへの置換（凍結3）。
- modifier／guide cellへのdetail適用、およびD2の帰結で述べたtaper band（A5）。
- LARGE（1024超）streamingでのdetail（scale契約は不変）。

### D10. 停止条件

次のいずれかが必要になった時点で実装を止め、本ADRへ差し戻す。

- `foundationDetail`を宣言しない既存fixtureのchecksumまたは容器byteが動く（D7違反）。
- guide／producer／modifier所有cellの標高が動く（D2・凍結1〜3違反）。
- 近傍fieldの読み取り、halo、support radius > 0が必要になる（D9違反）。
- 新capability名・新artifact type／version・Release format変更・新Schema `$id`が必要になる（凍結5）。
- 決定性（whole==tiled、thread／locale／timezone、再export）を保証できない。
- 宣言時checkでは表現できない矛盾（例: guideとdetailの優先関係の再定義）が必要になる。

## Consequences

- 公開productionで初めて、**guideを持たないrequestでも背景地表が起伏を持つ**。監査§3.1が「どの経路でも生成できない」と結論した局所標高の最小単位が埋まる。
- 「detailは2定数を置き換える」という単一規則により、ADR 0038が確立した優先順位（guide＞base level、modifier所有cellはmodifierのもの、producer所有cellはproducerのもの）に新しい例外が入らない。優先順位表は`V2-19-06`から**行が増えない**。
- `amplitude`がhard boundであり隣接段差の上限も契約として公開されるため、detailの影響は事前に見積もれる。placement側のmutation envelope・resource見積へ不確定要素を持ち込まない。
- cell-hash micro reliefとの差が機械判定可能になり、以後「coherentなdetailを入れた」という主張がtestで検証される。
- erosionを導入する後続Taskは、support radius > 0とhaloを**自分の契約として**宣言する必要があることがD9で確定する。
- `foundationDetail` absentが完全にbyte不変なので、既存Release・既存golden・v1境界は不変である（D7）。

## Alternatives considered

### A1. detailを`TerrainIntentV2`のFeature／constraintとして宣言する

「起伏の粗さ」は設計意図なのでintent側が自然に見える。しかし (a) detailが置き換える対象である`foundationBaseLevels`が既にrequest側にあり、2つの入力が別文書へ分かれると整合検査（datum交差判定）がrequest単独では閉じない、(b) 新しいFeatureKindまたは新constraint種別はhistoric 60-kind Schemaとcanonicalization・dispatch・composition profileへ波及し1 Taskを超える、(c) AI providerが起伏パラメータを生成し始めると、ADR 0017（画像からのHARD geometry禁止）と同種の「providerが地形の細部を直接決める」経路が増える。**不採用**（D3）。将来intent側へ移す場合は独立ADRとする。

### A2. Perlin／Simplex noiseを浮動小数点で実装する

`AGENTS.md` §7の「同じBlueprint・seed・generator versionから同じfield／checksum」を満たすには、JVM・CPU・最適化に依存しないbit-exactな演算が要る。既存のv2生成系は全てinteger-only（`*FixedMathV2`）で統一されており、ここだけ浮動小数点を持ち込むとdeterminism論証が別枠になる。value noise＋smoothstepの整数実装で必要な性質（連続・multi-scale・bounded）は満たせる。**不採用。**

### A3. `PlainFixedMathV2.cellHash`をそのまま多重解像度化して使う

Task Index `V2-19-12`が明文で禁じている（「cell-hash独立ノイズの流用はしない」）。格子補間を伴わないhashは解像度を変えても隣接相関を持たず、監査§3.1が指摘した非連続性がそのまま残る。D1の`maximumAdjacentStepMillionths`契約はこの流用を機械的に排除するために置く。**不採用。**

### A4. guide指定cellにもdetailを加算する（guide＋noise）

「guideは大局、detailは細部」という解釈は一見自然だが、HARD guideではtolerance内へ収める調停が必要になり（AGENTS.md §7の「HARDをpriorityで上書きしない」に触れる境界）、SOFT guideではconformanceの`RasterResidual`が著者意図との差ではなくnoise量になる。`V2-19-06`が確立した「guideが値を指定したcellはguideのもの」を維持する方が、契約も測定も壊れない。**不採用**（D2）。

### A5. modifier／producer境界へのtaper band（distance-weighted detail）

境界段差を滑らかにできるが、modifier ownership maskからのbounded distance field（BFS）を要し、support radius > 0・halo宣言・予算・決定性の追加契約が必要になる。D9が引く「近傍を読まない」境界を本Taskで越えることになり、erosion同時導入禁止の趣旨にも反する。段差はD2で宣言し、D8-7で測定してpinしたうえで、taperは後続Task（`V2-19-14`のreconcile pre-passと同時期が自然）へ分離する。**本ADRでは不採用。**

### A6. detailをexport時のpost-processとして`CoastalSurfaceFieldsV2`側で足す

適用点がfoundationの外になるため、`MacroFoundationV2`が返す標高（desired sidecar・modifier conflict判定・producer検査が読む値）と公開fieldがずれる。単一の標高決定点をfoundationに保つ方が、ADR 0038 D1のeffective owner契約と整合する。**不採用。**

## Acceptance／実装開始条件

1. 人間が本ADR全文をレビューし、**D2の適用範囲（background base-level cellのみ／guide・producer・modifierには適用しない）とD3の宣言面（request level optional、medium別振幅）を明示採択する。**
2. Statusを`Accepted`へ更新し、採択日・採択内容をDecision節へ記録する。
3. その後に限り`V2-19-12`の実装（kernel、model／Schema／codec、macro foundation配線、CLI／Paper verb、fixture、conformance case、docs同期）を開始する。
4. 実装完了時、D7の「既存fixture完全不変」とD8の証拠一式（block効果、coherence上限、非平坦性、不侵襲性、決定性）がtestで機械的に固定されていることを確認する。

**充足記録（2026-07-24）:** 条件1・2は同日の人間承認（D2 background base-level cellのみ／D3 request level optionalの明示採択）で充足した。条件3の実装は`V2-19-12`で実施し、条件4は`CoherentDetailKernelV2Test`（coherence上限・振幅bound・決定性）と`FoundationDetailBlockConformanceV2`（公開Releaseからのblock効果・非平坦性・不侵襲性）、および既存fixtureの絶対checksum pin（`foundationDetail` absent時の不変性）で充足した。

## References

- [cross-cutting-audit-2026-07-23](../audits/cross-cutting-audit-2026-07-23.md) §3.1／§8／提案T-D2
- [ADR 0038](0038-macro-foundation-contract.md) D1／D2-2／D5-3／D6／D9
- [ADR 0040](0040-coastal-contributor-set-cardinality.md)（modifier tierの被覆要求撤廃、部分集合contributor）
- [ADR 0016](0016-scale-classes-and-execution-planning.md)（scale／budget契約）
- [task-index §19](../design-v2/task-index.md) `V2-19-12`
- [task-execution-guide §7](../design-v2/task-execution-guide.md) Support capability gate
- [V2-19-06 audit](../design-v2/audits/v2-19-06-height-guide-macro-foundation-consumer.md)／[V2-19-07 audit](../design-v2/audits/v2-19-07-foundation-producer-tier.md)
