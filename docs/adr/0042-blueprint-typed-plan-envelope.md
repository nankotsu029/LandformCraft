# 0042: Blueprint typed plan envelope（`WorldBlueprintV2`拡張面積の内部集約とexecutableKinds単一ソース化）

- Status: **Proposed**（未採択。`V2-19-13`はADR著述までがScope。実装は本ADR採択後の新IDで行う）
- Date: 2026-07-24（起草。同日rev.2: コード実測に基づく事実訂正 — codec経路の削除、cross-plan一覧の完全化、schema型付け劣化の追加、D1主張の適正化）
- Decision scope: `V2-19-13`。設計対象は (a) `model.v2.WorldBlueprintV2`のper-kind typed plan集約の**内部表現とvalidation経路**、(b) 4 production pipelineの`executableKinds`宣言の単一ソース化。**canonical serialization（`world-blueprint-v2.schema.json`のwire形・`canonicalChecksum`・Release manifest checksum）は本ADRの変更対象外**である。
- Depends on: [ADR 0036](0036-canonical-feature-identifier-disposition.md)（canonical Feature識別子）、[ADR 0038](0038-macro-foundation-contract.md) D4（60 kind／NORMATIVE 6／PROVISIONAL 54）、[ADR 0039](0039-offline-production-route-eligibility.md)（route class）、`V2-15-05`〜`V2-15-10`（dispatch spine／4 pipeline）、[2026-07-23横断監査](../audits/cross-cutting-audit-2026-07-23.md)
- Blocks: 本ADRが`Accepted`になるまで、対象の集約実装（新ID）を開始しない。

## Context

[Task Index §19.2](../design-v2/task-index.md) `V2-19-13`は、Blueprintの「拡張面積（新しい地形kindを1つ配線するために編集しなければならない箇所の数）」を縮小する設計をADRで検討し、`Proposed`まで著述することを求める。実装は承認後の別IDへ分離し、**Schema `$id`・artifact type・checksumのin-place変更は禁止**である。

以下の事実は2026-07-24にHEADのコードで実測した（rev.2でcodec経路の誤り等を訂正済み）。

### 事実1: `WorldBlueprintV2`のper-kind typed plan面積 — 「黙って忘れられる」編集と「忘れると壊れる」編集の混在

`model.v2.WorldBlueprintV2`（[WorldBlueprintV2.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/v2/WorldBlueprintV2.java)、1751行）は、**17個のper-kind typed plan list**をrecord componentとして直接列挙している。

```
coastalFeaturePlans, sandyBeachPlans, harborBasinPlans, breakwaterHarborPlans, rockyCapePlans,
coastalTransitionPlans, meanderingRiverPlans, lakePlans, canyonPlans, waterfallPlans, deltaPlans,
tidalChannelPlans, mangroveWetlandPlans, coralReefPlans, fjordPlans, mountainPlans, volcanicPlans
```

加えて**7個のsingleton plan**（`geologyPlan`／`lithologyPlan`／`strataPlan`／`climatePlan`／`waterConditionPlan`／`hydrologyPlan`／`hydrologyReconciliationPlan`）を持つ。

1つのper-kind planを追加するときの編集点は次のとおりで、**忘れたときの故障モードが2種類に分かれる**ことが本質である。

| # | 編集点 | 忘れた場合 |
|---|---|---|
| 1 | record header component（`:31-47`） | 追加自体が成立しない（前提） |
| 2 | compact constructorの`V2Validation.sorted(...)`正規化代入1行（`:72-119`） | **silent** — compileは通り、未正規化listがcanonical serializationへ流れ得る |
| 3 | `withCanonicalChecksum`の全component再列挙（`:147-160`） | compile error（自己防護） |
| 4 | `validateDescriptors`のsignature・呼び出し引数・`uniqueMap`によるid重複検査（`:490-566`） | **silent** — signatureごと忘れればcompileは通り、id重複検査が存在しないまま素通りする |
| 5 | per-kind **presence-consistency loop**（「`kind == X` ⇔ typed plan存在」の双方向照合。例: `:699-705`／`:921-927`） | **silent** — kindだけ宣言されtyped planが欠けたBlueprintが妥当として通る |
| 6 | per-plan validation block（field存在・module契約・bounds照合） | **silent** |
| 7 | canonical constructor呼び出し側（`DiagnosticBlueprintCompilerV2`、production唯一の構築点） | compile error |
| 8 | `schemas/world-blueprint-v2.schema.json`の`properties`＋`required`（**25 plan key**を列挙、`additionalProperties: false`） | serialization時のschema検証で**loud**に失敗。ただし後述の穴がある |

