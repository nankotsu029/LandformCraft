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

Release 2 Undo（V2-6-09）はv1 `PlacementApplicationService`と分離した`PlacementUndoApplicationServiceV2`／`PaperPlacementUndoServiceV2`です。`doctor`はRelease 2 Undo pathの注入有無を表示します。Undo前のworld driftは黙って上書きせず拒否します。

CONSOLEはidentity `CONSOLE`、playerはUUIDです。command blockなど曖昧なsenderはconfirmation操作に使えません。

注意: 標準Paper consoleへ表示されたconfirmation commandは`latest.log`にも記録されます。LandformCraftのjournalはhashしか保存しませんが、console tokenの平文非永続化は現release candidateのblockerです。log directoryを秘密として保護し、tokenは一回使用後または10分で失効させ、公開betaとして運用しないでください。

Release 2 placementは平文tokenをjournal／safety stateへ保存しません。既定`/lfc place plan → confirm → execute`と恒久的な明示形`/lfc v2 place …`を提供し、permission nodeは`landformcraft.v2.*`だけです。V2-12-06でv1／r2 commandと旧permission aliasを削除しました。`V2-12-10`のretentionは`landformcraft.v2.retention`を使い、recovery plannerがretention window内と判定したsnapshotだけを、一回限り・10分失効・actor束縛の確認token経由で削除します。CLIの`journal-verify`／`recovery inspect`はread-onlyで、明示artifactだけを読みworldへ触れません。v2 job storeは`data/v2/jobs/`で保持上限4096件、取消jobはpublisherのcommit point前に停止します。v2 request authoringは`landformcraft.v2.request.create`／`.edit`を使用し、Paperでは`plugins/LandformCraft/data/v2/requests/`配下だけへ書きます。P0再オープン後のplacement rootはoperator Player／CONSOLE／RCON限定で、command block、非operator、actor mismatchを拒否します。consoleへ表示・入力したtokenはserver logへ残り得るため、logを秘密情報として保護してください。

Release 2のportable input rootは`plugins/LandformCraft/data/releases-v2/`、operational stateは`data/placement-v2/`です。prepared／confirmed／applied contextと各stage commit markerはfsync＋atomic publish＋strict read-backされます。起動時は非terminal R2 journalをstrict読取して警告しますが、曖昧なoperationを自動acceptしません。`/lfc v2 status`と`/lfc v2 recover diagnose`で証拠を確認してください。offline verbの入力rootは`plugins/LandformCraft/data/v2/`で、絶対path・`..`・symlinkを拒否します。world allow／deny policyはR2 planにも適用され、存在しないworldをinternal errorへ変換しません。

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

### Release 2 配置寸法（V2-11-02）

```yaml
disk:
  minimum-free-bytes: 536870912
  maximum-snapshot-bytes: 8589934592
  safety-margin-bytes: 268435456

placement:
  release2:
    measured-candidate-max-width: 64
    measured-candidate-max-length: 64
    measurement-profile:
      enabled: false
      isolated-world: ""
      max-width: 0
      max-length: 0
      apply-slice-blocks: 0
```

通常運用のR2配置寸法は、**検出したruntimeで実測済みの寸法**で固定です（`V2-11-02`のクランプ、`V2-11-06`の実測昇格）。

| runtime | 上限 | evidence |
|---|---|---|
| FAWE 2.15.2 | 1000×1000 | `V2-11-04`（500×500）／`V2-11-05`（1000×1000） |
| WorldEdit 7.3.19 | 64×64 | `V2-6-14`／`V2-6-15` smoke（64×64超は未実測） |

`measured-candidate-max-*`はこの上限以下へ絞る用途に使え、上限を超える値は起動時に拒否します（設定でcatalog上限を広げることはできません）。上限超のlayoutはworld変更・journal書込・reservation取得のいずれよりも前に拒否されます。配布configの既定値は保守的に64のままなので、FAWE環境で500／1000を使う場合だけ明示的に引き上げてください。V2-13-06の現行production default実測では1000×1000のfull placement＋Undoが壁時計約11.9分、peak RSS約5.24 GiBです（host固有値、[performance.md](performance.md)）。

`measurement-profile`は再実測専用の脱出口で、既定は無効です。`V2-11-04`／`V2-11-05`が使い、以後も**未実測寸法**（1000×1000超、またはWorldEdit runtimeでの64×64超）を実測する場合にだけ使います。有効化には次の3条件がすべて必要です。ひとつでも欠けると通常のruntime上限が適用されます。

1. `enabled: true`（明示flag）
2. `isolated-world`に指定した隔離world上のplacementであること（失っても構わないworldを使ってください）
3. CONSOLE／RCONのoperator実行であること（in-game Playerは未実測寸法へ到達できません）

