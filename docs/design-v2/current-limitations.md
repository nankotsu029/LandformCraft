# 現行実装の制約と再設計の出発点

> Status: V2-12-06でv1 production writer／generator／placement／commandを削除し、v1はpackaged legacy read／verify／migrate境界とimmutable goldenへ隔離した。productionで通常利用できるRelease 2は`surface-2_5d`の実測済み4 entryに限られ、FAWE 2.15.2は1000×1000、WorldEdit 7.3.19単独は64×64、1000超は未測定で拒否する。Track AのPhase gate `V2-19-16`は2026-07-24の人間承認で完了し、V2-19親Phaseは全16 Taskで閉じた（[V2-19-16 Phase gate audit](audits/v2-19-16-phase-gate.md)。Track A残りはV2-17のみ、`V2-15-47`／`V2-16-19`完了待ち）。

`V2-12-02`のproduction export経路（`core.v2.export`）には次の既知の境界がある。いずれも推測fallbackを避けるためのfail closedであり、緩和は後続Taskで行う。

- **対象capabilityのproduction Application Serviceは`surface-2_5d`、`hydrology-plan`、`environment-fields`（いずれもshared artifact）**である。`hydrology-plan`は`Release2HydrologyExportApplicationServiceV2`、`environment-fields`は`Release2EnvironmentExportApplicationServiceV2`経由でcoastal production featureへoverlayでき、hydrology dependencyは同一Releaseでstrict verifyされる。個別hydrology／environment Featureの公開配線は`V2-15-10`以降である。`sparse-volume`のproduction export経路は未接続で、既存のcapability別publisherを直接呼ぶ必要がある。
- **coastal contributorはV2-2の4 kindの任意の部分集合でよい（`V2-19-09`以降、[ADR 0040](../adr/0040-coastal-contributor-set-cardinality.md) D1）。** sandy beach単体、あるいはcoastal modifierを1つも宣言しないintent（例: `PLAIN`＋HARD mask）もexportできる。宣言されなかったkindは寄与ゼロで、そのcellはmacro foundation（background candidate、またはproducer）が所有する。欠落kindのdescriptor fieldは「footprintが空のcontributor」と同じcanonical OUTSIDE値を持ち、sidecarのfield集合・順序はcontributor集合に依存しない。**1 kindにつきcontributor 1個**の一意性は不変であり、部分集合を推測補完することもしない。地表全域の被覆はmodifier tierではなく`V2-18-10`のfoundation owner gateが単独で保証する（[ADR 0038](../adr/0038-macro-foundation-contract.md) D5-3／D7-2）。
  - `PLAIN.standalone_usage`はcatalog上`PARTIAL`のまま据え置きである（ADR 0040 D7）。単体構成でのblock materialization証拠が未取得のためであり、catalog注記に残る「the surface path still requires the four coastal contributors」はsealed checksum不変のため据え置いた**stale記述**である。正本は本節と [V2-19-09 audit](audits/v2-19-09-coastal-contributor-subset.md) とする。
