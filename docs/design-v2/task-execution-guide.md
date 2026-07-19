# v2 Task Execution Guide

> Status: V2-2〜V2-10の各Taskを、1回のエージェント作業で安全に閉じられる単位へ分けるための実行規約。進捗の正本は [docs/roadmap.md](../roadmap.md)、Taskの正本は [task-index.md](task-index.md)、Taskごとの実行モデル・Effort・レビュー体制の正本は [model-assignment.md](model-assignment.md) である。

## 1. 目的

Phase全体を一度に実装しない。各回は `V2-x-NN` のTaskを1個だけ選び、そのAcceptance gateまで実装、検証、文書同期して終了する。Task完了は親Phase完了を意味しない。親Phaseは末尾の統合監査Taskだけが閉じる。

Track A（V2-4→V2-5→V2-6）、Track B（V2-7）、Track C（V2-8）、Track D（V2-9）、Track E（V2-10）は、Taskが宣言した依存を満たす限り並行実行できる。Track Dは`V2-6-01`／`V2-6-02`と`V2-8-01`だけを前提とし、Track Eは対応するTrack D Taskを前提とする。Track D／EのPaper apply capabilityを`SUPPORTED`にする作業だけは、`V2-6-06`〜`V2-6-10`と`V2-6-19`の完了後に別Taskで接続する。ただし同一ファイルを複数エージェントが同時編集してはならず、`format.v2.release`等の共有領域を変更するTaskは直列にする。1 Taskは1 Task capsule（`.task-context/V2-x-NN.md`、Git管理外）と1新規セッションで実行し、モデルとEffortはセッション開始時に [model-assignment.md](model-assignment.md) から決める。

IDは順序変更に強い `V2-<phase>-<two digits>` とする。既存IDの意味を変更せず、新しい依存が見つかった場合は未使用IDを追加する。番号を詰め直して履歴やプロンプトを壊さない。

## 2. Task contract

[task-index.md](task-index.md) の各Taskは、次を個別に定義する。

- Task ID
- 目的
- 前提Task
- 対象Scope
- 明示的な非Scope
- 主に変更するpackage／Schema／docs
- 実装成果物
- 必須テスト
- 決定性／memory／security条件
- Acceptance gate
- 完了時に更新するdocs
- 次Taskへ進む条件
- 作業を停止すべき条件

Task欄の `D/M/S` は determinism、memory、security の条件である。共通して、v1 Schema、generator `3.0.0-phase6`、Release format 1のchecksumと意味を凍結し、`V1CompatibilityGoldenTest`、Release 1 verifier、placement／Undo回帰を対象変更に応じて維持する。

## 3. 実行エージェントへ渡す共通プロンプト

実行エージェント（Codex／Claude Code／Antigravity／Cursor）には、通常は次の短いプロンプトとTask capsuleだけを渡す。

```text
AGENTS.md、docs/roadmap.md、docs/design-v2/task-execution-guide.md、
docs/design-v2/task-index.mdと対象Taskの関連docsを読み、
担当TrackのTask V2-x-NNをAcceptance gateまで実装してください。
後続Taskへ進まず、必要なtest、Schema、examples、docs、roadmapを更新してください。
```

`docs/roadmap.md` が示す担当Trackの `次のTask` とTask indexの前提を照合し、矛盾時は実装せず文書の矛盾を報告する。モデル・Effort・レビュー担当は [model-assignment.md](model-assignment.md) の該当行に従い、昇格は失敗ベースのラダー以外で行わない。

## 4. 1回の作業量

1 Taskは次を目安にする。

- 主目的は1つ。
- 大規模な新規subsystemは1つまで。
- 新しく `SUPPORTED` にするfeatureは原則1つまで。共通kernelまたは同一featureの明示variantだけを例外にする。
- 独立したartifact format変更は1つまで。
- generatorとPaper world mutationを同じTaskにしない。
- 実装と親Phase総合監査と実world計測を同じTaskにしない。
- 完了判定を自動testで確認できる。
- Task途中の未公開artifactや `EXPERIMENTAL` moduleが残っても既存branchを壊さず、Task末尾でclean buildできる。
- 後続Taskを実装しなくても、現時点のcapability、非Scope、未完gateを説明できる。

差分が想定を超えた場合、Acceptance gateを削って押し込まない。新しい大規模subsystem、2個目の独立format、2個目の独立generator、またはPaper mutationが必要になった時点で停止し、追加Taskを提案する。

## 5. 実行規則

1. 作業前に `git status --short` を記録し、既存変更を保持する。
2. `docs/roadmap.md` のNext task、Taskの前提、関連設計、ADR、Schema、実装、testを確認する。
3. 対象TaskのScopeだけを実装する。Acceptance gateを満たしても後続Taskへ進まない。
4. featureはgeneratorだけで `SUPPORTED` にしない。contract、generator、validator、preview、positive／corruption fixture、resource budget testが揃うまで `EXPERIMENTAL` のままにする。
5. artifactはstrict read-backとcorruption test前にcapabilityを有効化しない。
6. 作業後に対象test、`./gradlew test`、`./gradlew build`、`git diff --check`、`git status --short`を確認する。
7. TaskのAcceptance証拠をroadmapへ短く記録し、詳細は関連docs／testへ置く。

