# LandformCraft

LandformCraft は、自然言語・役割付き参考画像・手動JSONから設計意図を作り、決定論的なJavaエンジンでMinecraft 1.21.11向け地形と小規模構造物を生成するPaperプラグイン／CLIです。

## Beta status

現在のrelease candidate versionは `0.9.0-beta.1`です。公開CLI／Paperの既定経路はv2（Request／TerrainIntent／Release format 2）で、V2-12-06でv1 production writer／generator／placement／通常commandは削除され、既存v1 artifactはpackaged legacy read／verify／migrate境界からだけ扱えます。V2-0／V2-1に加え、V2-2では4 coastal feature、independent validator／diagnostic preview、offline tile schematic、offline専用の`surface-2_5d` Release format 2 capabilityまでPhase gateを完了し、offline経路を`SUPPORTED`としました。V2-3もPhase gateを完了し、Hydrology IR／fixed prior、全域basin／D8 routing、固定3 pass reconciliation、独立validator／12-layer preview、strict `hydrology-plan` Release 2 capabilityと、offlineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDを`SUPPORTED`としました。V2-4もPhase gateを完了し、typed geology／lithology／strata、coarse climate prior／final temperature・moisture、regional water condition、snow、semantic material、Minecraft 1.21.11 palette、sparse ecology／feature-material plan、独立environment validator／10-layer preview、strict Release 2 `environment-fields` capabilityをofflineで`SUPPORTED`としました。対象kindはALPINE_MOUNTAIN_RANGE、GLACIAL_MOUNTAIN_RANGE、MANGROVE_WETLAND、CORAL_REEF、VOLCANIC_ARCHIPELAGOです。CANYONはV2-3の`SUPPORTED`を維持し、strata／feature-material経路をV2-4 portfolioへ統合しました。ecology／feature materialはportable Releaseへ収容されるsealed offline componentですが、WorldBlueprintの公開dispatch／Paper／entity配置へは未接続です。V2-5もPhase gateを完了し、sparse volume基盤（fixed-point SDF／ordered CSG／AABB index／3D tile cache／volume対応TerrainQuery）、cave network／lush cave／underground lake／sea cave／overhang／natural arch／sky island group／waterfall volume／post-volume local environment、volume validator／5-layer diagnostic preview、offline 3D volume tile read-back、strict Release 2 `sparse-volume` capabilityをofflineで`SUPPORTED`とし、後続volume待ちだったWATERFALLも`SUPPORTED`になりました。GLACIAL_CIRQUE_FIELD／LAGOON／REEF_PASS／VOLCANIC_CALDERA／LAVA_FLOW_FIELDはchild-plan完成前のため`EXPERIMENTAL`です。volume featureはoffline planレベルで、VOLUME_GUIDE intent kindの公開dispatchには接続していません。

