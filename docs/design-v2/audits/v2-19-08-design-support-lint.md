# V2-19-08 Design-time support lint（report-only）

> Status: **PASS（2026-07-24）**。[2026-07-23横断監査](../../audits/cross-cutting-audit-2026-07-23.md) の T-L1（§2.2／§5.1）に対応し、provider呼出し前の**reachable kind／capability集合提示**と、provider応答後・publish前の**dispatch dry-run**を追加して、結果を`audit-v2.json`の任意`supportLint`とCLI／Paper designサマリへ`NON_GATING`で記録する。**historic 60-kind Schemaは狭めない**。**fail-closed化しない**（全finding `NON_GATING`。gate化は別途人間承認）。**新capability・新artifact format・新Release format無し**、terrain field／tile／block semantic checksumとv1 goldenは不変。

## 1. 修正前の状態（監査の確定事実）

| 事項 | 修正前 |
|---|---|
| provider prompt | request ID・bounds・自由文・画像role文だけ。現在export可能なkind集合は渡らない（監査§5.1） |
| design段階の到達性検査 | 無し。「山と谷のある島」は正常にdesign・publishされ、`v2 export`で初めて`no production dispatch route: <KIND>`となる |
| 到達性の表示源 | Feature Support Catalogの`export`列。これはplan-level／shared capability supportを含むため**公開dispatch到達性と同義ではない**（監査§2.2）。lintはcatalogではなくdispatch registryを参照する必要がある |
| runtime precondition | `CoastalGeneratorRuntimeV2.REQUIRED`（coastal 4種必須）はpackage-privateで、dispatch選択の外側にあり、design段階からも操作者からも見えない |

## 2. 実装

### 2.1 reachable surface（provider呼出し前）

`DesignSupportLintServiceV2`（`core.v2.design`）が`ProductionDispatchRegistryV2`と`PublicDispatchReachabilityV2`から`DesignSupportSurfaceV2`（`model.v2.design`、contract `design-support-lint-v1`）を投影する。

| field | 現在値 | 出所 |
|---|---|---|
| `productionConnectedKinds` | `BREAKWATER_HARBOR`／`HARBOR_BASIN`／`ROCKY_CAPE`／`SANDY_BEACH` | dispatch route（`PRODUCTION_CONNECTED`） |
| `offlineProductionKinds` | `MEANDERING_RIVER`／`PLAIN`／`RIVER` | dispatch route（`OFFLINE_PRODUCTION`） |
| `contractOnlyKinds` | `BACKSHORE_PLAINS` | registryのcontract-only kind |
| `requiredCompanionKinds` | coastal 4種 | `ProductionRoutePreconditionsV2`（新設public、`CoastalGeneratorRuntimeV2.requiredKinds()`が唯一の出所） |
| `dispatchRegistryChecksum`／`reachabilityChecksum` | `182f713e…`／`7d46e315…` | 既存contractのchecksum（本Taskで変化しない） |

surfaceは`TerrainDesignRequestV2`へ載り、HTTP provider（OpenAI／Anthropic）は`TerrainIntentPromptV2.supportSurfaceText`を**独立したuser content section**として送る。

**promptVersionを変えない理由:** `TerrainIntentPromptV2.VERSION`（`terrain-intent-v2-structured-guards`）は凍結されたguard contractの識別子である。advisory blockは`design-support-lint-v1`と reachability projection checksum から決定論的に導出され、その両方をdesign auditが記録するため、送信したadvisory本文は依然として一意に特定できる。したがってguard contract IDを書き換えずに識別性を保っている。

### 2.2 dispatch dry-run（provider応答後・publish前）

`DesignSupportLintServiceV2.lint(surface, intent)`が、登録済みcapability set（`pipelineCapabilitySets()`、read-only追加）ごとに`ProductionDispatchRegistryV2.select`を試行する。selectは純粋なlookupで、生成もartifact書き込みも行わない。拒否は例外として伝播させず**dry-runの答え**として扱う。

| rule id | 条件 | 現在の実測例 |
|---|---|---|
| `v2.design.kind-not-publicly-dispatchable` | 宣言kindにrouteが無く、contract-onlyでもない | `PLATEAU`宣言intent |
| `v2.design.kind-contract-only` | contract-only companionとしてのみ受理される | `azure-coast`の`BACKSHORE_PLAINS` |
| `v2.design.kind-plan-only` | routeはあるがfinal block streamを変えない | 現在該当なし（V2-19-05／07で両route classともMATERIALIZED）。将来のplan-only routeを黙って通さないために保持 |
| `v2.design.route-companion-missing` | pipeline runtimeが要求するcompanion kindが不足 | beach単体、`PLAIN`単体 |
| `v2.design.dispatch-unselectable` | どのcapability setもintentを受理しない | 上記いずれかの帰結 |

全findingは`NON_GATING`である。`DesignAuditV2`のcompact constructorが`GATING` findingを持つlintを拒否し、schemaも`"gateClass": {"const": "NON_GATING"}`とする — fail-closed化はcode・schema・人間承認を同時に要する。

### 2.3 記録と表示

