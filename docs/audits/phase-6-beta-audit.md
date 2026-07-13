# Phase 6／Beta readiness audit

- Audit date: 2026-07-14
- Target: working treeの実code、test、Schema、resources、Gradle、CLI、Paper artifact
- Historical baseline: [phase-0-5-audit.md](phase-0-5-audit.md)
- Version under audit: `0.9.0-beta.1`
- Decision: **Beta blockerが残るため未リリース**

## Classification

`IMPLEMENTED`、`PARTIALLY_IMPLEMENTED`、`IMPLEMENTED_BUT_UNTESTED`、`DOCUMENTED_ONLY`、`BROKEN`、`MISSING`、`OBSOLETE`、`REMOVED`、`OUT_OF_SCOPE`を使用します。

## Audit table

| 対象 | 機能 | 状態 | 実装箇所 | テスト | docs | 問題 | 対応 |
|---|---|---|---|---|---|---|---|
| Web | 独自Web UI code／依存／asset | REMOVED | 該当codeなし | repository／JAR検索 | ADR 0009、roadmap | 計画文書だけ存在した | Phase 7、M7、worker推奨を削除 |
| Web版AI | manual JSON作成 | IMPLEMENTED | import provider、CLI／Paper design import | design contract／Paper smoke | user-guide | 独自UIとの混同 | 明確に区別 |
| CLI | validate／generate／preview／export／verify／design | IMPLEMENTED | `LandformCraftCli` | unit、実CLI round-trip | quickstart、commands | なし | 維持 |
| CLI | options／doctor／request／candidate／asset／recovery | IMPLEMENTED | CLI、repositories、Doctor、CustomAsset | CLI／asset unit、doctor実行 | commands | recoveryはread-only | world mutationはPaper限定 |
| CLI | job status／cancel | PARTIALLY_IMPLEMENTED | CLI、job repository、shutdown hook | repository／Ctrl+C tests | commands | 別CLI processのcancelは永続intentだけで実行中ownerへ通知しない | IPCまたは常駐owner契約までblocker |
| Error | stable code／correlation／safe fields | PARTIALLY_IMPLEMENTED | `LandformException`、CLI／Paper renderer | error path tests | troubleshooting | contextual IDは共通`resourceId`で、requestId／jobId／releaseId／placementIdの名前付きJSON fieldではない | typed context fieldへ拡張するまでblocker |
| Paper | version／doctor | IMPLEMENTED | command、Doctor | WorldEdit server smoke | admin-guide | key値は非表示 | 維持 |
| Paper | request／bounds／prompt | IMPLEMENTED_BUT_UNTESTED | workflow、5分chat state | create／bounds／validate smoke | user-guide | Player chat promptのtimeout／cancel実server未確認 | release checklistへ残す |
| Paper | design／Provider wiring | IMPLEMENTED_BUT_UNTESTED | workflow、ProviderSettings、config | fixture import smoke、HTTP fake contract | ai-providers | live有料APIとPlayer画像commandは未実行 | fake E2Eを追加してからrelease |
| Paper | generate／job／candidate／export | IMPLEMENTED | `PaperWorkflowService`、command | contract test、WorldEdit server E2E | commands | cancel実server未確認 | release checklistへ残す |
| Permission | 機能単位、admin children、権限対応補完 | IMPLEMENTED | plugin.yml、command、suggestions | artifact／suggestion tests | admin-guide | Player権限matrixの実server網羅は未実施 | release checklistへ残す |
| Placement | plan／apply／verify／rollback／Undo | IMPLEMENTED | placement service、WE gateway | service tests、WorldEdit apply／verify／Undo | operations | FAWE current apply未確認 | release blocker |
| Safety | overlap reservation | IMPLEMENTED | safety store | concurrent plan、隣接、restart tests | operations | 大規模実server競合は未実施 | 維持 |
| Safety | actor-bound confirmation | PARTIALLY_IMPLEMENTED | ActorIdentity、confirmation hash | mismatch／expiry／reuse／Recovery tests | security | journalはhashのみだがCONSOLE command候補がPaper logへ平文記録された | 非log化されたconsole confirmation経路までblocker |
| Safety | disk estimate／reservation | IMPLEMENTED | estimator、safety store | upper bound事前拒否test、64×64実journal | admin-guide | 500／1000 plan estimate未計測 | release blocker |
| Safety | retention cleanup | IMPLEMENTED | cleanup service／Schema | dry-run、actor、Recovery保護test、Paper smoke | operations | 大量journal性能未計測 | bounded admin実行 |
| Recovery | diagnose／rollback／accept | IMPLEMENTED_BUT_UNTESTED | placement service／Paper command | safe accept／manual classification unit | admin-guide | crash全stageと実world accept／rollback未実行 | release blocker |
| Provider | retry／correlation／成功監査 | PARTIALLY_IMPLEMENTED | HTTP client、Design audit | 429／5xx／timeout／header tests | ai-providers | Provider側exactly-once不能、durable response cacheなし | 保証境界を制限へ明記。再課金防止は未完成 |
| Asset | 8 built-in asset | IMPLEMENTED | built-in catalog | catalog／planner／Release tests | user-guide | なし | verified |
| Asset | custom validate／catalog | PARTIALLY_IMPLEMENTED | CustomAssetService、inspector、Schema | import／path／symlink／collision tests | user-guide | TerrainIntent v1はcustom asset IDを選択しない | catalog beta。生成統合はblocker |
| Structure | seed／rotation／terrain／preferred zone／fallback | IMPLEMENTED | planner／manifest／preview | deterministic／thread／bounds／collision tests | how-it-works | なし | fallbackをwarningとmagentaで明示 |
| Structure | tile統合／standalone asset／tamper | IMPLEMENTED | materializer、publisher／verifier | read-back／boundary／tamper tests | artifact-format | WorldEdit初回smokeで柵state差を検出 | 4方向stateと外周marginを修正後E2E成功 |
| Config | loader／resource／docs同期 | IMPLEMENTED | config.yml、plugin loader | artifact/config tests、doctor smoke | admin-guide | reloadはactive safety条件へ適用しない | startup snapshotとして保持 |
| Build | Java 21／Paper 1.21.11／provided dependencies | IMPLEMENTED_BUT_UNTESTED | Gradle、plugin.yml | artifact test、JAR namespace audit | README | 最後の小修正後にclean build不能 | release blocker |
| CI | build／contract／artifact／secret／CLI smoke | IMPLEMENTED_BUT_UNTESTED | `.github/workflows/ci.yml` | local tasksは途中版で成功 | checklist | GitHub runner未実行 | release前にrun |
| Performance | 500／1000 CLI generate／export／verify | IMPLEMENTED | generation／Release pipeline | 2026-07-14実測 | performance | preview単独時間は未分離 | 記録済み |
| Performance | 500／1000 Paper plan／snapshot／apply | MISSING | 64×64のみ実施 | 未実行 | limitations | tick impactを含め未計測 | release blocker |
| Entity／都市／biome／洞窟 | 明示的非目標 | OUT_OF_SCOPE | 該当なし | 該当なし | limitations | なし | 実装予定として扱わない |

