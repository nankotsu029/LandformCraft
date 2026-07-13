# アーキテクチャ

## 1. 目的と品質属性

LandformCraftは、文章と画像から最大約1000×1000のMinecraft地形と少量の人工物を作り、別環境へ安全に持ち運べる汎用システムです。

優先順位は次のとおりです。

1. 本番ワールドを破壊しない
2. 同じ設計・seed・generator versionから再現できる
3. AI Provider、Paper、WorldEditから生成コアを分離する
4. 1000×1000をメモリ上限内で処理する
5. 生成前に人間が検証・比較・承認できる
6. サーバー再起動や外部API失敗から復旧できる

## 2. 責務の分離

```text
入力層              自然言語、画像、bounds、seed
  ↓
AI解釈層            OpenAI / Anthropic / Imported JSON / Fixture
  ↓
設計層              GenerationRequest → TerrainIntent → WorldBlueprint
  ↓
生成・検証層        layout、heightfield、水系、素材、structure、validator
  ↓
出力層              preview、TerrainPlan、tile、manifest、checksum、ZIP
  ↓
Minecraft連携層     Paper、WorldEdit API、FAWE互換、snapshot、apply、undo
```

AIの責務は「何を作るか」を構造化することです。Javaの責務は、値を検証し、seedに基づいてブロックへ決定論的に変換することです。

## 3. packageと依存方向

```text
ai.openai ────────┐
ai.anthropic ─────┴→ ai.spi → model
generator ─────────────────→ model
validation ────────────────→ model
preview ────────→ generator, model
format ────────────────────→ model
worldedit ─────────────────→ format
core ───────────→ model, ai.spi, generator, validation, preview, format
cli ────────────→ core, ai.openai, ai.anthropic, format
paper ──────────→ core, ai.openai, ai.anthropic, format, worldedit
```

すべてルートの`src`内に置きますが、依存は下位の純粋packageへ向けます。`model`にPaper型が入ったり、`generator`がOpenAIを呼んだりする逆流は禁止します。

初期構成をGradle subprojectへ分けていたのは、AI、生成、Paper、WorldEditの依存方向をbuild時に強制するためでした。しかしPhase 0では実装量よりdirectoryとbuild fileの数が多く、変更時の追跡負担が上回ります。そのため現在は単一project＋package分離を採用します。次のいずれかが実際に必要になった時だけ再分割します。

- CLI／Worker／Paperを独立したbinaryとして別々にreleaseする
- AI SDKやWorldEdit依存をcompile classpathから物理的に隔離する
- package規約だけでは循環依存を防げなくなる
- module単位のtest／build cacheが開発時間を明確に改善する

### `model`

Java標準ライブラリだけで表現するrecord、enum、不変条件です。serializationの注釈やMinecraft型も原則として持ち込みません。

### `core`

Application Service、job state machine、artifact repository、checkpoint、非同期処理を統括します。現在はI/O用Virtual Threads、CPU生成用bounded pool、入力からpreviewまでを合成する`GenerationApplicationService`を実装しています。永続job実装とcheckpoint再開は今後追加します。

### AI packages

`ai.spi`が `TerrainDesignProvider` を定義します。Providerは構造化された `TerrainIntent` を返し、block listやコードを返しません。OpenAI／Anthropic実装はPhase 4です。

### 生成・検証・preview

`generator`はMinecraftサーバーなしで動きます。64×64／128×128の論理layoutを補間し、global X/Z noise、zone、局所侵食からheight、水域、material、featureを生成します。tile単独生成は16 block marginを計算して中央だけを返します。`validation`はSchema、height、水深、tile coverage、孤立水域、river pit、performance budgetを検査します。`preview`は地形データから8種類のPNGを1枚ずつ生成・解放します。

### Format／WorldEdit

`format`はportable artifactの論理形式を担当します。`worldedit`はWorldEdit公開APIによるBlockState変換、Clipboard、schematic I/Oだけを担当します。

### Paper／CLI

どちらも薄いadapterです。Paperはcommand、permission、Scheduler、selection、apply、undoを担当します。CLIは同じcoreをサーバーなしで呼びます。

## 4. 生成パイプライン

```text
GenerationRequest
  → request schema / semantic validation
  → TerrainDesignProvider または JSON import
  → TerrainIntent schema / semantic validation / normalization
  → BlueprintCompiler
  → WorldBlueprint
  → low-resolution layout
  → heightfield
  → water systems
  → terrain features
  → material layers
  → limited structures
  → tiles
  → result validation
  → previews
  → schematic export
  → release packaging
```

各段階は入力checksum、出力checksum、version、job stageをcheckpointへ保存します。途中状態を黙って再利用せず、互換性とchecksumが一致した安全なcheckpointだけを再開します。

