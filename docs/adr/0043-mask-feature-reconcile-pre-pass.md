# 0043: mask⇔feature SOFT reconcile pre-pass（宣言geometryのmaskへの決定論的整列）

- Status: **Accepted**（2026-07-24 人間承認、**D1 宣言feature集合全体への単一剛体平行移動／D3 request levelのoptional `maskFeatureReconcile`（tolerance 1..8 block、opt-in）を明示採択**）
- Date: 2026-07-24（起草・採択同日）
- Decision scope: `V2-19-14`。適用範囲は surface export spine の生成前段（`CoastalSurfaceExportPipelineV2.prepare`のcompile直前）、その宣言面である`GenerationRequestV2`（＋Schema／CLI／Paper authoring verb）、および新設stage `MaskFeatureReconcileStageV2`／report `MaskFeatureReconcileV2`（`core.v2.export`）
- Depends on: [ADR 0038](0038-macro-foundation-contract.md) D1／D2-2／D2-3／D5-3／D6／D9、[ADR 0040](0040-coastal-contributor-set-cardinality.md)、`V2-18-09`（macro foundation production spine）、`V2-18-10`（foundation owner gate）、`V2-18-13`／`V2-19-09`（mask再生成規則）、`V2-19-06`（`HEIGHT_GUIDE` consumer）、`V2-19-07`（foundation producer tier）、[2026-07-23横断監査](../audits/cross-cutting-audit-2026-07-23.md) §4.2-4／提案T-A2
- Blocks: `V2-19-14`の実装（stage、request宣言、CLI／Paper verb、fixture、conformance case、docs同期）

## Context

[横断監査 §4.2-4](../audits/cross-cutting-audit-2026-07-23.md)（rev.2、独立レビュー照合済み）は、地形タイプを安全に増やすうえでの構造的弱みの1つとして次を確定した。

> **mask⇔seed束縛**（`V2-18-09`のmask再生成規則が生成出力を入力へ焼き込む）。feature geometryとmaskの整合責任が全面的にユーザー側にある。

現在の実装を再確認した確定事実は次のとおりである。

1. **maskはHARDの正本である。** `MacroFoundationV2.hardLandWaterSource()`が宣言mask（`LAND_WATER_MASK`、HARD）をcompositorへHARD sourceとして注入し、`CoastalTransitionCompositorV2.sampleAt`はmaskが値を持つcellで「maskと一致するactive contributorが1つも無い」場合に rule `v2.coastal-transition-hard-conflict` で拒否する。ADR 0038 D2-3どおりmaskがland-waterの唯一の権威であり、この拒否は正しい。
2. **その結果、maskは生成出力とcell単位で一致していなければならない。** `V2-18-13`（`coastal-honored-400`のcape是正）と`V2-19-09`（beach単体fixture）はいずれもmaskを`active ? composed : macro-background`の規則で**再生成**した。すなわち現在の唯一の実務手順は「geometryまたはseedを触ったらmaskを作り直す」であり、helper test（`RegenerateHonoredCoastalMaskExampleTest`／`RegenerateBeachOnlyCoastalMaskExampleTest`）がその手順を担っている。
3. **したがって「手で描いたmask」「画像から抽出したmask」を先に固定し、feature geometryを後から寄せる方向の運用が成立しない。** 数block分の位置ずれ（画像のregistration差、著者がsplineを微調整した、seedを変えた）は、たとえ形が正しくても`v2.coastal-transition-hard-conflict`で全面拒否になる。Track Bが目指す「画像の海岸線どおりの地形」は、この束縛の裏返しである（監査 §5.1「それすらIntent側bindingの手書きを要する」）。

[Task Index §19.2](../design-v2/task-index.md) `V2-19-14`はこれを **「feature geometryをmask shorelineへ決定論的にsnapする補正（補正量tolerance超は従来どおり拒否）で、mask⇔seed束縛によるauthoring負担を軽減」** として登録し、**ADR＋人間承認必須**、かつ**HARD不発明原則との整合を明文化**することを条件としている。

本ADRはそのpre-passの契約（何を、どちら向きに、どの決定性・境界・予算で動かすか、そして**何を絶対に動かさないか**）を確定する。

## 凍結（本ADRが`Accepted`になっても変更しない）