## Initial state record

- git: Phase 3〜6の多数のtracked変更とuntracked実装／test／docs、ユーザー所有の `アーカイブ.zip` が存在。すべて保持した
- version: `0.1.0-SNAPSHOT`
- roadmap: Phase 0〜6完了、Phase 7 Web UI開始gate
- initial `./gradlew test`: success
- initial `./gradlew build`: success
- initial `./gradlew run --args="--help"`: success。旧8 commandだけを表示
- Web UI: code、依存、resource、frontend、package manager、HTTP endpointは存在せず、roadmap／README／architectureの計画記述だけを発見
- 主な不足: Paper workflow、CLI job／admin command、permission、error contract、overlap、actor binding、disk、cleanup、Recovery、Paper Provider wiring、custom asset catalog

## Web UI removal audit

`web.?ui`、frontend、React／Vue／Svelte、WebSocket、CSRF、embedded HTTP server、Phase 7、npm／pnpm／yarn、package.json、Vite／Webpack、REST controller、Web専用DTO／config／CIを検索しました。実装code、依存、resource、frontend directory、package manager設定はありませんでした。roadmap、architecture、READMEから将来計画を削除し、ADR 0009で判断を記録しました。Provider HTTP client、ブラウザ版AI manual import、core job abstractionはCLI／Paper実運用に必要なため維持しました。