**codecは拡張面積に含まれない**（rev.2訂正）。`LandformV2DataCodec`はJackson databindでrecordへ直接bindする（`readWorldBlueprint`＝`treeToValue`、`writeWorldBlueprint`＝`valueToTree`。`LandformV2DataCodec.java:319-332`／`:3125-3135`）。component名＝JSON keyであり、per-kind listの列挙コピペはcodecには存在しない。したがって**wire keyの集合・順序はrecordのcomponent宣言がそのまま正本**であり、recordとschemaの**2正本のlockstep**が拡張のたびに手作業で維持されている。

なお、record componentの追加は既存の全blueprint容器byteを必ず動かす（Jacksonは空listも`[]`として書き、schemaの`required`がそのkeyを要求する）。これは集約とは無関係の既存性質で、過去の全plan追加で発生しておりADR 0038 D9の容器byte可変域として扱われてきた。本ADRの凍結（D5-1）は「**集約refactor自体**がbyteを動かさない」ことであり、将来のplan追加が容器byteを動かす既存性質を変えるものではない。

### 事実2: schemaの型付けは既に劣化している — 実害は仮定ではない

schema側のper-kind entryを実測すると、**直近に追加された3種は`items: true`（無型）である**。

```
"fjordPlans":    { "type": "array", "maxItems": 256, "items": true },
"mountainPlans": { "type": "array", "maxItems": 256, "items": true },
"volcanicPlans": { "type": "array", "maxItems": 256, "items": true },
```

`coastalFeaturePlan`〜`coralReefPlan`までは`$defs`への`$ref`で型付けされているのに対し、fjord以降はschema層の構造検証が存在しない（Java record側の検証だけが残っている）。これは「線形拡張コストが将来品質を劣化させるかもしれない」ではなく、**既に劣化させた**という実測である。拡張面積の縮小は美観の問題ではなく、この省略が構造的に起きない仕組みの問題である。

### 事実3: cross-plan validationは11系統ある

集約は「per-kindを完全に均質化すればよい」わけではない。既存validationには**plan間の相互参照**が旧版rev.1の想定（3件）を大きく超えて存在する。

| # | 参照 | 内容 |
|---|---|---|
| 1 | canyon → meanderingRiver | `riverFeatureId`のgeometry checksum・floor幅照合（`:934-940`） |
| 2 | breakwater → harborBasin | `basinFeatureId`＋enclosure relation照合（`:731-742`） |
| 3 | coastalTransition → 全coastal plan | contributor被覆・interaction relation共有（`:821-843`） |
| 4 | waterfall → meanderingRiver | `riverFeatureId`束縛 |
| 5 | delta → meanderingRiver | `trunkRiverFeatureId`束縛 |
| 6 | tidalChannel → mangroveWetland | wetland hookのkind照合 |
| 7 | mangroveWetland → tidalChannel | tidal hookの相互束縛 |
| 8 | coralReef → lagoon／pass feature | lagoon hook・pass hookのkind照合 |
| 9 | fjord → GLACIAL_MOUNTAIN_RANGE | sidewall hookのkind照合 |
| 10 | volcanic → caldera／lava feature | caldera hook・lava hookのkind照合 |
| 11 | hydrologyPlan variables／constraints → featurePlans | 変数・制約のfeatureId解決 |

集約機構は per-plan hook だけでなく **cross-plan hook** を第一級で表現できる必要があり、その登録数は今後のkind配線（V2-15／V2-16）とともに増える。

### 事実4: 4 pipelineの`executableKinds`重複宣言

production export の4 pipeline（`surface-2_5d` coastal／`hydrology-plan`／`environment-fields`／`sparse-volume`）は、それぞれ `PipelineDescriptor` の `executableKinds`／`contractOnlyKinds` を**手書きのlist literalで別々に宣言**している。