1. **maskは動かさない。** 補正は常に「宣言geometry → mask」の一方向であり、mask値・mask digest・mask bindingは1 bitも変更しない。maskのno-data cellへ値を補完しない（**HARD不発明原則**、D5）。
2. **HARD gateを弱めない。** `v2.coastal-transition-hard-conflict`、`v2.coastal-transition-hard-unrepresented`、ADR 0038 D7-1 kernel不変条件（`OWNERLESS_CELL`／`UNDECLARED_OVERLAP`／`producer-mask-medium-conflict`）、`V2-18-10`の`surface-foundation-owner-gate-v1`、`V2-18-03` preflight、EDGE evaluatorはいずれも**判定・閾値ともに不変**である。pre-passはこれらの前段に入るだけで、通過条件を緩めない。
3. **形状を変えない。** 補正は**剛体平行移動のみ**である。回転・拡大縮小・per-vertex変形・per-feature個別移動・非整数block移動を行わない（D1）。
4. **priorityでHARDを上書きしない**（AGENTS.md §7）。pre-passは調停器ではなく整列器であり、矛盾が残ったcellの扱いは既存gateのままである。
5. **新capability名・新artifact type／version・Release format変更・新Schema `$id`を導入しない**（[task-index §19.1](../design-v2/task-index.md)共通停止、AGENTS.md §9）。
6. **erosion／taper band／近傍filterを同時導入しない**（[ADR 0041](0041-coherent-detail-kernel.md) D9の境界を維持する）。
7. **本ADRが`Proposed`の間は`V2-19-14`の実装を開始しない。**

## Decision

> **採択（確定）: D1 の補正対象は宣言feature集合**全体**への単一剛体平行移動（per-feature offset・形状変形・回転・拡大縮小は行わない）、D3 の宣言面は request level の optional `maskFeatureReconcile`（`toleranceBlocks` 1..8、opt-in、`foundationBaseLevels`必須）を採用する。** 2026-07-24 の人間承認（ADR全文レビュー＋D1 補正対象・D3 宣言面の明示採択）により Status を `Accepted` とする。代替案 A1（per-feature offset）・A4（intent側宣言）・A5（advisory-only）は不採用であり、記録として Alternatives 節に残す。`V2-19-14` の実装はこの採択をもって開始可能となった。

### D1. 補正＝宣言feature集合**全体**への単一の整数block平行移動

pre-passは1 export runにつき**ちょうど1つの平行移動 `(dx, dz)`（block単位の整数）**を決め、`TerrainIntentV2.features[].geometry`の**全control point**へ適用する。

- **対象。** `features`の全要素（coastal 4種、`BACKSHORE_PLAINS`等のcontract-only kind、`PLAIN`等のfoundation producerを区別しない）。
- **非対象。** `intentId`／`theme`／`coordinateSystem`／`relations`／`constraints`／`environment`／`mapReferences`／`structures`／`provenance`。intent内で座標を持つのは`Feature.geometry`だけであり（`Constraint`は`METRIC_RANGE`／`EDGE_CLASSIFICATION`の2種で座標を持たず、`StructureRequest`は`id`／`kind`／`count`／`preferredFeatureId`のみ）、この列挙は実装のguardで固定する。
- **feature集合全体を同一量だけ動かす理由。** relationが正本である（AGENTS.md §7）。`ENCLOSES`（breakwater→basin）、`ADJACENT_TO`（cape↔breakwater、backshore↔beach）、`OVERLAPS`のような宣言済み関係は相対位置に依存する。per-featureに別々のoffsetを与えると、maskへの一致度は上がってもこれらの関係が**黙って壊れる**。単一剛体移動は相対geometryを厳密に保存するため、relation・composition interaction・conformance portfolioの前提を1つも動かさない。
- **正規化座標での表現。** geometryは正規化millionths（`Point2`、0..10⁶）で、block座標は`blockMillionths = n × (extent − 1)`（`CoastalFoundationModuleV2`の既存規則、extentは`width`または`length`）である。したがって`d` blockに対応するdeltaは

  ```text
  Δn(d, extent) = sign(d) × floor( (|d| × 2×10⁶ + (extent−1)) / (2 × (extent−1)) )   // 0捨1入・原点対称
  ```

  とする。**符号について厳密に対称**であり、`Δn(+d) + Δn(−d) = 0` が整数演算で成り立つ（往復が誤差ゼロで戻る＝D8の証拠が成立する根拠）。