- `audit-v2.json`（`design-audit-v2.schema.json`）へ**任意**`supportLint`を追加。absentでは書き出されないため、v1→v2 migration bundleのauditと既存published packageのbyteは不変。
- CLI `v2 design`とPaper `/lfc v2 design`は`V2WorkflowServiceV2.summarizeSupportLint(audit)`という**単一の計算**を表示する（`supportLintContract`／`dispatchDryRun`／`selectablePipelines`／`reachableKinds`／`declaredKinds`／`supportLintFindings`）。

## 3. 実測

`examples/v2/diagnostic/oblique-multi-view`（`PLAIN`単体宣言）を実CLI `v2 design fixture`で通した結果:

```text
dispatchDryRun: NOT_SELECTABLE
selectablePipelines: []
declaredKinds: [PLAIN]
supportLintFindings: [{ruleId=v2.design.dispatch-unselectable, gateClass=NON_GATING, …},
                      {ruleId=v2.design.route-companion-missing, gateClass=NON_GATING,
                       featureKinds=[BREAKWATER_HARBOR, HARBOR_BASIN, ROCKY_CAPE, SANDY_BEACH], …}]
```

design packageは**publishされている**（exit code 0、`audit-v2.json`と`terrain-intent-v2.json`が存在）。`PLAIN`はrouted kindでありながら、coastal surface pipelineのruntimeがcoastal 4種を要求するため単体ではexportできない — 従来は`v2 export`まで判らなかった事実が、design時点で表示されるようになった。

`azure-coast`（coastal 4＋`BACKSHORE_PLAINS`）は`SELECTABLE`で、4 pipeline全て（surface-2_5d／hydrology-plan／environment-fields／sparse-volume）を選択でき、findingは`kind-contract-only`の1件だけである。`harbor-cove-64-honored-river`は`hydrology-plan`のみ選択可能で、これはcoastal surface pipelineが`RIVER`をexecutable kindに持たないという実態と一致する。

## 4. test

| test | 固定する事実 |
|---|---|
| `DesignSupportLintServiceV2Test.theReachableSetIsProjectedFromTheDispatchRegistryNotTheSupportCatalog` | reachable集合＝registry route集合。**`export == SUPPORTED`だがrouteが無いkindはreachableとして提示しない**（監査§2.2） |
| 同`theCoastalFixtureIsSelectableAndOnlyReportsItsContractOnlyCompanion` | 4 pipeline選択、contract-only 1件 |
| 同`theRiverFixtureSelectsTheHydrologyPipelineOnly` | offline routeのpipeline特定、plan-only rule現在空 |
| 同`aRoutedSubsetReportsTheMissingRuntimeCompanionsAndIsNotSelectable` | beach単体でcompanion不足＋unselectable |
| 同`anUnroutedKindIsReportedWithoutFailingTheLint` | `PLATEAU`はlintで報告されるがlint自体は失敗しない |
| 同`everyRegisteredRuleIsAdvisoryInThisContractVersion` | 未登録rule id無し、全`NON_GATING` |
| 同`lintOutputIsStableAcrossLocaleTimezoneAndThreads` | tr-TR／Pacific/Chatham／4 thread決定性 |
| `TerrainDesignApplicationServiceV2Test.theProviderIsToldTheReachableSetAndTheAuditRecordsTheDryRun` | 実HTTP body（fake server）にadvisoryが載る、`promptVersion`不変、published packageのstrict read-backでlintが一致 |
| `TerrainIntentPromptV2Test.supportSurfaceTextNamesTheReachableSetWithoutNarrowingTheSchema` | advisory文言（他kindも「still parse」する＝Schemaを狭めない） |
| `DesignSupportLintAuditContractV2Test` | lint無しauditの読取、canonical write→strict read-back、`GATING`改竄拒否、未知field拒否、record側の`GATING`拒否 |
| `LandformCraftCliV2Test.designSummaryAndAuditCarryTheReportOnlySupportLint` | 実CLIサマリとaudit本文 |
| `PaperV2ReferenceImageDesignV2Test.theOfflineDesignVerbReportsTheSupportLintOnThePaperSurface` | Paper adapter（`JavaPlugin`非生成）で同一計算 |
| `SchemaContractTest` | optional両分岐のexample（`design-audit-v2.json`／`design-audit-with-support-lint-v2.json`） |

## 5. 非Scopeと不変

- **fail-closed化**（lintでdesignを拒否する）は行わない。Task Index §19.2どおり別途人間承認。
- **historic 60-kind Schemaを狭めない**。unreachable kindのdesignは従来どおりpublishされる。
- **coastal 4種必須の緩和は`V2-19-09`**（ADR＋人間承認）。本Taskはその制約を*報告*するだけで、`ProductionRoutePreconditionsV2`がその唯一の公開表明箇所になる。
- dispatch route集合、registry checksum、`public-dispatch-reachability-v1` projection、Feature Support Catalog、Release format／capability、terrain field／tile／block semantic checksum、v1 goldenは**不変**。
- `runtime capability profile`（監査T-L1が「別入力に」とした項目）は、Release capability setがpipeline descriptor側で既にversion化されているため、本Taskでは`selectablePipelineIds`として表示するに留めた。
