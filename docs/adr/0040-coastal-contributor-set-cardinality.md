# 0040: coastal contributor set cardinality（`surface-2_5d` modifier tierの被覆要求撤廃）

- Status: **Accepted**（2026-07-24 人間承認、**D1 サイズ0〜4を明示採択**）
- Date: 2026-07-24（起草・採択同日）
- Decision scope: `V2-19-09`。適用範囲は `surface-2_5d` production export spine（`CoastalGeneratorRuntimeV2`／`CoastalSurfaceFieldsV2`）、`ProductionRoutePreconditionsV2`が公開するruntime precondition表、およびそれを読む`V2-19-08` design support lint
- Depends on: [ADR 0038](0038-macro-foundation-contract.md) D1／D5-3／D7／D9、`V2-18-09`（macro foundation production spine）、`V2-18-10`（surface foundation owner gateのfail-closed昇格）、`V2-19-07`（foundation producer tier）、`V2-19-08`（design support lint）、[2026-07-23横断監査](../audits/cross-cutting-audit-2026-07-23.md) §2.3／提案T-C1
- Blocks（解消済み）: `V2-19-09`の実装は2026-07-24の人間承認（D1 サイズ0〜4の明示採択）により開始可能となった

## Context

`CoastalGeneratorRuntimeV2`は、frozen Blueprintから4種のcoastal generatorとtransition compositorを組み立てる際、次を検査する。

```java
private static final Set<TerrainIntentV2.FeatureKind> REQUIRED =
        EnumSet.of(SANDY_BEACH, HARBOR_BASIN, BREAKWATER_HARBOR, ROCKY_CAPE);
...
if (!seen.containsAll(REQUIRED)) {
    throw new IllegalArgumentException(
            "surface-2_5d export requires all four V2-2 coastal contributors; missing " + ...);
}
```

[横断監査 §2.3](../audits/cross-cutting-audit-2026-07-23.md)（rev.2、独立レビュー照合済み）は、この検査により **「beachだけの海岸」がpublic surface exportできない**ことを実測で確定した（監査§8「coastal 4種は全部必須（docsが正しい）＝確認」）。同§2.3は、rev.1が`CoastalTransitionPlanCompilerV2`の「coastal feature ≥1」を根拠にdocsをstaleと誤判定したこと、および`current-limitations.md`の「4種必須」記述が正しいことも訂正記録している。

この要求はV2-2期の設計前提に由来する。当時、surface production経路には**背景ownerが存在せず**、feature非所有cellは`SurfaceBaselineV2`の定数充填へフォールバックしていた（[macro foundation監査](../audits/macro-foundation-conformance-audit-2026-07-22.md)の実測73.0%）。つまりcoastal 4種の合成が事実上「地表全域の正本」であり、1種でも欠ければ地表の意味が欠落した。「4種全部」はその状態での**modifier tierに置かれた被覆要求**である。

前提は`V2-18`／`V2-19-07`で完全に変わった。

1. **ADR 0038 D1** が、surface domain内の各XZ cellに**ちょうど1つのeffective foundation owner**を要求し、明示入力（HARD `LAND_WATER_MASK`＋`foundationBaseLevels`、および`V2-19-06`の`HEIGHT_GUIDE`）から生成されるmacro baseを**全域を覆うbackground candidate**と定めた。
2. **ADR 0038 D5-3** は、modifier tierについて次を明文で禁じている — 「surface modifierは**foundation ownerではない**。各cellに0..N個のmodifier寄与が共存でき、**被覆要求・単一owner要求を課さない**」。
3. **`V2-18-10`** が surface foundation owner被覆100%をfail-closed gate（`SurfaceFoundationOwnerGateV2`）としてexport必須化した。地表の被覆を保証する権限は、いまやfoundation tierのgateが**単独で**持つ。
4. **`V2-19-07`** が`MacroFoundationV2.ProducerLayer`を実体化し、background以外のfoundation ownerも成立するようになった。