2026-07-17の再設計（[ADR 0015](docs/adr/0015-adopt-v2-foundation-three-track-roadmap.md)）で、roadmapを3トラック（A: Environment→Sparse volume→Placement、B: 画像忠実性 V2-7、C: スケール V2-8）へ再構築しました。新境界として、通常画像からの決定論的land-water抽出core（`image-land-water-extract-v1`、V2-7-01）と、SMALL／MEDIUM／LARGE（最大3072、推奨3000）のscale契約・tile plan・事前admission（`scale-admission-v1`、V2-8-01）を`EXPERIMENTAL`で実装済みです。どちらもCLI／Paper／Requestへは未接続で、**3000×3000の生成はまだできません**（request／Schema水平上限はMEDIUM=1024、`V2-13-02`。Paper配置のcatalog上限は1000×1000のまま。1024² offline generationは`V2-13-03`のfollow-upでE2E成立し、1024² FAWE配置も`V2-13-04`／`V2-13-06`で実測済みですが、catalog昇格は別承認です）。Track AはV2-5（Sparse volume）を`V2-5-18`のPhase gateまで完了し、offline `sparse-volume` Releaseを生成・strict verifyできます。V2-6はplacement plan〜Recovery、provider／manual／image v2 design path、Release hardening、運用metricsに加え、strict canonical source（ADR 0033）と明示的なPaper application lifecycle（ADR 0034、V2-6-21）まで実装しました。operation-bound Undoと保守的Recoveryを備えたRelease 2 lifecycleは、V2-12-06以降唯一のproduction placement経路です。WorldEdit 7.3.19実機smoke（`V2-6-14`）とFAWE 2.15.2単独smoke（`V2-6-15`）は完了しました。500／1000 Paper実測Task（`V2-6-16`／`V2-6-17`）は無効化済みです。`V2-6-18`で能力別Feature Support CatalogとPaper寸法hard limitを固定し、未測定寸法とPaper能力列をproduction `SUPPORTED`にしません。`V2-6-19`のRelease candidate auditでV2-6 Phase gateを閉じました（[audit](docs/design-v2/audits/v2-6-phase-gate.md)）。gate自体は能力を昇格せず、続く`V2-11-01`が実機evidenceの範囲だけをcatalog昇格しました。`paper_apply`／`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`が`SUPPORTED`なのは、smoke実測済み`surface-2_5d` capabilityのSANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPEを適用する場合だけで、`hydrology-plan`／`environment-fields`／`sparse-volume` featureは`EXPERIMENTAL`、V2-9／V2-10 foundation featureは`UNSUPPORTED`のままです。`V2-11-04`／`V2-11-05`のFAWE 2.15.2実測（500×500／1000×1000）を受けて、`V2-11-06`がcatalogのPaper寸法上限を1000×1000へ昇格しました。V2-13-06はapply sliceを同一FAWEホストで較正し、production既定を1024 mutations/sliceへ変更しました（1000²／1024²でAPPLY約93.4%、lifecycle約74.3%短縮、full verify／Undo／Recovery不変）。この性能変更は寸法や能力の昇格ではありません。この寸法evidenceはFAWE単独で、WorldEdit 7.3.19では64×64のまま、1000超は通常配置で拒否されます。

2026-07-18にV2-9／V2-10をTrack D／Eとして独立させ5トラック体制へ拡張し、Track D（V2-9 Terrain foundation）は`V2-9-14`のPhase gateまで完了しました。surface（PLAIN／HILL_RANGE／MOUNTAIN_RANGE／VALLEY）、一般RIVER graph（roles／`WATERFALL_CHAIN` preset）、FLOODPLAIN／MARSH、ROCKY_COAST／SEA_CLIFF、island／cone、marine bathymetry（OCEAN_BASIN〜SUBMARINE_CANYON）、CAVE_ENTRANCE／UNDERGROUND_RIVER、macro land-water topologyのoffline plan-level生成・validation・previewを`SUPPORTED`としましたが、これらはplan-level APIのみで、公開Intent dispatch（diagnostic bindingのまま）、Release 2 capability、CLI／Paper、配置へは接続していません（[V2-9 Phase gate audit](docs/design-v2/audits/v2-9-phase-gate.md)）。Track E（V2-10 Deferred terrain families）も`V2-10-09`のPhase gateまで完了し、glacial ice／deposition、karst hydrology graph、additional marine（ABYSSAL_PLAIN／SEAMOUNT）、ESCARPMENT／PLATEAU、LAVA_TUBE、surface SPRING、OXBOW_LAKEのoffline plan-level生成・validationを`SUPPORTED`としました（previewはpreview index証拠のあるsliceのみ。MORAINE_FIELD／OUTWASH_PLAINのpreviewは`EXPERIMENTAL`のまま）。これらも同じくplan-level APIのみで、preset（ICE_FJORD／CENOTE／BARRIER_ISLAND／ATOLL）、profile（PERMAFROST_PLAIN等）、deferred候補は昇格していません（[V2-10 Phase gate audit](docs/design-v2/audits/v2-10-phase-gate.md)）。
V2のRelease 2配置はPaperの既定`/lfc place`経路（明示形`/lfc v2 place`）へ接続し、`V2-11-01`でsmoke実測済みの`surface-2_5d` 4 featureだけを`SUPPORTED`にし、`V2-11-06`で寸法上限をFAWE実測の1000×1000（WorldEdit単独は64×64）へ昇格しました。それ以外のfeatureは未SUPPORTEDのため評価用です。配布configの寸法既定値は保守的に64×64のままで、引き上げは運用者の明示設定です。v1 commandとRelease 1 writerは削除済みで、Release 1はread／verify／migrate専用です。Beta hardeningのrelease checklist未完了が残るため、まだ公開可能なbetaとは判定していません。評価時もデータのbackup、Release検証、test worldでの事前配置を必須運用とします。