| pipeline | executableKinds | contractOnly |
|---|---|---|
| coastal (`CoastalSurfaceExportPipelineV2:78-88`) | coastal 4種 + **PLAIN** | BACKSHORE_PLAINS |
| hydrology (`HydrologyPlanExportPipelineV2:93-100`) | coastal 4種 + **RIVER, MEANDERING_RIVER** | BACKSHORE_PLAINS |
| environment (`EnvironmentFieldsExportPipelineV2:57-62`) | coastal 4種 | BACKSHORE_PLAINS |
| sparse-volume (`SparseVolumeExportPipelineV2:65-71`) | coastal 4種 | BACKSHORE_PLAINS |

構造の実態は「**基底集合（PRODUCTION_CONNECTED＝coastal 4種）＋pipeline別のOFFLINE_PRODUCTION加算集合（coastal: PLAIN、hydrology: RIVER／MEANDERING_RIVER、environment／sparse-volume: なし）＋共有contract-only（BACKSHORE_PLAINS）**」であるが、基底とcontract-onlyが**4 descriptorへ同じ内容で4回コピー**されており、単一の正本が無い。さらに`ProductionDispatchRegistryV2`は自前の`COASTAL_OFFLINE_PRODUCTION_KINDS`（`:60-61`）を別建てで持ち、descriptor側との一致は暗黙である。coastal基底へ1 kindを足すとき、4 descriptor＋registry定数の5箇所が手作業同期になる。

### 制約（Task Indexの凍結）

- **canonical serializationを変えない。** `world-blueprint-v2.schema.json` の `$id`（`:3`）は固定、`additionalProperties: false`、`canonicalChecksum` は全fixtureの絶対pin（tile semantic checksum `20318e6c…` 等）で機械的に固定されている。集約はこれらを**1 byteも動かしてはならない**。
- Release format／capability／artifact type／dispatch route集合／registry checksum／reachability projection・v1 golden不変。

## 凍結（本ADRが`Accepted`になっても変更しない）

1. **wire形と`canonicalChecksum`。** blueprintのcanonical JSONのkey集合・順序・値・`$id`は不変。JacksonはrecordのJSON keyを**component宣言順**で書くため、**既存componentの宣言順の変更も禁止**する。集約は**Javaの内部validation経路**だけを対象とし、serializationの出力byteを変えない。
2. **schema `$id`・artifact type・version。** `world-blueprint-v2.schema.json` の `$id` を変えず、新schema `$id`・新artifact type／versionを導入しない。
3. **validation規則の意味。** typed plan／cross-plan の**判定内容**（何を不正とするか）を集約で緩めない。集約は「同じ判定を1箇所へ集める」ことを行い、判定を削除しない。fail-closed方向の**強化**（現在silentな欠落を構築時エラーへ変える防護、無型schemaの型付け追補）は許可するが、現在validな入力を新たに拒否する変更はAcceptance（D5-2）で排除する。
4. **dispatch route集合とroute class。** `PRODUCTION_CONNECTED`＝coastal 4種（ADR 0039）、`OFFLINE_PRODUCTION` allowlist、contract-only集合の**内容**は不変。単一ソース化は同一集合を1箇所から供給するだけで、集合を変えない。registry checksum／reachability projectionは不変。
5. **新capability名・Release format変更・composition profile変更を導入しない**（[task-index §19.1](../design-v2/task-index.md)共通停止）。
6. **本ADRが`Proposed`の間は集約実装を開始しない。** 実装は採択後の新ID。

## Decision

> **本ADRは`Proposed`であり、以下は採択待ちの提案である。** `V2-19-13`のScopeはこの提案の著述までで、`D1`〜`D3`の設計と`D4`の段階計画に対する人間承認を得た時点で`Accepted`とし、実装を新IDへ割り当てる。推奨は「**D1（内部registry集約、wire不変）とD3（executableKinds単一ソース、集合不変）を1つずつ別leafで実装し、wire形を変えるD2は本ADRでは採用せず将来ADRへ分離する**」である。

### D1. `WorldBlueprintV2` typed planの内部registry集約（wire不変、推奨）

**目的の適正化（rev.2）:** D1の目的は「編集箇所を1行へ縮める」ことでは**ない**。Java recordのcompact constructorではcomponentごとの正規化代入（`sandyBeachPlans = V2Validation.sorted(...)`）を汎用ループへ畳めない（component変数は個別のlocalであり、reflectionによる代入はrecordのfinal性に反する）。record component・`withCanonicalChecksum`・compiler呼び出し側・schemaの編集は**残る**。D1が達成するのは、**事実1の表で「silent」に分類される編集忘れ（正規化・id重複検査・presence照合・per-plan／cross-plan hook配線）を、構築時fail-closedまたはdrift guard testの失敗として必ず顕在化させる**ことである。

