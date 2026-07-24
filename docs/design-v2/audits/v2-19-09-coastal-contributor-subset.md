# V2-19-09 evidence: coastal contributor subsets（ADR 0040）

> Status: 完了（2026-07-24）。ADR [0040](../../adr/0040-coastal-contributor-set-cardinality.md) はD1「サイズ0〜4」で同日人間承認済み。進捗の正本は [docs/roadmap.md](../../roadmap.md)、Scopeの正本は [task-index §19](../task-index.md)。

## 1. 何が壊れていたか

[2026-07-23横断監査](../../audits/cross-cutting-audit-2026-07-23.md) §2.3が実測で確定した欠陥（提案T-C1）:

```java
// CoastalGeneratorRuntimeV2（V2-19-09以前）
private static final Set<FeatureKind> REQUIRED =
        EnumSet.of(SANDY_BEACH, HARBOR_BASIN, BREAKWATER_HARBOR, ROCKY_CAPE);
...
if (!seen.containsAll(REQUIRED)) {
    throw new IllegalArgumentException(
            "surface-2_5d export requires all four V2-2 coastal contributors; missing " + ...);
}
```

**「beachだけの海岸」はpublic surface exportできない。** 監査§8はこれを「確認（rev.1の誤りを訂正）」として記録している。

これはV2-2期の設計前提の残存物である。当時はfeature非所有cellに背景ownerが存在せず、`SurfaceBaselineV2`の定数充填へフォールバックしていた（[macro foundation監査](../../audits/macro-foundation-conformance-audit-2026-07-22.md)実測73.0%）。coastal 4種の合成が事実上「地表全域の正本」であり、1種でも欠ければ地表の意味が欠落した。

前提はV2-18／V2-19-07で完全に変わっている。

- ADR 0038 **D5-3**: 「surface modifierは**foundation ownerではない**。…**被覆要求・単一owner要求を課さない**」
- **`V2-18-09`／`V2-18-10`**: macro foundationのbackground candidateが全cellを所有し、owner被覆100%がfail-closed gate（`SurfaceFoundationOwnerGateV2`）になった。
- **`V2-19-07`**: `MacroFoundationV2.ProducerLayer`が実体化し、background以外のfoundation ownerも成立する。

つまり「4種必須」はADR 0038 D5-3が明示的に禁じた**modifier tierの被覆要求**であり、契約と実装の直接の不整合だった。

## 2. 実装（ADR 0040 D1〜D5）

| 決定 | 変更 |
|---|---|
| D1 cardinality | `CoastalGeneratorRuntimeV2`の`REQUIRED`検査を削除。contributor集合はcoastal 4 kindの**任意の部分集合（サイズ0〜4）**。サイズ0（coastal featureゼロ）ではblueprintがtransition planを封印しないため、runtimeはcompositorを持たず`composeAt`が canonical `CompositionSample.outside()` を返す |
| D2 無条件緩和 | foundationの有無で分岐しない。legacy baseline経路（foundation入力なし）はcontributor数によらず`SurfaceFoundationOwnerGateV2`が`v2.export.foundation-owner-coverage-incomplete`で拒否する。被覆を守るgateはfoundation tierに1つだけ |
| D3 欠落kindのfield | `CoastalSurfaceFieldsV2.writeDescriptorFields`が9個のper-kind descriptor fieldを一箇所で書く。欠落kindはそのkindのcanonical OUTSIDE値（beach width＝`NO_DATA`／band 0、harbor region 0・water 0・depth `NO_DATA`、breakwater region 0、cape region 0・exposure 0・index 0）。**新sentinel・新field id・新field semanticなし。`FIELD_IDS`の集合と順序は不変** |
| D4 resolver／validation／preview | block resolverは中立値をそのまま読むため分岐追加なし。`CoastalValidatorV2`はplan listを走査するため未宣言kindのmetricは生成されない（欠測をPASSとして捏造しない）。`SurfaceReleaseCapabilityVerifierV2.requiresCoastalMetrics`は既存実装のままサイズ0を正しく扱う |
| D5 runtime precondition | `ProductionRoutePreconditionsV2`の4 pipeline全てのrequired companion集合が**空**になった。クラス・API・rule id `v2.design.route-companion-missing`・`design-support-lint-v1` contract・`DesignSupportSurfaceV2`のJSON shapeは**すべて不変**（contract bumpなし） |

