# Changelog

## Unreleased — 2026-07-17 redesign (offline v2 track only)

### Fixed

- V2-6-21 P0再オープン（2026-07-20）: Release 2 operation stateをprepared／confirmed／appliedのcommit marker付きwrite-ahead順序へ変更し、fsync・atomic publish・strict read-backと全12 write point×before／after failpointを固定。confirm／apply間restart、Undo reservationの同placement置換と第三者overlap拒否、失敗時のapply lease復元、Recovery retry、baseline exact restoreをproduction file storeで検証した。R2 commandはoperator Player／CONSOLE／RCON限定、actor bindingとworld allow／deny・missing worldをstable domain error化し、permission-aware completionからdeny world／tokenを除外した。v1、Release format、Schema、example、support catalogは不変。

### Added

- V2-11-03 Docs／Schema／example consistency sync — **完了**（2026-07-20再監査のP2指摘を実装側に合わせて同期。Quickstartの`sandy-coast-001`生成→`rocky-coast-001`配置の誤記を修正し、CONSOLE／RCONではconfirmation tokenをchatへ出さずowner-onlyの`data/confirmations/<key>.command`（10分有効・1回用）へ保存しpathだけを表示する実運用を追記。`roadmap.md#terrain-generation-v2`参照3件を明示anchorで復旧し、`DocsLinkConsistencyTest`が全Markdownの相対link・anchorを検査。Schema 2件へ`title`、139件へ`description`を追加（validation semantics不変）。共通検証から唯一漏れていた`feature-support-catalog-v2.schema.json`をbundleへ加え、drift（5 Paper能力列の`const: UNSUPPORTED`が`V2-11-01`昇格後のsealed exampleと125件矛盾）を`supportLevel` enumへ同期、`FeatureSupportCatalogCodecV2.read`から共通strict検証を通す。昇格evidenceの強制は引き続き`FeatureSupportCatalogConsistencyVerifierV2`が正本。inventory検査としてschemaのdisk＝bundle＝packaged resource一致と`examples/`178 documentの参照必須を固定し、未参照だった14 exampleをstrict読取corpusへ追加。契約version・canonical checksum・昇格範囲は不変。次は`V2-11-04`）
- V2-11-02 R2 placement dimension guard／measurement profile — **完了**（2026-07-20再監査 判定#5の解消。`Release2MeasuredDimensionGateV2.production(...)`が`placement.release2.measured-candidate-max-*`をcatalog hard limit 64へクランプし64超の設定値を起動時に拒否、`Release2PlacementDimensionPolicyV2`が唯一のadmission pointとしてworld mutation・journal書込・reservation取得より前に上限超layoutを拒否。再実測専用`Release2MeasurementProfileV2`は既定無効で明示flag＋隔離world＋CONSOLE／RCON operatorの3条件必須、能力昇格は伴わない。`disk.minimum-free-bytes`／`disk.safety-margin-bytes`を`Release2DiskBudgetV2`としてR2 reservation floorへ初反映（従来は固定1 MiB）。`FilePlacementSafetyStoreV2.rebuild`をplan target箱からsealed effect envelope基準へ修正し、consumed confirmationのanti-replay保持を固定。新規寸法hard-codeなし。次は`V2-11-03`）
- V2-11-01 Paper apply capability promotion／Track D・E connection — **完了**（2026-07-20再監査で検出した「catalog／testは昇格済み、正本は未着手」の矛盾を、既存昇格の正式確定で解消。`paper_apply`／`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`をsmoke実測済み`surface-2_5d`のSANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPEの4 entryだけ64×64以内で`SUPPORTED`化し、runtime `paper-1.21.11+worldedit-7.3.19|fawe-2.15.2`と`V2-6-14`／`15` smoke＋[V2-6 Phase gate audit](docs/design-v2/audits/v2-6-phase-gate.md)へbind。`hydrology-plan`／`environment-fields`／`sparse-volume`の21 entryは`EXPERIMENTAL`、Release capability未接続のV2-9／V2-10 foundation entryは`UNSUPPORTED`維持。consistency verifierがsmoke-evidenced prefix／runtime／evidence link／Release capability path無しの昇格を拒否し、false-promotion corpusで偽装evidence昇格・500寸法limitを回帰固定。dimension limitは64×64のまま、寸法昇格は`V2-11-06`。次は`V2-11-02`）
- V2-6-19 Release candidate audit／V2-6 Phase gate — **完了**（`PlacementPhaseGateV2Test`：catalog Paper 5能力列非昇格・hard limit 64×64・sealed example checksum・capability list／5 valid prefix不変・placement example portfolio決定性・R2全lifecycle（plan→confirm→snapshot-all→containment→apply→settle→full verify→Undo）のrepeat／thread／locale／timezone同一block-state map；full clean test／build 909 tests；監査発見のlocale依存`toLowerCase()` 6箇所を`Locale.ROOT`へchecksum不変修正；能力昇格なし、昇格Taskとして`V2-11-01`を登録；[audit](docs/design-v2/audits/v2-6-phase-gate.md)。V2-6完了 19/21、`V2-6-16`／`17`無効化）
- V2-6-11 Provider／manual／image v2 capability integration（`ai.spi.v2` capability negotiation、OpenAI／Anthropic／import／fixture／manual／soft-draft paths、`TerrainDesignApplicationServiceV2`、Design Package v2、ADR 0029；no-fallback、v1 default不変；次は`V2-6-12`）
- V2-6-12 Release 2 cross-capability hardening（capability dependency matrix、cross-version reader policy、artifact limits catalog、placement eligibility、shared placement input contract、ADR 0030；全valid prefix directory／ZIP＋tamper corpus；Release 1 allowlist未緩和；次は`V2-6-13`）
- V2-6-13 Operational metrics／diagnostics／retention（`core.v2.operations`、closed-label metrics、redacted diagnostics、audit JSONL、actor-bound retention、Paper `/lfc ops`、ADR 0031；自動削除defaultなし；次は`V2-6-15`）
- V2-6-14 WorldEdit 7.3.19 Release 2 smoke — **完了**（`/lfc r2` E2E `APPLIED`→`UNDONE`、JAR／WE／plan／envelope／snapshot／verify checksum；[evidence](docs/design-v2/audits/v2-6-14-worldedit-smoke-evidence.md)；default water property read-back正規化；次は`V2-6-15`）
- V2-6-15 FAWE 2.15.2 standalone Release 2 smoke — **完了**（`runFaweServer`／`run-fawe`、`/lfc r2` E2E `APPLIED`→`UNDONE`、FAWE `2.15.2+e9ed0d1`、WE非併用、canonical stream=`V2-6-14`と同値；[evidence](docs/design-v2/audits/v2-6-15-fawe-smoke-evidence.md)）
- V2-6-16／V2-6-17 500×500／1000×1000 Paper実測 — **無効化（CANCELLED）**（典型開発ホストではcontainment／settle／verifyの壁時計とメモリが許容外。未測定寸法をcatalog `SUPPORTED`へ上げない）
- V2-6-18 Final supported catalog — **完了**（`FeatureSupportCatalogV2`／71 entry／13能力×role、placement hard limit 64×64、consistency verifier、Schema／sealed example、false-promotion corpus；Paper列と500／1000は非SUPPORTED；次は`V2-6-19`）
- V2-6-21 Release 2 Paper application／command lifecycle（strict source bind、plan→confirm→snapshot-all→containment→apply→settle/full verify、reverse rollback、operation-bound Undo／Recovery、restart inspection、`/lfc r2`、bounded worker shutdown、ADR 0034；v1 command／Release format／Schema不変；`V2-6-14`再開後に完了）