- **surface exportは明示foundation入力を必須とする（`V2-18-10`以降）。** feature非所有cellを`SurfaceBaselineV2`で一律に埋める旧経路は、2026-07-22の[macro foundation監査](../audits/macro-foundation-conformance-audit-2026-07-22.md)が大域land-water構図の破壊（400×400実測でfeature非所有cell 73.0%）を確定し、`V2-18-09`のmacro foundation stageで置換され、`V2-18-10`でfoundation owner被覆100%がexport必須になった。requestはHARD `LAND_WATER_MASK`と`foundationBaseLevels`を宣言する必要があり、欠けたrequestは`v2.export.foundation-owner-coverage-incomplete`でfail closedに拒否される（baseline引数はoverrideにならない。[ADR 0038](../adr/0038-macro-foundation-contract.md) D7-2／D8-2）。export経路は依然として独自の地形を発明せず、maskとfeature形状の矛盾も拒否する。
- **macro foundation背景の標高は任意でcoherent detailを持てる（`V2-19-12`以降、[ADR 0041](../adr/0041-coherent-detail-kernel.md)）。** `foundation-base-levels`が与えるmedium別の平坦な2定数は、request任意`foundationDetail`（`CoherentDetailKernelV2`、`coherent-detail-fixed-v1`）を宣言すると空間的に連続なmulti-scale detail（整数演算・振幅hard bound・halo/support radius 0でwhole==tiled）へ置換される。これは横断監査§3.1が指摘した「公開productionにcoherentなmulti-scale detailが無い（cell-hash micro reliefは空間的に非連続）」の最小回復であり、`PlainGeneratorV2`のcell-hash micro reliefとは隣接段差の公開上限で機械的に区別できる。適用対象は**背景base level cellの標高のみ**で、`HEIGHT_GUIDE`指定cell・producer所有cell・coastal modifier所有cell・land-water mediumには一切触れない（優先順位表に行を増やさない）。残る制約: erosion等の近傍filter（近傍fieldを読む処理）はScope外で別ADR＋別Task、producerのcell-hash micro reliefのkernel置換とmodifier境界のtaper bandも別Task。`foundationDetail`を宣言しないrequestのterrain semantic checksum・容器byte・既存Releaseは不変である。
- **宣言geometryはHARD maskへ剛体平行移動で整列できる（`V2-19-14`以降、[ADR 0043](../adr/0043-mask-feature-reconcile-pre-pass.md)）。** HARD `LAND_WATER_MASK`はcompositorのHARD sourceであり、maskと一致するactive contributorが無いcellは`v2.coastal-transition-hard-conflict`で拒否される。そのため`V2-18-13`／`V2-19-09`はいずれもmaskを`active ? composed : macro-background`で再生成しており、横断監査§4.2-4が指摘した**mask⇔seed束縛**（geometryかseedを触るたびにmaskを作り直す）が実務手順だった。request任意`maskFeatureReconcile { toleranceBlocks: 1..8 }`を宣言すると、export spineがBlueprint compile直前に宣言feature geometry**全体**を1回の整数block平行移動でmaskへ整列する。**maskは動かさず**（値・digest・binding・no-data cellは不変）、既存fail-closed gateの判定・閾値も不変で、**pre-pass専用の拒否ruleは追加していない**（tolerance内に一致する配置が無いrunは従来どおり既存gateが拒否する）。既にmaskと一致しているgeometryは全順序の定義により動かない。**残る制約**: 補正は剛体平行移動だけであり、**手描きmaskや画像抽出maskへのcell単位の形状追従（shape reconcile）はできない**。per-featureのoffset・回転・拡大縮小・sub-block解像度の探索・mask側の補正・`HEIGHT_GUIDE`のregistrationはいずれもScope外で別ADRである。また目的関数はcompositorのper-cell HARD述語そのものではなく**推定器**であり（contributorが重なるcellで乖離し得る）、最終的な正しさの判定は常に既存HARD gateが持つ。`maskFeatureReconcile`を宣言しないrequestのterrain semantic checksum・容器byte・既存Releaseは不変である。
- **desired constraint fieldはcomposition結果をsealしたものである。** 外部由来のHARD land-water maskをbundleとして持ち込んで束縛する経路（画像由来maskの直接binding）は`V2-7`／`V2-12-04`の入力側Taskに残る。同監査でdesired＝actualの自己参照（residual恒等0）とHARD `EDGE_CLASSIFICATION`未評価を確定し、入力mask束縛を`V2-18-06`、target-driven検証を`V2-18-04`、desired／actual分離を`V2-18-07`へ登録した。
- **surface columnのblock語彙は宣言された閉じたrole catalogである（`V2-19-10`以降）。** `SurfaceMaterialProfileV2`（`surface-material-profile-v1`）が13 role／11 distinct block stateを宣言し、各stateは`EnvironmentBlockStateCatalogV2`のallowlistを構築時に通る。任意block state・非canonical識別子・NBT payloadは拒否し、外部script／式は評価しない。
  - **sealed material profile／palette planは`environment-fields` Releaseのblockへ届く。** 解決済みsemantic material classがwet／snow variantのcellだけ、露出した`NATURAL_SURFACE` roleを palette の`SURFACE` mappingで置換する（凍結済みV2-4-07 rule tableのconditional override 2本の写し）。structural・built structure（breakwater crest／core）・subsurfaceは常にroleのbase assignmentを保つため、環境materialはsolid質量もfluidも動かさない。`surface-2_5d`／`hydrology-plan`のtile／block semantic checksumは不変である。
  - ただし共有pipelineのgeology priorは**uniform 1 province**であり、`erosionResponse`が全域ROCK側に落ちるため wet cell は`SEDIMENT_WET`（mud）ではなく`HOST_ROCK_WET`（cobblestone）になる。実province fieldはV2-4-01のuniform priorを置き換える別Taskの範囲である。植生（`EcologyPlanV2`のassemblage／placement）はまだblockへ届かず、`VEGETATED_SURFACE`のgrass_blockはroleのbase assignmentである。
  - environment validationの**定数healthy sampler**（2026-07-23横断監査§2.8）は`V2-19-10`で廃止し、公開対象Blueprintのsealed planから測る`CoastalEnvironmentFieldStackV2`へ置換した。宣言fieldを持たないtopographic exposure／slopeは明示のbounded derivationとして契約化してあり、宣言planの無いfeature scoped入力（river／lake／tidal proximity、tidal range、freshwater discharge、wetland／reef／island／canyon mask）は「該当featureの宣言なし」の値のままで推測しない。
