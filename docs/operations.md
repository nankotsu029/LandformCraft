# 運用と復旧

日常運用の手順は [Admin Guide](admin-guide.md)、初回手順は [Quick Start](quickstart.md)、commandは [commands.md](commands.md) を正本とします。

## 既定経路（V2-12-05）

V2-12-06でv1 production command／state backendを退役し、Paper／CLIはv2のみになりました。`/lfc <verb>`＝`/lfc v2 <verb>`、`lfc <verb>`＝`lfc v2 <verb>`です。version中立の維持verb（`help`／`version`／`doctor`／`ops`／`asset`／`selection`）と、既存v1 artifact向けのread-only verify／migrate経路は維持します。D8 drain証跡は [V2-12-06 retirement evidence](design-v2/audits/v2-12-06-v1-retirement-evidence.md) を参照してください。

## Lifecycleとthread

Paper起動時にdefault configの初回copy、hard range検証、bounded CPU executor、admission制限付きVirtual Thread I/O、command、Provider、placement repositoryを初期化します。Provider、画像、artifact I/O、generationはPaperメインスレッドで実行しません。Bukkit world read/writeだけをschedulerへdispatchします。停止時は新規mutationを拒否し、workflow futureをcancelし、dispatcherと所有executorを5秒共通deadlineでshutdownします。

## 正常配置

1. Release directory／ZIPをCLI verifyする。
2. `apply plan`でRelease、world identity、height／border、inclusive boundsを検証する。
3. 同world reservationとdisk reservationをstrict atomic stateへ保存する。
4. 同じactorが10分以内にtokenでexecuteする。
5. execute直前にRelease、actor、予約、world identityを再検査する。
6. tileごとにsnapshot checksum→apply→full verify→journal checkpointを保存する。
7. terminal stateでregion／disk reservationを解放する。

失敗時はsnapshot済みtileを逆順復元します。復元も確定できなければ `RECOVERY_REQUIRED`です。

## Reservation

region reservationはworld UUID、inclusive XYZ bounds、placement ID、operation、actor、createdAt、expiresAt、stateを保存します。整数overflowを拒否し、座標を1つ共有すればoverlap、`max+1 == next.min`だけなら非重複です。planとexecute直前の双方で検査し、apply／Undo／rollback／Recovery中は保持します。startupでは非terminal journalから再構築します。

disk reservationは実際のsnapshot root `FileStore`ごとにRelease working space、全tile snapshot worst case、journal、temporary、rollback overhead、safety marginを合算します。他placementの予約を差し引き、usable space不明または不足ならworld変更前に `LFC-SNAPSHOT-NO-SPACE`です。実snapshot bytesをjournalへ保存します。

Release 2の配置寸法は`V2-11-02`で設定によらずcatalogの実測上限へクランプされ、`V2-11-06`でその上限がruntime別になりました（FAWE 2.15.2は1000×1000、WorldEdit 7.3.19は64×64。上位寸法のevidenceは`V2-11-04`／`V2-11-05`のFAWE実測のみ）。配布configの既定値は64のままで、引き上げは運用者の明示設定です。上限超のlayoutはworld変更・durable state書込・reservation取得のどれよりも前に拒否され、再実測専用の`measurement-profile`（明示flag＋隔離world＋CONSOLE／RCON operator、既定無効）だけが例外です。`disk.minimum-free-bytes`と`disk.safety-margin-bytes`も同Taskで初めてRelease 2経路へ反映され、R2 reservation floorは両者の和です。設定は [admin-guide.md](admin-guide.md) を参照してください。

Release 2（V2-6-03、ADR 0022）はv1 storeと分離した`FilePlacementSafetyStoreV2`を使います。regionはeffect envelope AABBの集合、diskはenvelope disk estimate、confirmationはRelease／envelope／reservation／operation／actor／expiry／nonceへ結合します。V2-6-06のapply gateはdurable ownershipとconsumed confirmationを最初のscheduler submission前に再検査します。

Release 2のsnapshot-all（V2-6-04）はv1のtileごとのsnapshot→applyと異なり、最初のapply前に全effect envelopeを`core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2`でsnapshotします。canonical tile-index順に各effect AABBを`PlacementWorldGatewayV2.streamRegionBlockStates`（X最速→Z→Y）から2回走査し、palette＋VarIntのsnapshot file（`release-2-placement-snapshot-file-v1`）をstagingへ書きます。両走査のcanonical block-state stream hashが一致しない場合はworld drift（TOCTOU）として拒否します。全fileのstrict read-back（構造／palette／checksum）とsealed index（`placement-snapshot-plan-v2.schema.json`、plan／envelope／reservation／confirmation binding）のread-back後にのみdirectoryをatomic moveでpublishし、journalを`SNAPSHOTTING`（全tile PENDING）→`SNAPSHOT_COMPLETE`（全tile SNAPSHOTTED、apply-ready）へ進めます。実書込bytesはreservationのdisk leaseを上限にhard stopし、失敗・cancel・shutdownではstagingを全削除してjournalは`CONFIRMATION_ISSUED`のまま残ります（canonical partialなし）。crash後は`cleanupAbandoned`がstaging残骸だけを削除し、published directoryは`loadPublished`のstrict再検証でのみ受理します。apply gatewayはこの段階で決して呼び出しません。