## Phase 6 structure verification

| Type | built-in ID | terrain条件 | standalone schematic | unit／Release | WorldEdit E2E |
|---|---|---|---|---|---|
| 桟橋 | `small-pier-v1` | water edge | verified | verified | Releaseへ統合 |
| 橋 | `small-bridge-v1` | water crossing | verified | verified | Releaseへ統合 |
| 小屋 | `fishing-hut-v1` | dry flat | verified | verified | Releaseへ統合 |
| 遺跡 | `stone-ruin-v1` | dry flat | verified | verified | 配置Releaseに含有 |
| 道 | `path-v1` | dry following | verified | verified | 配置Releaseに含有 |
| 石積み | `retaining-wall-v1` | dry following | verified | verified | Releaseへ統合 |
| 階段 | `stone-steps-v1` | dry following | verified | verified | Releaseへ統合 |
| 柵 | `fence-v1` | dry following | verified | verified | connection修正後成功 |

semantic checksum、Minecraft version、rotation、anchor、terrain following、preferred zone／fallback、water／cliff／slope／bounds、spacing、tile boundary、required-assets、structures manifest、preview実座標、同seed／thread決定性、asset tamper拒否をcodeとunit testで確認しました。配置不能はterrain-only warningとして隔離します。WorldEdit E2Eの初回は外部blockへ接続した`oak_fence` state差をfull verifyが検出して全tileを自動rollbackしました。4方向stateを明示しstructureをtile外周から1 block内側へ制限したReleaseでapply／full verify／Undoが成功しました。

## Executed integration evidence

### CLI

- 500×500: generation 1,258 ms、export 18.52 s、directory／ZIP verify 1.09／0.91 s、max RSS 108,560,384 B、Release 480 KiB／ZIP 328,831 B
- 1000×1000: generation 4,277 ms、export 67.77 s、directory／ZIP verify 1.29／1.12 s、max RSS 108,593,152 B、Release 1.5 MiB／ZIP 1,083,255 B
- rocky-coast validate、phase6 generate／8 preview／structure export、directory／ZIP verify、design import／verify、doctor JSONを実行

### Paper＋WorldEdit 7.3.19

Paper 1.21.11 build 132でplugin load、version、doctor、request、bounds、fixture design、generate、job status、candidate list／preview／validate、export plan／create／verify、apply planを実行しました。64×64、1 tile、4 structureのReleaseをCONSOLE actorでplanし293,621,754 bytesを予約、828 bytesのsnapshotを作成、apply後の全block verifyに成功しました。別tokenでUndoし、journal `UNDONE`をread-backしました。重複boundsの別planは`LFC-PLACEMENT-OVERLAP`、cleanup plan／executeも成功しました。

### Paper＋FAWE 2.15.2

WorldEditを含めない独立`run-fawe`を作成し、FAWEとLandformCraftだけがplugin initialization対象であることを確認しました。sandboxのnetwork／port bind制限でserver loadへ到達できず、権限昇格も実行環境の承認上限により拒否されたためapply／verify／Undoは未実行です。成功とは扱いません。

## Artifact audit