新package（案: `model.v2.plan`）へ **closed typed plan registry** を導入する。

**`BlueprintPlanTypeV2<P>`（plan種別descriptor、closed set）**

| descriptor要素 | 置換する現行コピペ |
|---|---|
| plan record class `Class<P>` | record component型 |
| id extractor（featureId／planId） | `Comparator.comparing(Plan::featureId)` |
| 最大件数（256／8 等） | `V2Validation.sorted(..., 256, ...)` の上限 |
| presence predicate（このplanを要求するFeatureKind） | per-kind presence loop の `kind == X` 判定 |
| per-plan validation hook | 各typed planのfield存在・module契約検査 |
| schema上のexpected `$defs`参照名 | schema `properties`の`items.$ref`（事実2の穴を閉じる） |

registryは**封印済み集合**（enum または `List.of(...)`）とし、外部クラス・script・ServiceLoaderから拡張できない（AGENTS.md §7）。hookはlambdaではなく**named static method**で登録し、stack traceとレビュー可読性を保つ。

**機構**

1. compact constructorは正規化代入の後、`Map<BlueprintPlanTypeV2<?>, List<?>>`（context map）を**一度だけ**列挙して構築し、registry駆動の`validatePlans(context)`を呼ぶ。
2. registryは全登録typeについて (a) context mapにentryが存在すること（**欠落＝即throw** — map行の追加忘れをfail-closed化）、(b) listがsorted・unique・size上限内であること（**正規化行の忘れをfail-closed化** — 正しい実装では正規化済みなので常に通り、行を忘れた実装では未sorted入力が構築時に拒否される）、(c) presence predicateとの双方向照合、(d) per-plan hook、を実行する。
3. **cross-plan hook**（事実3の11系統）はper-plan hookとは別に登録し、registryがcontext mapから各typeのby-id mapを生成して渡す。個別の`uniqueMap`＋手書きloopを置換する。
4. `validateDescriptors`の17個のplan引数はcontext map 1個へ縮む（modules／stages／fields等の非plan引数は現行のまま）。

**残る手編集と防護（正直な収支）**

| 残る編集 | 防護 |
|---|---|
| record component追加 | 追加の前提（防護不要） |
| 正規化代入1行 | registry再検査(b)が構築時throw（silent→loud） |
| `withCanonicalChecksum`引数 | compile error（現行どおり） |
| context map entry 1行 | registry欠落検査(a)が構築時throw |
| registry descriptor登録 | **reflection drift guard test** — recordの全`List<plan型>` componentがregistryへ登録済みであることを`getRecordComponents()`走査で照合し、未登録component＝test失敗 |
| compiler呼び出し側 | compile error（現行どおり） |
| schema `properties`＋`required` | serialization時schema検証（現行どおりloud）に加え、**schema型付けguard** — registryのexpected `$defs`参照名とschemaの`items.$ref`をtestで機械照合し、`items: true`の混入＝test失敗（事実2の穴を恒久に閉じる） |

per-plan／cross-plan hookの**本体**（field-id・module契約・bounds照合のロジック）は消えない — inline loopから登録済みhookへ移るだけで、コード量はほぼ同じである。消えるのは「配線のコピペ」と「配線忘れがsilentに通る性質」であり、これがD1の全てである。

**既存3種の型付け追補:** 実装leafでは事実2の`fjordPlans`／`mountainPlans`／`volcanicPlans`へtyped `$defs`を追補する。これはschema層の検証強化（fail-closed方向）であり、Java record側が既に強制している契約のschema反映なので、**現在validな全fixtureは通過し続ける**ことをAcceptanceで固定する（凍結3）。schema fileはartifactではなくrepo文書であり、artifact checksumへ影響しない。

**実装注意（stop条件未満のリスク）:** registryはrecord classと相互参照になり得る（descriptorがcomponent accessor相当を持ち、recordのconstructorがregistryを呼ぶ）。static初期化循環を避けるため、registryはrecordの外の独立classとし、record側からの参照はconstructor実行時（メソッド呼び出し）に限定する。