したがって現状の「coastal 4種全部」は、ADR 0038 D5-3が明示的に禁じた**modifier tierの被覆要求そのもの**であり、契約と実装の直接の不整合である。監査の提案T-C1（本ADRの起点）が求めているのはこの整理であって、新機能の追加ではない。

## 凍結（本ADRがAcceptedになっても変更しない）

1. **kindごと1 contributorの一意性。** `CoastalTransitionPlanV2`の「1 kindにつき contributor 1個」（`surface-2_5d export supports one contributor per coastal kind`）は不変。
2. **coastal 4種の`PRODUCTION_CONNECTED`集合・Paper `SUPPORTED` exact set・`public-dispatch-reachability-v1` projection。** 本ADRはsupport列も公開dispatch route集合も変えない（[ADR 0039](0039-offline-production-route-eligibility.md) 凍結1／2を継承）。
3. **`SurfaceBaselineV2`の削除・deprecation方針。** ADR 0038 D8のまま。本ADRは引数の受理／無視規則を変えない。
4. **新capability名・新artifact type／version・Release format変更・新Schema `$id`を導入しない。** 必要になったら停止し別ADR／Task IDを登録する（AGENTS.md §9、task-index §19.1共通停止）。
5. **HARD情報の推測補完の禁止。** 欠落したcontributorのcellを「推測されたcoastal形状」で埋めない。欠落kindは**寄与ゼロ**であり、そのcellはfoundationが所有する。
6. **本ADRが`Proposed`の間は`V2-19-09`の実装（runtime緩和、fixture追加、portfolio変更、docs同期）を開始しない。**

## Decision（人間承認で確定した）

> **採択（確定）: D1のサイズ0〜4（空集合を含む任意の部分集合）を採用する。** 2026-07-24の人間承認（ADR全文レビュー＋D1 cardinalityの明示採択）によりStatusを`Accepted`とする。代替案A1（サイズ1〜4のみ）は不採用であり、記録としてAlternatives節に残す。`V2-19-09`の実装はこの採択をもって開始可能となった。

### D1. 許可するcontributor集合＝coastal 4 kindの**任意の部分集合**（空集合を含む）

`CoastalGeneratorRuntimeV2`は、Blueprintが封印したcoastal transition contributorの集合が `{SANDY_BEACH, HARBOR_BASIN, BREAKWATER_HARBOR, ROCKY_CAPE}` の**任意の部分集合**（サイズ0〜4）であることを受理する。`REQUIRED`集合とその検査を削除する。

- **サイズ1〜4:** 従来どおり`CoastalTransitionPlanV2`（contributor ≥1）が封印され、compositorが宣言されたcontributorだけを合成する。
- **サイズ0（coastal featureをまったく宣言しないintent）:** `DiagnosticBlueprintCompilerV2`はcoastal transition planを1つも作らない（既存挙動。`coastalPlans.isEmpty()`で compiler呼出し自体をskip）。この場合runtimeはcompositorを持たず、surface全域がfoundation（background candidate＋`V2-19-07` producer）に所有される。

D1の根拠はADR 0038 D5-3であり、**新しい許可条件を発明していない**。「modifier tierに被覆要求を課さない」という既決契約を実装へ反映するだけである。

### D2. 緩和は無条件（legacy baseline経路に別規則を置かない）

`CoastalGeneratorRuntimeV2`はfoundationの有無を**知らないまま**部分集合を受理する。地表被覆の権限はfoundation tierに一本化する。

- macro foundation経路: 欠落kindのcellはbackground owner（またはproducer）が所有し、ADR 0038 D1-4のexactly-one owner契約を満たす。
- legacy baseline経路（明示foundation入力なし）: `V2-18-10`以降、contributor数にかかわらず`SurfaceFoundationOwnerGateV2`がfoundation owner被覆0%として**必ず**export拒否する（ADR 0038 D8-2、override不可）。したがってcoastal runtime側に「baseline経路では4種必須」という第二の被覆規則を残す必要はなく、残せば**被覆を守るgateが2箇所に分散**して`V2-18-10`が確立した単一責務を壊す。

