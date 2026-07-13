# LandformCraft

LandformCraft は、自然言語や参考画像を構造化された地形設計へ変換し、決定論的な Java エンジンで Minecraft 地形を生成する Paper プラグイン／オフラインツールです。

正式な製品名とプラグイン名は `LandformCraft`、Paper のメインクラスは `com.github.nankotsu029.landformcraft.Landformcraft` です。旧案の `AIWorldComposer` や GyoSai には依存しません。

## 基本方針

```text
自然言語・画像
    ↓
AIまたは手動JSONが TerrainIntent を作る
    ↓
Javaの決定論的ジェネレーターが WorldBlueprint と地形を作る
    ↓
検証・PNGプレビュー
    ↓
128×128タイルの .schem と Release Package を出力
    ↓
WorldEditまたはFAWEで安全に配置・Undo
```

AIは「何を作るか」だけを設計します。100万列分のブロックを列挙させたり、AIが生成した Java コードをサーバーで実行したりしません。

## 現在の実装状況

現在は **Phase 2（SchematicとRelease Package）開始時点** です。Phase 0の入力契約とPhase 1の純粋Java地形エンジンは完了しています。

- Java 21、Gradle Kotlin DSL、Paper 1.21.11 の単一プロジェクト構成
- コードはルートの `src/main`、テストは `src/test` に集約
- `GenerationRequest`、`TerrainIntent`、`WorldBlueprint` などの Java record
- `CompletableFuture` と明示所有する `ExecutorService` の実行基盤
- admission制限付きVirtual Threadsと、上限付きqueueを持つCPU生成pool
- Futureの取消を実タスクのinterruptへ伝播し、全pool共通期限で停止するlifecycle
- Paper Scheduler 経由でメインスレッドへ戻す `PaperMainThreadDispatcher`
- Paper プラグインと CLI の起動スキャフォールド
- 入力例、JSON Schema、設計・安全性・ロードマップ文書
- Draft 2020-12 Schema検証と、重複key／未知fieldを拒否するJSON・YAML codec
- seedから再現可能なheightmap、海岸、島、川、湖、surface material、vegetation
- global座標に基づく128×128 tile planとSHA-256
- overview／height／water／slope／materials／features／structures／validation PNG
- CLIからの非同期preview生成
- 64×64／128×128論理layoutからの補間、zone反映、局所侵食
- 16 block margin付きtile単独生成と全セルseam一致テスト
- 孤立水域、river flow reversal、height／water整合validator
- 500×500／1000×1000 performance budget検証

まだ`.schem`書き出し、Release Package、AI API、Paperコマンド、ワールド配置は実装されていません。進捗の正本は [docs/roadmap.md](docs/roadmap.md) です。

## 要件

- JDK 21
- Gradle Wrapper（同梱）
- Paper 1.21.11（Paper プラグインを実行する場合）
- WorldEdit 7.3.19 または対応する FAWE（schematic／配置機能の実装後）

WorldEdit と FAWE は同時にサーバーへ導入しないでください。

## ビルドと確認

```bash
./gradlew clean build
./gradlew test
./gradlew run --args="--help"
./gradlew run --args="generate examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/phase1-preview"
./gradlew runServer
```

Paper 用の配布JARは `build/libs/LandformCraft-<version>.jar` です。CLI distribution用のJARは `-cli` classifierで区別します。`runServer` は開発用Paperサーバーを `run/` に作成します。

## ソース構成

現段階ではコード量が少ないため、Gradle subprojectへ分割せず、1つの`src`内をJava packageで分離します。独立配布や依存隔離が実際に必要になるまでは、この形を維持します。

| package | 責務 | 現在 |
|---|---|---|
| `landformcraft.model` | 外部ライブラリ非依存のrecord／enum | Phase 0契約あり |
| `landformcraft.core` | Application Service、job、Executor所有 | Executor基盤あり |
| `landformcraft.ai.spi` | AI Provider共通契約 | 契約あり |
| `landformcraft.ai.openai` | OpenAI API adapter | Phase 4予定 |
| `landformcraft.ai.anthropic` | Anthropic API adapter | Phase 4予定 |
| `landformcraft.generator` | 決定論的な地形生成 | Phase 1初版あり |
| `landformcraft.validation` | 入力・Intent・生成結果検証 | Schema／地形validator初版あり |
| `landformcraft.preview` | PNG preview | 8レイヤー実装済み |
| `landformcraft.format` | Plan、Manifest、checksum、ZIP | Phase 2予定 |
| `landformcraft.worldedit` | WorldEdit公開APIと`.schem` | Phase 2〜3予定 |
| `landformcraft.paper` | Scheduler、配置、Undo | 起動基盤のみ |
| `landformcraft.cli` | サーバー不要の生成CLI | Phase 1 generate実装済み |

## ドキュメント

- [アーキテクチャ](docs/architecture.md)
- [データモデルとスキーマ](docs/data-model.md)
- [成果物形式](docs/artifact-format.md)
- [開発手順](docs/development.md)
- [セキュリティ](docs/security.md)
- [運用と復旧](docs/operations.md)
- [コマンド計画](docs/commands.md)
- [進行フェーズと完了条件](docs/roadmap.md)
- [性能budgetと計測結果](docs/performance.md)
- [設計判断記録](docs/adr/README.md)
- [Codex／開発エージェント向け規約](AGENTS.md)

## 入力例

- [examples/rocky-coast/request.yml](examples/rocky-coast/request.yml)
- [examples/rocky-coast/terrain-intent.json](examples/rocky-coast/terrain-intent.json)

API キーは入力、チャット、manifest、Git に書かず、`OPENAI_API_KEY` や `ANTHROPIC_API_KEY` などの環境変数から読み取ります。
