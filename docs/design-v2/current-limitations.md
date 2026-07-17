# 現行実装の制約と再設計の出発点

> Status: 設計調査。現行v1の制約は継続する。V2-0〜V2-3はv1から分離して完了し、`V2-4-01`〜`V2-4-05`のtyped geology／lithology／strata、coarse climate prior／final temperature・moisture、regional wetness／salinity／hydroperiodを`EXPERIMENTAL`で追加した。snow、ecology／material、waterfall volume、mountain environment、volcanic material、公開CLI／Paperおよび配置への接続は未実装である。次は`V2-4-06`で、詳細は [Task Index](task-index.md)、進捗は [docs/roadmap.md](../roadmap.md) を正本とする。

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

[TerrainIntent.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/TerrainIntent.java) と [terrain-intent.schema.json](../../schemas/terrain-intent.schema.json) が持つ地形情報は、`topology`、`seaSides`、`landRatio`、全域共通のrelief、海岸集約値、水系個数、coarse zone、少数structureに限られる。

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

重大な点として、`theme`、`bayCount`、`capeCount`、`shallowShelfWidth` は現行generatorの形状計算から参照されない。Schemaに存在し保存されても、生成結果へ直接効かない。`areaShare` も実現面積ではなく、[LogicalLayoutGenerator.java](../../src/main/java/com/github/nankotsu029/landformcraft/generator/LogicalLayoutGenerator.java) のGaussian zone scoreの係数である。

`examples/azure-coast/request.yml` は砂浜幅、中央の曲線防波堤、湾内外の深度、岩礁cape、zone間遷移を詳細に指定する。しかし生成済みv1 Intentでは `SANDY_BEACH`、`ROCKY_COAST`、`CLIFFS` などへ圧縮され、曲線、幅、接続、断面を保持できていない。`examples/mountain-stream` の滝、段瀑、滝壺、植生、岩種も同じ理由でtheme以上の生成契約にならない。

## 4. WorldBlueprint v1はコンパイル済みIRではない

[WorldBlueprint.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/WorldBlueprint.java) は次の8要素だけを持つ。

```text
schemaVersion / requestId / bounds / intent / seed
tileSize / logicalResolution / generatorVersion
```

[BlueprintCompiler.java](../../src/main/java/com/github/nankotsu029/landformcraft/core/BlueprintCompiler.java) が行う主処理は、requestとIntentのSchema version一致、candidate seed導出、最大辺に応じた64または128のlogical resolution選択である。land-water mask、curve、feature dependency、drainage、地質、気候、volume、validation targetへの意味的compileはない。

したがって現行 `WorldBlueprint` は「検証済み入力をgeneratorへ渡す固定bundle」ではあるが、「生成途中の意味を保持する実行計画」ではない。v2ではこの責務を根本的に変える必要がある。

## 5. 現行generatorの実態

[TerrainGenerator.java](../../src/main/java/com/github/nankotsu029/landformcraft/generator/TerrainGenerator.java) と [TerrainPlan.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/TerrainPlan.java) の中心表現は次である。

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

[SurfaceMaterial.java](../../src/main/java/com/github/nankotsu029/landformcraft/model/SurfaceMaterial.java) は `GRASS/SAND/STONE/GRAVEL/MUD/SNOW` の6値だけである。[TerrainGenerator.materialAt](../../src/main/java/com/github/nankotsu029/landformcraft/generator/TerrainGenerator.java) は水深、傾斜、zone、maxY付近、1本のwetness noiseからこの6値を選ぶ。

現状には次がない。

- snow、ecology、semantic material、Minecraft palette（climate prior／final temperature・moistureとregional wetness／salinity／hydroperiodまではV2-4-05で追加済み）
- temperature、precipitation、moisture、wind exposure、salinity、sunlight（regional salinity／wetness fieldはV2-4-05で追加。snow／ecology未実装）
- soil depth、groundwater、habitat、species assemblage
- basalt/tuff/andesite、clay、wet rock、underwater substrate等のsemantic material profile
- 樹木、根、珊瑚、水草等の実block配置

`VEGETATION` は草地上で立つ診断bitであり、植生descriptorやplacementではない。雪も気候上のsnowlineではなく、`maxY` 近傍を一律 `SNOW` にする規則である。

## 8. v1では画像が直接制約になっていない

[ReferenceImageProcessor.java](../../src/main/java/com/github/nankotsu029/landformcraft/validation/ReferenceImageProcessor.java) は、path、symlink、magic、byte、pixel、frame、EXIF、metadataを厳格に扱う優れた入力境界を持つ。ただしpixelの利用先はProviderである。

- `TOP_DOWN_SKETCH` だけがboundsへの座標対応と四辺のwater ratioを持つ。
- `HEIGHT_REFERENCE`、`ZONE_REFERENCE`、`MATERIAL_REFERENCE` はAIへのrole説明であり、generator fieldへdecodeされない。
- [GenerationApplicationService.java](../../src/main/java/com/github/nankotsu029/landformcraft/core/GenerationApplicationService.java) はgenerate時にrequestとIntentだけを読み、画像を読まない。
- Design Packageは画像binaryを保存せず、Release v1にもconstraint artifactはない。

したがって画像は `画像 → AI要約 → TerrainIntent v1` で情報が圧縮され、正確なmask、curve、height guideとして再現性の入力にならない。

この制約はv1経路についての記述である。V2-1ではreference imageとは別のRequest契約で`LAND_WATER_MASK`、`HEIGHT_GUIDE`、`ZONE_LABEL_MAP`をcanonical fieldへ変換するmanual pathを実装済みである。ただし、それらを読むspecialized generatorはまだないため、地形表現の制約が解消するのはV2-2以降の各feature Taskと親Phase gateが完了した後である。

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
- 配置はtileごとにsnapshot直後にapplyする。流体や重力blockが未snapshotの隣tileへ影響し得るv2では、全影響範囲のsnapshotをmutation前に完了する必要がある。
- full cuboid verifyはPaper main thread上の大規模scanとなるため、500/1000角の実world計測が現行betaでも未完了である。

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
| `surface-2_5d`はoffline生成／検証／exportのみでPaper配置は未対応 | `V2-6-01` | `V2-6-19` |
| global water graph／lake／delta／fjord不在 | `V2-3-01` | `V2-3-15` |
| 完成したgeology／climate／ecology／semantic material不在（climate coreまで完了） | `V2-4-05` | `V2-4-15` |
| 同一X/Zの複数solid/fluid interval不在 | `V2-5-01` | `V2-5-18` |
| Release 2を安全に配置するtransaction不在 | `V2-6-01` | `V2-6-19` |

途中Taskが完了しても、この表の制約全体を解消済みとは表現しない。実装済み部分は`EXPERIMENTAL`または個別capabilityとして記録し、親Phase統合Taskが全Acceptanceを再確認した場合だけsupported状態へ移す。