**帰結（拒否理由の変化）:** legacy baseline経路で部分集合を宣言したrequestは、これまで `surface-2_5d export requires all four V2-2 coastal contributors` で拒否されていたが、以後は`SurfaceFoundationOwnerRejectedV2`（owner被覆不足）で拒否される。**拒否されるという結論は不変**であり、理由が正確になる。これをnegative testで固定する。

### D3. 欠落contributorのfield意味論＝「footprintが空のcontributor」と同一

欠落kindのdescriptor fieldには、そのkindが宣言されていた場合の**canonical OUTSIDE値**を書く。新sentinel・新field semantic・新field idを導入しない。

| field | 欠落時の値 | 由来 |
|---|---|---|
| `BEACH_LOCAL_WIDTH` | `SandyBeachGeneratorV2.NO_DATA`（`Integer.MIN_VALUE`） | `band == OUTSIDE`時のwidth値 |
| `BEACH_BAND` | `0`（`BeachBand.OUTSIDE`） | 同上 |
| `HARBOR_REGION` | `0`（`HarborRegion.OUTSIDE`） | `HarborSample(OUTSIDE, 0, NO_DATA, NO_DATA, false)` |
| `HARBOR_WATER` | `0` | 同上 |
| `HARBOR_DEPTH` | `NO_DATA` | 同上 |
| `BREAKWATER_REGION` | `0`（`BreakwaterRegion.OUTSIDE`） | `BreakwaterSample.OUTSIDE` |
| `CAPE_REGION` | `0`（`CapeRegion.OUTSIDE`） | `CapeSample.OUTSIDE` |
| `CAPE_ROCK_EXPOSURE` | `0` | 同上 |
| `CAPE_DESCRIPTOR_INDEX` | `0` | 同上 |

**`CoastalSurfaceFieldsV2.FIELD_IDS`の集合・順序は不変**とする。sidecarのshapeをcontributor集合に依存させると、Releaseのfield indexがintentごとに変わり、strict read-backとdiff可能性を失う。「欠落＝全cell OUTSIDE」は既存の値域内であり、新しい意味を持ち込まない。

### D4. block resolverとvalidation／previewの扱い

- **block resolver:** `CoastalSurfaceFieldsV2.resolver`はD3の中立値をそのまま読むため分岐を追加しない。欠落contributorは**final canonical block streamに一切の効果を持たない**（`V2-19-01`のmaterialization契約と整合）。
- **validation:** `CoastalValidatorV2`はBlueprintのplan listを走査するため、宣言されていないkindのmetricは**生成されない**。欠測を`0`やPASSとして捏造しない（`V2-19-01`が禁じた定数healthy samplerと同型の事故を作らない）。
- **preview:** 診断preview setの枚数・layer idは固定のまま維持し、欠落kindのlayerは全cell OUTSIDEとして描画される。preview indexのshapeをintentに依存させない。
- **Release strict verify:** `SurfaceReleaseCapabilityVerifierV2.requiresCoastalMetrics`はcoastal plan群が空のときだけmetric必須を解除する既存実装であり、D1のサイズ0を**すでに**正しく扱う。サイズ1〜4ではmetric必須が維持される。

### D5. runtime preconditionの再定義（`V2-19-08`表の帰結）

`ProductionRoutePreconditionsV2.REQUIRED_COMPANIONS`（`V2-19-08`が「dispatch選択の外側にあるruntime制約」として公開表明した表）から、4 pipelineすべてのcoastal四種要求を**削除する**。結果として現時点の全pipelineのrequired companion集合は**空**になる。

