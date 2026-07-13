# コマンド計画

Phase 1のCLI preview生成だけを実装済みです。Paper、schematic export、配置コマンドは未実装です。Paper側の名称は正式名に合わせて `/landformcraft`、短縮aliasを `/lfc` とする案です。

## Paper command案

### Request

```text
/landformcraft request create <request-id>
/landformcraft request bounds selection
/landformcraft request bounds <request-id> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>
/landformcraft request validate <request-id>
```

WorldEdit selectionを使用するcommandはWorldEdit／FAWEがない場合に明確なエラーを返します。

### Design

```text
/landformcraft design create <request-id> openai
/landformcraft design create <request-id> anthropic
/landformcraft design import <request-id> <relative-json-path>
```

pathはrequest root配下の相対pathだけを許可します。APIキーをcommand引数として受け取りません。

### Generate／candidate

```text
/landformcraft generate <request-id>
/landformcraft job status <job-id>
/landformcraft job cancel <job-id>
/landformcraft candidate list <request-id>
/landformcraft candidate info <candidate-id>
/landformcraft candidate preview <candidate-id>
```

長時間処理はjob IDを直ちに返し、chat threadをblockしません。

### Export

```text
/landformcraft export plan <candidate-id>
/landformcraft export create <plan-id> confirm
/landformcraft export verify <release-id>
```

### Apply／undo

```text
/landformcraft apply plan <release-id> <world> <x> <y> <z>
/landformcraft apply execute <placement-plan-id> confirm
/landformcraft apply status <placement-id>
/landformcraft apply undo <placement-id> confirm
```

`plan`は読み取り専用、`execute`は変更操作です。confirm tokenはplanのchecksum、world、origin、期限へ結び付け、別planへ流用できないようにします。

## Permission案

| Permission | 用途 | Default |
|---|---|---|
| `landformcraft.request` | request作成／検証 | op |
| `landformcraft.design` | AI／import設計 | op |
| `landformcraft.generate` | candidate生成 | op |
| `landformcraft.export` | Release作成 | op |
| `landformcraft.apply.plan` | dry-run | op |
| `landformcraft.apply.execute` | world変更 | op |
| `landformcraft.apply.undo` | rollback | op |
| `landformcraft.admin` | 全権限と運用操作 | op |

実装時に`plugin.yml`へ追加し、各subcommandで明示的に検査します。

## 実装済みCLI

```bash
landformcraft generate <request.yml> <terrain-intent.json> [output-directory] [candidate-index]
```

このコマンドはSchema検証、Blueprint compile、地形生成、検証、PNGとJSON summaryの保存を行います。summaryには生成時間、推定retained memory、推定peak working memoryを含みます。既定出力先は`build/landformcraft-preview`です。

Gradleからは次のように実行します。

```bash
./gradlew run --args="generate examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/phase1-preview"
```

## 将来のCLI案

```bash
landformcraft request validate request.yml
landformcraft design import request.yml terrain-intent.json
landformcraft generate request.yml
landformcraft preview <job-id>
landformcraft export <job-id>
landformcraft verify <release-directory-or-zip>
```

Gradle distributionの実行名は初期段階から `landformcraft` に固定しています。将来、machine-readableな `--json` 出力、`--data-dir`、cancel signal、進捗logを追加します。usage errorの非ゼロexit codeは実装済みです。secretを引数へ渡すoptionは作りません。

## エラー契約

command errorは最低限次を含めます。

- 安定したerror code
- request／job／release／placement ID
- 失敗したstage
- ユーザーが安全に取れる次の操作
- 詳細logのcorrelation ID

secret、Authorization header、画像内容、内部stack traceは一般ユーザー向けchatへ表示しません。