## 5. 低解像度から高解像度へ

大域的な海・陸・山・川・zone配置は64×64または128×128の論理mapで決めます。その後、補間、global noise、侵食などで要求解像度へ拡大します。

AIに1000×1000を直接描かせないことで、応答量、曖昧さ、局所ノイズを抑えます。AI出力を変えてもgeneratorの品質改善を独立して行えます。

## 6. 1000×1000とtile

基本の内部表現は列ごとの2次元データです。

```text
heightMap[x,z]
waterLevelMap[x,z]
surfaceMaterialMap[x,z]
biomeMap[x,z]
featureMask[x,z]
caveDescriptor
```

地下は全block listでなく、表面、表土厚、岩盤、水、空気、cave maskの列規則として表現します。

既定tileは128×128です。1000×1000は8×8の64 tileになります。各tileを生成、検証、出力したら大きな一時bufferを解放します。

連続性の規則:

- noiseは `sample(globalX, globalZ)` で評価する
- River、erosion、slopeなどはmarginを含む領域で計算する
- marginを除く中心tileだけを成果物へ採用する
- tile IDとoriginはmanifestで一意に定義する

## 7. 非同期とスレッド境界

| 処理 | 実行先 |
|---|---|
| HTTP、画像読込、ファイルI/O | admission上限付きVirtual Thread per task executor |
| heightmap、erosion、PNG、検証 | 上限付きqueue／platform thread pool |
| pipeline合成、timeout、失敗伝播 | `CompletableFuture` |
| Bukkit/Paper world read/write | Paper Schedulerのメインスレッド |
| WorldEdit/FAWE edit | APIの非同期契約に従い、完了・closeを追跡 |

Paperメインスレッド上でfutureを `join()`／`get()` しません。非同期結果をworldへ反映するときだけ `PaperMainThreadDispatcher` へ渡し、その前にvalidationとconfirmを完了します。world操作の実行開始をcommit pointとし、それ以前のplugin停止はscheduler taskとstageを一緒にcancelします。返却stageは観測専用で、外部cancelがworld操作と食い違わないようにします。

CPU生成taskは `CancellationToken` を受け取り、tile行や反復回数など有界な間隔で確認します。Future cancelはdelegateをinterruptしtokenもcancel状態になります。停止時は全poolを先にcancelし、poolごとでなく全体共有の5秒deadlineでterminationを待ちます。

## 8. 実行モード

### Mode A: 手動Intent import

Web版ChatGPT／Claudeなどで人間がIntentを作り、JSONを検証してimportします。APIキー不要で、最初の実用モードです。

### Mode B: サーバー直接API

Paperから非同期にProviderを呼びます。便利ですが、秘密管理、quota、timeout、plugin停止時cancelが必要です。

### Mode C: 外部Worker

Paperはjobを登録し、外部processがAI、画像、生成、exportを行います。最終推奨モードです。Paper再起動への強さ、秘密分離、CLI／Web共用、将来のGPU処理を得られます。

## 9. Job stateと復旧

標準状態は `GenerationStage` enumを正本にします。

```text
QUEUED → VALIDATING_REQUEST → CALLING_AI → VALIDATING_INTENT
       → COMPILING_BLUEPRINT → GENERATING_LAYOUT → GENERATING_TERRAIN
       → GENERATING_FEATURES → GENERATING_STRUCTURES
       → VALIDATING_RESULT → RENDERING_PREVIEWS
       → EXPORTING_TILES → PACKAGING → READY
```

任意の段階から `FAILED`／`CANCELLED` へ遷移できます。再起動後に自動継続できない状態は `RECOVERY_REQUIRED` とし、人間またはrecovery policyが最後の安全なcheckpointから再開します。

## 10. 安全な配置

```text
Release checksum検証
  → target world / bounds / height / permission検証
  → dry-run placement plan
  → 明示confirm
  → tile snapshot
  → tile apply
  → tile verify
  → 次tile
  → 全成功でAPPLIED
```

失敗時は配置済みtileだけを逆順にsnapshotから復元します。snapshotが作れない場合はapplyしません。1000×1000全体を1回の巨大transactionとして扱いません。

## 11. WorldEditとFAWE

コードはWorldEdit 7.3.19の公開APIへcompileOnlyで依存します。FAWEが導入されている環境ではWorldEdit互換APIとして利用します。WorldEditとFAWEを同時に導入しません。

FAWE固有機能が必要になっても、generatorやcoreへFAWE型を漏らさず専用adapterへ閉じ込めます。

## 12. 設計判断

採用理由と影響は [adr/README.md](adr/README.md) から追跡します。外部仕様の正本はこの文書、data model、artifact format、schemaへ反映し、ADRだけに仕様を閉じ込めません。