- クラス・API・rule id（`v2.design.route-companion-missing`）・`design-support-lint-v1` contract id・`DesignSupportSurfaceV2`のJSON shape（`requiredCompanionKinds`を含む）は**すべて不変**とする。値が空になるだけである。将来のpipelineがcompanion要求を持つ場合に備えた枠組みとして残す（`V2-19-08`の設計意図どおり）。
- **contract bumpは行わない。** 変わるのは表の**内容**であって、契約のshape・rule集合・severity分類ではない。
- design lintの帰結: `PLAIN`単体宣言（`oblique-multi-view`）は`V2-19-08`実測で`NOT_SELECTABLE`＋companion不足だったが、本ADR後は`SELECTABLE`となり両findingが消える。これは**lintの退行ではなく、報告していた制約が実際に無くなったこと**の正しい反映である。`V2-19-08`のtestはこの遷移を明示的に更新する。

### D6. checksum影響の宣言（`V2-19-09` gateが要求する項目）

| 対象 | 影響 | 根拠 |
|---|---|---|
| 既存4種fixture（`harbor-cove-64-honored`／`coastal-honored-400`／`-river`／`-meander`／`-guided`／`-plain`）のterrain field／tile／**block semantic checksum** | **不変** | 4種宣言時に実行される計算は値レベルで同一。削除するのは検査だけで、値を生む経路は変えない |
| 同fixtureの容器byte（intent／blueprint canonicalChecksum／manifest／Release checksum） | **不変** | Schema・plan・field descriptor・artifact集合のいずれも変更しない |
| `production-dispatch-registry-v2` registry checksum | **不変** | route集合・handler chainを変更しない |
| `public-dispatch-reachability-v1` projection checksum | **不変** | support列・reachability・block materialization分類を変更しない |
| Feature Support Catalog sealed checksum | **不変** | 能力列を昇格しない（D7） |
| v1 Schema／generator `3.0.0-phase6`／Release format 1／v1 golden／placement／Undo | **不変** | 本ADRはv2 spineのみを対象とする |
| 新規部分集合fixture | 新規追加。既存checksumに影響しない | — |

**この「既存fixture完全不変」は実装の努力目標ではなくAcceptance条件**とし、既存の絶対checksum pin testで機械的に固定する。もし実装が既存checksumを動かすなら、それはD1の解釈違反であり停止条件（作業を止めて本ADRへ戻る）。

### D7. capability昇格は行わない

- `PLAIN.standalone_usage`は`PARTIAL`のまま据え置く。D1により`PLAIN`＋HARD `LAND_WATER_MASK`のみのintentは**exportできるようになる**が、`SUPPORTED`昇格には[task-execution-guide §7](../design-v2/task-execution-guide.md)が要求する証拠一式（対象Featureのblock effect class宣言と、baseline Releaseとのfinal canonical block stream差分によるmaterialization実測）を、standalone構成そのもので揃える必要がある。`V2-19-07`が測ったのはcoastal複合fixture上の`PLAIN`である。
- `BuiltInFeatureSupportCatalogV2`の`PLAIN` `standalone_usage`注記（現在「the surface path still requires the four coastal contributors」）は、本ADR実装時に**事実に合わせて訂正**する（据え置き理由をmaterialization証拠不足へ書き換える）。注記の訂正はcatalog sealed checksumに影響しない範囲で行い、影響する場合は据え置き理由の記述をcatalog外（docs）へ置く。

  **実装時の判定（2026-07-24）:** 注記textはsealed catalogの正本byteに含まれるため、いかなる文言変更もsealed checksumを動かす。D6が同checksumを**不変**と宣言しているため、注記は**変更せず据え置き**、訂正は[current-limitations](../design-v2/current-limitations.md)と[V2-19-09 audit](../design-v2/audits/v2-19-09-coastal-contributor-subset.md)へ置いた。据え置いた注記は「PLAIN単体はexportできない」と述べており、実装より**保守的に**外れている（能力を過大表現していない）。訂正は次に意図的にcatalogを変更するTask（`PLAIN.standalone_usage`昇格leafが自然な同居先）が同一変更で行う。