このprofileでの配置は**能力昇格ではありません**。catalogの`SUPPORTED`寸法は実測evidenceの範囲（`V2-11-06`で1000×1000）に限られ、profileでの実測だけでは広がりません。有効時はstartup logへ警告を出します。

`apply-slice-blocks: 0`はV2-13-06で実測選定したproduction既定値（1024 mutations/slice）を使います。非0は隔離ホスト較正専用で、`measurement-profile.enabled: true`の場合に限り、閉じた候補`32`／`128`／`256`／`512`／`1024`だけを受理します。通常運用で調整する設定ではありません。production既定値は[較正evidence](design-v2/audits/v2-13-06-apply-slice-calibration-evidence.md)でAPPLY約93.4%短縮、lifecycle約74.3%短縮を確認済みですが、寸法catalogは1000×1000のままです。

`disk.minimum-free-bytes`と`disk.safety-margin-bytes`は`V2-11-02`でRelease 2経路へも反映されます（それ以前は`maximum-snapshot-bytes`だけが効き、R2のreservation／snapshot／Undo admissionは固定1 MiBの空き容量下限を使っていました）。R2のreservation floorは`minimum-free-bytes + safety-margin-bytes`です。

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

Release 2 運用（V2-6-13）:

```text
/lfc ops metrics
/lfc ops diagnose <correlation-id>
```

metricsはclosed-labelのqueue／memory／disk／stage証拠だけを返し、diagnoseはaudit JSONLのcorrelationからredacted findingsを返します。secret値やabsolute pathは出しません。Release 2 snapshot retentionのactor-bound dry-run／confirmは`Release2RetentionServiceV2`（ADR 0031）であり、startup自動削除はしません。

## Recovery

```text
/lfc apply recover status <placement-id>
/lfc apply recover diagnose <placement-id>
/lfc apply recover rollback <placement-id> <token>
/lfc apply recover accept <placement-id> <token>
```

diagnoseはRelease、snapshot、world identity、各tileのRelease一致／snapshot一致を調べ、`SAFE_TO_ROLLBACK`、`SAFE_TO_ACCEPT`、`SAFE_TO_RESUME`、`MANUAL_INTERVENTION_REQUIRED`、`CORRUPTED`へ分類します。acceptは全tileがReleaseと一致すると再検証できた場合だけです。判断不能ならtokenを発行しません。journal、snapshot、Release、world backupを保存し、audit logのcorrelation IDと合わせて調査します。

Release 2（format 2）のoperationは上記v1経路と分離した`PaperPlacementRecoveryServiceV2`（V2-6-10、ADR 0028）で復旧します。diagnoseはworldを変更せず、published snapshotのstrict再検証とeffect envelope全体のexact world scanの証拠がある場合だけ、actor-bound一回用token付きのrollback／acceptを準備できます。曖昧な状態は`MANUAL_INTERVENTION_REQUIRED`となり自動actionを提示しません。retained snapshotのcleanupはterminal `ROLLED_BACK`／`UNDONE`限定のdry-run→実行bindingです。運用詳細は [operations.md](operations.md) を参照してください。

## WorldEditとFAWE

両方はWorldEdit公開APIを提供しますが、同時導入を拒否します。WorldEdit／FAWE固有の非同期契約をBukkit APIの非同期呼出し許可と解釈しません。world read/writeはPaper scheduler経由です。upgrade時はtest serverでselection→plan→apply→verify→Undoを再smokeします。

## Upgrade／downgrade

upgrade前にactiveなplan／apply／Undoを完了し、data rootとworldを同時backupして旧JARを保管します。Schema v1、generator version、Minecraft versionをrelease noteで確認します。Phase 3のterminal journalは読取り時に`SYSTEM:LEGACY`とunknown byte usageへ保守的に補完しますが、旧bearer confirmationをactor-bound tokenへ変換しません。新generatorで同じ見た目を保証しないため、既存Releaseは既存checksumのまま運びます。新しいSchema／journalを書いた後のdowngradeは保証しません。

## Security、log、beta運用

relative path、ZIP、JSON、画像、schematicはすべて不信入力です。API response本文、Authorization header、absolute internal pathを利用者へ返しません。利用者向けerrorにはstable code、safe message、correlation ID、operation、stage、suggested actionを出し、stack traceは管理logまたはCLI `--verbose`だけです。

ベータではproduction worldへ直接初回配置せず、同じPaper／WorldEdit implementationのtest world、verified backup、disk alert、retention手順、Recovery担当者を準備してください。
