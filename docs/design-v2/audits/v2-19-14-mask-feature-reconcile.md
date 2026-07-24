# V2-19-14 監査記録: mask⇔feature SOFT reconcile pre-pass

- Task: `V2-19-14`（Track A、[Task Index §19.2](../task-index.md)）
- Date: 2026-07-24
- 正本ADR: [ADR 0043](../../adr/0043-mask-feature-reconcile-pre-pass.md)（同日`Accepted`、**D1 宣言feature集合全体への単一剛体平行移動／D3 request levelのoptional `maskFeatureReconcile`（tolerance 1..8、opt-in）**を人間承認で明示採択）
- 起点: [2026-07-23横断監査](../../audits/cross-cutting-audit-2026-07-23.md) §4.2-4（mask⇔seed束縛）／提案T-A2

## 1. 解消した欠陥

監査§4.2-4が確定した構造的弱み — **「`V2-18-09`のmask再生成規則が生成出力を入力へ焼き込み、feature geometryとmaskの整合責任が全面的にユーザー側にある」** — に対し、逆方向（宣言geometry→mask）の bounded・deterministic な整列経路を追加した。

修正前の実測挙動は次のとおりである。

- HARD `LAND_WATER_MASK`はcompositorへHARD sourceとして注入され、maskと一致するactive contributorが1つも無いcellは`v2.coastal-transition-hard-conflict`でexport拒否される（ADR 0038 D2-3どおりで、この拒否自体は正しい）。
- そのためmaskは生成出力とcell単位で一致している必要があり、`V2-18-13`／`V2-19-09`はいずれもmaskを`active ? composed : macro-background`で**再生成**した。すなわち「geometryかseedを触ったらmaskを作り直す」が唯一の実務手順だった。
- 結果として、**形が正しくても数block分ずれた宣言geometryは全面拒否**であり、手描きmask／画像抽出maskを先に固定する運用が成立しなかった。

## 2. 実装

### 2.1 pre-pass（`MaskFeatureReconcileStageV2`、`core.v2.export`）

`CoastalSurfaceExportPipelineV2.prepare`のBlueprint compile直前に入り、1 export runにつき**ちょうど1つ**の整数block平行移動`(dx, dz)`を決めて`TerrainIntentV2.features[].geometry`の全control pointへ適用する。

| 項目 | 内容 |
|---|---|
| 候補集合 | `|dx| ≤ T` かつ `|dz| ≤ T`（Chebyshev球、`(2T+1)²`個、`(0,0)`を必ず含む） |
| 目的関数 | `disagreement(d) = Σ_{u ∈ A} [ composed(u).landWater ≠ mask(u+d) ]`。`A`はHARD source無しで合成したcoastal activeなcell集合（`v2.coastal-transition-hard-conflict`が評価される集合と同一） |
| no-data | `mask(u+d)`がno-dataのcellは比較対象から除外（**maskが言っていないことを推測しない**） |
| 無効候補 | activeなcellがdomain外へ出る／control pointが正規化範囲`[0, 10⁶]`を外れる候補は順位づけから除外 |
| 全順序 | `(disagreement, |dx|+|dz|, dz, dx)`の辞書式最小。第2キーにより**既に一致しているgeometryは絶対に動かない** |
| 正規化delta | `Δn(d, extent) = sign(d) × floor((|d|×2×10⁶ + (extent−1)) / (2×(extent−1)))`。`Δn(+d) + Δn(−d) = 0`が整数演算で厳密に成立 |

適用範囲は`features[].geometry`だけである。intent内で座標を持つのはここだけで（`Constraint`は`METRIC_RANGE`／`EDGE_CLASSIFICATION`の2種、`StructureRequest`は`id`／`kind`／`count`／`preferredFeatureId`）、`mapReferences`・`relations`・`constraints`・`environment`・`structures`・`provenance`は不変である。

### 2.2 宣言面