`CoastalTransitionCompositorV2.CompositionSample.outside()`をpublic accessorとして公開した（既存private定数の露出のみ。値は不変）。

## 3. checksum影響（ADR 0040 D6）

| 対象 | 結果 | 証拠 |
|---|---|---|
| 4種fixtureのtile semantic checksum | **不変** `20318e6c…` | `CoastalContributorSubsetV2Test.theFourContributorReleaseIsUnchangedByTheRelaxation`（絶対pin） |
| 4種fixtureの容器byte・Release checksum | **不変** | 既存`MacroFoundationProducerV2Test`／portfolio／strict verify回帰が全て素通り |
| `production-dispatch-registry-v2` registry checksum | **不変** | route集合・handler chain未変更 |
| `public-dispatch-reachability-v1` projection checksum | **不変** | support列・reachability・materialization分類未変更 |
| Feature Support Catalog sealed checksum | **不変** | catalog未変更（§5参照） |
| v1 Schema／generator `3.0.0-phase6`／Release format 1／v1 golden／placement／Undo | **不変** | v2 spineのみ変更 |
| 新Schema／新capability／新artifact format | **なし** | — |

## 4. conformance case（ADR 0040 D8）

### 4.1 `harbor-cove-64-honored-beach`（サイズ1、headline case）

`harbor-cove-64-honored`から`HARBOR_BASIN`／`BREAKWATER_HARBOR`／`ROCKY_CAPE`と、それらを端点とするrelation／constraint／structureを除いたintent。HARD `LAND_WATER_MASK`は`V2-18-13`が確立した規則`active ? composed : macro-background`をbeach-only compositionへ適用して再生成した（背景は`harbor-cove-64-honored`のmask）。

- 再生成helper: `RegenerateBeachOnlyCoastalMaskExampleTest`（dev-only `@Disabled`）
- 生成物: `examples/v2/diagnostic/maps/harbor-cove-64-honored-beach-land-water-u8.png`、sha256 `9127fa3e…`、背景に対する変化 **42 cell**
- 背景はshoreline／harbor water／rocky headlandの形状を**declared HARD入力として保持**する。beach-only intentが落としたのはそれらを整形した**modifier**であってcellの分類ではない。ADR 0038 D1どおりmacro foundationがそれらのcellを所有する

**probeの記録:** honored maskをbyte同一で再利用する案は`v2.coastal-transition-hard-conflict`（`(21,24)`）で拒否された。4種合成では他contributorが取ったcellの分類がmaskに焼かれており、beach単独ではbeach自身の分類と矛盾するためである。既存rule追加なしで正しくfail closedすることの確認でもある。

### 4.2 `harbor-cove-64-honored-coastless`（サイズ0）

`PLAIN` 1件＋`harbor-cove-64-honored`のmask（**byte同一で再利用**、sha `b1d98bff…`）だけを宣言する。活性modifierが存在しない以上maskとの矛盾は原理的に生じないため、maskをそのまま使える。

実測（公開Releaseから）:

- sealed blueprintの`coastalTransitionPlans`／`coastalFeaturePlans`はともに**空**
- 公開`ACTUAL_LAND_WATER` sidecarは宣言maskと**cell単位で完全一致**（4096/4096）
- HARD `EDGE_CLASSIFICATION`（north-is-land／south-is-sea）はmacro foundation単独で充足
- sidecarのfield semantic集合はDESIRED／ACTUAL/RESIDUAL LAND_WATERで4種fixtureと同形

### 4.3 negative（fail closedの維持）

| ケース | 結果 |
|---|---|
| legacy baseline経路（`foundationBaseLevels`なし）＋beach-only | `SurfaceFoundationOwnerRejectedV2`／`v2.export.foundation-owner-coverage-incomplete`。**「4種不足」ではなくowner被覆不足**として拒否される（D2） |
| 同一kindのcontributor 2個 | 既存`surface-2_5d export supports one contributor per coastal kind`（凍結1） |
| 部分集合宣言でmaskが矛盾 | 既存`v2.coastal-transition-hard-conflict`（§4.1 probeで実測。規則追加なし） |

### 4.4 既存caseの不変性

`IntentConformancePortfolioV2`の既存6 caseはmeasurement値・checksumともに不変。portfolioのmeasurementはcontributorごとに**任意化**した（`beach`／`backshorePlains`は`Optional`、`arms`は宣言なしで空list）。各caseは`declaredCoastalKinds`を明示宣言し、testは「測定の有無＝宣言の有無」を先に固定するため、contributorが黙って落ちた回帰は依然として失敗する。