- Paper列・`paper_apply`は全kindで不変（V2-17専管）。

### D8. conformance case（`V2-19-09` gateが要求する項目）

本ADRは次の常設caseの追加を要求する。いずれも**公開Releaseのartifactからのみ**測る。

1. **`harbor-cove-64-honored-beach`（サイズ1の部分集合、headline case）.** `harbor-cove-64-honored`から`HARBOR_BASIN`／`BREAKWATER_HARBOR`／`ROCKY_CAPE`と、それらを端点とするrelation／constraintを除いたintent。HARD `LAND_WATER_MASK`は`V2-18-13`と同じ規則（`active ? composed : macro-background`）で再生成し、再生成helperをdev-only disabled testとして同梱する（現行geometryからbyte一致で再現できることが再生成の不変条件）。監査§2.3が「できない」と実測した「beachだけの海岸」が実際にexportできることの証拠とする。
2. **`harbor-cove-64-honored-coastless`（サイズ0）.** `PLAIN`＋既存`harbor-cove-64-honored`のmask（**byte同一で再利用**）のみを宣言するintent。coastal modifierがゼロでもfoundationがdomainを所有し、owner gate・EDGE HARD・strict read-backを通ることを証拠とする。maskをbyte同一で再利用できるのは、活性modifierが存在しない以上maskとの矛盾が原理的に生じないためである。
3. **negative（fail closedの維持）.**
   - legacy baseline経路（foundation入力なし）＋部分集合 → owner被覆不足で拒否（D2）。
   - 部分集合宣言でmaskが矛盾する場合 → 既存`v2.coastal-transition-hard-conflict`／`hard-unrepresented`で拒否（規則追加なし）。
   - 同一kindの重複contributor → 既存の一意性違反で拒否（凍結1）。
4. **既存4種caseの不変性.** portfolioの既存5 caseはmeasurement値・checksumともに不変であることを同時に固定する。

portfolioのmeasurement契約（`IntentConformancePortfolioV2`）は現在beach・breakwater・`BACKSHORE_PLAINS`の存在を前提に組まれているため、**contributorごとに測定を任意化**する必要がある。これは測定の追加ではなく、既存measurementの適用範囲をcaseの宣言集合に合わせる変更であり、既存caseの測定値を変えてはならない。

### D9. 停止条件

次のいずれかが必要になった時点で実装を止め、本ADRへ差し戻す。

- 既存4種fixtureのterrain semantic checksumまたは容器byteが動く（D6違反）。
- 欠落contributorのために新しいsentinel／field id／field semantic／preview layerが必要になる（D3違反）。
- `design-support-lint-v1`のcontract bump、rule id追加、severity変更が必要になる（D5違反）。
- 新capability名・新artifact format・Release format変更が必要になる（凍結4）。
- portfolioの既存caseのmeasurement値が動く（D8-4違反）。

## Consequences

- **契約と実装の不整合が解消する。** ADR 0038 D5-3が禁じたmodifier tierの被覆要求が実装から消え、地表被覆の権限は`V2-18-10`のfoundation owner gateに一本化される。
- **監査T-C1が閉じる。** 「beachだけの海岸」がpublic surface exportできるようになり、`current-limitations.md`の「4 coastal contributor必須」記述は実装に合わせて更新される。
- **`V2-19-08`のlint出力が変わる。** `PLAIN`単体宣言の`NOT_SELECTABLE`＋`route-companion-missing`が消える。lintの枠組み（rule id・contract・shape）は維持され、値だけが「制約なし」になる。
- **`V2-15`／`V2-16`の後続配線leafの前提が単純化する。** 新しいmodifier kindを配線する際、「coastal 4種と同時に宣言しなければ動かない」という無関係な結合条件を満たす必要がなくなる。
- **`PLAIN.standalone_usage`昇格の道が開くが、本ADRでは昇格しない。** 昇格は独立したmaterialization証拠を伴う後続Taskとする（D7）。
- 本ADRは生成アルゴリズムを一切変更しないため、既存Release・既存golden・v1境界は不変である（D6）。