Release 2のcontainment preflight（V2-6-05、ADR 0023）は`SNAPSHOT_COMPLETE`のあと、最初のapply前に`core.v2.placement.safety.PlacementContainmentPreflightV2`で実行します。閉じた`PlacementBlockPhysicsCatalogV2`で分類し、mutation内のfluid／gravity／neighborについてeffect envelope内のclosure／support／radiusを証明できない場合、またはunsupported／unknown stateがある場合はevidenceを発行せず拒否します。成功時だけ`placement-containment-evidence-v2.schema.json`の`CONTAINED` artifactをplan／envelope／snapshotへbindingしてsealします。settle simulationやapplyは行いません。

Release 2のcanonical apply（V2-6-06、ADR 0024）は`PlacementApplyTransactionServiceV2`が実行します。Release、段階別plan binding、reservation、consumed confirmation、published snapshot、containment evidence、canonical sourceをstrict再検証し、全preflight成功後だけjournalをatomicに`APPLYING`へpublishします。final block streamはtile-index順、X最速→Z→Y exact coverage、明示`SOLID → AIR_CARVE → FLUID`、overlay ordinal昇順でbounded slice化され、`PaperPlacementWorldGatewayV2`がPaper scheduler上のWorldEdit公開APIへ渡します。receiptがscheduler受理、main-thread実行、mutation数、resource closeを証明した後だけ次sliceへ進み、完了tileはcanonical `APPLIED` prefixとしてcheckpointします。observer timeout／cancelは受理済みoperationを取消さず、submission後のfailure／cancel／shutdown／source driftは`RECOVERY_REQUIRED`です。全tile apply後もstateは`APPLYING`のままです。tile checkpoint aloneはterminal成功ではありません。

V2-6-20（ADR 0033）の`VerifiedReleaseCanonicalBlockSourceV2`は、このcanonical sourceをRelease format 2 directory／ZIPからproduction再構築します。source作成時にcontainer全体をstrict verifyし、surface系はsurface tile、sparse-volume系はvolume-composed final tileを選択します。各cursorは対象tileだけを再checksum・bounded decodeし、release-local X最速→Z→Yをtarget minimum cornerへ変換します。sourceはcloseableで、ZIP stagingはclose時にcleanupされます。

V2-6-21（ADR 0034）の`Release2PlacementApplicationServiceV2`は、上記の個別安全serviceを`plan → confirm → execute`へ結線します。planはstrict source、tile physics、effect envelope、atomic reservation、actor-bound tokenまででworldを変更しません。confirmはtoken消費後にsnapshot-allとcontainmentを完了し、executeはcanonical apply→bounded settle→full exact verifyを実行します。failureで`RECOVERY_REQUIRED`となった場合はobserver cancellationとは独立した逆順rollbackを試み、exact verify済み`ROLLED_BACK`以外を復旧成功にしません。既存version付きoperational JSONだけを`data/placement-v2/`へ保存し、prepared／confirmed／appliedの必要fileをfsync＋atomic publish＋strict read-backした後、対応するsealed journal commit markerを最後に書き、main journalを進めます。全12 write pointのbefore／after publish failureをproduction file storeで注入し、restart時のpre-mutation診断、post-mutation rollback／Recoveryを固定しています。Paper commandは既定`/lfc <verb>`と恒久的な明示形`/lfc v2 <verb>`だけです。

Release 2のbounded settle／full verify（V2-6-07、ADR 0025）は`PlacementSettleVerifyServiceV2`が実行します。apply完了journal（`APPLYING`かつ全tile `APPLIED`）だけを入力に、`PlacementSettleVerifyPolicyV2`のtick／quiescence／timeoutで`SETTLING`し、effect envelope外のblock updateをhard rejectします。続けて`VERIFYING`でeffect envelope全体をcanonical X→Z→Y順のscheduler-sliced exact stream比較し、成功時だけ`PlacementVerifyEvidenceV2`（`VERIFIED`）をsealしてjournalをterminal `APPLIED`（全tile `VERIFIED`）へ進めます。timeout／mismatch／cancel／shutdown／slice・queue budget超過は`RECOVERY_REQUIRED`です。sample verifyへ弱めません。

