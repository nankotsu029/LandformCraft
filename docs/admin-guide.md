# Admin Guide

## 導入とdirectory

Paper 1.21.11／Java 21へLandformCraftとWorldEdit 7.3.19または対応FAWEの一方を導入します。起動後のportable data rootは既定で `plugins/LandformCraft/data/` です。

```text
data/
├── requests/ designs/ jobs/ candidates/ exports/
├── imports/ assets/
├── placements/ snapshots/ cleanup-plans/
├── placement-safety-state.json
└── recovery-audit.jsonl
```

`exports`はRelease正本、`placements`と`snapshots`は復旧に必要です。server停止中にまとめてbackupしてください。active placement中に個別fileだけをcopyしないでください。

## Permission

`landformcraft.admin`は全childを持ちます。閲覧系は `help`、`version`、`doctor`、`request.list/validate`、`job.status`、`candidate.read`、`export.verify`、`apply.status`、`asset.read`。作成系は `request.create/edit`、`design.create/import/verify`、`generate`、`export.plan/execute`。world変更は `apply.plan/execute`、`undo.plan/execute`。特権操作は `recovery`、`asset.manage`、`cleanup` です。planとexecuteを別roleへ分けられます。

CONSOLEはidentity `CONSOLE`、playerはUUIDです。command blockなど曖昧なsenderはconfirmation操作に使えません。

注意: 標準Paper consoleへ表示されたconfirmation commandは`latest.log`にも記録されます。LandformCraftのjournalはhashしか保存しませんが、console tokenの平文非永続化は現release candidateのblockerです。log directoryを秘密として保護し、tokenは一回使用後または10分で失効させ、公開betaとして運用しないでください。

## Configと環境変数

`config.yml`で実際に使うkeyはstorage、executor上限、Provider有効化／環境変数名／default model、Provider policy、disk budget、retention日数、world allow／deny、WorldEdit有効化です。不正値は起動時に拒否します。

```yaml
providers:
  openai:
    enabled: false
    api-key-env: OPENAI_API_KEY
    default-model: ""
  anthropic:
    enabled: false
    api-key-env: ANTHROPIC_API_KEY
    default-model: ""
```

API key値をYAMLへ書かず、Paper processのenvironmentへ設定します。default modelが空ならcommandでmodel IDが必須です。稼働中placementの予約・disk条件をreloadで変更せず、backup後にserver restartしてください。

画像・生成上限はrequest／image validationのSchema契約、confirmationとreservationの期限は安全契約、snapshot／cleanup／Recoveryの配置先はdata layout契約として固定しています。通知backendはなく、監査logはPaper logとdata root内JSONLへ出します。loaderが読まない見せかけのconfig keyは用意しません。これらを可変化する場合はactive placementへ影響しないstartup-only settingとしてversioned migrationとtestを追加します。

## Disk監視とcleanup

planはRelease読込、全tile snapshot worst case、journal、temporary、rollback overhead、safety marginを見積もり、`FileStore.getUsableSpace()`と他placementの予約を差し引きます。見積不能、別filesystem条件不明、上限超過、空き不足はworld変更前に拒否します。

```text
/lfc cleanup plan
/lfc cleanup execute <plan-id> <token>
/lfc cleanup status
```

planはdry-runです。APPLIED／UNDONE／ROLLED_BACKかつretention超過だけを候補にし、active、RECOVERY_REQUIRED、journalなしsnapshotを自動削除しません。実行時にactor、期限、checksum、size、journal updatedAtを再検査します。

## Recovery

```text
/lfc apply recover status <placement-id>
/lfc apply recover diagnose <placement-id>
/lfc apply recover rollback <placement-id> <token>
/lfc apply recover accept <placement-id> <token>
```

diagnoseはRelease、snapshot、world identity、各tileのRelease一致／snapshot一致を調べ、`SAFE_TO_ROLLBACK`、`SAFE_TO_ACCEPT`、`SAFE_TO_RESUME`、`MANUAL_INTERVENTION_REQUIRED`、`CORRUPTED`へ分類します。acceptは全tileがReleaseと一致すると再検証できた場合だけです。判断不能ならtokenを発行しません。journal、snapshot、Release、world backupを保存し、audit logのcorrelation IDと合わせて調査します。

## WorldEditとFAWE

両方はWorldEdit公開APIを提供しますが、同時導入を拒否します。WorldEdit／FAWE固有の非同期契約をBukkit APIの非同期呼出し許可と解釈しません。world read/writeはPaper scheduler経由です。upgrade時はtest serverでselection→plan→apply→verify→Undoを再smokeします。

## Upgrade／downgrade

upgrade前にactiveなplan／apply／Undoを完了し、data rootとworldを同時backupして旧JARを保管します。Schema v1、generator version、Minecraft versionをrelease noteで確認します。Phase 3のterminal journalは読取り時に`SYSTEM:LEGACY`とunknown byte usageへ保守的に補完しますが、旧bearer confirmationをactor-bound tokenへ変換しません。新generatorで同じ見た目を保証しないため、既存Releaseは既存checksumのまま運びます。新しいSchema／journalを書いた後のdowngradeは保証しません。

## Security、log、beta運用

relative path、ZIP、JSON、画像、schematicはすべて不信入力です。API response本文、Authorization header、absolute internal pathを利用者へ返しません。利用者向けerrorにはstable code、safe message、correlation ID、operation、stage、suggested actionを出し、stack traceは管理logまたはCLI `--verbose`だけです。

ベータではproduction worldへ直接初回配置せず、同じPaper／WorldEdit implementationのtest world、verified backup、disk alert、retention手順、Recovery担当者を準備してください。
