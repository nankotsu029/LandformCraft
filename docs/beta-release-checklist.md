# Beta Release Checklist

2026-07-14の監査結果を反映しています。`[x]`は今回実行して証拠を得た項目だけです。最後のsource変更後に再実行が必要な項目はcheckを外しています。

## Build／契約

- [ ] 最終sourceで`./gradlew clean test`
- [ ] 最終sourceで`./gradlew build`
- [ ] 最終sourceで`./gradlew shadowJar`
- [x] Schema／example validation test
- [x] package boundary／secret pattern／artifact content test
- [x] 途中artifactでPaper／Bukkit／WorldEdit／FAWE／frontend非同梱を確認
- [ ] 最終artifactでplugin.yml version、command、permission、config loaderを再確認
- [ ] GitHub Actionsを成功させる

## CLI E2E

- [x] help／version／doctorと`--json`契約
- [x] manual JSON validate→generate→8 preview→export→directory／ZIP verify
- [ ] fake OpenAI＋画像→Design→generate→exportの単一E2E
- [ ] fake Anthropic＋画像→Design→generate→exportの単一E2E
- [x] provider HTTP／image／Designの個別contract test
- [x] persisted job status／cancel intent、Ctrl+C、invalid inputのstable error／correlation ID unit test
- [ ] 別CLI processから実行中job ownerへのcancel伝播
- [ ] error JSONをrequestId／jobId／releaseId／placementIdの名前付きcontext fieldへ拡張
- [x] custom asset validate／import／tamper／remove unit test

## Paper＋WorldEdit 7.3.19

- [x] plugin load、version、doctor
- [x] request、bounds、design import、generate、candidate、export
- [x] apply planのactor／bounds／disk予約
- [x] apply、full verify、status、Undo、journal
- [x] overlap拒否と自動rollback
- [x] cleanup planはdry-run、executeはactor-bound
- [ ] Player prompt timeout／cancel、Player permission matrix
- [ ] crash injectionとRecovery各分類

## Paper＋FAWE 2.15.2

- [x] WorldEditを外した別server directoryを作成
- [x] plugin initialization対象がFAWEとLandformCraftだけであることを確認
- [ ] server load→apply→verify→Undo smoke

## Safety concurrency

- [x] 同world同boundsの同時planは片方だけ成功するunit test
- [x] 隣接する非重複boundsは成功するunit test
- [x] restart後reservation再構築unit test
- [x] actor mismatch、expired、reused、別operation token unit test
- [x] disk上限不足をworld変更前に拒否するunit test
- [x] RECOVERY_REQUIRED snapshot保護unit test
- [ ] 同時execute、Undo／Recovery対別placementの実server競合
- [ ] CONSOLE対Player tokenの実servermatrix
- [ ] CONSOLE confirmation tokenがPaper logへ平文永続化されない経路
- [ ] usable space不足／同一FileStore二重予約／cleanup解放のfilesystem E2E

## Performance

- [x] 500×500 CLI generate／export／directory・ZIP verify
- [x] 1000×1000 CLI generate／export／directory・ZIP verify
- [ ] preview時間の独立計測
- [ ] 500×500 Paper plan／snapshot estimate
- [ ] test worldで500×500 apply／Undo、tick impact
- [ ] 1000×1000 Paper plan／snapshot estimate
- [x] 1000×1000実配置を未実行とrelease noteへ明記

## Release

- [x] CHANGELOGとrelease noteを作成
- [ ] current auditのBeta blockerが0
- [ ] worldとdata rootのbackup／restore rehearsal
- [ ] 最終JAR、CLI distribution、sample Release、Design Packageの完全SHA-256を公開記録
- [ ] upgrade／downgrade制限をtest serverで確認

全項目が閉じるまでdecisionは **Beta blockerが残るため未リリース** です。
