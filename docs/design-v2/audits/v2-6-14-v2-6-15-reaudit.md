# V2-6-14 / V2-6-15 re-audit（2026-07-19）

Track A smoke Tasks の再点検結果。正本は Task Index Gate／AGENTS.md／task-execution-guide §8。

## Shared findings

| Item | Result |
|---|---|
| `./gradlew runServer` | **PASS** — Paper 1.21.11-132、plugins `LandformCraft`＋`WorldEdit 7.3.19`、`Done` 到達、`*:25566` listen |
| Direct `versions/*/paper-*.jar` | **FAIL** — `NoClassDefFoundError: joptsimple/OptionException`（paperclip 経由でない） |
| Sandbox／agent 直 bind | 間欠的に `Operation not permitted`。Gradle run-paper 経路では再現せず |
| Release 2 Paper command path | **実装済み（V2-6-21）** — v1 `/lfc apply*`を維持し、R2は`/lfc r2`へ明示分離。strict source、Undo／Recovery、restart inspection、shutdownを配線済み |
| mock／attestation-only COMPLETE | **禁止**（task-execution-guide §8） |

### Implementation scope audit

2026-07-19のV2-6-14再開調査で、blockerは`null` 2個の単純な注入漏れではないことを確認した。

- `Landformcraft`にはRelease 2 plan／reservation-confirm／snapshot-all／containment／apply／settle-full-verifyを組み立てるapplication serviceがない
- `/lfc apply plan|execute|status`と`/lfc undo|apply recover`はv1 `PlacementApplicationService`へ固定され、R2 Undo／Recovery adapterはdoctor／help表示以外から呼ばれない
- `PaperPlacementWorldGatewayV2.advanceSettleTick`はPaper Schedulerの`runTask`へ逐次dispatchされるため、tickが進まないとは断定できない。ただしreceiptのupdate countはplaceholderの0であり、bounded multi-tick settleとdelayed physics containmentは実機smokeで確認が必要である
- production `PlacementCanonicalBlockSourceV2`は`V2-6-20`で実装済みである。strict Release readerとPaper mutationを分割した判断を維持し、残るblockerは`V2-6-21`のapplication lifecycleである
- 上記を閉じるにはpublic R2 command routing、service lifecycle／shutdown、strict source binding、positive／failure／restart testが必要であり、V2-6-14が許可する「smoke defectの小規模修正」を超える

したがってV2-6-14内へ実装を押し込まず停止する。`V2-6-20`（verified Release 2 canonical block source）と`V2-6-21`（R2 Paper application／command lifecycle）を直列の先行Taskとして登録し、両方の完了後に本smokeを再開する。

## V2-6-14 WorldEdit 7.3.19 smoke

### Previous status error

`COMPLETE`（operator attestation）は Gate を満たさない。

- evidence に JAR SHA-256／placement ID／envelope・journal checksum／peak+disk 実測値がない
- Release 2 end-to-end を public command で実行できない（上記未配線）
- attestation だけでは AGENTS／Task Index の「exact build/version付き実機evidence」にならない

### Corrected status

**`BLOCKED_EXTERNAL`**（2026-07-19 re-audit）

### What works

- WE 7.3.19 単独 plugin init（gradle `runServer`）
- v1 placement path は従来どおり command 接続済み（本 Task の Release 2 Acceptance の代用にしない）

### Unblock requirements

1. runbook どおり generate→verify→plan→confirm→snapshot-all→apply→settle→full verify→Undo を **今回build** で実行
2. `docs/design-v2/audits/v2-6-14-worldedit-smoke-evidence.md` に machine-verifiable 値を記入
3. FAWE を同 profile に入れない

## V2-6-15 FAWE 2.15.2 standalone smoke

### Corrected status

**`BLOCKED_EXTERNAL`**（維持・理由更新）

### What works

- `run-fawe/plugins` に FAWE 2.15.2＋LandformCraft（WorldEdit jar なし）— ADR 0032 と一致
- 2026-07-14 init-only ログあり（**Acceptance 不可**）

### Blockers（解決不能／未解決）

1. **FAWE profile の正規起動経路未整備** — `run-fawe` に paperclip がなく `versions/*.jar` 直起動は失敗。`runServer` 既定は `run/`（WE profile）。FAWE 用 `runDirectory` 切替が build に未設定
2. 過去の「bind不能」だけを唯一理由にしない（gradle WE boot では bind 成功を確認）

### Unblock requirements

1. V2-6-14 の R2 E2E 配線＋証拠完了（前提 Task）
2. FAWE 専用 runDir（例: `run-fawe`）を paperclip／`runServer` で起動可能にする
3. WE jar 無し・FAWE 2.15.2 のみで同型 E2E evidence を記入

## Roadmap impact

- 完了数は **15/21**（V2-6-21完了、V2-6-14 COMPLETE取消は維持）
- Next = **`V2-6-14`再開**
- `V2-6-16`／`V2-6-17`は無効化済み。次は`V2-6-18`（本監査では開始しない）