## できること

- 手動JSON、OpenAI Responses API、Anthropic Messages APIからTerrainIntentを作成
- 6 roleのPNG／JPEGを安全に前処理してDesign Packageへ記録
- seed再現可能な地形、8種類のbuilt-in小規模構造物、8種類のPNG previewを生成
- tiled Sponge Schematic v3、checksum、directory／ZIPを含むRelease Packageを生成・検証
- WorldEditまたはFAWEの公開APIでsnapshot付き配置、verify、rollback、Undo、明示Recovery
- world UUID＋inclusive boundsの予約、actor-bound一回用token、disk容量予約、snapshot cleanup
- 制限付きcustom asset catalog（Sponge v3、vanilla allowlist、entity／biome／block entityなし。TerrainIntent v1からの選択は未対応）

## できないこと

独自Web UI、巨大都市の全自動生成、AIによる全block列挙、AI生成コードの実行、entity／高度なblock entity、biome書換えは対象外です。洞窟・高度な植生・fjord・delta等も現行v1では未対応です。V2-4のecology／semantic materialはoffline `environment-fields`の`SUPPORTED` componentですが、公開Intent dispatch、entity／実block配置、cave-local environmentへは未接続です。V2-5のvolume feature（cave等）とWATERFALLもoffline `SUPPORTED`ですが、通常のproduction export／Paper配置へは未接続です。実体化されていないmountain／reef／volcanic child kindは`EXPERIMENTAL`です。1024超〜3000×3000のLARGE生成はscale契約のみで生成経路は未実装（V2-8で段階実装）、通常画像からの制約抽出も抽出coreのみで入力封筒・draft保存・昇格は未実装（V2-7で段階実装）です。既定CLI／Paperと配置はv2へ接続済みですが、productionで通常利用できる地形範囲は`surface-2_5d`の実測済み4 featureに限られます。詳しくは [制限事項](docs/limitations.md) と [Roadmap](docs/roadmap.md) を参照してください。

## 必要環境

- Java 21
- Paper 1.21.11
- 配置する場合はWorldEdit 7.3.19、またはPaper 1.21.11対応FAWEをどちらか一方
- buildには同梱Gradle Wrapper

Paper、Bukkit、WorldEdit、FAWE本体は配布JARへshadeしません。WorldEditとFAWEを同時に導入しないでください。

## 5分Quick Start

```bash
./gradlew clean build
./gradlew run --args="request validate examples/v2/diagnostic/harbor-cove-64.request-v2.json"
./gradlew run --args="export examples/v2/diagnostic/harbor-cove-64.request-v2.json examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json build/exports harbor-cove-64 water 54 46"
./gradlew run --args="preview build/exports/harbor-cove-64"
```

Paperへ `build/libs/LandformCraft-0.9.0-beta.1.jar` とWorldEditまたはFAWEを配置し、test worldで次を実行します。

```text
/lfc version
/lfc doctor
/lfc place plan harbor-cove-64/<release-id> world 0 64 0
/lfc place confirm <placement-id> <one-time-token>
/lfc place execute <placement-id>
/lfc status <placement-id>
/lfc undo plan <placement-id>
/lfc undo execute <placement-id> <one-time-token>
```

完全な手順は [Quick Start](docs/quickstart.md) を参照してください。

## CLI

CLIはv2のみです。`lfc <verb>`は恒久的な明示形`lfc v2 <verb>`と同じで、主要コマンドは`request`、`design`、`generate`、`export`、`preview`、`job`、`candidate`、`journal-verify`、`recovery inspect`、`migrate`、`doctor`、`version`、`asset`です。共通optionは`--data-dir`、`--json`、`--quiet`、`--verbose`です。

```bash
./gradlew run --args="--help"
./gradlew run --args="doctor --data-dir build/beta-data --json"
```