- Paper JAR: `build/libs/LandformCraft-0.9.0-beta.1.jar`、SHA-256 `f7b517397f23204cfd50fda15c55043f19538bb13df2c5ea0e77d75274110315`
- CLI JAR: `build/libs/LandformCraft-0.9.0-beta.1-cli.jar`、SHA-256 `28cede2ebbafcbf0130b8fe8e096aacd33a9bcd6a9366b7c07b94d5fc46caf8b`
- 500 ZIP SHA-256: `ed76a1d312eb089276d410cd96fe530e42a160b23bd3a0cad829283c2425679f`
- 1000 ZIP SHA-256: `507dea4ac982bf812443d8d44f56611c897789f31d10867678f25c0e6ac8c5b1`
- JARにはPaper／Bukkit／WorldEdit／FAWE本体、Web UI／frontend namespaceを含めないことを検索確認

上記JARは柵修正を含みますが、その後のplan表示強化と非Recovery診断error整形という2つの小修正を含みません。したがってrelease artifactではありません。

最後のJava変更4 fileは既存shadow JARとPaper API依存をclasspathにした`javac --release 21 -Xlint:all -Werror`で個別再コンパイルし成功しました。これはresource処理、全source、test、JAR組立を含むGradle buildの代替ではありません。

## Commands executed

次は実際に実行した主要commandです。成功していないものは結果を併記します。

```text
git status --short                                      success
git diff --stat                                         success
find . -maxdepth 3 -type f -print | sort                success
./gradlew test                                          success（開始時および実装途中）
./gradlew build                                         success（開始時および実装途中）
./gradlew clean test                                    success（最終小修正より前）
./gradlew run --args="--help"                          success
./gradlew run --args="doctor --data-dir build/beta-data --json"  success
./gradlew run --args="validate examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json"  success
./gradlew run --args="generate examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/beta-perf-500"  success
./gradlew run --args="export examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/beta-perf-exports-500"  success
./gradlew run --args="generate examples/performance/request-1000.yml examples/rocky-coast/terrain-intent.json build/beta-perf-1000"  success
./gradlew run --args="export examples/performance/request-1000.yml examples/rocky-coast/terrain-intent.json build/beta-perf-exports-1000-v2"  success
./gradlew run --args="verify <500/1000 release directory or ZIP>"  four successful verifies
./gradlew run --args="design import examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/e2e-designs build/e2e-jobs"  success
./gradlew run --args="design-verify <generated design directory>"  success
./gradlew shadowJar                                     success（柵修正を含む途中artifact）
./gradlew shadowJar                                     rejected（最終source、実行環境の承認使用量上限）
git diff --check                                        success
javac --release 21 -Xlint:all -Werror <final four Java files>  success
```

Paper commandは`run/logs/latest.log`、FAWE起動失敗は`run-fawe/logs/latest.log`へ保存されています。WorldEdit serverは正常停止し、FAWE serverはport bind前に停止しました。

## Remaining beta blockers

1. 最終sourceに対する`clean test`、`build`、`shadowJar`、artifact再監査。実行環境の承認使用量上限で再実行できなかった
2. FAWE 2.15.2単独serverのload→apply→full verify→Undo smoke
3. 500×500／1000×1000 Paper plan、snapshot estimate。500×500 apply／Undo／tick impact
4. Paper Player prompt、Player actor対CONSOLE、job cancel、crash各stageのRecovery E2E
5. OpenAI／Anthropic fakeを画像入力からReleaseまで通す統合E2E。live APIは任意でCI外
6. Provider成功応答のdurable cacheがなく、process crashをまたぐ再課金防止を完了していない
7. custom asset catalogをTerrainIntent v1の生成で選択できない
8. error JSONのcontext IDが共通`resourceId`であり、resource種別ごとの名前付きfield契約を満たしていない
9. CLI `job cancel`は別processの実行中jobへcancelを伝播しない。Ctrl+CとPaper in-process cancelだけが実taskへ連動する
10. CONSOLEへ表示するconfirmation commandの平文tokenが標準Paper logへ複製される。domain artifactはhashのみだが非永続化要件は未達

この項目が残る間のdecisionは **Beta blockerが残るため未リリース** です。`0.9.0-beta.1`はrelease candidate versionであり、公開済みを意味しません。