### D2. wire形の`plans`オブジェクト化（本ADRでは不採用）

blueprint JSONの25 plan keyを単一の `plans` オブジェクト（plan-type keyed）へ畳む案は、`$id`・key集合・`canonicalChecksum`・Release manifest checksumを全て動かす。凍結1・2・[task-index §19.1](../design-v2/task-index.md)の「Schema ID・artifact type・checksumのin-place変更禁止」に正面から反する。さらにrev.2の実測が示すとおり、**wire形を畳んでもJava側の拡張面積はほぼ縮まない**（record component・正規化・validation・schemaのlockstepはwire形と独立に残る。codecはJacksonが自動追随するためどちらでも面積ゼロ）。つまりD2は「checksum凍結を全損して得るものがJSONの見た目だけ」であり、費用対効果が成立しない。**本ADRでは採用しない。** 将来wire縮小が必要になった場合は、schema `$id` version bump＋migration＋独立ADR＋人間承認を要する別事案とし、ADR 0035（version中立・legacy read維持）とADR 0038 D9（checksum可変域）に沿って設計する。Alternatives A1に記録する。

### D3. 4 pipeline `executableKinds`の単一ソース化（集合不変、推奨）

pipeline kind集合の**単一の正本**を新設し（案: `core.v2.export.ProductionRouteKindsV2`）、4 descriptorと`ProductionDispatchRegistryV2`の`COASTAL_OFFLINE_PRODUCTION_KINDS`はそこから導出する。

- 正本は事実4の実態どおり**3つの宣言**として持つ（「environment＝hydrology∖river」のような導出規則は実態に無いので置かない）。
  1. PRODUCTION_CONNECTED基底集合（coastal 4種）
  2. pipeline別OFFLINE_PRODUCTION加算表（coastal: `PLAIN`、hydrology: `RIVER`／`MEANDERING_RIVER`、environment／sparse-volume: 空）
  3. 共有contract-only集合（`BACKSHORE_PLAINS`）
- 各 `PipelineDescriptor` の `executableKinds`＝基底∪自pipelineの加算集合、`contractOnlyKinds`＝共有contract-onlyを正本から算出する。`PipelineDescriptor` の既存正規化（sort・dedup・`contractOnly`との排他）はそのまま通し、**結果の集合が現行と完全一致**することをtestで固定する。
- `ProductionDispatchRegistryV2`のoffline判定も同じ加算表を読む（現在の暗黙一致を同一出所へ）。
- registry checksum・reachability projection・`PRODUCTION_CONNECTED`の意味・Paper `SUPPORTED` exact setは不変（集合を変えないため）。

これは純内部refactorであり、route集合・checksumを動かさない。D1と**別leaf**にして直列で行う（`core.v2.export` 共有領域のため、[task-index §19.1](../design-v2/task-index.md)の同時編集禁止に従う）。

### D4. 段階計画（実装は承認後の新ID）

| 段階 | 内容 | wire／checksum | 採否 |
|---|---|---|---|
| 段階0（本Task `V2-19-13`） | 本ADRを`Proposed`で著述、拡張面積の実測と制約を確定 | 不変 | 本Task |
| 段階1（新ID・案） | D1: typed plan registry内部集約（silent編集忘れのfail-closed化＋drift guard＋schema型付け追補） | 不変（絶対pin通過） | **採択後に実装** |
| 段階2（新ID・案） | D3: `executableKinds`単一ソース化 | 不変（集合再現） | **採択後に実装** |
| 段階3（将来・別ADR） | D2: wire `plans`オブジェクト化 | **変わる** → 独立ADR＋schema version bump＋人間承認 | 本ADRでは不採用 |

段階1と段階2は独立で、順序自由・各1 leafとする。段階3は本ADRのScope外。

### D5. Acceptance（実装leafが満たす条件、本Taskでは規定のみ）

集約実装（新ID）は次を機械的に固定して初めて完了とする。

