# コマンドリファレンス

## Paper

rootは `/landformcraft`、aliasは `/lfc` です。tokenはTab補完しません。token付きclick eventは入力欄へcopyするだけで自動実行しません。
`/lfc help` は「確認」「設計・生成」「配置・復旧」に分けて表示します。CLIの `--help` も「設計・生成」「Release・検証」「管理」に分け、各commandの用途と実行例を表示します。

| Command | Permission | 説明 |
|---|---|---|
| `help` | `landformcraft.help` | help |
| `version` | `landformcraft.version` | plugin／generator／Schema／Minecraft／Paper／WE検出 |
| `doctor` | `landformcraft.doctor` | secret非表示のread-only診断 |
| `request create <id>` | `request.create` | request作成 |
| `request bounds selection <id>` | `request.edit` | WorldEdit選択をboundsへ設定 |
| `request bounds <id> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | `request.edit` | inclusive bounds設定 |
| `request prompt <id>` | `request.edit` | 5分のchat prompt session |
| `request validate/info <id>` | `request.validate` | Schema／record検証、表示 |
| `request list` | `request.list` | 一覧 |
| `design create <request> <openai\|anthropic> <model>` | `design.create` | 非同期Provider設計 |
| `design import/fixture <request> <relative-json>` | `design.import` | imports rootから設計 |
| `design status <job>` | `job.status` | 設計job状態 |
| `design info/verify <design>` | `design.verify` | Design Package検証 |
| `generate <design>` | `generate` | candidate生成job開始 |
| `job status/cancel <job>` | `job.status` / `job.cancel` | 永続job操作 |
| `candidate list <request-id>` | `candidate.read` | request別candidate一覧 |
| `candidate info/preview/validate <id>` | `candidate.read` | preview directory、file、checksum |
| `export plan <candidate>` | `export.plan` | Release生成計画、token発行 |
| `export create <plan> <token>` | `export.execute` | token確認後のRelease job |
| `export status <job>` | `job.status` | export job状態 |
| `export verify/info <request/release>` | `export.verify` | strict verification |
| `export list` | `export.verify` | Release一覧 |
| `selection` | `selection` | WorldEdit選択表示 |
| `apply plan <release> <world> <x> <y> <z>` | `apply.plan` | verify、予約、disk見積、token |
| `apply execute <placement> <token>` | `apply.execute` | snapshot付き配置 |
| `apply status <placement>` | `apply.status` | journal状態 |
| `apply undo <placement>` | `undo.plan` | world drift検査前のUndo計画 |
| `undo execute <placement> <token>` | `undo.execute` | snapshot逆順復元 |
| `apply recover status/diagnose <placement>` | `recovery` | 復旧状態／保守的診断 |
| `apply recover rollback/accept <placement> <token>` | `recovery` | 明示復旧。acceptは全一致時だけ |
| `asset validate/import <schem> <metadata>` | `asset.read` / `asset.manage` | 制限付きasset |
| `asset list/info <id>` | `asset.read` | catalog読取 |
| `asset remove <id>` | `asset.manage` | 使用中は拒否 |
| `cleanup plan/status` | `cleanup` | retention dry-run／状態 |
| `cleanup execute <plan> <token>` | `cleanup` | 再検査して削除、audit |

permissionの完全名はすべて `landformcraft.` prefix付きです。`landformcraft.admin`は全childを持ちます。

## CLI

```text
landformcraft [--data-dir <dir>] [--json] [--quiet] [--verbose] <command>
```

実行名はGradleでは `./gradlew run --args="..."` です。

```text
validate <request.yml> <intent.json>
generate|preview <request.yml> <intent.json> [output] [candidate-index]
export <request.yml> <intent.json> [exports-root] [candidate-index]
verify <release-directory-or-zip>
journal-verify <placement-journal.json>
design <import|fixture> <request.yml> <intent.json> [designs] [jobs]
design <openai|anthropic> <request.yml> <model-id> [designs] [jobs]
design-verify <design-directory>
version
doctor
request <create|bounds|prompt|validate|info|list> ...
job <status|cancel> <job-id>
candidate list <request-id>
candidate <info|preview|validate> <candidate-id>
asset <validate|import|list|info|remove> ...
recovery <list|status|diagnose> [placement-id]
```

`--json` error契約は `errorCode`、`safeMessage`、`correlationId`、`operation`、`resourceId`、`stage`、`suggestedAction`、`exitCode`です。usage errorは2、処理失敗は1、成功は0です。CLIからworld mutationはしません。

`job status`は永続snapshotを読めます。`job cancel`はcancel intentを永続化しますが、別processで実行中のCLI taskへ通知するIPCはこのrelease candidateにありません。foreground commandはCtrl+C shutdown hookで実Futureへcancelを伝播し、Paper jobは同じplugin process内の所有Futureをcancelします。cross-process cancelが必要な運用ではbeta公開しないでください。
