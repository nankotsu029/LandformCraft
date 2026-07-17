# Codex Skills

## 正本docsとの関係

`.agents/skills/` は反復workflowだけを定義する。常時規則は `AGENTS.md`、進捗は `docs/roadmap.md`、TaskのScope／非Scope／Acceptance／停止条件は `docs/design-v2/task-index.md` を正本とする。Skill本文へ現在の次Task、完了件数、Phase状態、将来変わるversionや個別parameterを固定しない。

## Skill一覧

| Skill | 用途 | 明示呼び出し例 |
|---|---|---|
| `landformcraft-execute-v2-task` | 現在または指定V2 Taskを1件だけAcceptanceまで実装する | `$landformcraft-execute-v2-task で現在の次Taskを実装してください` |
| `landformcraft-guard-v1-compatibility` | v1 Schema、generator、Release 1、placement、Undoの互換性を監査する | `$landformcraft-guard-v1-compatibility でこのdiffのv1回帰を確認してください` |
| `landformcraft-audit-determinism-resources` | whole/tile/thread/order差、checksum、memory/CPU/artifact、cancel cleanupを監査する | `$landformcraft-audit-determinism-resources でこのkernelを監査してください` |
| `landformcraft-verify-release-artifacts` | strict Schema/read-back、semantic checksum、directory/ZIP、tampering、atomic publishを検証する | `$landformcraft-verify-release-artifacts でRelease 2 verifierを監査してください` |
| `landformcraft-build-validation-preview` | 独立validator、corruption fixture、stable metric、bounded previewを実装・監査する | `$landformcraft-build-validation-preview で対象featureの診断を実装してください` |
| `landformcraft-audit-phase-gate` | 明示されたPhase末尾Taskで全証拠と `SUPPORTED` 昇格を監査する | `$landformcraft-audit-phase-gate で指定Phaseのgateを監査してください` |
| `landformcraft-review-v2-placement-safety` | V2-6 Release 2配置のenvelope、reservation、snapshot-all、verify、rollbackを監査する | `$landformcraft-review-v2-placement-safety で指定V2-6 Taskを監査してください` |

## Skill間の境界

- Task実行を入口にし、対象Task内の専門workflowだけを互換、決定性、Release、validation、配置Skillへ委ねる。
- v1互換Skillは既存契約を守る。Release Skillはartifact/containerのstrictnessを守る。両方が必要なら併用する。
- 決定性Skillは実行matrixとresource/cancelを扱う。validation Skillは観測の独立性、metric、previewを扱う。
- Phase gate Skillは証拠を統合して完了可否を判断する。feature実装や大規模修正を引き受けない。
- 配置SkillはV2-6 Release 2だけを扱う。v1配置の回帰は互換Skill、offline ReleaseはRelease Skillを使う。
- `landformcraft-audit-phase-gate` はimplicit invocationを無効にし、明示呼び出しなしで起動させない。

## Skill選択境界テスト

各行はdescriptionだけを一覧表示した状態で期待する選択結果を示す。「読む正本」は対象に応じた関連docs／Schema／code／testと作業前の `git status --short` を含む。

| Skill | 起動すべきprompt | 起動すべきでないprompt | 隣接Skillと競合するprompt／期待 | 使う場合に追加で読む正本 | 使わない場合との差 |
|---|---|---|---|---|---|
| `landformcraft-execute-v2-task` | 「roadmapの次のV2 TaskをAcceptanceまで実装」 | 「v1 CLIのtypoだけ修正」 | 「V2 TaskのRelease capabilityを実装」→Task実行を主、Release検証を併用 | roadmap、Task Guide、対象Task節、関連設計／ADR／Schema／code／test | 1 Task限定、非Scope、停止条件、docs同期を強制する |
| `landformcraft-guard-v1-compatibility` | 「v2変更がv1 checksumとUndoを壊さないか監査」 | 「新しいv2 beach validatorを実装」 | 「Release 2追加とRelease 1回帰」→Release検証と併用 | migration plan、v1 Schema/examples、golden、Release 1、placement tests | v1の意味とblock streamまで明示的に凍結する |
| `landformcraft-audit-determinism-resources` | 「whole/tile/thread/localeと1000角memoryを監査」 | 「通常のunit testを1件実行」 | 「preview checksumとrender peak」→validationを主、決定性を併用 | pipeline、Blueprint、対象kernel／budget tests | 実行matrix、合算peak、cancel commit pointまで検査する |
| `landformcraft-verify-release-artifacts` | 「Release 2 directory/ZIP tamperingとatomic publishを実装」 | 「単一coastal featureを生成」 | 「validation artifactをReleaseへ追加」→Release検証を主、validationを併用 | artifact-format、security、migration、関連ADR／Schema／publisher／verifier | strict index、semantic binding、tampering corpusを要求する |
| `landformcraft-build-validation-preview` | 「generator-independent validatorとcorruption fixtureを追加」 | 「Release ZIP verifierだけ修正」 | 「validator結果をReleaseへ収容」→validationを主、Release検証を併用 | validation-and-preview、対象feature設計／Schema／query／renderer | 独立観測面、rule ID、stable metric、bounded layerを要求する |
| `landformcraft-audit-phase-gate` | 「`$landformcraft-audit-phase-gate` で指定Phase末尾Taskを監査」 | 「docsのStatusだけ修正」 | 「Phase途中featureをSUPPORTEDへ」→起動せずTask実行へ戻す | roadmap、対象Phase全Task、implementation roadmap、全証拠／audit | 明示起動だけで全Phase portfolioを判定し、欠損時は閉じない |
| `landformcraft-review-v2-placement-safety` | 「V2-6のeffect envelopeとsnapshot-allを実装」 | 「通常のPaper command test」 | 「Release 2 verifierとPaper apply」→配置を主、Release検証を併用 | V2-6 Task、migration／volume配置節、security／operations、gateway／journal | world mutation順序、full verify、rollback、外部実測gateを強制する |

明示呼び出しは一覧表の例どおり `$skill-name` を使う。単一featureでRelease Skill、通常docsでPhase gate Skill、v1小修正でV2 Task Skill、通常testで配置Skill、Phase途中でPhase gate Skillを選ばないことを回帰条件とする。

## 更新方針

- 正本docsの構造、Task contract、workflow境界が変わったときだけSkillを更新する。
- 進捗や個別Taskの値が変わっただけではSkillを更新せず、実行時に正本を読み直す。
- description変更時は上のpositive／negative／競合promptを再評価し、暗黙起動範囲を広げない。
- 新Skillは複数回使う単一workflowで、既存Skillと重複せず、維持costを上回る効果がある場合だけ追加する。
- Skillへ長い設計内容を複製せず、既存docsへの参照と短い手順・停止条件だけを置く。