### D2. 補正量の決め方（決定論的・整数・全順序）

**候補集合**: `|dx| ≤ T` かつ `|dz| ≤ T` の整数対（Chebyshev球、`(2T+1)²`個）。`T`は宣言tolerance（D3）。`(0,0)`は常に候補に含まれる。

**目的関数**: 宣言geometryから生成したcoastal composed rasterと宣言maskの**不一致cell数**。

```text
disagreement(d) = Σ_{u ∈ A} [ composed(u).landWater ≠ mask(u + d) ]
A = { u | composeAt(u, HardLandWaterSourceV2.NONE).active() }
```

- `A`は「coastal modifierが1つでもactiveなcell」であり、`v2.coastal-transition-hard-conflict`が評価されるcell集合とちょうど同じである。
- `mask(u+d)`が**no-data**のcellは比較対象から除外する（**maskが言っていないことを推測しない**、凍結1）。
- `u+d`がdomain外になる候補、または平行移動でcontrol pointが`[0, 10⁶]`を外れる候補は**無効**とし、順位づけから除外する。`(0,0)`は宣言intentそのものなので常に有効であり、pre-passは必ず答えを持つ。
- coastal contributorが0個（ADR 0040のサイズ0構成）なら`A = ∅`で全候補が`disagreement = 0`となり、tie-breakにより`(0,0)`＝恒等が選ばれる。

**全順序（tie-break）**: `(disagreement, |dx|+|dz|, dz, dx)` の辞書式最小。第2キーにより、**同点なら必ず移動量の小さい方が勝つ**。とくに `disagreement(0,0)` が最小値なら`(0,0)`が選ばれる。すなわち

> **既に一致している宣言geometryは絶対に動かない（pre-passは恒等写像になる）。**

これは努力目標ではなく順序の定義から従う性質であり、Acceptanceでtestに固定する（D8-1）。

**近似であることの明示**: 目的関数はcompositorのper-cell HARD述語（「maskと一致するactive layerが存在するか」）そのものではなく、**HARD sourceを与えずに合成した分類との不一致数**である。contributorが重なるcellでは両者が乖離し得る。したがってpre-passは**推定器**であり、その結果は下流の既存HARD gateが**そのまま**検証する（D4）。近似で足りる理由は、pre-passが解こうとしている問題が「registration（位置ずれ）」であり、位置ずれが純粋な平行移動である限り最小値0が真の解と一致するためである。形状そのものの不一致は本pre-passの守備範囲ではない（D9）。

### D3. 宣言面＝request levelのoptional `maskFeatureReconcile`

`foundationBaseLevels`（ADR 0038 D2-2(b)）／`foundationDetail`（ADR 0041 D3）と同じ位置・同じ方式で宣言する。

```text
GenerationRequestV2.MaskFeatureReconcile
- toleranceBlocks : 1..8   （剛体平行移動の各軸の上限。Chebyshev半径）
```

- **optional。** absentのrequestはSchema上もcanonical byte上も従来と完全に同一で、既存Release・既存checksumは1 byteも動かない（D7）。
- **opt-in。** 既定は「pre-passを走らせない」である。geometryを動かす操作を暗黙の既定にしない。
- **`foundationBaseLevels`必須。** pre-passはHARD maskとの整列であり、明示foundation入力を持たないlegacy baseline requestでは意味を持たない（宣言したら`LFC-REQUEST-INVALID`）。
- **`toleranceBlocks = 0`は拒否する。** 効果ゼロの宣言を作らない（`V2-19-01`）。
- **評価予算を宣言時に検査する。** `width × length × (2×toleranceBlocks+1)² ≤ 128,000,000` を`GenerationRequestV2`構築時に要求する（D6）。超過はclampせず拒否する。
- authoring verbは `v2 request mask-reconcile <request-id> <tolerance-blocks>` をCLIとPaperへ追加する（権限は既存の`request.edit`）。`V2-18-10`／`V2-19-12`と同じ理由で、宣言できてもauthoringから到達できない入力は作らない。

### D4. pre-passは**新しい拒否規則を追加しない**