1. **wire完全不変。** 既存全fixtureのblueprint canonical JSON byte・`canonicalChecksum`・terrain field／tile／block semantic checksum・Release manifest checksumが**集約refactorによって1 byteも動かない**（既存の絶対checksum pinで固定。動いたら凍結1違反で停止）。将来のplan追加が容器byteを動かす既存性質はこの条件の対象外である（事実1末尾）。
2. **validation等価＋強化のみ。** 集約前後で、既存のvalid fixtureは同一に通り、既存の全negative（typed plan／cross-plan不整合、id重複、presence不一致、module契約mismatch）は**同一のexception**で拒否される。schema型付け追補（fjord／mountain／volcanic）後も既存valid fixtureが全て通ることを確認する。現在validな入力を新たに拒否する変更が必要になったら停止（D6）。
3. **完全性guardの実証。** (a) reflection drift guard（recordの全plan-list component ⊆ registry）、(b) schema型付けguard（`items: true`の混入検出）、(c) 「正規化行を忘れた実装」「context map entryを忘れた実装」が構築時に**loudに**失敗することのnegative実証（テスト用の欠落シミュレーションまたは同等の機械的論証）。旧rev.1の「dummy plan追加がdescriptor 1行で済むことの実証」は、D1の適正化により**撤回**する — 計測すべきは編集行数ではなく「silentに欠落し得る編集点が0件であること」である。
4. **dispatch不変（D3）。** registry checksum・reachability projection・route集合・contract-only集合・`PRODUCTION_CONNECTED`集合が不変。
5. **決定性。** whole／tile／thread／locale（`tr-TR`）／timezone（`Pacific/Chatham`）でserializationとvalidation結果が不変。
6. 新capability・新artifact format・新schema `$id`・composition profile変更を伴わない。

### D6. 停止条件

実装leafで次のいずれかが必要になったら停止し、本ADR（または新ADR）へ差し戻す。

- canonical serialization出力・`canonicalChecksum`・schema `$id`・record component宣言順を変えないと集約できない（＝D2段階3が必要）。
- validation判定を緩める、または現在validな入力を新たに拒否する必要がある（凍結3違反）。
- dispatch route集合・checksumが動く（凍結4違反）。
- registryを外部拡張点（ServiceLoader等）にする必要がある（AGENTS.md §7違反）。
- record⇔registryのstatic初期化循環が実行時に顕在化し、遅延参照で解消できない。
- schema checksumを絶対pinするtestが存在し、型付け追補（fjord／mountain／volcanic）と衝突する（その場合は追補だけを切り出して本ADRへ戻る）。

## Consequences

- **「黙った欠落」が構造的に不可能になる。** 現在silentな5系統の編集忘れ（正規化・uniqueMap・presence loop・per-plan／cross-plan hook配線・schema型定義の省略）が、compile error・構築時fail-closed・drift guard testのいずれかで必ず顕在化する。編集行数の削減は副次効果（`validateDescriptors`の17引数と17個のuniqueMap・presence loopの消滅）であり、主効果ではない。
- **既に発生した劣化が修復・再発防止される。** 事実2の`items: true` 3件が型付け追補で是正され、schema型付けguardにより再発しない。
- **wire不変を保ったまま。** 段階1・2はcanonical byteとchecksumを一切動かさないため、既存Release・v1 golden・placement／Undo回帰・全fixtureの絶対pinがそのまま通る。「面積の質の改善」と「format変更」を分離したことで、format変更（段階3）の是非を将来独立に判断できる。
- **executableKindsの単一正本化。** coastal基底への1 kind追加が4 descriptor＋registry定数へ自動反映され、5箇所の手作業同期とdriftが排除される。
- **cross-plan契約が一覧化される。** 11系統のplan間参照がregistryのcross-plan hookとして一箇所に並び、新kind配線時に「どの既存planと相互参照すべきか」が見通せる。
- **段階3（wire縮小）を保留する判断の根拠が明確になる。** wire形を畳んでもJava側面積は縮まない（D2）ため、checksum凍結を保つ現状維持が積極的な選択となる。

## Alternatives considered

### A1. wire形を`plans`オブジェクトへ即時集約（D2の即時採用）

`$id`・`canonicalChecksum`・Release manifest checksum・全fixtureの絶対pinを動かし、[task-index §19.1](../design-v2/task-index.md)の凍結と`V2-19-13` gateの「Schema ID・checksumのin-place変更禁止」に反する。migration・legacy read（ADR 0035）・version dispatchも要り1 leafを超える。加えてJava側拡張面積はほぼ縮まない（D2）。**不採用。** 将来はschema version bump＋独立ADR。