## Alternatives considered

### A1. 非空部分集合（1〜4）のみ許可し、サイズ0は別Taskへ分離する

サイズ0を除けば`CoastalSurfaceFieldsV2`のcompositor無し分岐を書かずに済み、差分は小さい。しかし「coastal contributor ≥1」もまた**modifier tierの被覆要求**であり、ADR 0038 D5-3に対して「4種必須」とまったく同じ性質の違反である。閾値を4から1へ下げるだけでは原則違反が残り、`V2-19-07`が`V2-19-09`へ委ねた`PLAIN`単体export（catalog注記に明記）も解消しない。

さらに実務的な副作用として、A1では`ProductionRoutePreconditionsV2`の意味が「全kind必須（all-of）」から「いずれか1つ必須（any-of）」へ変わり、`design-support-lint-v1`のcontract bumpまたはproperty意味の再定義が必要になる。**閾値を1に残す方が契約の変更面が広い。** 不採用。

### A2. `REQUIRED`を保ちつつoverride flagを追加する

`V2-18-03`のHARD preflightがoverride flagを設けなかった方針（task-index §18.2）と矛盾する。fail-closed規則にescape hatchを足すのはガバナンス上の後退である。**不採用。**

### A3. 欠落contributor用に新しい「absent」sentinelやfield集合の可変化を導入する

sidecar shapeがintentごとに変わり、Release間のdiff可能性とstrict read-backの前提を壊す。既存OUTSIDE値で十分に表現できる（D3）。**不採用。**

### A4. 部分集合ごとに専用pipelineを登録する（`surface-2_5d.beach-only`等）

dispatch registryのroute数が組合せ爆発し、[ADR 0039](0039-offline-production-route-eligibility.md) 凍結4「side doorのad-hoc増殖禁止」に反する。**不採用。**

### A5. 本ADRで`PLAIN.standalone_usage`を`SUPPORTED`へ昇格する

[task-execution-guide §7](../design-v2/task-execution-guide.md)が要求するstandalone構成でのmaterialization証拠が揃っていない。`V2-19-01`が確立した「plan-only metricはblock metricの代替にならない」原則にも反する。**不採用**（D7）。

## Acceptance／実装開始条件

1. 人間が本ADR全文をレビューし、**D1のcardinality（サイズ0〜4を許可するか、A1のサイズ1〜4に留めるか）を明示採択する。**
2. Status を `Accepted` に更新し、採択日・採択内容をDecision節へ記録する。
3. その後に限り`V2-19-09`の実装（runtime緩和、fixture追加、portfolio任意化、docs同期）を開始する。
4. 実装完了時、D6の「既存fixture完全不変」がtestで機械的に固定されていることを確認する。

**充足記録（2026-07-24）:** 条件1・2は同日の人間承認（D1 サイズ0〜4の明示採択）で充足した。条件3の実装は`V2-19-09`で実施し、条件4は`CoastalContributorSubsetV2Test`の絶対checksum pin（既存4種fixtureのblueprint／manifest／tile semantic checksum）で充足した。

## References

- [cross-cutting-audit-2026-07-23](../audits/cross-cutting-audit-2026-07-23.md) §2.3／§8／提案T-C1
- [ADR 0038](0038-macro-foundation-contract.md) D1／D5-3／D7／D8／D9
- [ADR 0039](0039-offline-production-route-eligibility.md) 凍結1／2／4
- [task-index §19](../design-v2/task-index.md) `V2-19-09`
- [task-execution-guide §7](../design-v2/task-execution-guide.md) Support capability gate
- [current-limitations](../design-v2/current-limitations.md)