- **LARGE（1024超）は拒否する。** V2-8のstreaming gateが閉じるまでexportしない。
- **CLI／Paper command接続は`V2-12-03`で解消済み**で、`V2-12-05`から既定v2である。ただし本production export serviceが扱うのは`surface-2_5d`だけであり、未接続capabilityを推測してworld mutationへ流さない。詳細は [Task Index](task-index.md)、進捗は [docs/roadmap.md](../roadmap.md) を正本とする。

## 1. 調査範囲と判断基準

最初の制約調査は2026-07-14時点の作業ツリーを対象にした。Task再分割時の2026-07-15にはV2-0／V2-1の実装済み変更を含む作業ツリーを再確認し、既存の未コミット変更を保持した。現在の確認対象は次のとおりである。

- `AGENTS.md`、`README.md`
- `docs/` の52 file。通常docs、ADR、`docs/design-v2/`、Phase監査、release noteを含む
- `schemas/` のDraft 2020-12 Schema 22件
- `examples/` の47 file。v1 artifact、V2 diagnostic scenario、manual constraint fixture、Azure Coastの4画像を含む
- `src/main` の319 fileと `src/test` の52 file
- `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、Gradle Wrapper、`plugin.yml`、`config.yml`

判断の優先順位は、実行コード、test、生成artifact、Schema、docsの順とする。既存の [Phase 0〜5監査](../audits/phase-0-5-audit.md) と [Phase 6／Beta監査](../audits/phase-6-beta-audit.md) も参照したが、監査文だけを実装根拠にはしていない。

## 2. 結論

現行エンジンは、安全な入出力、決定性、tile、Release、配置／rollbackの基盤を持つ一方、地形表現は「globalな集約値から1枚の地表高を作る」範囲に留まる。多様な地形を安定して扱うには、enumやnoise parameterの追加では足りない。

推奨する境界は次のとおりである。

```text
維持する: Provider境界 / pure model / bounded executor / tile / checksum
          Releaseのatomic publish / strict verify / confirm / snapshot / rollback

置き換える: TerrainIntent v1の集約表現
            wrapperに近いWorldBlueprint v1
            monolithicなTerrainGenerator
            単一water levelと単一surface column
            固定validator / 固定8-preview orchestration

追加する: feature geometry / hard-soft constraint / compiled field IR
          hydrology graph / geology-climate-ecology fields
          built-in module catalog / sparse local volume / typed validation target