request levelのoptional `maskFeatureReconcile { toleranceBlocks: 1..8 }`（Schema `generation-request-v2.schema.json`へ追加、absentでは書き出さない）。`foundationBaseLevels`必須、`toleranceBlocks = 0`は拒否、`width × length × (2T+1)² ≤ 128,000,000`を**宣言時**に検査する（clampせず拒否）。authoring verbは`v2 request mask-reconcile <request-id> <tolerance-blocks>`（CLI／Paper、`request.edit`）。

### 2.3 foundation stageの分割

`MacroFoundationStageV2.resolve`を`bindInputs`（map decode、1回だけ）＋`resolve(inputs, request, intent)`（純関数、producer layer構築）へ分割した。pre-passはmaskをbind後・producer構築前に走るため、mapを2回decodeせずに済み、producer（`PLAIN`等）も整列後のgeometryから構築される。挙動は`maskFeatureReconcile` absent時に完全不変である。

## 3. 実測（すべて公開Releaseから）

### 3.1 reconcile report（`harbor-cove-64-honored-drift`）

```text
MaskFeatureReconcileV2[toleranceBlocks=4, offsetXBlocks=0, offsetZBlocks=-2,
  evaluatedCells=958, disagreementBefore=151, disagreementAfter=0,
  candidateOffsets=81, rejectedOffsets=65, translatedFeatures=5]
```

`rejectedOffsets=65`は、このfixtureのcoastal compositionがdomain edgeへ接しており、多くのoffsetがactive cellを世界の外へ押し出すためである。pre-passはそれらを**評価せず**（存在しないcellを推測しない）候補から外す。

### 3.2 恒等復元（positive、最重要）

`harbor-cove-64-honored-drift`は`harbor-cove-64-honored`の全feature geometryへ`Δn(0,+2)`（南へ2 block）を適用したもので、land-water maskとrequest本体は**byte同一で再利用**している（requestは`maskFeatureReconcile` 1項目だけを追加）。

- pre-passは`(0, −2)`を選び、`Δn(0,−2) = −Δn(0,+2)`により**publish済みintentのgeometryは`harbor-cove-64-honored`と完全一致**する（`MaskFeatureReconcileV2Test.thePrePassRestoresTheAuthoredGeometryExactly`）。
- したがって公開Releaseのtile semantic checksumは四contributor fixtureの絶対pin **`20318e6c…`** と一致し、`FeatureMaterializationV2`によるbaselineとのfinal canonical block stream差分は **`changedCells = 0`**（`comparedCells = 167936`）である。
- これは`V2-19-01`が禁じた「意図的no-op（identity slice）をFeature昇格の証拠に使う」ケースではない: 本caseはFeature昇格を主張せず、比較対象は**別intentから独立にexportした2つの公開Release**であり、差分ゼロであること自体が測定対象である。この区別はportfolio testのコメントにも明記した。

### 3.3 negative（pre-passが実際に仕事をしていることの制御）

| case | 期待 | 実測 |
|---|---|---|
| drift intent を`maskFeatureReconcile`無しでexport | 既存gateで拒否 | `v2.coastal-transition-hard-conflict`（`MaskFeatureReconcileV2Test.theDriftedGeometryIsRejectedWithoutThePrePass`、portfolio側にも`theDriftCaseIsOnlyExportableBecauseOfThePrePass`として常設） |
| drift intent を tolerance 1 でexport | `(0,−2)`が候補外→**新ruleではなく既存gate**で拒否 | 同上。message chainに`mask feature reconcile failed`が現れないことも固定 |
| `foundationBaseLevels`無しで宣言 | 宣言時拒否 | `IllegalArgumentException`（CLIは非0終了） |
| tolerance 0／9 | 宣言時拒否 | 同上 |
| 1024²×tolerance 6 | 評価予算超過で宣言時拒否 | 同上（tolerance 5は受理） |

### 3.4 不変条件