- 補正量は候補集合の構成上つねに `|dx|, |dz| ≤ T` であり、**「toleranceを超える補正」は実行され得ない**。Task Indexの「補正量tolerance超は従来どおり拒否」は、本ADRでは **「toleranceの内側に一致する配置が無いrunは、pre-pass前と同じ既存HARD gate（`v2.coastal-transition-hard-conflict`等）で、同じ理由で拒否される」** という形で実現する。pre-pass専用のrejection ruleを新設しない。
- report-only段階を先行させるという[§19.1共通契約](../design-v2/task-index.md)の要求は、本pre-passでは「fail-closed化を一切行わない」という形で最初から満たされる。どの時点でも動作するproduction経路（＝`maskFeatureReconcile` absent）が存在する。
- **正直に宣言する残余risk:** opt-inしたrunについては、D2の目的関数が近似であるため、pre-passが選んだoffsetが結果として別のcellで矛盾を生む可能性を排除できない。その場合の結末は「既存gateによる拒否」であり、公開物は生成されない。一方、`disagreement(0,0) = 0`のrunは順序の定義により絶対に動かないので、**既に整合している構成が本Taskによって壊れることはない**。

### D5. HARD不発明原則との整合（Task Indexが明文化を要求した項目）

| 原則 | 本pre-passでの担保 |
|---|---|
| HARD情報を推測で補完しない（AGENTS.md §6／§7、[ADR 0017](0017-deterministic-image-mask-extraction.md)） | maskのno-data cellは目的関数から除外するだけで、値を与えない。maskの値・範囲・digestを変更しない |
| HARDをpriorityやlast-write-winsで上書きしない | maskは常に権威のまま。pre-passはHARD側でなくSOFT側（著者の宣言geometry）だけを動かす |
| 「SOFT reconcile」の意味 | 動かすのは**著者が書いたgeometryという可変な設計意図**であり、HARD constraintではない。補正はopt-in、bounded、可視（report）であり、HARD gateの判定に一切介入しない |
| 画像からHARD geometryを作らない（ADR 0017） | pre-passは画像を読まない。読むのは既にpromote済みの宣言mask fieldと、宣言geometryから生成したrasterだけである |
| 生成物を入力へ焼き込まない | pre-passはmaskを書き換えないので、mask⇔seed束縛を**逆方向に強化しない**。束縛を緩めるのは常にgeometry側の移動である |

**公開intentは補正後のものとする。** Releaseの`source/terrain-intent.json`には実際に生成へ用いたgeometryを封印する。宣言geometryを公開しつつ補正後で生成すると、conformance portfolio（公開intent×公開blockの照合）が意味を失い、Releaseが自己矛盾する。補正量は`MaskFeatureReconcileV2` reportでCLI／Paper summaryへ表示し、著者の手元のrequest／intentは当然そのまま残る。

### D6. 決定性・resource budget（新しいhard-codeを最小化する）

1. **決定性。** 全て整数演算・単一thread・固定順序（`dz`昇順→`dx`昇順）で、locale／timezone／hash順序／module登録順に依存しない。目的関数は宣言intentと宣言maskだけの純関数である。
2. **CPU。** 追加コストは (a) 宣言intentからのBlueprint compile 1回、(b) domain全体のcomposed raster 1 pass、(c) `|A| × (2T+1)²` 回のint比較、である。`(c)`は宣言時に `width × length × (2T+1)² ≤ 128,000,000` で上限を持つ（`|A| ≤ width × length`）。offsetが`(0,0)`のときは (a) のBlueprintをそのまま再利用し、再compileしない。
3. **Memory。** `A`のlandWaterを1 cell 1 byteで保持する（`width × length` bytes、MEDIUM上限1024²で1 MiB）。`(2T+1)²`個のlong counter（T=8で289個）以外の追加常駐は無い。`CoastalSurfaceFieldsV2.estimatedResidentBytes`の契約は不変。
4. **admission。** 既存の`ExportBudgetV2`／scale契約（[ADR 0016](0016-scale-classes-and-execution-planning.md)）／`ScaleDimensionPolicyV2` MEDIUM ceilingを再利用し、新しい寸法・tile・予算のhard-codeを追加しない。LARGEは引き続き`SUPPORTED`と表現しない。
5. **cancel。** 候補走査は既存の`CancellationToken`で行ごとに観測する。拒否・cancel時にartifactを作らない（pre-passはpublishより前段である）。

### D7. Checksum影響の宣言（`V2-19-14` gateが要求する項目）