Release 2のrollback（V2-6-08、ADR 0026）は`core.v2.placement.rollback.PlacementRollbackServiceV2`が実行します。入力は永続化済み`RECOVERY_REQUIRED` journal（全tileが`SNAPSHOTTED`または`APPLIED`）だけです。最初のworld mutation前に、plan／envelope／reservation／snapshot binding、durable reservation ownership、published snapshotの`loadPublished` strict再検証、全snapshot fileのstrict decodeとsealed index checksum照合、union effect envelopeの被覆完全性を検査し、missing／tampered snapshotやcoverage gapはmutationゼロのまま拒否します。復元はsnapshot済みeffect envelopeを逆canonical tile-index順にbounded restore slice（`release-2-placement-restore-slice-v1`、receiptでscheduler受理・main-thread実行・resource closeを証明）で書き戻し、tile完了ごとに`ROLLING_BACK` journalへcanonical `RESTORED` suffixをcheckpointします。全tile復元後にbounded rollback settle（envelope外update拒否）を行い、union effect envelope全体をsnapshot baselineとexact stream比較して一致した時だけterminal `ROLLED_BACK`（全tile `RESTORED`）をsealし、region／disk reservation leaseを解放します。restore失敗／receipt不正／settle timeout・envelope外update／verify不一致／cancel／shutdown／slice budget超過は`PlacementRollbackFailureCodeV2`で分類して`RECOVERY_REQUIRED`へ戻します。published snapshotは削除しません。user Undoは`V2-6-09`、startup Recoveryは`V2-6-10`です。

Release 2のUndo（V2-6-09、ADR 0027）は`core.v2.placement.undo`が実行します。`PlacementUndoPrepareCompilerV2`はterminal `APPLIED`（全tile `VERIFIED`）だけからUNDO reservationとactor-bound confirmationを発行し、sealed `PlacementUndoPlanV2`へ載せます（sealed apply planは書き換えない）。journalは`APPLIED`のままです。同じplacement IDのapply leaseはself-overlapとして拒否せずUNDO leaseへ置換しますが、別placement IDのeffect envelope overlapは拒否します。Undo context保存失敗時は元のapply leaseとterminal journalを復元します。`PlacementUndoServiceV2`はconfirmation verify／consume、actor照合、expected applied streamとのcurrent-world drift preflight（不一致は`WORLD_DRIFT`、mutationゼロ、force overwrite禁止）、published snapshotのstrict再検証の後、`UNDOING`（VERIFIED prefix＋RESTORED suffix）で逆順restore→bounded settle→snapshot baseline exact verify→terminal `UNDONE`＋reservation解放を行います。snapshotは`KEEP_SNAPSHOTS_FOR_CLEANUP`で保持します。Paperは`PaperPlacementUndoServiceV2`／`PlacementUndoApplicationServiceV2.isRelease2Path()`でv1 Undoと識別します。

Release 2のstartup Recovery（V2-6-10、ADR 0028）は`core.v2.recovery.PlacementRecoveryServiceV2`が実行します。diagnoseは永続journal、published snapshot、現在worldの3証拠だけを読み取り専用で分類し、pre-mutation状態（`PLANNED`〜`SNAPSHOT_COMPLETE`）は`NO_WORLD_MUTATION`（lease解放のみ）、terminal（`APPLIED`／`ROLLED_BACK`／`UNDONE`）は`ALREADY_TERMINAL`、mutated状態はpublished snapshotのstrict再検証・durable lease所有・binding照合・union effect envelope全体のbounded exact world scanの後にのみ`SAFE_TO_ROLLBACK`とします。canonical block source（manifest checksum・capability set・overlay ordinal照合済み）から再構築したexpected applied streamとscanが完全一致した場合だけ`SAFE_TO_ACCEPT`を加え、証拠が欠損・改変・不整合なら`MANUAL_INTERVENTION_REQUIRED`（自動actionなし）です。prepareはactor-bound一回用confirmation（`RECOVERY_ROLLBACK`／`RECOVERY_ACCEPT`、TTL 10分、平文非永続）付きsealed `PlacementRecoveryPlanV2`を発行し、worldもjournalも変更しません。rollback executeは中断journal（`APPLYING`／`SETTLING`／`VERIFYING`／`ROLLING_BACK`／`UNDOING`、復元進捗付き`RECOVERY_REQUIRED`）をVERIFIED→APPLIED／RESTORED→SNAPSHOTTEDの決定論的late reconciliationで`RECOVERY_REQUIRED`へresealし、V2-6-08 rollback transactionへ委譲します。accept executeは新たなfull exact scanが再び完全一致した時だけterminal `APPLIED`（全tile `VERIFIED`）をsealしてleaseを解放し、driftは`WORLD_DRIFT`でmutationゼロ拒否します。cleanup retentionはterminal `ROLLED_BACK`／`UNDONE`だけを対象に、dry-run planのexact file setとjournal checksumが実行時も不変であることを再検証してから削除し、file／byte budgetを事前admissionします。Paperは`PaperPlacementRecoveryServiceV2`／`PlacementRecoveryApplicationServiceV2.isRelease2Path()`でv1 recoveryと識別します。v1 recovery（下記）の意味は変更していません。