### A2. record componentを可変長の汎用`List<Object>`／`Map<String,JsonNode>`にする

typed accessorとcompile-time型安全（`sandyBeachPlans()` が `List<SandyBeachPlanV2>` を返す）を失い、AGENTS.md §7「HARD同士の矛盾をcompile errorにする」「不変record／enum基本」に反する。Jackson databindのcomponent名＝JSON key対応も壊れ、wire互換の維持に独自serializerが要る。**不採用。**

### A3. コード生成（annotation processor等）でrecordを生成する

コピペは消えるが、生成器という新しいbuild時subsystem（[task-execution-guide §4](../design-v2/task-execution-guide.md)「大規模subsystemは1つまで」）を持ち込み、生成物のcanonical byte再現・決定性の論証が別枠になる。D1のguard群で「silent欠落0件」という同じ安全性が達成でき、生成器の複雑性に見合わない。**不採用。**

### A4. `executableKinds`をpipelineに宣言させず`ProductionDispatchRegistryV2`が全て所有する

descriptor自身がexecutableKindsをhandler契約と束ねて持つ現構造（`PipelineDescriptor` がstrict verifierに渡る）を崩し、pipeline単体の自己記述性を失う。単一ソースは「1箇所で宣言し各descriptorが導出」で足り、所有権をregistryへ移す必要はない。**不採用**（D3は導出案を採る）。

### A5. reflectionでcompact constructorの正規化代入を汎用化する

record componentのfinal性のため、reflectionによるcomponent代入はJava言語仕様上成立しない（compact constructor内のlocal変数への代入は個別文でしか書けない）。正規化を「構築後にsortedなcopyを返すfactory」へ移す案は、canonical constructorを直接呼ぶ経路（Jackson databindを含む）が未正規化のまま通ることを許し、fail-closed性を失う。D1の「正規化行は残すが、忘れをregistry再検査で構築時fail-closed化する」が正しい妥協点である。**不採用。**

### A6. 何もしない（現状維持）

silent欠落5系統とschema型付けの省略（既に3件発生）が放置され、V2-15／V2-16の残り配線でkindごとに再発リスクを負い続ける。**不採用。** ただし段階3（wire縮小）については「当面何もしない＝checksum凍結を優先」を積極的に選ぶ（Consequences最終項）。

## Acceptance／採択条件（`V2-19-13`の完了条件）

1. 人間が本ADR全文をレビューし、**D1（内部registry集約・wire不変・silent欠落のfail-closed化・schema型付け追補）とD3（executableKinds単一ソース・集合不変）を推奨案として採択し、D2（wire縮小）を本ADRのScope外＝将来独立ADRとすることを確認する。**
2. Statusを`Accepted`へ更新し、採択日・採択内容をDecision節へ記録する。
3. その後に限り、段階1（D1）と段階2（D3）をそれぞれ新IDのleafとして割り当てて実装する。各leafはD5のAcceptance（wire完全不変・validation等価＋強化のみ・完全性guard実証・dispatch不変・決定性）を機械的に固定する。
4. **本Task（`V2-19-13`）はStep 1手前まで＝本ADRの`Proposed`著述で完了**する。実装（Step 3）は本Taskで行わない。

## References

- [cross-cutting-audit-2026-07-23](../audits/cross-cutting-audit-2026-07-23.md)（横断監査rev.2）
- [ADR 0035](0035-v1-retirement-governance.md)（version中立・legacy read維持・改名governance）
- [ADR 0036](0036-canonical-feature-identifier-disposition.md)（canonical Feature識別子）
- [ADR 0038](0038-macro-foundation-contract.md) D4（60 kind対応表）／D7（fail-closed）／D9（checksum可変域）
- [ADR 0039](0039-offline-production-route-eligibility.md)（route class／`PRODUCTION_CONNECTED`＝coastal 4種）
- [task-index §19](../design-v2/task-index.md) `V2-19-13`／§19.1共通契約
- [task-execution-guide §4](../design-v2/task-execution-guide.md)（1回の作業量）／§7（Support capability gate）
- [WorldBlueprintV2.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/v2/WorldBlueprintV2.java)（17 typed plan list＋7 singleton・cross-plan 11系統）
- [world-blueprint-v2.schema.json](../../schemas/world-blueprint-v2.schema.json)（`$id`固定・25 plan key・`items: true` 3件）