| 対象 | 影響 | 根拠 |
|---|---|---|
| `maskFeatureReconcile`を宣言しない既存fixture（`harbor-cove-64-honored`／`-plain`／`-guided`／`-river`／`-meander`／`-beach`／`-coastless`／`-detail`／`coastal-honored-400`／`shore-2to1-400`）のterrain field／tile／**block semantic checksum** | **不変** | pre-passはoptional値がpresentの場合だけ実行される |
| 同fixtureの容器byte（request／intent／blueprint canonicalChecksum／manifest） | **不変** | absentのoptionalはJSONへ書き出さない（`foundationBaseLevels`／`foundationDetail`と同一方式） |
| `production-dispatch-registry-v2` registry checksum／`public-dispatch-reachability-v1` projection | **不変** | route集合・support列・materialization分類を変更しない |
| Feature Support Catalog sealed checksum | **不変** | capability昇格を行わない（D8） |
| v1 Schema／generator `3.0.0-phase6`／Release format 1／v1 golden／placement／Undo | **不変** | v2 spineのみを対象とする |
| 新fixture `harbor-cove-64-honored-drift` | 新規追加 | 既存checksumに影響しない |

**「既存fixture完全不変」は努力目標ではなくAcceptance条件**とし、既存の絶対checksum pin（tile semantic checksum `20318e6c…`）で機械的に固定する。動いたらD3・D4の解釈違反であり、停止して本ADRへ戻る（D10）。

### D8. 証拠（conformance case）と昇格しないもの

常設conformance caseとして`harbor-cove-64-honored-drift`を追加する。すべて**公開Releaseのartifactからのみ**測る。

1. **入力の作り方（往復が誤差ゼロであることの担保）。** `harbor-cove-64-honored`のintentの全feature geometryへ`Δn(0, +2)`（＝南へ2 block）を適用したものをdriftしたintentとし、land-water maskとrequest本体は**byte同一で再利用**する（requestは`maskFeatureReconcile: {toleranceBlocks: 4}`だけを追加）。dev-only helper testが「honored intent＋Δ」からcommit済みfixtureをbyte一致で再生成できることを固定する（`V2-18-13`の`RegenerateHonoredCoastalMaskExampleTest`と同じ方式）。
2. **恒等性（最重要のpositive）。** pre-passは`(0, −2)`を選び、`Δn(0,−2) = −Δn(0,+2)`（D1の対称性）により**reconciled intent = honored intentのgeometryと完全一致**する。結果として `FeatureMaterializationV2` によるdrift Release対baseline `harbor-cove-64-honored` Releaseのfinal canonical block stream差分が **`changedCells = 0`**、tile semantic checksumが `20318e6c…` に一致する。「reconcileが著者の意図をbit単位で復元した」ことの機械的証拠である。
   - これは`V2-19-01`が禁じた「意図的no-op（identity slice）をFeature昇格の証拠に使う」ケースではない。本caseはFeature昇格を主張せず、**差分ゼロであること自体が測定対象**である（比較対象が自分自身ではなく、別intentから独立にexportした2つのReleaseである点も異なる）。この区別を証跡文書とtest commentへ明記する。
3. **pre-pass無しでは拒否されること（negative制御）。** 同じdrift intentを`maskFeatureReconcile`無しでexportすると、既存rule `v2.coastal-transition-hard-conflict`で拒否される。これが無いとcase 2は「何も直していない」ことの証明になってしまう。
4. **tolerance不足（negative）。** 同じdrift intentをtolerance 1で走らせると`(0,−2)`が候補外となり、既存gateで拒否される（**新ruleを増やさない**ことの実証）。
5. **恒等保存。** 既に一致している`harbor-cove-64-honored`へtoleranceを宣言してもoffsetは`(0,0)`で、Releaseはbyte不変である（D2のtie-break性質の実測）。
6. **一方向性。** 公開Releaseのland-water sidecarと宣言mask PNGが完全一致し、mask digest（`expectedSha256`）が宣言値のままであること（maskを動かしていないことの実測）。
7. **決定性。** 再export、`tr-TR`／`Pacific/Chatham`で差分0。
8. **portfolio全case適合の維持。** 新caseは既存の全portfolio assertion（EDGE、beach↔backshore連続性、arm landfall、land-mass会計）を`harbor-cove-64-honored`と同一の値で満たす。

