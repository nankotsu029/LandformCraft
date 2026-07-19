# コマンドリファレンス

## Release 2明示配置（V2-6-21）

Release 2は既存Release 1の`apply`／`undo`／`apply recover`と混同しないよう、必ず`r2` rootを使います。inputは`plugins/LandformCraft/data/releases-v2/`からの安全な相対directory／ZIP pathです。

```text
/lfc r2 plan <release-path> <world> <x> <y> <z>
/lfc r2 confirm <placement-id> <token>
/lfc r2 execute <placement-id>
/lfc r2 status <placement-id>

/lfc r2 undo-plan <placement-id>
/lfc r2 undo-execute <placement-id> <token>

/lfc r2 recover-diagnose <placement-id>
/lfc r2 recover-plan <rollback|accept> <placement-id>
/lfc r2 recover-execute <rollback|accept> <placement-id> <token>
```

`plan`はstrict Release verify、tile/physics分類、effect envelope、region/disk reservation、actor-bound token発行までで、worldを変更しません。`confirm`がtokenを消費してsnapshot-allとcontainmentを完了します。`execute`はcanonical apply→bounded settle→full exact verifyを実行し、失敗時は逆順rollbackを試みます。曖昧な状態は`RECOVERY_REQUIRED`のままで、`recover-diagnose`が許可しないactionを実行できません。prepared／confirmed／appliedのoperation contextはstage commit markerを最後にfsync＋atomic publishし、main journalより先にdurable化します。

permissionは`landformcraft.r2.plan`、`.confirm`、`.execute`、`.status`、`.undo`、`.recovery`です。WorldEdit 7.3.19／FAWE 2.15.2実機smoke（64×64）とV2-6 Phase gate（`V2-6-19`）まで検証済みで、`V2-11-01`によりcatalog上の`paper_apply`（および`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`）は`surface-2_5d` capabilityのSANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPEだけ、64×64以内で`SUPPORTED`です。他featureのPaper適用は`EXPERIMENTAL`または`UNSUPPORTED`であり、64×64を超える寸法は未測定のため拒否されます。

R2 commandはpermissionに加えてserver operatorであるPlayer、CONSOLE、RCONだけを受理します。command block／proxied・unknown sender／非operatorは拒否し、plan対象worldは`world-policy.allowed`／`denied`を実行時に再検査します。deny worldと存在しないworldはworld mutation前にstable errorで失敗します。Tab補完はpermissionとworld policyを反映し、confirmation tokenを一切候補へ出しません。

## Paper

rootは `/landformcraft`、aliasは `/lfc` です。tokenはTab補完しません。token付きclick eventは入力欄へcopyするだけで自動実行しません。
`/lfc help` は「確認」「設計・生成」「配置・復旧」に分けて表示します。CLIの `--help` も「設計・生成」「Release・検証」「管理」に分け、各commandの用途と実行例を表示します。

| Command | Permission | 説明 |
|---|---|---|
| `help` | `landformcraft.help` | help |
| `version` | `landformcraft.version` | plugin／generator／Schema／Minecraft／Paper／WE検出 |
| `doctor` | `landformcraft.doctor` | secret非表示のread-only診断 |
| `ops metrics` | `landformcraft.doctor` | Release 2 closed-label運用metrics（V2-6-13） |
| `ops diagnose <correlation-id>` | `landformcraft.doctor` | audit correlationからredacted診断 |
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
| `apply undo <placement>` | `undo.plan` | world drift検査前のUndo計画（v1、または注入済みRelease 2 path） |
| `undo execute <placement> <token>` | `undo.execute` | snapshot逆順復元（v1、または注入済みRelease 2 path） |

Release 2 Undo（V2-6-09）は`PaperPlacementUndoServiceV2`／`PlacementUndoApplicationServiceV2.isRelease2Path()`でv1と分離します。world drift時はmutationせず拒否し、force overwriteはありません。
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
