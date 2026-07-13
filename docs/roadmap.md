# 開発ロードマップ

このファイルを進捗の正本とします。READMEやIssueと状態が異なる場合は、実装とテストを確認してここを更新します。

## 現在地

**現在: Phase 2（SchematicとRelease Package）の開始時点**

完了済み:

- [x] 独立した `LandformCraft` プロジェクト名とpackageを決定
- [x] Java 21／Gradle Kotlin DSL／Paper 1.21.11の単一`src`＋package分離構成
- [x] 主要なJava recordとAI Provider SPIの初期版
- [x] admission／queue上限とcancel連動を持つExecutor／CompletableFuture基盤
- [x] dispatch受理をcommit境界とするPaper Scheduler dispatcher初版
- [x] request／TerrainIntentの入力例とJSON Schema初版
- [x] architecture、security、operations、artifact formatを文書化
- [x] YAML／JSON codecとDraft 2020-12 Schema Validator
- [x] `TerrainPlan`、`TilePlan`、`StructurePlan`、`ValidationResult` record
- [x] `ExportManifest` recordとWorldBlueprint／Manifest JSON Schema
- [x] unknown field、duplicate key、future schema versionの拒否テスト
- [x] 永続ジョブとartifact repositoryの非同期interface

Phase 1完了:

- [x] seed固定のheightmap、海、海岸、島、川、湖
- [x] surface materialとvegetation／cliff／coast feature mask初版
- [x] global座標計算と128×128 tile plan、端tile切り詰め、SHA-256
- [x] 8種類のPNG previewと非同期Application Service／CLI generate
- [x] seed再現性、tile coverage、bounds、水深整合性のテスト
- [x] 64×64／128×128の低解像度layoutから高解像度への補間
- [x] zoneを反映した山、谷、砂浜、岩場の高さ／素材制御
- [x] deterministicな局所侵食
- [x] 16 block margin付きtile単独計算と全セルseam比較テスト
- [x] river flow reversal／water connectivity／孤立水域validator
- [x] 500×500と1000×1000の性能・memory budget計測

Phase 2で次に実装するもの:

- [ ] surface／subsoil／stone／water／airをblock columnへmaterialize
- [ ] WorldEdit BlockState変換とtile別`.schem`
- [ ] `manifest.json`、全artifact checksum、README
- [ ] temporary directoryからのatomic publishとZIP
- [ ] CLI `export`／`verify`

## マイルストーン

| マイルストーン | 範囲 | 完了条件 |
|---|---|---|
| M0 Contract | Phase 0 | **完了**: exampleをschemaで検証し、recordへround-tripできる |
| M1 Terrain Preview | Phase 1 | **完了**: AIなしで同一seedのPNGを再現できる |
| M2 Portable Release | Phase 2 | tiled `.schem`、manifest、checksum、ZIPをCLIで生成・検証できる |
| M3 Safe Placement | Phase 3 | Paper上でsnapshot付き配置とundoを実証できる |
| M4 Text to Intent | Phase 4 | OpenAI／Anthropic／manual importが同じSPI契約を通る |
| M5 Image Intent | Phase 5 | 役割付き画像から検証可能なIntentを作れる |
| M6 Structures | Phase 6 | 少量のasset配置を衝突検査込みで行える |
| M7 Interactive UI | Phase 7 | 候補比較・部分修正・download／Paper連携ができる |

## Phase 0: 仕様とスキーマ

実装対象:

- `GenerationRequest`
- `ReferenceImage`
- `TerrainIntent`
- `WorldBlueprint`
- `TerrainPlan`／`TilePlan`
- `ValidationResult`
- `ExportManifest`
- JSON Schemaとversioning
- 座標系、単位、null、enum拡張の規則

Exit criteria:

- 正常・異常fixtureが自動検証される
- Bukkit／WorldEditなしでmodelテストが動く
- schema versionとgenerator versionが別管理される
- 不明field、範囲外値、危険なpathを拒否できる

## Phase 1: 純粋Java地形エンジン

実装対象:

- 64×64または128×128の低解像度レイアウト
- heightmap、海、島、湖、川、山、谷、海岸
- surface material、vegetationの最小構成
- global座標による128×128 tile生成とmargin
- overview／height／water／slope／materials／features／structures／validation PNG
- seed再現性と性能budget

最初は100〜500角で検証し、メモリと時間の計測後に1000角を解放します。

Exit criteria:

- 同じ入力・seed・versionでchecksumが一致する
- 隣接tile境界に継ぎ目がない
- 500×500を所定のmemory budget内で生成できる
- 川の逆流、孤立水域、範囲外heightをvalidatorが検出する

## Phase 2: SchematicとRelease Package

実装対象:

- WorldEdit BlockState変換
- tile別 `.schem`
- `manifest.json`、checksum、README、ZIP
- 一時directoryからのatomic publish
- CLIのvalidate／generate／preview／export／verify

Exit criteria:

- Releaseを別directoryで検証・展開できる
- 欠損、改変、重複tileを拒否できる
- WorldEditで各tileを正しい相対位置へ貼り付けられる

## Phase 3: Paper／WorldEdit／FAWE連携

実装対象:

- Paper commandとpermission
- WorldEdit選択範囲の取得
- 永続job、進捗通知、再起動時のrecovery
- 配置前bounds検査、tile snapshot、段階配置、verify、undo
- WorldEdit環境とFAWE環境を分けたsmoke test

Exit criteria:

- API／生成処理がPaperメインスレッドをblockしない
- world操作がPaper Scheduler経由に限定される
- 中途失敗時に適用済みtileを逆順で復元できる
- confirmなしで本番worldを変更しない

## Phase 4: 自然言語AI

実装対象:

- OpenAI Provider
- Anthropic Provider
- Imported JSON／fixture Provider
- Structured Intent、schema検証、retry、timeout、rate／cost limit
- provider、model、prompt version、usageの秘密を除いた監査記録

Exit criteria:

- provider変更がgeneratorへ影響しない
- 不正JSONや範囲外Intentを生成前に拒否できる
- timeout、429、5xx、cancelが安全にjob状態へ反映される

## Phase 5: 画像入力

実装対象:

- 形式、size、pixel数、metadata検査
- `MOOD_REFERENCE` 等のrole
- 上面図の座標正規化
- 文章と画像の矛盾検出
- 根拠と変換履歴の保存

Exit criteria:

- 役割なし画像を曖昧に解釈しない
- 過大画像、壊れた画像、危険なpathを拒否する
- 画像なしrequestと同じpipelineへ合流する

## Phase 6: 少量の建築物

実装対象:

- schematic asset catalog
- anchor、rotation、地形追従、衝突検査
- 小さな桟橋、橋、小屋、遺跡
- 限定的な手続き型の道、石積み、階段、柵

Exit criteria:

- asset checksumと対応Minecraft versionを検証する
- 水系、崖、他structureとの衝突を検出する
- 建築物の失敗で地形全体を破壊しない

## Phase 7: Web UIと修正指示

実装対象:

- 画像upload、prompt、bounds、進捗
- 複数候補のpreview比較
- 自然言語修正と部分再生成
- Release downloadとPaperへの受け渡し
- 外部Workerの標準実行モード

Exit criteria:

- Web、CLI、Paperが同じApplication Service／artifactを使用する
- 認証、CSRF、upload制限、job ownershipが実装される
- worker停止・再起動から安全に復旧する

## 当面の非目標

- AIによる全ブロック座標の列挙
- AI生成Javaコードのサーバー実行
- 1000×1000全域の全blockを一括メモリ展開
- prompt送信直後の無確認な本番配置
- 高度な街や巨大建築の自動生成