```

## 3. TerrainIntent v1の表現限界

[TerrainIntent.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/TerrainIntent.java) と [terrain-intent.schema.json](../../src/main/resources/legacy/v1/contracts/terrain-intent.schema.json) が持つ地形情報は、`topology`、`seaSides`、`landRatio`、全域共通のrelief、海岸集約値、水系個数、coarse zone、少数structureに限られる。

| 現行要素 | 実際に表せること | 表せないこと |
|---|---|---|
| `Topology` | coast、island、archipelago等の大分類 | fjord、delta、火山、canyon等の固有形成則 |
| `seaSides` | 海が接する外周方角 | 海岸線、湾口、lagoon、島列の正確な形 |
| `landRatio` | 海陸thresholdの全域比率 | hardなland-water mask、局所比率 |
| `ReliefIntent` | 全域のmin/average/max | ridge、crater、谷断面、局所標高band |
| `CoastlineIntent` | irregularity、湾／岬の個数 | spline、beach幅、海底勾配、湾の接続 |
| `WaterIntent` | river/lake個数、最大海深、遠浅幅 | source、outlet、流域、流量、合流、分流、湖面、滝 |
| `TerrainZone` | 9方向の希望位置とscore強度 | polygon、穴、重なり、境界、優先度、局所parameter |
| `StructureIntent` | built-in 8 typeの個数と希望zone | 任意asset ID、地形規模のbreakwater、正確な経路 |

重大な点として、`theme`、`bayCount`、`capeCount`、`shallowShelfWidth` は現行generatorの形状計算から参照されない。Schemaに存在し保存されても、生成結果へ直接効かない。`areaShare` も実現面積ではなく、retired v1 `LogicalLayoutGenerator` のGaussian zone scoreの係数である。

`src/main/resources/legacy/v1/fixtures/azure-coast/request.yml` は砂浜幅、中央の曲線防波堤、湾内外の深度、岩礁cape、zone間遷移を詳細に指定する。しかし生成済みv1 Intentでは `SANDY_BEACH`、`ROCKY_COAST`、`CLIFFS` などへ圧縮され、曲線、幅、接続、断面を保持できていない。`src/main/resources/legacy/v1/fixtures/mountain-stream` の滝、段瀑、滝壺、植生、岩種も同じ理由でtheme以上の生成契約にならない。

## 4. WorldBlueprint v1はコンパイル済みIRではない

[WorldBlueprint.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/WorldBlueprint.java) は次の8要素だけを持つ。

```text
schemaVersion / requestId / bounds / intent / seed
tileSize / logicalResolution / generatorVersion
```

retired v1 `BlueprintCompiler` が行う主処理は、requestとIntentのSchema version一致、candidate seed導出、最大辺に応じた64または128のlogical resolution選択である。land-water mask、curve、feature dependency、drainage、地質、気候、volume、validation targetへの意味的compileはない。

したがって現行 `WorldBlueprint` は「検証済み入力をgeneratorへ渡す固定bundle」ではあるが、「生成途中の意味を保持する実行計画」ではない。v2ではこの責務を根本的に変える必要がある。

## 5. 現行generatorの実態

retired v1 `TerrainGenerator` と [TerrainPlan.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/TerrainPlan.java) の中心表現は次である。

```text
heightMap[x,z]          int
waterDepthMap[x,z]      int
surfaceMaterial[x,z]    6値enum ordinal
featureMask[x,z]        RIVER/LAKE/VEGETATION/CLIFF/COAST bit
structures[]
```

実装上の重要な制約は次のとおりである。

- `LogicalTerrainLayout` は `continental` と `relief` のdouble field 2枚だけ。
- riverは、最初のsea sideを基準にした平行な蛇行帯を一定深さまでcarveする。流域、source、合流、流量はない。
- lakeはseed由来の中心と半径を持つ円形carveで、流入、流出、spillway、独立した湖面を持たない。
- すべての水はrequestの単一 `waterLevel` を使う。
- erosionは乾地に対する4近傍の単回加重平均である。
- `ARCHIPELAGO` は固定された2中心のradial signalである。
- zoneは専用generatorを選ぶのではなく、主にreliefとsurface materialのswitchへ影響する。

この方式は平原、丘、通常の海岸の概形には適するが、fjordのU字断面、deltaの分流、火山火口、段丘、dune crest、karst、glacial cirqueなどの成立条件を持たない。

## 6. 2.5D列モデルの限界

[BlockColumnMaterializer.java](../../src/main/java/com/github/nankotsu029/landformcraft/worldedit/BlockColumnMaterializer.java) は、各X/Zを次の1本の列へ展開する。

```text
bedrock → stone → 固定3層subsoil → surface → waterまたはair
```

同じX/Zに複数のsolid intervalを持てないため、cave、overhang、natural arch、sea cave、sky island、滝裏の空間は構造的に表現できない。これはparameter不足ではなくデータモデル上の不可能性である。

一方、[SpongeSchematicWriter.java](../../src/main/java/com/github/nankotsu029/landformcraft/worldedit/SpongeSchematicWriter.java) は全block Listを作らず、cellを要求時sampleしてSponge v3へstream出力する。この性質はv2でも再利用できる。差し替えるべきなのはwriter全体ではなく、column-onlyなsample sourceである。

注意点として、現writerはpalette IDが128未満で1-byte VarIntになる前提でNBT `Data` 長をblock数と同一にしている。地質・植生・珊瑚等でpaletteが増える前に、一般のVarInt長へ対応させる必要がある。

## 7. 地質・気候・生態・素材は意味場になっていない

[SurfaceMaterial.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/SurfaceMaterial.java) は `GRASS/SAND/STONE/GRAVEL/MUD/SNOW` の6値だけである。retired v1 `TerrainGenerator.materialAt` は水深、傾斜、zone、maxY付近、1本のwetness noiseからこの6値を選ぶ。

現状には次がない。

- ecologyの公開CLI／Paper接続、cave-local ecology、entity／block entity（climate、水条件、snow、semantic material、閉じたMinecraft 1.21.11 palette、sparse ecology placementはV2-4 gateを通過したoffline `SUPPORTED` componentだが、公開dispatch／Paperは未接続）
- temperature、precipitation、moisture、wind exposure、salinity、sunlight（regional salinity／wetnessはV2-4-05、snow potential／coverはV2-4-06、habitat／assemblage placementはV2-4-11で追加）
- soil depth、groundwater、habitat、species assemblage
- basalt/tuff/andesite、clay、underwater substrate等のfeature固有semantic materialのpalette／Paper接続（host lithology×wet/snowのbase catalogはV2-4-07、volcanic/canyon overlayはV2-4-12で追加済み・standalone）
- 樹木、根、珊瑚、水草等の実block配置

`VEGETATION` は草地上で立つ診断bitであり、植生descriptorやplacementではない。雪も気候上のsnowlineではなく、`maxY` 近傍を一律 `SNOW` にする規則である。

## 8. v1では画像が直接制約になっていない

retired v1 `ReferenceImageProcessor` は、path、symlink、magic、byte、pixel、frame、EXIF、metadataを厳格に扱う優れた入力境界を持つ。ただしpixelの利用先はProviderである。

- `TOP_DOWN_SKETCH` だけがboundsへの座標対応と四辺のwater ratioを持つ。
- `HEIGHT_REFERENCE`、`ZONE_REFERENCE`、`MATERIAL_REFERENCE` はAIへのrole説明であり、generator fieldへdecodeされない。
- retired v1 `GenerationApplicationService` はgenerate時にrequestとIntentだけを読み、画像を読まない。
- Design Packageは画像binaryを保存せず、Release v1にもconstraint artifactはない。

したがって画像は `画像 → AI要約 → TerrainIntent v1` で情報が圧縮され、正確なmask、curve、height guideとして再現性の入力にならない。

この制約はv1経路についての記述である。V2-1ではreference imageとは別のRequest契約で`LAND_WATER_MASK`、`HEIGHT_GUIDE`、`ZONE_LABEL_MAP`をcanonical fieldへ変換するmanual pathを実装済みである。生成側consumerは`V2-18-09`（`LAND_WATER_MASK`＝macro foundationのland-water medium）と`V2-19-06`（`HEIGHT_GUIDE`＝同foundationのbackground elevation、medium別base levelはfallback）で接続済みで、`ZONE_LABEL_MAP`は宣言・binding・検証まで可能だが読む生成側を持たない。したがって地形表現の制約が完全に解消するのは、残るroleと各feature Taskの配線、および親Phase gateが完了した後である。

## 9. validationとpreviewの限界

[TerrainValidator.java](../../src/main/java/com/github/nankotsu029/landformcraft/validation/TerrainValidator.java) が検査するのは、cell bounds、水深整合、tile被覆、river存在、水域接続、river pit、structure安全性が中心である。次は検査しない。

- 実現したland ratio、relief分布、zone面積
- bay/cape数、海岸複雑度、遠浅幅
- beach幅／傾斜／砂率
- fjord、delta、volcano、canyon、cave等の成立指標
- hard/soft constraintの達成度

[TerrainPreviewRenderer.java](../../src/main/java/com/github/nankotsu029/landformcraft/preview/TerrainPreviewRenderer.java) は8 file名を固定列挙する。[ReleaseVerifier.java](../../src/main/java/com/github/nankotsu029/landformcraft/format/ReleaseVerifier.java) も同じ8 fileをstrict allowlistにしているため、rendererだけを拡張するとRelease v1はunknown artifactとして失敗する。

## 10. 性能、tile、Release、配置の現状

流用価値の高い部分も明確である。

- 最大1000×1000、vertical span 512のhard limit。
- 既定128×128 tile、global X/Z sample、16 block margin。
- whole terrainと単独tileの全cell一致、locale/timezone/thread非依存をtest済み。
- CPUはbounded pool、I/Oはadmission制限付きVirtual Thread、cancelはinterrupt/tokenへ伝播。
- Releaseはchecksum、strict read-back、fsync、atomic move、Sponge v3 tileを持つ。
- 配置はregion/disk reservation、actor-bound confirm、snapshot、apply、full verify、逆順rollback、Undo、Recoveryを持つ。

ただしv2では次が問題になる。

- 現行96 MiB推定は「3 int grid + 1 byte grid」前提で、新しいfieldやvolumeを含まない。
- 固定margin 16は大河川、delta、侵食kernel、3D featureすべてに共通利用できない。
- Release format 1はartifact集合を固定しており、field sidecarや追加previewを収容できない。
- 現行v1配置はtileごとにsnapshot直後にapplyする。v2はV2-6-04〜V2-6-10でsnapshot-all、containment、canonical apply、bounded settle／full verify、rollback／Undo／Recoveryを実装し、V2-6-21とV2-12-03〜05でpublic接続と既定v2 routingまで完了した。
- full cuboid verifyはPaper main thread上の大規模scanとなるため長時間を要する（1000角で壁時計約1.8 h、peak RSS約6 GiB）。`V2-11-04`／`V2-11-05`のFAWE単独実測を経て`V2-11-06`が500／1000角をcatalog `SUPPORTED`へ昇格したが、この寸法evidenceはFAWE 2.15.2単独であり、WorldEdit 7.3.19単独runtimeは64×64のままである。

## 11. 既存テストから引き継ぐべき品質基準

現行test suiteは、v2の回帰基盤として重要である。

- `TerrainGeneratorTest`: seed、thread、locale、timezone、candidate、edge tile、seam、river pit、性能
- `ReferenceImageProcessorTest`: path、symlink、hardlink、TOCTOU、format、EXIF、metadata、pixel、cancel
- `ReleasePackageTest`: directory/ZIP、WorldEdit read-back、checksum、未知／欠損file、危険path
- `PlacementApplicationServiceTest`: confirm、actor、overlap、disk、rollback、Undo、Recovery
- `PackageBoundaryTest`: modelのJDK-only、AI/Paper/WorldEdit依存境界

v2はこれらを破棄せず、field、feature、volume、constraint、module単位へ拡張する。

## 12. 再設計の判断

推奨案は、[TerrainIntent v2](terrain-intent-v2.md) と [WorldBlueprint v2](world-blueprint-v2.md) をv1と並設し、[多段階pipeline](generation-pipeline-v2.md) をbuilt-in module catalogから構成する方式である。大部分は2.5D fieldで維持し、必要なfeatureだけを [局所3D overlay](volumetric-terrain.md) にする。

v1 Schema、generator `3.0.0-phase6`、Release format 1は意味とchecksumを凍結したcompatibility pathとして残す。既存v1 artifactは書き換えないread-only対象とし、互換期間中にv1生成／publishを継続する場合も出力契約を変えない。既存Releaseをv2で暗黙再解釈せず、v2への変換は新しいIntent、Blueprint、Releaseを作る明示的upgradeとする。

## 13. 制約を解消する作業単位

制約の解消はPhase全体を一度に行わない。[Task Execution Guide](task-execution-guide.md) に従い、次の境界で分割する。

| 現在の重大な制約 | 最初に扱うTask | 完了を確定するTask |
|---|---|---|
| `surface-2_5d`はoffline生成／検証／exportのみでPaper配置は未対応（解消: 評価用`/lfc r2`経路と64×64 smoke evidence、`V2-11-01`で4 entry×64×64のcatalog昇格まで完了） | `V2-6-01` | `V2-11-01`（完了） |
| global water graph／lake／delta／fjord不在 | `V2-3-01` | `V2-3-15` |
| offline geology／climate／ecology／semantic material不在（解消済み） | `V2-4-01` | `V2-4-15`（完了） |
| 同一X/Zの複数solid/fluid interval不在 | `V2-5-01` | `V2-5-18` |
| Release 2 transactionのrollback／Undo／Recovery（解消済み: `V2-6-08`〜`V2-6-10`実装、`V2-6-19`監査） | `V2-6-08` | `V2-6-19`（完了） |

途中Taskが完了しても、この表の制約全体を解消済みとは表現しない。実装済み部分は`EXPERIMENTAL`または個別capabilityとして記録し、親Phase統合Taskが全Acceptanceを再確認した場合だけsupported状態へ移す。V2-4 environmentは`V2-4-15`でこの条件を満たしたが、public／Paper境界は解消していない。