- V2-6-09 Release 2 Undo（`PlacementUndoPlanV2`／`PlacementUndoPrepareCompilerV2`／`PlacementUndoServiceV2`／`PlacementUndoApplicationServiceV2`／`PaperPlacementUndoServiceV2`、ADR 0027、UNDO confirm＋world-drift preflight＋逆順restore＋baseline full verify→`UNDONE`、force禁止、snapshot保持；startup Recoveryは`V2-6-10`）
- V2-6-07 bounded settle／full verify（`PlacementSettleVerifyPolicyV2`／`PlacementSettleVerifyServiceV2`／`PlacementExpectedBlockResolverV2`／`PlacementVerifyEvidenceV2`、ADR 0025、`SETTLING → VERIFYING →` terminal `APPLIED`、effect envelope exact stream、continuity metrics、`RECOVERY_REQUIRED` classification；rollback／Undo／Recovery／public command未接続）
- V2-10-09 Deferred terrain Phase gate（`DeferredTerrainPhaseGateV2Test`、21 V2-10 sealed example portfolio決定性、14 FeatureKind／12 dedicated module false-promotion検査、17非kind名不在、protected host checksum回帰、1000角ScaleProfile admission、full suite。offline plan-level generate／validation `SUPPORTED`、preview はpreview index証拠のあるsliceのみ`SUPPORTED`（`MORAINE_FIELD`／`OUTWASH_PLAIN`は`EXPERIMENTAL`）、intent／standalone／export `PARTIAL`、Paper以降`UNSUPPORTED`。[audit](docs/design-v2/audits/v2-10-phase-gate.md)）
- V2-10-11 EXPERIMENTAL `OXBOW_LAKE` foundation（`OxbowLakePlanV2`、`FoundationOxbowLakeSliceCompilerV2`、HARD `ORIGINATES_AT` parent `RIVER`／`MEANDERING_RIVER` + HARD `SUPPORTED_BY` host、FIXED `CLOSED` stagnant basin、cutoff reach bind、wetland handoff metrics、catalog未登録、open-spill `LAKE`／sealed river fixtures不変）
- V2-10-10 EXPERIMENTAL surface `SPRING` foundation（`SpringPlanV2`、`FoundationSpringSliceCompilerV2`、HARD `SUPPORTED_BY` surface host + HARD `EMPTIES_INTO` general `RIVER`、`RiverPlanV2` SOURCE bind、`HydrologyPlanV2.NodeKind.SPRING` id、outflow continuity metrics、catalog未登録、`KARST_SPRING`／sealed river fixtures不変）
- V2-10-08 `BARRIER_ISLAND`／`ATOLL` COMPOSITE_PRESET（`BarrierIslandPlanV2`／`AtollPlanV2`、`FoundationAdvancedIslandReefSliceCompilerV2`、`AdvancedIslandReefCatalogContractV2`、child `SINGLE_ISLAND`／`CORAL_REEF`＋`LAGOON`／`REEF_PASS` reuse、catalog候補5種、`FLOATING_REEF` deferred、FeatureKind／module未導入）
- V2-10-07 EXPERIMENTAL `LAVA_TUBE` foundation（`LavaTubePlanV2`、`FoundationLavaTubeSliceCompilerV2`、HARD `WITHIN`→`VOLCANIC_CONE`、HARD `ORIGINATES_AT` caldera/lava-flow provenance、`SweptSpline`＋`CARVE_SOLID` only、roof/support/entrance plan fields、catalog未登録、dynamic lavaなし）
- V2-10-06 EXPERIMENTAL `ESCARPMENT`／`PLATEAU` foundation（`EscarpmentPlanV2`／`PlateauPlanV2`、`FoundationEscarpmentPlateauSliceCompilerV2`、HARD `OVERLAPS` transition、`DryLandModifierContractV2` for dune/badlands/salt-flat modifiers、`MESA`／`BUTTE`は`PlateauProfile`のみ、catalog未登録、surface `CANYON`不変）
- V2-10-05 advanced river／lake **contract／split only**（`AdvancedRiverLakeSplitContractV2`、first slices `SPRING`→`V2-10-10`／`OXBOW_LAKE`→`V2-10-11`、他5種deferred、FeatureKind／generator未導入、V2-10 Task数 9→11）
- V2-10-04 EXPERIMENTAL `ABYSSAL_PLAIN`／`SEAMOUNT` additional-marine foundation（`AbyssalPlainPlanV2`／`SeamountPlanV2`、`FoundationAdditionalMarineSliceCompilerV2`、HARD `WITHIN`→`OCEAN_BASIN`、basin containment／depth-relief／slope／transition metrics、catalog未登録。`OCEAN_TRENCH`／`MID_OCEAN_RIDGE`／`SUBMARINE_VOLCANO`はcatalog評価のみ）
- V2-10-03 EXPERIMENTAL `SINKHOLE`／`KARST_SPRING` karst hydrology graph foundation（`SinkholePlanV2`／`KarstSpringPlanV2`／`KarstHydrologyGraphPlanV2`、`CenotePlanV2` COMPOSITE_PRESET、host `CAVE_NETWORK` checksum bind、loss/spring static balance、catalog未登録、`KARST_CAVE_SYSTEM`／`CENOTE` FeatureKind化なし）
- V2-10-02 EXPERIMENTAL `MORAINE_FIELD`／`OUTWASH_PLAIN` glacial-deposition foundation（`MoraineFieldPlanV2`／`OutwashPlainPlanV2`、glacial parent geometry bind、integer raster whole／tile export、`PermafrostPlainProfileV2` on `PLAIN`＋cold climate、catalog未登録）
- V2-10-01 EXPERIMENTAL `VALLEY_GLACIER`／`ICE_CAP`／`ICE_SHEET` glacial-ice foundation（共通`GlacialIcePlanV2`、bounded sparse `ADD_SOLID`、cold climate＋snow、`IceFjordPlanV2` COMPOSITE_PRESET、catalog未登録）
- V2-9-14 Terrain foundation Phase gate（`FoundationPhaseGateV2Test`、22 foundation plan example portfolio決定性、1000角ScaleProfile admission、false-promotion検査、full clean suite。offline plan-level generate／validation／preview `SUPPORTED`、intent／standalone／export `PARTIAL`、Paper以降`UNSUPPORTED`。[audit](docs/design-v2/audits/v2-9-phase-gate.md)）
- V2-9-13 river graph roles／`WATERFALL_CHAIN` preset（`RiverPlanV2` role extension、`WaterfallChainPlanV2`、FeatureKindなし、legacy hydrology fixtures不変）
- V2-9-12 macro land-water topology contract（`MacroLandWaterTopologyPlanV2`、manual mask／zone compile、FeatureKindなし、whole／tile／thread freeze）
- V2-9-11 EXPERIMENTAL `UNDERGROUND_RIVER`／`FLOODED_CAVE` flooded-volume connection（`UndergroundRiverPlanV2`、carve→単一`ADD_FLUID`、frozen cave＋lake checksum bind、catalog未登録）
- 3トラックroadmap（A: コア地形 / B: 画像忠実性 V2-7 / C: スケール V2-8）と実行モデル割当の正本 `docs/design-v2/model-assignment.md`（ADR 0015）
- scale契約 `model.v2.scale`（`ScaleClassV2`／`ScaleProfileV2`／`TilePlanV2`）と `core.v2.scale.ScaleAdmissionV2`（`scale-admission-v1`、ADR 0016）— `EXPERIMENTAL`、生成経路へ未接続
- 決定論的land-water抽出core `format.v2.constraint.extract`（`image-land-water-extract-v1`、`ExtractedMaskDraftV2`、ADR 0017）— `EXPERIMENTAL`、CLI／Paper／Requestへ未接続
- Minecraft palette adapter `model.v2.minecraft`／`format.v2.minecraft`（`MinecraftPalettePlanV2`、`minecraft-palette-resolver-v1`、ADR 0018）— V2-4 offline `SUPPORTED`、Paper apply未接続、v1 palette不変
- Mangrove regional shaping `generator.v2.landform.mangrove`（`MangroveWetlandPlanV2`、Stage 6 `MANGROVE_TIDAL_LINK`、`kind-feature-constraint-v2`）— V2-4 offline `SUPPORTED`、Paper未接続
- Coral reef bathymetry `generator.v2.landform.reef`（`CoralReefPlanV2`、Stage 6 `REEF_LAGOON_PASS`、`kind-feature-constraint-v3`）— V2-4 offline `SUPPORTED`、Paper未接続
- Sparse ecology placement `model.v2.ecology`／`generator.v2.ecology`（`EcologyPlanV2`、`ecology-placement-contract-v1`、habitat／assemblage／density-spacing）— V2-4 Release componentとしてoffline `SUPPORTED`、WorldBlueprint／Paper未接続、cave／entity非対応
- Volcanic／canyon feature material overlay `model.v2.material.feature`／`generator.v2.material.feature`（`FeatureMaterialProfilePlanV2`、basalt／tuff／ash・strata／talus／sediment）— V2-4 Release componentとしてoffline `SUPPORTED`、shape generator不変、Paper未接続
- Environment validator／preview `validation.v2.environment`／`preview.v2`（`EnvironmentValidatorV2`、10-layer diagnostic bundle、`environment-validation-artifact-v2`／`environment-preview-index-v2`）— V2-4 offline `SUPPORTED`
- Release 2 `environment-fields` capability（ADR 0019、`ReleaseEnvironmentPublisherV2`、palette／ecology／snow／material exact set）— V2-4 offline `SUPPORTED`、Paper未接続
- V2-4 Phase gate audit（5 scenario、order／thread／locale／timezone determinism、1000角budget、tampering／cancel、v1／Release 1／V2-2／V2-3回帰）
- Volume SDF primitives `model.v2.volume`／`generator.v2.volume.sdf`（`volume-sdf-primitive-contract-v1`、`volume-sdf-fixed-v1`／`volume-sdf-q-v1`、6 analytic primitives）— `EXPERIMENTAL`、CSG／feature／Release未接続
- Ordered volume CSG `model.v2.volume.VolumeCsgPlanV2`／`generator.v2.volume.csg`（`volume-csg-contract-v1`、`volume-csg-ordered-v1`、ADD_SOLID／CARVE_SOLID／ADD_FLUID）— `EXPERIMENTAL`、feature／Release未接続
- Volume AABB spatial index `model.v2.volume.VolumeAabbIndexPlanV2`／`generator.v2.volume.index`（`volume-aabb-index-contract-v1`、`volume-aabb-index-v1`、halo query／ordinal order）— `EXPERIMENTAL`、feature／Release未接続
- Volume 3D tile cache `model.v2.volume.VolumeTileCachePlanV2`／`generator.v2.volume.cache`（`volume-tile-cache-contract-v1`、`volume-tile-cache-v1`、LRU／intervals／admission／dense detector）— `EXPERIMENTAL`、feature／Release未接続
- Volume TerrainQuery composition `generator.v2.volume.query`（`terrain-query-volume-v1`、base＋ordered CSG、intervals／ceiling／surface）— `EXPERIMENTAL`、feature／Paper未接続、v1 adapter不変
- Cave network `model.v2.volume.CaveNetworkPlanV2`／`generator.v2.volume.cave`（`cave-network-contract-v1`、`cave-network-v1`、graph→CARVE_SOLID）— `EXPERIMENTAL`、lake／sea cave／Release未接続
- Lush cave `model.v2.volume.LushCavePlanV2`／`generator.v2.volume.cave.lush`（`lush-cave-contract-v1`、`lush-cave-v1`、WITHIN／REACHABLE_FROM／wet surfaces／ecology hooks）— `EXPERIMENTAL`、full ecology／lake／lighting／Release未接続
- Underground lake `model.v2.volume.UndergroundLakePlanV2`／`generator.v2.volume.water`（`underground-lake-contract-v1`、`underground-lake-v1`、CARVE→ADD_FLUID／rim／air cavity）— `EXPERIMENTAL`、sea／Paper fluid／Release未接続
- Sea cave `model.v2.volume.SeaCavePlanV2`／`generator.v2.volume.seacave`（`sea-cave-contract-v1`、`sea-cave-v1`、cliff opening／static ADD_FLUID）— `EXPERIMENTAL`、dynamic tide／Paper／Release未接続
- Overhang `model.v2.volume.OverhangPlanV2`／`generator.v2.volume.overhang`（`overhang-contract-v1`、`overhang-v1`、SUPPORTS_FROM／ADD_SOLID＋CARVE recess）— `EXPERIMENTAL`、natural arch／Paper gravity／Release未接続
- Natural arch `model.v2.volume.NaturalArchPlanV2`／`generator.v2.volume.arch`（`natural-arch-contract-v1`、`natural-arch-v1`、two-pier＋through carve）— `EXPERIMENTAL`、bridge asset／sky island／Release未接続
- Sky island group `model.v2.volume.SkyIslandGroupPlanV2`／`generator.v2.volume.skyisland`（`sky-island-group-contract-v1`、`sky-island-group-v1`、multipoint＋underside carve）— `EXPERIMENTAL`、ecology／Paper／Release未接続
- Waterfall volume `model.v2.volume.WaterfallVolumePlanV2`／`generator.v2.volume.waterfall`（`waterfall-volume-contract-v1`、`waterfall-volume-v1`、fall checksum binding／column＋behind＋plunge fluid）— `EXPERIMENTAL`、dynamic fluid／Paper／Release未接続
- Post-volume local environment `model.v2.environment.local.VolumeLocalEnvironmentPlanV2`／`generator.v2.environment.local`（`volume-local-environment-contract-v1`、`volume-local-environment-v1`、surface／wetness／sparse moss-root）— `EXPERIMENTAL`、lighting／Paper／Release未接続
- Volume validator／preview `validation.v2.volume`／`preview.v2`（`VolumeValidatorV2`、5-layer diagnostic bundle、`volume-validation-artifact-v2`／`volume-preview-index-v2`）— `EXPERIMENTAL`、Release／`SUPPORTED`未接続
- 再設計調査記録 `docs/audits/redesign-2026-07-current-state.md`、LARGE実行モデル設計 `docs/design-v2/scale-and-streaming.md`
- Offline 3D volume tile read-back `format.v2.tile.VolumeTileBlockResolverV2`（`terrain-query-volume-v1`合成→canonical block state、cave air／fluid／floating solid、strict inspector＋WorldEdit 7.3.19 read-back、127/128 VarInt 3D境界）— V2-5-16
- Release 2 `sparse-volume` capability（`ReleaseSparseVolumePublisherV2`／`ReleaseSparseVolumeVerifierV2`、SDF／CSG／AABB／validation／volume tileのexact set、CSG→SDF／AABB→CSG binding、directory／ZIP parity）— V2-5-17
- V2-5 Phase gate audit（`VolumePhaseGateV2Test`、lifecycle／capability順、13 example strict read安定性、whole／tile／XYZ seam scene合成、dense allocation拒否・1000角admission、full suite・v1／Release 1／V2-2〜V2-4回帰）と `docs/design-v2/audits/v2-5-phase-gate.md` — V2-5-18
- Release 2 placement contract `model.v2.placement`／`core.v2.placement`（`PlacementPlanV2`、`PlacementJournalV2`、`release-2-placement-contract-v1`／`release-2-placement-journal-v1`、ADR 0020）— V2-6-01、envelope／reservation／apply未接続、v1 journal不変
- Release 2 mutation／effect envelope `PlacementEnvelopePlanV2`／`PlacementEnvelopeCompilerV2`（`release-2-placement-envelope-contract-v1`、physics policy、AABB union、ADR 0021）— V2-6-02、reservation／snapshot／apply未接続
- Surface foundation contract `model.v2.foundation`／`core.v2.foundation`／`generator.v2.foundation`（`SurfaceFoundationPlanV2`、`surface-foundation-field-contract-v1`、ScaleAdmission、owner/transition merge）— V2-9-01、`EXPERIMENTAL`、kind SUPPORTEDなし、WorldBlueprint／Paper／LARGE未接続
- PLAIN／HILL_RANGE vertical slices `PlainPlanV2`／`HillRangePlanV2`／`FoundationPlainHillSliceCompilerV2`（microrelief＋groundwater handoff、ridge／saddle budget＋plain transition、validation／preview／sealed export）— V2-9-02、`EXPERIMENTAL`、`BACKSHORE_PLAINS` ID維持、WorldBlueprint／SUPPORTED未接続
- MOUNTAIN_RANGE／VALLEY vertical slices `MountainRangePlanV2`／`ValleyPlanV2`／`FoundationMountainValleySliceCompilerV2`（derived peak／saddle／spur／pass／foothill、V／U cross-section、floor／shoulder／connection role、validation／preview／sealed export）— V2-9-03、`EXPERIMENTAL`、ALPINE／GLACIAL／FJORD seed不変、WorldBlueprint／SUPPORTED未接続
- General RIVER foundation `RiverPlanV2`／`FoundationRiverSliceCompilerV2`（source→mouth reach graph、bank／bed／discharge／floodplain handoff、orphan／self-loop拒否）— V2-9-04、`EXPERIMENTAL`、`MEANDERING_RIVER` serialization／SUPPORTED不変、WorldBlueprint未接続
- FLOODPLAIN／MARSH hydrologic foundation `FloodplainPlanV2`／`MarshPlanV2`／`FoundationFloodplainMarshSliceCompilerV2`（river adjacency、groundwater／hydroperiod／wetness、open-water fluid／solid ownership）— V2-9-05、`EXPERIMENTAL`、`MANGROVE_WETLAND`不変、WorldBlueprint未接続
- ROCKY_COAST／SEA_CLIFF foundation `RockyCoastPlanV2`／`SeaCliffPlanV2`／`FoundationRockyCoastCliffSliceCompilerV2`（rock shelf／cliff face／talus／notch、coast transition、sea-cave／overhang host AABB handoff）— V2-9-06、`EXPERIMENTAL`、catalog未登録、`ROCKY_CAPE`／SeaCave／Overhang checksum不変、WorldBlueprint未接続
- SINGLE_ISLAND／ARCHIPELAGO／VOLCANIC_CONE foundation `SingleIslandPlanV2`／`ArchipelagoPlanV2`／`VolcanicConePlanV2`と各slice compiler（island mass／shore／drainage／apron、group spacing／saddle、cone／crater）— V2-9-07、`EXPERIMENTAL`、catalog未登録、`VolcanicIslandConeAdapterV2` suggested-params only、`VOLCANIC_ARCHIPELAGO` checksum／hook不変、WorldBlueprint未接続
- OCEAN_BASIN／CONTINENTAL_SHELF／CONTINENTAL_SLOPE bathymetry `OceanBasinPlanV2`／`ContinentalShelfPlanV2`／`ContinentalSlopePlanV2`と3 slice＋coast-to-basin transect（depth／ownership／streaming underwater export、dense 3Dなし）— V2-9-08、`EXPERIMENTAL`、catalog未登録、WorldBlueprint未接続
- SUBMARINE_CANYON bathymetric carve `SubmarineCanyonPlanV2`／`FoundationSubmarineCanyonSliceCompilerV2`（shelf／slope／basin host relations、centerline depth carve、whole／tile＋streaming underwater export）— V2-9-09、`EXPERIMENTAL`、surface CANYON未変更、catalog未登録、WorldBlueprint未接続
- CAVE_ENTRANCE surface-volume connector `CaveEntrancePlanV2`／`FoundationCaveEntranceSliceCompilerV2`（`CaveEntranceParameters`、HARD ENTRANCE_OF＋SUPPORTED_BY、frozen CaveNetworkPlan checksum bind、opening／approach carve、seamless query／export）— V2-9-10、`EXPERIMENTAL`、cave-network checksum不変、catalog未登録、WorldBlueprint未接続
- Release 2 region／disk reservation／bound confirmation `PlacementReservationPlanV2`／`PlacementSafetyStateV2`／`FilePlacementSafetyStoreV2`／`PlacementConfirmationBinderV2`（ADR 0022）— V2-6-03、snapshot／apply未接続、v1 reservation不変
- Release 2 snapshot-all `core.v2.placement.snapshot`（`PlacementWorldGatewayV2`／`PlacementSnapshotFileCodecV2`／`PlacementSnapshotAllCompilerV2`、`PlacementSnapshotPlanV2`＝`release-2-placement-snapshot-v1`、2回走査world drift拒否、staging→strict read-back→atomic publish、`SNAPSHOT_COMPLETE`＝apply-ready）— V2-6-04、apply／settle／rollback未接続、v1 snapshot不変
- Release 2 fluid／gravity／neighbor containment preflight `core.v2.placement.safety`／`format.v2.minecraft.PlacementBlockPhysicsCatalogV2`（`PlacementContainmentPolicyV2`／`PlacementContainmentEvidenceV2`、ADR 0023）— V2-6-05、apply／settle未接続、snapshot外検出非依存
- Release 2 canonical apply transaction `core.v2.placement.apply`／`paper.PaperPlacementWorldGatewayV2`／`worldedit.v2.WorldEditBlockMutationAccessV2`（strict prerequisite chain、feature-neutral X→Z→Y stream、明示solid→air carve→fluid／overlay順、bounded executor／queue／slice、late completion reconciliation、atomic `APPLYING`／canonical tile prefix journal、ADR 0024）— V2-6-06、settle／full verify／rollback／Undo／Recovery／public enable未接続、v1 placement不変
- Skill 5件追加（survey-repository / assign-task-model / change-schema-or-ir / add-image-constraint / sync-docs-roadmap）