| 項目 | 実測 |
|---|---|
| 既に一致しているgeometry（`harbor-cove-64-honored`＋tolerance 4宣言） | offset `(0,0)`、`disagreementBefore = 0`、`translatedFeatures = 0`、tile semantic checksum `20318e6c…`（`geometryThatAlreadyAgreesWithTheMaskIsNeverMoved`） |
| maskの一方向性 | 公開requestの`constraintMaps`が宣言と完全一致、公開intentの`artifactId`が宣言input digest（`constraint:land-water:sha256-b1d98bff…`）のまま。公開geometryだけが宣言と異なる（`thePrePassNeverMovesTheDeclaredMask`） |
| 決定性 | `tr-TR`／`Pacific/Chatham`での再export後もreport・blueprint checksum・tile semantic checksumが一致 |
| `Δn`の対称性 | extent 2／33／64／65／400／1024 × 1..8 blockで`Δn(+d) + Δn(−d) = 0` |
| portfolio | 新case `harbor-cove-64-honored-drift`が既存の全assertion（EDGE 1.000000、beach↔backshore連続性、両arm landfall、land-mass会計）を四contributor fixtureと同じ値で充足。`MacroFoundationPhaseGateV2Test`のgated caseは7→**8**へ拡大（gateを狭めていない） |

### 3.5 checksum影響（ADR 0043 D7）

| 対象 | 影響 |
|---|---|
| `maskFeatureReconcile`を宣言しない既存fixture（`harbor-cove-64-honored`／`-plain`／`-guided`／`-river`／`-meander`／`-beach`／`-coastless`／`-detail`／`coastal-honored-400`／`shore-2to1-400`）のterrain field／tile／block semantic checksumと容器byte | **不変**（absentのoptionalはJSONへ書き出さない。tile pin `20318e6c…`通過、full suite PASS） |
| `production-dispatch-registry-v2` registry checksum／`public-dispatch-reachability-v1` projection／sealed catalog checksum | **不変**（route集合・support列・materialization分類・capability昇格なし） |
| v1 Schema／generator `3.0.0-phase6`／Release format 1／v1 golden／placement／Undo | **不変** |

## 4. 非Scope（隠さず記録する残存制約）

- **形状（shape）reconcile。** 手描きmaskと生成rasterのcell単位一致は剛体平行移動では達成できない。任意形状への追従はfeature parameterの再導出またはmask側の許容band導入を要し、別ADRである。本pre-passが解くのは**registration（位置ずれ）**だけである。
- **目的関数は推定器である。** compositorのper-cell HARD述語そのものではなく（contributorが重なるcellで乖離し得る）、最終的な正しさの判定は既存HARD gateが持つ。opt-inしたrunでpre-passが選んだoffsetが別のcellで矛盾を生む可能性は排除できず、その場合の結末は既存gateによる拒否（公開物は生成されない）である。`disagreement(0,0) = 0`のrunは順序の定義により絶対に動かないため、既に整合している構成が本Taskで壊れることはない。
- per-featureのoffset、回転、拡大縮小、sub-block解像度の探索、mask側の補正、`HEIGHT_GUIDE`のregistration、modifier境界のtaper band、erosion、LARGEでのpre-pass。
- **reportの表示は`surface-2_5d` routeだけである。** pre-pass自体は`CoastalSurfaceExportPipelineV2.prepare`を共有する全pipeline（`hydrology-plan`／`environment-fields`／`sparse-volume`）で走るが、`Release2HydrologyExportApplicationServiceV2`等は`intentContributionCoverage`と同様に`Optional.empty()`を返すため、CLI／Paper summaryへ`maskFeatureReconcile`が出るのはsurface routeに限られる。これは`V2-18-02` coverage reportからある既存の非対称で、本Taskでは広げも狭めもしていない。
- capability昇格・dispatch route追加・新Schema `$id`・新artifact type／versionは一切ない。

## 5. 検証

```bash
./gradlew test
./gradlew build
```

full suite PASS（`MaskFeatureReconcileV2Test` 11件、`IntentConformancePortfolioV2Test`の新規2件を含む）。
