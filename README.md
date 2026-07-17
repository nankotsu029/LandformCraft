# LandformCraft

LandformCraft は、自然言語・役割付き参考画像・手動JSONから設計意図を作り、決定論的なJavaエンジンでMinecraft 1.21.11向け地形と小規模構造物を生成するPaperプラグイン／CLIです。

## Beta status

現在のrelease candidate versionは `0.9.0-beta.1`、公開CLI／Paperが使用するgeneratorは `3.0.0-phase6`、公開Request／TerrainIntent／Release契約はv1です。V2-0／V2-1に加え、V2-2では4 coastal feature、independent validator／diagnostic preview、offline tile schematic、offline専用の`surface-2_5d` Release format 2 capabilityまでPhase gateを完了し、offline経路を`SUPPORTED`としました。V2-3もPhase gateを完了し、Hydrology IR／fixed prior、全域basin／D8 routing、固定3 pass reconciliation、独立validator／12-layer preview、strict `hydrology-plan` Release 2 capabilityと、offlineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDを`SUPPORTED`としました。V2-4は進行中で、V2-4-01〜V2-4-04のtyped geology field、semantic lithology、strata／derived scalar、coarse climate prior／final temperature・moisture contractを`EXPERIMENTAL`で実装済みです。WATERFALL、ALPINE／GLACIAL mountain、VOLCANIC_ARCHIPELAGOはvolume／environment／material完成前のため`EXPERIMENTAL`です。V2は公開CLI／Paper、Release 1、Paper配置へ接続していません。Beta hardeningの実機未完了項目が残るため、まだ公開可能なbetaとは判定していません。評価時もデータのbackup、Release検証、test worldでの事前配置を必須運用とします。独自Web UIは提供せず、CLIとPaperコマンドを正規UIとします。ブラウザ版ChatGPT／ClaudeでTerrainIntent JSONを作りCLIへimportする運用は利用できます。

## できること

- 手動JSON、OpenAI Responses API、Anthropic Messages APIからTerrainIntentを作成
- 6 roleのPNG／JPEGを安全に前処理してDesign Packageへ記録
- seed再現可能な地形、8種類のbuilt-in小規模構造物、8種類のPNG previewを生成
- tiled Sponge Schematic v3、checksum、directory／ZIPを含むRelease Packageを生成・検証
- WorldEditまたはFAWEの公開APIでsnapshot付き配置、verify、rollback、Undo、明示Recovery
- world UUID＋inclusive boundsの予約、actor-bound一回用token、disk容量予約、snapshot cleanup
- 制限付きcustom asset catalog（Sponge v3、vanilla allowlist、entity／biome／block entityなし。TerrainIntent v1からの選択は未対応）

## できないこと

独自Web UI、巨大都市の全自動生成、AIによる全block列挙、AI生成コードの実行、entity／高度なblock entity、biome書換えは対象外です。洞窟・高度な植生・fjord・delta等も現行v1では未対応です。V2-0〜V2-3では分離したcontract、constraint／coastal／Hydrologyのoffline経路を完成し、V2-4-01〜V2-4-04ではtyped geology／lithology／strataと、V2-3 fixed runoff priorから明示version transitionするcoarse climate prior／final temperature・moisture contractを追加しました。regional wetness／salinity／hydroperiod、snow、ecology、semantic materialは未実装で、environment moduleも`EXPERIMENTAL`です。waterfall、mountain、volcanicは後続のvolume／environment／material依存が残るため`EXPERIMENTAL`です。V2はCLI／Paperと配置へ未接続です。詳しくは [制限事項](docs/limitations.md) と [Roadmap](docs/roadmap.md) を参照してください。

## 必要環境

- Java 21
- Paper 1.21.11
- 配置する場合はWorldEdit 7.3.19、またはPaper 1.21.11対応FAWEをどちらか一方
- buildには同梱Gradle Wrapper

Paper、Bukkit、WorldEdit、FAWE本体は配布JARへshadeしません。WorldEditとFAWEを同時に導入しないでください。

## 5分Quick Start

```bash
./gradlew clean build
./gradlew run --args="validate examples/sandy-coast/request.yml examples/sandy-coast/terrain-intent.json"
./gradlew run --args="generate examples/sandy-coast/request.yml examples/sandy-coast/terrain-intent.json build/preview"
./gradlew run --args="export examples/sandy-coast/request.yml examples/sandy-coast/terrain-intent.json build/exports"
./gradlew run --args="verify build/exports/sandy-coast-001/<release-id>"
```

Paperへ `build/libs/LandformCraft-0.9.0-beta.1.jar` とWorldEditまたはFAWEを配置し、test worldで次を実行します。

```text
/lfc version
/lfc doctor
/lfc apply plan rocky-coast-001/<release-id> world 0 64 0
/lfc apply execute <placement-id> <one-time-token>
/lfc apply status <placement-id>
/lfc apply undo <placement-id>
/lfc undo execute <placement-id> <one-time-token>
```

完全な手順は [Quick Start](docs/quickstart.md) を参照してください。

## CLI

主要コマンドは `validate`、`design`、`design-verify`、`generate`、`preview`、`export`、`verify`、`journal-verify`、`doctor`、`request`、`job`、`candidate`、`asset`、`recovery` です。共通optionは `--data-dir`、`--json`、`--quiet`、`--verbose` です。

```bash
./gradlew run --args="--help"
./gradlew run --args="doctor --data-dir build/beta-data --json"
```

CLIからMinecraft worldは変更しません。

## Paper

Paperではrequest→design/import→job→generate→candidate→export→apply→Undo／Recoveryを実行できます。長文promptは5分期限の一回用chat sessionで受け取り、`cancel`で中断できます。API keyをchatやコマンドへ入力してはいけません。コマンドとpermission一覧は [コマンドリファレンス](docs/commands.md) を参照してください。

## AI Providerと画像

キーは `OPENAI_API_KEY`／`ANTHROPIC_API_KEY`など、`config.yml`で指定した環境変数からだけ取得します。model IDはcommandまたは空でないdefault modelとして明示します。画像roleは `MOOD_REFERENCE`、`TOP_DOWN_SKETCH`、`HEIGHT_REFERENCE`、`ZONE_REFERENCE`、`MATERIAL_REFERENCE`、`STRUCTURE_REFERENCE` です。詳しくは [User Guide](docs/user-guide.md) と [AI Provider](docs/ai-providers.md) を参照してください。

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
- [v1→v2 Migration Plan](docs/design-v2/migration-plan.md)
- [Phase 6 Beta Audit](docs/audits/phase-6-beta-audit.md)
- [0.9.0-beta.1 Release Note](docs/releases/0.9.0-beta.1.md)

API key、Authorization header、Cookie、secret fileを入力、chat、manifest、fixture、log、Gitへ保存しないでください。