`MacroFoundationPhaseGateV2Test`（V2-18-12 Phase gate、人間承認済み）は**4種を宣言するcaseだけ**を対象にfilterした。同gateのassertion（beach continuity／breakwater landfall／sea-stack会計）は4種合成についての主張であり、欠落contributorを許容させると静かに弱まるためである。filterには「4種caseが1件でも脱落したら失敗する」drift guard（`theGateStillCoversEveryFourContributorPortfolioCase`、gated 6件）を付けた。subset caseは`IntentConformancePortfolioV2Test`と`CoastalContributorSubsetV2Test`が担い、Phase gate昇格は`V2-19-16`が行う。

## 5. capability（ADR 0040 D7）

昇格なし。`PLAIN.standalone_usage`は`PARTIAL`据え置きである。D1により`PLAIN`＋HARD maskのintentは**exportできるようになった**が、`SUPPORTED`には [task-execution-guide §7](../task-execution-guide.md) が要求する証拠一式を**standalone構成そのもの**で揃える必要がある（`V2-19-07`が測ったのはcoastal複合fixture上の`PLAIN`）。Paper列・`paper_apply`は全kind不変（V2-17専管）。

`BuiltInFeatureSupportCatalogV2`の`PLAIN` `standalone_usage`注記は、**sealed catalog checksumを動かさないため据え置いた**（ADR 0040 D6が同checksumを不変と宣言している。D7の「影響する場合はcatalog外へ置く」に従う）。据え置いた注記「the surface path still requires the four coastal contributors, so a PLAIN-only intent cannot export」は実装より**保守的に**外れた**stale記述**であり、正本は [current-limitations](../current-limitations.md) と本文書である。訂正は次に意図的にcatalogを変更するTask（`standalone_usage`昇格leafが自然な同居先）が同一変更で行う。

## 6. design lintへの帰結（V2-19-08の測定値が変わった箇所）

| 対象 | V2-19-08実測 | V2-19-09以降 |
|---|---|---|
| `DesignSupportSurfaceV2.requiredCompanionKinds` | `[BREAKWATER_HARBOR, HARBOR_BASIN, ROCKY_CAPE, SANDY_BEACH]` | `[]` |
| beach単体intent | `NOT_SELECTABLE`＋`route-companion-missing` 3件 | `SELECTABLE`、findingなし |
| `PLAIN`単体（`oblique-multi-view`、CLI／Paper design） | `NOT_SELECTABLE`＋`route-companion-missing` | `SELECTABLE` |

これはlintの退行ではなく、**報告していた制約が実際に無くなったこと**の正しい反映である。rule id・contract id・`NON_GATING`分類・JSON shapeは不変で、`RULE_COMPANION_MISSING`は将来のpipelineのために残す（空になっただけ）。

## 7. 実行したtest

```bash
./gradlew test
./gradlew build
```

新規: `CoastalContributorSubsetV2Test`（7 test）、`RegenerateBeachOnlyCoastalMaskExampleTest`（dev-only `@Disabled`）。
更新: `IntentConformancePortfolioV2`／`IntentConformancePortfolioV2Test`（case 2件追加＋measurement任意化）、`MacroFoundationPhaseGateV2Test`（4種caseへのfilter＋drift guard）、`MacroFoundationProducerV2Test`（producer単体intentの期待をexport成功へ反転）、`DesignSupportLintServiceV2Test`／`LandformCraftCliV2Test`／`PaperV2ReferenceImageDesignV2Test`（lint出力の変化）。

## 8. 非Scope

- Paper `SUPPORTED`昇格（V2-17専管）、`PLAIN.standalone_usage`昇格（§5）
- 新coastal kindのmodifier追加、`SurfaceBaselineV2`の削除（ADR 0038 D8のまま）
- Release format／capability／Schema `$id`／artifact type・versionの変更（いずれも実施していない）

## References

- [ADR 0040](../../adr/0040-coastal-contributor-set-cardinality.md)
- [ADR 0038](../../adr/0038-macro-foundation-contract.md) D1／D5-3／D7／D9
- [cross-cutting-audit-2026-07-23](../../audits/cross-cutting-audit-2026-07-23.md) §2.3／§8／提案T-C1
- [task-index §19](../task-index.md) `V2-19-09`
- [current-limitations](../current-limitations.md)