## Confirmation

token hashはoperation、plan checksum、Release checksum、world UUID、origin／bounds、actor、createdAt、expiresAt、nonceへ結合します。LandformCraftのjournal／safety state／auditには平文を保存せず、一回使用、actor mismatch、別operation、期限切れ、再利用を拒否します。再起動後もPLANNED journalと期限内tokenは検証可能です。ただし標準PaperのCONSOLEへ送ったcommand候補はserver loggerにも複製されるため、現release candidateはCONSOLE tokenの厳密な非永続化を満たしません。該当logを秘密として保護し、このblockerが解消するまでbeta公開しないでください。

## Cleanup

cleanupはstartupで自動実行しません。planはretention超過のterminal journalに列挙されたsnapshotだけを検査します。RECOVERY_REQUIRED、active、journalなし、symlink、root外、checksum不一致は削除しません。execute時にactor、token、journal updatedAt、size、checksumを再検査し、audit JSONLへ記録します。

Release 2（V2-6-13、ADR 0031）は`core.v2.operations.Release2RetentionServiceV2`がrecovery cleanupをactor-bound dry-run／confirm／auditで包みます。auditは`operations-audit-v2.jsonl`へcorrelation ID付きで追記し、absolute path／secret／raw payloadを拒否します。運用metricsはclosed-label `OperationalMetricsSnapshotV2`、診断は`/lfc ops diagnose <correlation-id>`です。自動削除defaultはありません。

V2-12-10でこの`Release2RetentionServiceV2`をv2 commandへ接続しました。`/lfc v2 retention plan <placement-id>`はそのplacementのapplied journalからplan＋journalを読み、recovery plannerがretention window内と判定したsnapshotのbyte総量を示す1回限りの確認token（10分失効、actor束縛）を発行します。`/lfc v2 retention execute <placement-id> <plan-id> <token>`がtokenを消費して削除し、`/lfc v2 retention status <placement-id>`はjournal stateとsnapshot byte使用量を表示します。削除対象はrecovery cleanup planが列挙したretention window外のterminal snapshotだけで、この判定は`V2-6-13`と同一です。retentionは実行中serverのplacement stateを読むためPaper専用で、`landformcraft.v2.retention`権限を要します（旧v1 `cleanup`の後継。v1 `cleanup`は`V2-12-05`まで不変）。本番配線では、placement統合が有効なとき`Release2PlacementApplicationServiceV2`の`PlacementRecoveryServiceV2`を`RetentionCleanupPortV2.from(...)`で接続します（統合が無ければ削除対象も無く、deferred portのまま）。

read-onlyのv2 placement state確認はCLIから行えます。`lfc v2 journal-verify <placement-journal-v2.json>`はv2 placement journalをstrict検証し、`lfc v2 recovery inspect <journal|plan> <artifact.json>`はjournal state（`RECOVERY_REQUIRED`含む）またはrecovery planのclassificationをread-onlyで表示します。いずれもworldを変更せず、明示したartifact fileだけを読むためCLI専用です（Paperからは安定code`V2_CLI_ONLY`）。これらはv1の`journal-verify`／`recovery status|diagnose`のread-only視点をv2へ引き継ぐものです。

## Recovery

startupはAPPLYING／ROLLING_BACK／UNDOINGを `RECOVERY_REQUIRED`へ移しreservationを再構築します。diagnoseはRelease checksum、snapshot checksum、world identity、tileのRelease／snapshot一致を検査します。

- `SAFE_TO_ACCEPT`: 全tileがReleaseに一致。accept可能
- `SAFE_TO_ROLLBACK`: snapshotへ戻せる証拠あり
- `SAFE_TO_RESUME`: 再開可能な形だがbetaは保守的rollback tokenを出す
- `MANUAL_INTERVENTION_REQUIRED`: 判断不能。tokenなし
- `CORRUPTED`: Release／snapshot証拠が破損。tokenなし

不明状態をAPPLIEDへ推測しません。Recovery snapshotはcleanupしません。

## Provider retryとidempotency境界

HTTP 429／5xx／timeout／transport errorはpolicy上限内だけretryし、`Retry-After`を尊重します。OpenAIへ決定論的client request IDを送り、Design auditへjob ID、request checksum、provider、model、prompt version、attempts、response IDを保存します。同じDesign Packageを後続generateで再利用できますが、新しいdesign jobは現在Providerを再呼出しします。成功応答のdurable cacheは未実装であり、Providerがresponseを作成した後、clientが受信する前に切断した場合のexactly-once／非課金は保証できません。