**昇格しないもの:** capability列（`paper_apply`を含む）、dispatch route集合、`PRODUCTION_CONNECTED`集合、寸法上限、`CompositionProfileRegistryV2`の登録。pre-passはFeatureではなく入力整列の規則であり、新しいFeatureKindを追加しない。

### D9. 明示的な非Scope

- **形状（shape）reconcile。** 手描きmaskと生成rasterのcell単位一致は平行移動では達成できない。任意形状への追従はfeature parameterの再導出またはmask側の許容band導入を要し、本ADRの守備範囲外である（別ADR）。
- **per-featureのoffset**、回転、拡大縮小、非整数block移動、per-vertex snap（D1、relation破壊の回避）。
- **mask側の補正**（maskをgeometryへ寄せる／maskへ膨張・収縮を施す）。凍結1。
- **`HEIGHT_GUIDE`のregistration。** 本pre-passはland-water shorelineだけを目的関数とする。guideの位置合わせは別入力・別metricであり、同時に導入しない。
- **modifier境界のtaper band**（[ADR 0041](0041-coherent-detail-kernel.md) A5）、erosion、近傍filter。
- **foundation producer（`PLAIN`等）を目的関数へ含めること。** producerは平行移動の**対象**ではあるが（relation保存のため）、offsetの**決定**には寄与しない。producer由来のmedium矛盾は既存の`v2.foundation.producer-mask-medium-conflict`が引き続き拒否する。
- **LARGE**（1024超）でのpre-pass（scale契約は不変）。

### D10. 停止条件

次のいずれかが必要になった時点で実装を止め、本ADRへ差し戻す。

- `maskFeatureReconcile`を宣言しない既存fixtureのchecksumまたは容器byteが動く（D7違反）。
- maskの値・digest・bindingを変更する必要が生じる（凍結1違反）。
- 既存HARD gateの判定・閾値・rule idを変更する必要が生じる（凍結2違反）。
- 剛体平行移動以外の変形、またはper-featureのoffsetが必要になる（凍結3・D1違反）。
- pre-pass専用のfail-closed ruleが必要になる（D4違反）。
- 評価予算を宣言時に安全に定義できない（AGENTS.md §13、D6違反）。
- 新capability名・新artifact type／version・Release format変更・新Schema `$id`が必要になる（凍結5）。
- 決定性（whole==tiled、thread／locale／timezone、再export）を保証できない。

## Consequences

- 「maskを先に固定し、geometryを後から寄せる」という運用が、**bounded・deterministic・可視**な形で初めて成立する。画像から抽出→promote→宣言したmaskに対し、feature geometryの数block分の位置ずれが全面拒否にならなくなる。
- mask⇔seed束縛は**弱まる方向にしか動かない**。pre-passはmaskを書き換えないため、監査が問題視した「生成出力を入力へ焼き込む」構造をこれ以上強化しない。
- relationが剛体移動で保存されるため、composition interaction・conformance portfolio・Phase gateの前提はそのまま維持される。
- 新しい拒否規則が増えないので、`V2-19-16` Phase gateが再検証すべきfail-closed契約の集合は`V2-19-13`時点から変わらない。
- 目的関数が近似であることを契約として明示したため、「pre-passが通したのだから正しい」という誤読が生じない。最終的な正しさの判定は常に既存HARD gateが持つ。
- 形状reconcile（D9）が未解決のまま残ることを隠さない。手描きmaskへの完全追従は依然として不可能であり、[current-limitations](../design-v2/current-limitations.md)へ記載する。

## Alternatives considered

### A1. per-featureに独立のoffsetを与える

maskへの一致度は単一剛体移動より必ず良くなる（自由度が増えるため）。しかし`ENCLOSES`／`ADJACENT_TO`／`OVERLAPS`といった宣言済みrelationと、breakwater armのlandfall・basin entranceのような相互依存geometryが**黙って壊れる**。AGENTS.md §7は「feature接続、内包、上下流、supportはrelationを正本とする」と定めており、relationを検証せずに相対位置を変える機構は入れられない。relationを検証しながらper-feature最適化を行うのは別規模の問題である。**不採用**（D1）。将来必要になったら独立ADRとする。

### A2. maskをfeature geometryへ寄せる（mask側の補正）

「生成できる形」に入力を合わせるので必ず成功するが、これは監査が問題視した mask⇔seed束縛の**強化**そのものであり、HARDの正本を生成結果で上書きする行為でもある（ADR 0038 D2-3違反）。**不採用**（凍結1）。