### Changed

- AGENTS.md、task-execution-guide、task-index、implementation-roadmap、README、architectureを3トラック体制・新境界へ同期
- V2-4対象module／featureと`environment-fields` lifecycleをoffline `SUPPORTED`へ昇格
- V2-5 Phase gate（V2-5-18）でvolume plan compiler群・waterfall volume・local environment・`sparse-volume`と、deferredだった`WATERFALL` kind／moduleをoffline `SUPPORTED`へ昇格し、Track Aの次Taskを`V2-6-01`へ更新
- v1 Schema、generator `3.0.0-phase6`、Release format 1、既存checksum、公開CLI／Paper挙動は一切変更なし

## 0.9.0-beta.1 — Unreleased release candidate (2026-07-14 audit)

### Added

- Paper request、design、generate、job、candidate、export、version、doctor、Recovery、cleanup、asset commands
- 機能単位permissionと権限対応completion
- stable public error code、correlation ID、safe message契約
- actor domain identityとactor-bound／一回用confirmation
- world UUID＋inclusive boundsのatomic placement reservationとoverlap拒否
- snapshot worst-case disk見積もり、FileStore別永続予約、実使用量journal
- retention dry-run／確認付きcleanup／checksum再検査／audit
- Recovery診断分類、rollback、full-match限定accept、audit
- Paper OpenAI／Anthropic設定、環境変数key、model／policy hard range
- CLI common option、doctor、request、job、candidate、asset、read-only recovery
- 制限付きSponge v3 custom asset catalogとsecurity検査
- beta向けQuick Start、User／Admin Guide、仕組み、障害対応、制限、release checklist

### Changed

- versionを `0.9.0-beta.1`へ更新
- WorldEdit／FAWEが無い場合も非placement Paper commandを利用可能に変更
- OpenAI requestへ決定論的client request IDを付加
- placement journal／Schemaへactor、confirmation時刻、disk reservation／usageを追加
- READMEとroadmapをBeta stabilizationへ再構成
- Phase 3 terminal journalを`SYSTEM:LEGACY`として保守的に読めるupgrade互換loaderを追加
- preferred zone fallbackをmanifest／warning／previewへ明示し、柵の4方向connection stateを決定論的にmaterialize
- Paper jobの即時statusと単調な永続化、request別candidate一覧／完全性検証へ強化

### Removed

- 独自Web UI／Phase 7 UI／Web専用workerを将来計画から削除
- broad apply／undo permissionをplan／execute／status／recoveryへ分割

### Security

- 別actor、overlap領域、disk不足、symlink asset、RECOVERY_REQUIRED snapshot削除をworld変更／削除前に拒否
- API key値、Provider response本文、internal exception、absolute pathを利用者向けerrorへ出さない契約を追加

### Compatibility

placement journalとpermission名がbeta前snapshotから変わります。upgrade前にworldとdata rootをbackupしてください。downgradeは保証しません。
