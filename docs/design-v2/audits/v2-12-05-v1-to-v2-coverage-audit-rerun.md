# V2-12-05 v1→v2 カバレッジ監査（再実行）と ADR 0035 D10 command inventory

> Status: **PASS（未承認のカバレッジ劣化なし）**。再実行日 2026-07-21。対象commit: `V2-12-10` 完了時点のworking tree。
> 正本関係は [ADR 0035](../../adr/0035-v1-retirement-governance.md) D0 に従う。本監査は `V2-12-05`（再開）のScope成果物であり、default routing切替の**前提条件**である。task-index の指示どおり、前回監査（[初回](v2-12-05-v1-to-v2-coverage-audit.md)）の結果は再利用せず、現在のコードに対して最初からやり直した。

## 1. 目的と前回からの差分

前回（2026-07-21・初回）は D6 承認範囲外の劣化を4件（F1〜F4）検出して STOP した。ADR 0035 rev.4 D11 が案A（劣化を承認せず v2 実装で解消）を承認し、`V2-12-08`〜`V2-12-10` を追加した。本再実行は、それらの完了後に F1〜F4 が実際に解消したかを現在のコードで確認する。

判定基準（前回と同一）:

* v1 で可能だった操作が v2 で同等以上に実行できる。
* 差が残る場合、それが ADR 0035 D6 の明示承認範囲（WorldEdit 単独 runtime の寸法 / provider failure code の粒度）に収まる。
* `git tag` は **0件**（確認済み）であるため、D5 条件1の代替判定「v1経路にしか存在しない利用可能機能はゼロ」を証拠付きで満たす必要がある。

## 2. 方法

1. `paper/LandformCraftCommand.onCommand` と `cli/LandformCraftCli.run` の dispatch を全件列挙した。
2. `core.v2.command.V2CommandVerbV2` の現行 verb 表（36 constant）と、CLI/Paper のハンドラ実装から v2 等価物を照合した。
3. 前回の F1〜F4 それぞれについて、解消 Task（`V2-12-08`/`09`/`10`、D6 rev.4）の成果物が現行コードに存在するかを確認した。
4. D2a-R5 の provider 同等性（設定 key / failure mapping / retry / quota / audit）を再検査した。

本監査は read-only。コードは変更していない。

## 3. 前回検出項目の解消確認

| # | 前回の劣化 | 解消Task | 現行の v2 等価物 | 判定 |
|---|---|---|---|---|
| F1 | request authoring（create/bounds/selection/prompt/list）が v2 に無い | `V2-12-08` | `V2CommandVerbV2` の `REQUEST_CREATE`/`REQUEST_BOUNDS`/`REQUEST_SELECTION`(Paper)/`REQUEST_PROMPT`(Paper)/`REQUEST_PROMPT_INLINE`(CLI)/`REQUEST_CONSTRAINT_MAP`/`REQUEST_LIST`。backend は `core.v2.command.V2RequestStoreV2`。authoring→`v2 export` の E2E あり | **解消** |
| F2 | retention cleanup / journal-verify / recovery(read-only) が command 未接続 | `V2-12-10` | `RETENTION_PLAN`/`EXECUTE`/`STATUS`(Paper、`Release2RetentionServiceV2` 接続)、`JOURNAL_VERIFY`(CLI)、`RECOVERY_INSPECT`(CLI)。本番配線は `Release2PlacementApplicationServiceV2.retentionCleanupPort()` を `Landformcraft` で接続 | **解消** |
| F3 | 非同期 job→candidate→確認付き export lifecycle が v2 に無い | `V2-12-09` | `JOB_STATUS`/`CANCEL`/`LIST`、`CANDIDATE_LIST`/`INFO`、`EXPORT_PLAN`/`EXPORT_CREATE`(Paper)。backend は `core.v2.job.ExportJobServiceV2`/`ExportJobStoreV2`/`ExportPlanStoreV2`。cancel は commit point 前に停止し公開物ゼロ | **解消** |
| F4 | `ProviderFailureCode` 9値 → `DesignFailureCodeV2` 4値の粒度低下 | D6 rev.4 で明示承認 | `AbstractHttpTerrainDesignProviderV2.mapFailure` は現行も 9→4 collapse。ADR 0035 D6 rev.4 が **surface される error code の粒度**に限って明示承認済み | **承認済み劣化（D6 範囲内）** |

## 4. D2a-R5 provider 同等性（再検査）

| 項目 | v1 | v2 | 判定 |
|---|---|---|---|
| 設定 key | `ProviderSettings`、`OPENAI_API_KEY`/`ANTHROPIC_API_KEY` | 同一 | 同等 |
| retry | `TerrainDesignPolicy.maxAttempts`（1..10） | 同一 policy を共有 | 同等 |
| quota | `ProviderQuota(policy, clock)` | 同一クラス・同一呼出 | 同等 |
| audit | `DesignAudit` | `DesignAuditV2`（同項目＋contract version） | 同等以上 |
| error mapping | `ProviderFailureCode` 9値 | `DesignFailureCodeV2` 4値へ collapse | **D6 rev.4 で明示承認済みの劣化**（範囲: surface code 粒度のみ） |

設定 key・retry・quota・audit は同等以上。error mapping の粒度低下は D6 が承認した唯一の provider 劣化であり、範囲外の provider 劣化は検出されなかった。

## 5. D5 条件1の代替判定（tagged release が無い場合）

`git tag` は 0件。したがって「v1経路にしか存在しない利用可能機能はゼロ」を確認する。

現行の全 v1 verb を、v2 等価物・維持理由で分類した完全 inventory は §7。結論として、**退役対象（ADR D2a R1〜R8）の v1 verb はすべて v2 に同等以上の経路を持つ**。v2 に無いまま残るのは、ADR が削除対象から明示除外した維持対象だけである:

* `asset`（K3。v2 等価物が無いことを ADR が容認。default 面に維持）。
* `selection`（K2。version 中立の WorldEdit 入力補助）。
* `doctor`/`version`/`help`（K4。version 非依存）。
* `verify`/`design-verify`（D2b。legacy reader/verifier。migration 用に維持）。

これらは「v1経路にしか存在しない**退役対象**機能」ではない（K/D2b の維持対象）。したがって代替判定は **成立**。

## 6. 判定

| Gate 条件 | 結果 |
|---|---|
| D10 command inventory が揃う | **達成**（§7） |
| v1→v2 カバレッジ監査を実施 | **達成**（本書） |
| カバレッジ監査で劣化なし、または D6 承認範囲内 | **達成**（F1〜F3 解消、F4 は D6 承認範囲内） |
| D5 条件1の代替判定 | **成立**（退役対象の v1 専用機能ゼロ） |
| 人間承認 | **別途必要**（default 切替は user-visible。本監査は承認を代替しない） |

**未承認の劣化は無い。** 前回の STOP 条件（D3-K3 / D6）はすべて解消した。default routing 切替へ進める。

## 7. ADR 0035 D10 command inventory（switchover 後の最終形）

`V2-12-05` 後の default は v2、v1 は明示 opt-in（Paper `/lfc v1 <verb>`、CLI `lfc --v1 <verb>`）。`V2-12-06` で v1 経路と `r2` alias を削除する。

### 7.1 Paper `/lfc`

| 現行 v1 verb | 分類 | `V2-12-05` 後の default | v1 opt-in | `V2-12-06` 後 |
|---|---|---|---|---|
| `help` / `version` | K4 | 同一（version 中立） | — | 維持 |
| `doctor` | K4 | v1+v2 双方を診断（同一） | — | 維持 |
| `ops metrics\|diagnose` | v2 | 同一 | — | 維持 |
| `asset …` | **K3** | 同一（v2 等価物なし。ADR 容認） | — | **維持（R6 対象外）** |
| `selection` | K2 | 同一（version 中立） | — | 維持 |
| `request …` | R4 | **v2 request**（V2-12-08） | `/lfc v1 request` | v2 のみ |
| `design …` | R4/R5 | **v2 design** | `/lfc v1 design` | v2 のみ |
| `generate <id>` | R4 | **v2 generate / export plan→create**（V2-12-09） | `/lfc v1 generate` | v2 のみ |
| `job …` | R4→v2 | **v2 job**（V2-12-09） | `/lfc v1 job` | v2 のみ（D8 後 archive） |
| `candidate …` | R4→v2 | **v2 candidate**（V2-12-09） | `/lfc v1 candidate` | v2 のみ |
| `export …` | R2/R4→v2 | **v2 export / export plan→create** | `/lfc v1 export` | v2 のみ |
| `apply plan\|execute\|status\|undo` | R3 | **v2 place / status / undo**（verb 名は `place`） | `/lfc v1 apply` | v2 のみ |
| `undo execute` | R3 | **v2 undo execute** | `/lfc v1 undo` | v2 のみ |
| `apply recover …` | R3 | **v2 recover**（Paper） | `/lfc v1 apply recover` | v2 のみ |
| `cleanup …` | R3 | **v2 retention**（V2-12-10） | `/lfc v1 cleanup` | v2 のみ |
| `v2 <verb>` | v2 | 正式名（維持） | — | 恒久 alias |
| `r2 <op>` | v2 | deprecated alias | — | 削除 |

### 7.2 CLI `lfc`

| 現行 v1 verb | 分類 | `V2-12-05` 後の default | v1 opt-in |
|---|---|---|---|
| `version` / `doctor` | K4 | 同一 | — |
| `asset …` | K3 | 同一 | — |
| `validate <req> <intent>` | R4 | `v2 request validate`（request のみ。intent は `v2 design` 経由） | `lfc --v1 validate` |
| `verify <release>` | D2b | legacy verifier | `lfc --v1 verify` |
| `design-verify <dir>` | D2b | legacy verifier | `lfc --v1 design-verify` |
| `journal-verify <path>` | R3→v2 | **v2 journal-verify**（V2-12-10、同一文法） | `lfc --v1 journal-verify` |
| `design …` | R4/R5 | **v2 design** | `lfc --v1 design` |
| `generate` / `preview` | R4 | **v2 generate**（baseline 明示が追加要件） | `lfc --v1 generate` |
| `export` | R2/R4 | **v2 export** | `lfc --v1 export` |
| `request …` | R4 | **v2 request**（authoring は V2-12-08） | `lfc --v1 request` |
| `candidate …` | R4→v2 | **v2 candidate** | `lfc --v1 candidate` |
| `job …` | R4→v2 | **v2 job** | `lfc --v1 job` |
| `recovery …` | R3→v2 | **v2 recovery inspect**（read-only、V2-12-10） | `lfc --v1 recovery` |
| `v2 <verb>` / `v2 migrate …` | v2 | 正式名（維持） | — |

**v1 のみに残る利用可能機能（退役対象 R1〜R8）はゼロ。** `verify`/`design-verify` は D2b legacy reader（維持対象）で退役対象ではない。

## 8. 参照

* [ADR 0035](../../adr/0035-v1-retirement-governance.md) — D2a/D2b/D3/D5/D6/D10/D11
* [初回監査（STOP）](v2-12-05-v1-to-v2-coverage-audit.md)
* `core.v2.command.V2CommandVerbV2` — 現行 v2 verb 表
