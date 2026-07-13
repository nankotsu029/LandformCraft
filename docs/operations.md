# 運用と復旧

日常運用の手順は [Admin Guide](admin-guide.md)、初回手順は [Quick Start](quickstart.md)、commandは [commands.md](commands.md) を正本とします。

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

## Confirmation

token hashはoperation、plan checksum、Release checksum、world UUID、origin／bounds、actor、createdAt、expiresAt、nonceへ結合します。LandformCraftのjournal／safety state／auditには平文を保存せず、一回使用、actor mismatch、別operation、期限切れ、再利用を拒否します。再起動後もPLANNED journalと期限内tokenは検証可能です。ただし標準PaperのCONSOLEへ送ったcommand候補はserver loggerにも複製されるため、現release candidateはCONSOLE tokenの厳密な非永続化を満たしません。該当logを秘密として保護し、このblockerが解消するまでbeta公開しないでください。

## Cleanup

cleanupはstartupで自動実行しません。planはretention超過のterminal journalに列挙されたsnapshotだけを検査します。RECOVERY_REQUIRED、active、journalなし、symlink、root外、checksum不一致は削除しません。execute時にactor、token、journal updatedAt、size、checksumを再検査し、audit JSONLへ記録します。

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
