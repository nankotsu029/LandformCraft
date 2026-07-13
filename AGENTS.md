# AGENTS.md

このファイルは LandformCraft を変更する Codex／開発エージェント向けの規範です。仕様の詳細は `docs/`、進捗の正本は `docs/roadmap.md` を参照してください。

## 最初に確認するもの

1. `README.md`
2. `docs/architecture.md`
3. `docs/roadmap.md`
4. 変更対象に関係する `docs/` と `schemas/`
5. 作業前の `git status --short`（Git 初期化前なら、その事実を報告する）

ユーザーの既存変更は保持し、無関係なファイルを整理・削除・reformat しないこと。

## 固定された識別子

- 製品／Paper Plugin 名: `LandformCraft`
- group: `com.github.nankotsu029`
- ベース package: `com.github.nankotsu029.landformcraft`
- Paper main: `com.github.nankotsu029.landformcraft.Landformcraft`
- Java: 21
- Minecraft／Paper: 1.21.11
- ビルド: Gradle Kotlin DSL、Gradle Wrapperを使用

識別子を変更すると既存設定や外部連携を壊すため、明示的な依頼なしに変更しないこと。

## 守るべき設計境界

- コードは基本的にルートの `src/main/java`、resourceは `src/main/resources`、testは `src/test/java` に置く。
- Gradle subprojectは、独立配布・別runtime・依存隔離が必要になるまで追加しない。
- `landformcraft.model` packageは外部ライブラリ、Bukkit、Paper、WorldEditの型に依存させない。
- データ契約は不変な Java `record` と enum を基本とする。
- `landformcraft.generator` packageはAI ProviderやMinecraftワールドへ依存させない。
- AIは `TerrainIntent` を生成する。全ブロック一覧や実行可能コードを生成・実行させない。
- 同じ正規化済みBlueprint、seed、generator versionから同じ結果を再生成できるようにする。
- UI（CLI、Paper、将来のWeb）は `landformcraft.core` のApplication Serviceを呼び、生成処理を直接持たない。
- WorldEditは公開APIだけを `landformcraft.worldedit` packageへ隔離する。FAWE固有APIが必要なら専用packageへ隔離する。
- Release Packageを持ち運び可能な正本とし、`.schem` はタイル成果物として扱う。

## スレッド規則

- OpenAI／Anthropic API、画像解析、artifactのファイルI/OをPaperメインスレッドで行わない。Paper lifecycle中の小さな既定configの初回copyだけは例外とする。
- CPU負荷の高いheightmap、侵食、検証、PNG生成には上限付き `ExecutorService` を使う。
- Virtual Threadsは主にブロッキングI/Oへ使い、CPU並列数の制御には使わない。
- 非同期処理の合成と失敗伝播には `CompletableFuture` を使う。`join()`／`get()`でPaperメインスレッドを待たせない。
- Executorは所有者を明確にし、プラグイン停止・CLI終了時に必ずshutdownする。
- Executorへの投入数とqueueは必ず上限を持たせ、飽和はfailed futureとしてjob層へ返す。
- Futureのcancel、実タスクのinterrupt、job stateを連動させる。CPU loopは定期的にinterrupt／cancel tokenを確認する。
- Bukkit／Paperワールドの読み書きは、必ずPaper Scheduler経由でメインスレッドへディスパッチする。
- Schedulerへ受理されたworld操作を観測側Futureのtimeoutだけで「取消済み」にしない。validation／confirmはdispatch前に完了する。
- WorldEdit／FAWE自身の非同期契約とclose完了を確認し、Bukkit APIの非同期呼び出しと混同しない。

## 地形と安全性の不変条件

- 最大水平範囲は1000×1000。全ブロックを1個の巨大Listへ展開しない。
- 既定タイルは128×128。ノイズはタイル内座標でなくglobal X/Zでsampleする。
- 河川・侵食など近傍が必要な処理はmargin付きで計算し、中央部分だけ採用する。
- 本番配置の順序は `validate → preview/export → confirm → snapshot → apply → verify`。
- タイルごとに進捗とsnapshotを保存し、失敗時は適用済みタイルを逆順で復元する。
- パス、ZIP、画像、JSON、schematicは信頼しない入力として検査する。

## 秘密情報

- APIキー、Authorizationヘッダー、Cookie、秘密ファイルをGit、fixture、ログ、manifestへ保存しない。
- キーは環境変数または外部Secretから取得する。Minecraftチャットやコマンド引数へ入力させない。
- AIへ送る画像・文章・メタデータを最小化し、サーバーファイル全体を送らない。
- エラーログではキーと機微データをredactする。

## 実装と検証

通常の検証コマンド:

```bash
./gradlew test
./gradlew build
./gradlew run --args="--help"
```

- 純粋Javaロジックは単体テストを追加する。
- seed再現性、タイル境界、エラー／キャンセル／再開経路をテストする。
- `JavaPlugin` を直接 `new` して単体テストしない。Paper adapterはrun-paperによるsmoke testを使う。
- 変更後は最低限、対象テストと `./gradlew build` を実行する。
- 外部仕様、状態、コマンド、設定、スキーマを変更したら対応するdocs／schema／exampleも同時に更新する。
- 未実装機能をREADMEやdocsで実装済みと表現しない。

## 完了の定義

- コンパイルとテストが成功する。
- 境界、スレッド、安全性の規則を破っていない。
- ユーザー向け挙動とエラーが文書化されている。
- `docs/roadmap.md` の進捗と完了条件が実態に一致する。
- 秘密情報や生成物が差分へ混入していない。