## 6. 親Phaseと統合Task

個別Task完了時は親Phaseを `進行中` とし、未完Taskを残す。各Phase末尾の統合監査Taskは、全前提Task、Phase-level fixture、全capability strict verify、determinism／memory／security portfolio、v1回帰、clean buildを再確認する。統合TaskだけがPhase gateを `完了` に変更し、次Phaseの最初のTaskを `Next task` にできる。

統合Taskで設計欠落や回帰が見つかった場合は、統合Task内で大規模修正を抱え込まない。原因Taskを再openするか、新しい同Phase Taskを追加し、親Phaseを未完のままにする。

## 7. Support capability gate

### Feature support

単一の`lifecycleStatus`を能力判定へ使わない。各entryは `intent_compile`、`offline_generate`、`validation`、`preview`、`export`、`standalone_usage`、`child_plan_usage`、`volume_overlay_usage`、`paper_apply`、`post_apply_validation`、`snapshot`、`rollback`、`restart_recovery`を持ち、各能力を `SUPPORTED`／`PARTIAL`／`EXPERIMENTAL`／`UNSUPPORTED`／`NOT_APPLICABLE`で記録する。個別能力を`SUPPORTED`へ変更できるのは、その能力に対応する次の証拠が揃った時だけである。

- version付きIntent／Blueprint contractとbuilt-in descriptor
- generatorと宣言済みfield ownership／merge rule
- baseline、feature、cross-feature validator
- diagnostic previewとindex登録
- positive、boundary、corruption fixture
- whole／tile／seam／thread／locale／timezone決定性
- 1000角またはfeature最大寸法のresource budget test
- Release 2 capabilityのstrict directory／ZIP read-back
- 親Phase統合TaskまたはV2-6能力catalog Taskの承認

個別feature Taskでは実装した能力を `EXPERIMENTAL` または`PARTIAL`にできるが、親Phase統合Task前に `SUPPORTED` としない。child-plan限定をstandaloneへ、plan-level volumeをpublic Intentへ、offline exportをPaper applyへ波及させない。roleと能力定義の正本は [Terrain Feature Taxonomy](terrain-feature-taxonomy.md) とする。

### Release 2 capability

capabilityを有効化できるのは、artifact type／version、必須集合、上限、semantic checksum、publisher、strict verifier、directory／ZIP tampering test、直前capability setの回帰が同じTaskで揃った時だけである。未知capability、未知type/version、index外file、missing／extra／checksum改変へfallbackしない。予約名だけのcapabilityをmanifestへ書かない。

### Paper apply

Release 2 Paper applyの実装を開始できるのは、V2-5統合Taskが完了し、offline Release 2 self-verify、3D schematic read-back、corruption validationが通った後だけである。実world mutationを有効化できるのは、placement contract、mutation／effect envelope、region／disk reservationとbound confirmation、snapshot-all、fluid／gravity containment、apply、settle/full verify、rollback、Recoveryが揃い、対応runtimeの実機smokeを通過した後である。対応寸法を `paper_apply: SUPPORTED` にするには該当する実world計測Taskも必要である。Featureごとのplacement実装ではなくcanonical surface／solid／air／fluid streamを共通適用する。

## 8. 実機依存Task

WorldEdit／FAWE smokeは、指定versionの実機環境、十分なdisk、隔離world、操作権限が必要である。環境がなければ次のように扱う。

- mock、unit、offline read-backだけでTaskを完了扱いにしない。
- 状態は `BLOCKED_EXTERNAL` とし、必要環境、実行手順、収集すべき証拠を記録する。
- 後続の実測依存Taskと親Phase統合Taskへ進まない。
- 別環境の過去結果や異なるbuild／FAWE versionを流用しない。
- Beta hardeningの未完項目も独立して残し、v2の類似smokeで自動的に閉じない。

`V2-6-16`（500×500）／`V2-6-17`（1000×1000）実world計測は無効化（CANCELLED）である。再実測が必要なら新Task IDを追加し、未測定寸法をcatalog `SUPPORTED`へ上げない。再実測Taskは`V2-11-04`（500）／`V2-11-05`（1000）として登録済みで、専用高memory実測ホスト、Gradleから分離した単独Paper JVM、RCON、server PID単独のheap／RSS／GC計測、`V2-11-02` measurement profileを前提とする。

## 9. 停止条件

各Task固有条件に加え、次では作業を停止する。

- 前提Taskまたは必要なPhase gateが未完。
- 現在のコード、Schema、docsの意味がTask contractと矛盾し、互換性を保った一意な解決ができない。
- v1 golden、Release 1 strict verify、placement／Undo互換を破る必要がある。
- hard constraintを推測、fallback、last-write-winsで解決する必要がある。
- resource上限を事前に安全に定義できない。
- security boundaryを弱める必要がある。
- 実機、外部資格情報、Provider capability、依存versionが不足する。
- 予定外の大規模subsystemまたは独立artifact formatが必要になる。
- clean buildを維持できない。

停止時は未完を完了と書かず、blocking evidence、影響、追加Task案だけを報告する。