### A3. 不一致cellをtolerance内なら黙って許容する（compositorのHARD判定を緩める）

`v2.coastal-transition-hard-conflict`に「不一致がN cell以下なら通す」という閾値を入れれば、位置ずれは吸収できる。しかしこれはHARD constraintをpriority／閾値で上書きすることであり（AGENTS.md §7）、maskとの一致を測るconformance metric（`V2-18-07`のRasterResidual、`V2-18-11`のEDGE実測）の意味も同時に壊す。pre-passが**geometryを動かして真に一致させる**のと、**一致していないことを許す**のは別物である。**不採用**（凍結2）。

### A4. 補正をintent側（`TerrainIntentV2`）で宣言する

「どれだけずらしてよいか」は設計意図に見える。しかし (a) pre-passの相手であるmask実体と予算はrequest側にあり、宣言が別文書へ分かれると評価予算の検査がrequest単独で閉じない、(b) AI providerがoffset toleranceを生成し始めると「providerが位置合わせの許容量を決める」経路が増える、(c) 新constraint種別はhistoric 60-kind Schemaとcanonicalizationへ波及し1 Taskを超える。`foundationDetail`（ADR 0041 A1）と同じ判断である。**不採用**（D3）。

### A5. 補正を著者向けの「提案」に留め、生成には反映しない（advisory-only）

pre-passがoffsetを報告するだけで、著者が自分でintentを書き換える方式。安全側だが、authoring負担の軽減という目的をほとんど達成しない（結局手作業が残る）。またadvisoryだけなら、報告値が正しいことを検証する手段が無い。**不採用。** ただしreport自体は本ADRでも出力する（D5）ため、advisoryとしての価値は同時に得られる。

### A6. 剛体移動をsub-block（millionths）解像度で探索する

理屈上は一致度が上がるが、候補集合が巨大になり評価予算を定義できない。またblock単位に量子化されるrasterに対しsub-blockの探索を行っても、目的関数が階段状で最適解が一意に定まらず、tie-breakの正当性が説明できない。整数block探索は`|A| × (2T+1)²`で上限が閉じる。**不採用**（D2・D6）。

## Acceptance／実装開始条件

1. 人間が本ADR全文をレビューし、**D1（宣言feature集合全体への単一剛体平行移動。per-feature・形状変形は行わない）とD3（宣言面＝request levelのoptional `maskFeatureReconcile`、tolerance 1..8 block、opt-in）を明示採択する。**
2. Statusを`Accepted`へ更新し、採択日・採択内容をDecision節へ記録する。
3. その後に限り`V2-19-14`の実装（stage、model／Schema／codec、export spine配線、CLI／Paper verb、fixture、conformance case、docs同期）を開始する。
4. 実装完了時、D7の「既存fixture完全不変」とD8の証拠一式（恒等復元、pre-pass無しでの拒否、tolerance不足での拒否、恒等保存、一方向性、決定性、portfolio適合）がtestで機械的に固定されていることを確認する。

## References

- [cross-cutting-audit-2026-07-23](../audits/cross-cutting-audit-2026-07-23.md) §4.2-4／§5.1／§5.2-6／提案T-A2
- [ADR 0038](0038-macro-foundation-contract.md) D1／D2-2／D2-3／D5-3／D6／D9（macro foundation契約、maskがland-waterの正本）
- [ADR 0040](0040-coastal-contributor-set-cardinality.md)（coastal contributor部分集合）
- [ADR 0041](0041-coherent-detail-kernel.md)（optional request宣言・budget・決定性の先例、A5 taper band）
- [ADR 0017](0017-deterministic-image-mask-extraction.md)（画像由来draftをHARD化しない）
- [ADR 0016](0016-scale-classes-and-execution-planning.md)（scale／budget契約）
- [task-index §19](../design-v2/task-index.md) `V2-19-14`
- [task-execution-guide §7](../design-v2/task-execution-guide.md) Support capability gate
- [V2-19-06 audit](../design-v2/audits/v2-19-06-height-guide-macro-foundation-consumer.md)／[V2-19-07 audit](../design-v2/audits/v2-19-07-foundation-producer-tier.md)／[V2-19-09 audit](../design-v2/audits/v2-19-09-coastal-contributor-subset.md)