CLIからMinecraft worldは変更しません。

## Paper

Paperはv2のみです（`/lfc <verb>`＝`/lfc v2 <verb>`）。request authoring→design→generate/export（job／candidate／二段階export）→place→status→undo→recover→retentionを実行できます。長文promptは5分期限の一回用chat sessionで受け取り、`cancel`で中断できます。API keyをchatやコマンドへ入力してはいけません。コマンドとpermission一覧は [コマンドリファレンス](docs/commands.md) を参照してください。

Release 2は`plugins/LandformCraft/data/releases-v2/`へstrict Release directoryまたはZIPを置き、operator Player／CONSOLE／RCONから`/lfc place plan <relative-path> <world> <x> <y> <z>`（明示形は`/lfc v2 place plan …`）を開始します。world allow／deny policy外、存在しないworld、command block、非operatorは拒否します。表示されたtokenを`place confirm`へ渡すまでsnapshotもworld mutationも行わず、`place execute`はapply後にsettle／effect-envelope全体のexact verifyとdurable operation commitを完了した時だけ`APPLIED`になります。tokenはTab補完しません。

## AI Providerと画像

キーは `OPENAI_API_KEY`／`ANTHROPIC_API_KEY`など、`config.yml`で指定した環境変数からだけ取得します。model IDはcommandまたは空でないdefault modelとして明示します。画像roleは `MOOD_REFERENCE`、`TOP_DOWN_SKETCH`、`HEIGHT_REFERENCE`、`ZONE_REFERENCE`、`MATERIAL_REFERENCE`、`STRUCTURE_REFERENCE` です。既定の`design`はTerrainIntent v2向けのcapability明示dispatch（OpenAI／Anthropic／manual／soft draft、no-fallback）を使います。詳細は [AI Provider](docs/ai-providers.md) のV2-6-11節とADR 0029、[User Guide](docs/user-guide.md)を参照してください。

## Release、配置、Undoの安全性

Releaseは持ち運び可能な正本です。配置順序は `validate → preview/export → confirm → snapshot → apply → verify` です。同じworldの重複領域、別actor／world／origin／operationのtoken、期限切れ／再利用token、disk不足、改変Releaseをworld変更前に拒否します。Recoveryで判断できない状態は成功にせず `MANUAL_INTERVENTION_REQUIRED` とします。

## ビルド

```bash
./gradlew clean test
./gradlew build
./gradlew shadowJar
```

- Paper JAR: `build/libs/LandformCraft-0.9.0-beta.1.jar`
- CLI JAR: `build/libs/LandformCraft-0.9.0-beta.1-cli.jar`

## ドキュメント

- [Quick Start](docs/quickstart.md)
- [User Guide](docs/user-guide.md)
- [Admin Guide](docs/admin-guide.md)
- [How It Works](docs/how-it-works.md)
- [地形・構造物・画像入力ガイド](docs/terrain-design-guide.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Limitations](docs/limitations.md)
- [Beta Release Checklist](docs/beta-release-checklist.md)
- [Architecture](docs/architecture.md)
- [Artifact Format](docs/artifact-format.md)
- [Security](docs/security.md)
- [Roadmap](docs/roadmap.md)
- [Terrain Generation v2 Design](docs/design-v2/implementation-roadmap.md)
- [TerrainIntent v2](docs/design-v2/terrain-intent-v2.md)
- [Direct Constraint Maps v2](docs/design-v2/image-constraint-maps.md)
- [Scale and Streaming（LARGE実行モデル）](docs/design-v2/scale-and-streaming.md)
- [Model Assignment（Task実行体制）](docs/design-v2/model-assignment.md)
- [2026-07再設計調査](docs/audits/redesign-2026-07-current-state.md)
- [v1→v2 Migration Plan](docs/design-v2/migration-plan.md)
- [Phase 6 Beta Audit](docs/audits/phase-6-beta-audit.md)
- [0.9.0-beta.1 Release Note](docs/releases/0.9.0-beta.1.md)

API key、Authorization header、Cookie、secret fileを入力、chat、manifest、fixture、log、Gitへ保存しないでください。
