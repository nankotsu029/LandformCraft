# V2-12-05 v1→v2 カバレッジ監査 と ADR 0035 D10 command inventory

> Status: **STOPPED（未承認のカバレッジ劣化を検出）→ 解消方針決定済み（案A、ADR 0035 rev.4 D11）**。実施日 2026-07-21。対象commit: `fa37e1e` 時点のworking tree。
> 正本関係は [ADR 0035](../../adr/0035-v1-retirement-governance.md) D0 に従う。本監査は `V2-12-05` のScope成果物であり、default routing切替の**前提条件**である。

## 1. 目的

`V2-12-05` は CLI／Paper の既定経路を v2 へ切替え、v1 を明示 opt-in の deprecated 経路へ移す。Task Index の Gate は次を要求する。

* 既定操作が全て v2 で完走する
* v1 が opt-in 化される
* **カバレッジ監査で劣化なし**（または ADR 0035 D6 で明示承認済み）
* ADR 0035 D10 command inventory（削除する verb と維持する verb の**両方**）が揃う
* 人間承認

加えて ADR 0035 D5 は、tagged release が存在しない場合の deprecation window 条件1の代替として、`V2-12-05` が「**v1経路にしか存在しない利用可能機能はゼロ**」を証拠付きで確認することを求める。`git tag` は0件であるため、この代替判定が必須である。

本監査はこの2つの判定を実施し、**いずれも不成立**であることを確認した。

## 2. 方法

1. Paper adapter (`paper/LandformCraftCommand.onCommand`) と CLI (`cli/LandformCraftCli.run`) の dispatch 分岐を全件列挙した。
2. 各 verb の backend を特定し、ADR 0035 D2a の削除行（R1〜R8）／D3 の維持行（K1〜K5）へ分類した。
3. 各 v1 verb に対する v2 等価物を、`core.v2.command.V2CommandVerbV2` の verb 表と `paper/PaperV2WorkflowServiceV2` / `core.v2.command.V2WorkflowServiceV2` の公開メソッドから照合した。
4. ADR 0035 D2a-R5 の追加条件（provider 設定 key、`ProviderFailureCode` への error mapping、retry、quota、audit 記録の同等性）を個別に検査した。
5. 等価物が存在しない機能を「劣化候補」とし、D6 の明示承認範囲（WorldEdit 単独 runtime の**寸法のみ**）に該当するか判定した。

コードは変更していない。本監査は read-only である。

## 3. ADR 0035 D10 command inventory（完全版）

D10 の表は代表 verb のみを載せているため、本節が Paper／CLI の**全** verb を対象とした完全 inventory である。`V2-12-06` の R6 は、本表で「維持」または「legacy 専用」とされた verb を削除してはならない。

### 3.1 Paper `/lfc <verb>`

| v1 verb | backend | ADR分類 | v2 等価物 | `V2-12-05` 後の予定 | `V2-12-06` 後 |
|---|---|---|---|---|---|
| `help` | 表示のみ | K4 | 同一 | 維持（version非依存） | 維持 |
| `version` | 表示のみ | K4 | 同一 | 維持（version非依存） | 維持 |
| `doctor` | `DoctorService` (K2) ＋ Release 2 path 表示 | K4 | 部分的（既に v2 path を表示） | 維持。v1／v2 双方の state を診断 | 維持（v2＋legacy migration 診断） |
| `ops metrics\|diagnose` | `OperationalOperationsServiceV2` | v2専用 | 同一 | 維持 | 維持 |
| `asset validate\|import\|list\|info\|remove` | `CustomAssetService` | **K3（削除対象から明示除外）** | **なし（ADRが容認）** | 維持 | **維持。R6 の削除対象外** |
| `selection` | `worldedit/` 共通 adapter | K2 | なし（v2 は座標明示） | 維持（version中立の入力補助） | 維持 |
| `job status\|cancel` | `FileGenerationJobRepository` (R4) | K4（verb名のみ中立） | **なし** — v2 offline export は同期実行で job store を持たない | **F3 参照（劣化）** | D8 drain 後に v2 または archive 表示 |
| `cleanup plan\|execute\|status` | `SnapshotCleanupService` (R3) | R3 | `core.v2.operations.Release2RetentionServiceV2`（**command 未接続**） | **F2 参照（未接続）** | v2 retention へ接続 |
| `request create` | `PaperWorkflowService` (R4) | R4 | **なし** | **F1 参照（劣化）** | — |
| `request bounds <id> ...` / `request bounds selection <id>` | `PaperWorkflowService` (R4) ＋ selection | R4 | **なし** | **F1 参照（劣化）** | — |
| `request prompt <id>`（chat 1件を prompt として取込） | `PaperWorkflowService` (R4) ＋ `AsyncChatEvent` | R4 | **なし** | **F1 参照（劣化）** | — |
| `request list` | `PaperWorkflowService` (R4) | R4 | **なし** | **F1 参照（劣化）** | — |
| `request validate\|info <id>` | `PaperWorkflowService` (R4) | R4 | `v2 request validate\|info <path>`（**id ではなく path**） | v2 既定（意味が id→path へ変わる） | v2 のみ |
| `design ...` | `TerrainDesignApplicationService` (R4) | R4 | `v2 design` | v2 既定、v1 opt-in | v2 のみ |
| `generate <request-id>`（非同期 job → candidate） | `PaperWorkflowService` (R4) | R4 | `v2 generate`（**同期・path 入力・job 無し**） | **F3 参照（劣化）** | v2 のみ |
| `candidate list\|info\|preview\|validate` | `PaperWorkflowService` (R4) | R4 | 部分的（`v2 generate` の Release 2 directory ＋ `v2 preview`）。**candidate store と request 単位の列挙は無い** | **F3 参照（劣化）** | — |
| `export plan\|create\|status\|verify\|info\|list` | `ReleaseApplicationService` (R4)／`ReleasePublisher` (R2) | R2/R4 | `v2 export`（**単発・token 確認と job 無し**） | **F3 参照（劣化）** | v2 のみ |
| `apply plan\|execute\|status\|undo` | `PlacementApplicationService` (R3) | R3 | `v2 place plan\|confirm\|execute`／`v2 status`／`v2 undo plan` | v2 既定、v1 opt-in | v2 のみ |
| `undo execute <id> <token>` | `PlacementApplicationService` (R3) | R3 | `v2 undo execute` | v2 既定、v1 opt-in | v2 のみ |
| `apply recover status\|diagnose\|rollback\|accept` | `PlacementApplicationService` (R3) | K4（verb名）／R3（backend） | `v2 recover diagnose\|plan\|execute` | v2 既定、v1 opt-in | v2 のみ |
| `v2 <verb>` | `V2CommandRouterV2` | v2 | — | 正式名（維持） | **恒久 alias として維持** |
| `r2 <op>` | 同上（deprecated alias） | v2 | — | deprecated alias 維持 | 削除 |

### 3.2 CLI `lfc <verb>`

| v1 verb | ADR分類 | v2 等価物 | 備考 |
|---|---|---|---|
| `validate <request.yml> <intent.json>` | R4 | `v2 request validate`（request のみ。intent の単独検証は `v2 design` 経由） | 部分的 |
| `design <import\|fixture\|openai\|anthropic> ...` | R4/R5 | `v2 design` | 等価 |
| `design-verify <dir>` | **D2b（legacy reader。削除しない）** | `DesignArtifactVerifierV2` | 維持 |
| `generate` / `preview` | R4 | `v2 generate` | 等価（baseline 明示が追加要件） |
| `export` | R2/R4 | `v2 export` | 等価 |
| `verify <release>` | **D2b（legacy verifier へ縮退）** | `ReleaseSurfaceVerifierV2` | 維持 |
| `journal-verify` | R3 | v2 journal 検証は placement service 内部 | **command 未接続（F2 と同種）** |
| `request create\|bounds\|prompt\|validate\|info\|list` | R4 | `v2 request validate\|info` のみ | **F1 参照（劣化）** |
| `candidate list\|info\|preview\|validate` | R4 | 部分的 | **F3 参照（劣化）** |
| `job status\|cancel` | K4／R4 | **なし** | **F3 参照（劣化）** |
| `recovery list\|status\|diagnose` | K4／R3 | `v2 recover diagnose`（Paper 専用） | CLI から v2 recovery を read-only 参照する経路が無い |
| `asset ...` | **K3** | なし（ADRが容認） | 維持 |
| `doctor` / `version` | K4 | 同一 | 維持 |
| `v2 <verb>` / `v2 migrate ...` | v2 | — | 正式名（維持） |

## 4. 検出したカバレッジ劣化

D6 が明示承認しているのは **WorldEdit 単独 runtime の寸法のみ**である。以下はいずれもその範囲外であり、**未承認の劣化**である。

### F1. in-game／CLI の request authoring 経路が v2 に存在しない（重大）

v1 は request を**作成・編集**できる。

* `request create <id>` — 既定 bounds の request を新規作成する
* `request bounds <id> ...` / `request bounds selection <id>` — bounds を設定する。Paper 版は **WorldEdit selection から直接** bounds を取り込む
* `request prompt <id>` — Paper では次の chat 1件を prompt として取り込む（5分失効、`cancel` で取消、secret 検査あり）。CLI では引数から設定する
* `request list` — 保存済み request を列挙する

v2 の request 経路は `V2CommandVerbV2.REQUEST_VALIDATE` / `REQUEST_INFO` のみで、いずれも**既存 `generation-request-v2.json` を strict read して検査するだけ**である（`PaperV2WorkflowServiceV2.inspectRequest`、`V2WorkflowServiceV2.inspectRequest`）。作成・編集・列挙・selection 取込・chat prompt 取込のいずれも v2 に存在しない。

backend の `createRequest` / `setBounds` / `setPrompt` / `requestList` は `core/PaperWorkflowService`（**R4＝削除対象**）にのみ存在する。したがって「v1経路にしか存在しない利用可能機能」に該当する。

**影響**: v2 既定へ切替えると、運用者は外部エディタで `generation-request-v2.json` を手書きしない限り request を用意できない。`V2-12-06` で R4 を削除すると機能自体が失われる。

### F2. v1 にあり v2 command に未接続の運用機能（中）

* `cleanup plan|execute|status` — v1 は `SnapshotCleanupService`（R3）を command から実行できる。v2 側には `core.v2.operations.Release2RetentionServiceV2` と `RetentionCleanupPortV2` が**存在するが、`ops` verb は `metrics` と `diagnose` しか公開していない**。retention cleanup を実行する v2 command が無い。
* `journal-verify`（CLI） — v2 journal を CLI から検証する verb が無い。
* `recovery list|status|diagnose`（CLI read-only） — v2 recovery は Paper 専用のため、CLI から v2 の recovery 状態を read-only 参照できない。

F2 は実装が既に存在するため、**command 接続だけで解消できる**（Task Index の「D10維持verbのv2 backend接続」に含まれる作業）。ただし `V2-12-05` の Scope 内で `ops retention` 相当の新 verb を追加するのが妥当かは、`非Scope: 新機能` との境界判断が必要である。

### F3. 非同期 job／candidate／export lifecycle が v2 に存在しない（重大）

v1 の Paper 経路は `generate <request-id>` → job → candidate → `export plan` → token 確認 → `export create` という**非同期・確認付きの多段 lifecycle** を持ち、`job status|cancel` で監視・取消できる。

v2 の `generate` / `export` は `Release2ExportApplicationServiceV2` を**同期的に1回呼ぶ**形で、job ID も candidate store も確認 token も無い。`V2CommandVerbV2` に `job` verb は存在しない。

**影響**:
* 長時間の v2 export を Paper から取消・監視できない（`job cancel` 相当が無い）。
* 同一 request から複数候補を生成し比較する運用（`candidate list <request-id>`）ができない。
* D10 表は `job` を「v1／v2 dispatch」と記載しているが、**dispatch 先の v2 backend が存在しない**ため、`V2-12-05` の「D10維持verbのv2 backend接続」を `job` について履行できない。

### F4. provider failure code の粒度低下（軽微・要判断）

ADR 0035 D2a-R5 の追加条件（provider 設定 key、error mapping、retry、quota、audit の同等性）を検査した結果は次のとおりである。

| 項目 | v1 | v2 | 判定 |
|---|---|---|---|
| 設定 key | `ProviderSettings`（K2）、`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | 同一（`LandformCraftCli.requireEnvironment`、`Landformcraft` の設定読取） | **同等** |
| retry | `TerrainDesignPolicy.maxAttempts`（1〜10） | 同一 policy を共有（`AbstractHttpTerrainDesignProviderV2` が `TerrainDesignPolicy` を受ける） | **同等** |
| quota | `ProviderQuota(policy, clock)` ＋ `quota.record(payload.usage())` | 同一クラス・同一呼出 | **同等** |
| audit | `DesignAudit`（requestId／providerId／attempts／intentChecksum 等） | `DesignAuditV2`（同項目＋contract version。`attempts` 1〜10 の検証も同一） | **同等以上** |
| error mapping | `ProviderFailureCode` **9値** | `DesignFailureCodeV2` **4値**へ collapse（`AUTHENTICATION`＋`INVALID_REQUEST`→`INVALID_REQUEST`、`RATE_LIMITED`＋`LOCAL_RATE_LIMIT`＋`TOKEN_BUDGET_EXCEEDED`→`BUDGET_EXCEEDED`、`TIMEOUT`＋`SERVER_ERROR`＋`TRANSPORT_ERROR`→`PROVIDER_TRANSPORT`） | **粒度低下** |

元の `ProviderFailureCode` は例外の cause chain に残るが、運用者に見える code と message（`"provider transport failed"` 固定）からは復元できない。認証失敗と request 不正、timeout と server error、rate limit と token budget 超過が区別できない。

D2a-R5 は「error mapping が v2 側で**同等であること**」を削除の追加条件としているため、この粒度低下を（a）許容と判断して ADR へ記録するか、（b）`DesignFailureCodeV2` を拡張するか、いずれかの決定が必要である。**`V2-12-05` はこの決定を単独で下せない**（`DesignFailureCodeV2` は error code であり、ADR 0035 D4 が canonical checksum 影響識別子として凍結対象に挙げている）。

## 5. 判定

### 5.1 Gate 判定

| Gate 条件 | 結果 |
|---|---|
| D10 command inventory が揃う | **達成**（本書 §3） |
| v1→v2 カバレッジ監査を実施 | **達成**（本書 §4） |
| カバレッジ監査で劣化なし、または D6 で明示承認済み | **不成立**（F1／F3 は重大、F2／F4 は要判断。いずれも D6 の承認範囲＝WorldEdit 単独 runtime の寸法に該当しない） |
| 既定操作が全て v2 で完走 | **未実施**（劣化が未承認のため routing を切替えていない） |
| v1 が opt-in 化 | **未実施**（同上） |
| D10 維持 verb の v2 backend 接続 | **不能**（`job` の v2 backend が存在しない — F3） |
| 人間承認 | **未取得** |

### 5.2 D5 条件1の代替判定

`git tag` は0件であるため、代替判定「v1経路にしか存在しない利用可能機能はゼロ」が必須である。

**不成立。** F1（request authoring）と F3（job／candidate／export lifecycle）は、いずれも R4 削除対象の `PaperWorkflowService` / `TerrainDesignApplicationService` / `ReleaseApplicationService` にのみ実装があり、v2 に等価物が無い。

なお K3（`asset`）は ADR が削除対象から明示除外した**維持対象**であり、退役対象の「v1経路」には含まれないと解釈した。K2（`selection`、`Sha256` 等）と D2b（`verify`、`design-verify`）も同様に維持対象であるため、本代替判定の対象外とした。この解釈が誤りであれば、代替判定はさらに広い範囲で不成立となる。

## 6. 停止

ADR 0035 D3-K3 は「**`V2-12-05` で他の未カバー機能が判明した場合は停止し、本ADRを amend する**」と定めている。D6 も「これ以外の機能・寸法劣化が見つかった場合、`V2-12-05` は停止し本ADRの amendment を取得する」と定めている。

したがって本 Task は **STOPPED** とし、default routing の切替、`--v1` / `/lfc v1` opt-in の実装、user docs 全面更新、CHANGELOG 更新は**実施しない**。`V2-12-06` へも進まない。

## 7. 解消案と決定

> **決定済み（2026-07-21、nankotsu029）: 案A を採用。** [ADR 0035](../../adr/0035-v1-retirement-governance.md) rev.4 の D11 として正本化した。F1／F2／F3 は劣化を承認せず、`V2-12-08`（v2 request authoring）／`V2-12-09`（v2 job・candidate・export lifecycle）／`V2-12-10`（v2 運用verbの接続）の3 Task で v2 側へ実装して解消する。F4 のみ D6 へ明示承認として追記し、`DesignFailureCodeV2` は拡張しない。
>
> これに伴い rev.4 は D2a の R4 追加条件へ `V2-12-08`＋`V2-12-09` の完了を、R3 追加条件へ `V2-12-10` の完了を加えた（削除対象集合 R1〜R8 は不変）。`V2-12-05` は `V2-12-08`〜`V2-12-10` 完了後に再開し、**本監査を最初からやり直す**。前回結果を再利用して「解消済み」と記録しない。

以下は決定時に検討した選択肢である。

| 案 | 内容 | 影響 |
|---|---|---|
| **A（採用）** | **v2 側へ不足機能を実装する**新 Task を追加してから `V2-12-05` を再開する。`V2-12-08`（v2 request authoring: create／bounds／selection 取込／prompt／list）、`V2-12-09`（v2 job／candidate／export lifecycle と `job` verb backend）、`V2-12-10`（retention cleanup／CLI 側 v2 recovery・journal 検証 verb） | 最も安全。`V2-12` の Task 数が 7→10 へ増える |
| B | 劣化を ADR 0035 D6 へ**明示承認として追記**し、当該機能を v2 で提供しないと決める（`request` は外部エディタ前提、job／candidate 運用は廃止） | 実装量は最小だが、ユーザー可視の機能後退を恒久化する |
| C | A と B の併用。F1（request authoring）は A で実装し、F3 の candidate 比較運用は B で廃止承認、F3 の `job cancel` 相当だけを実装する | 現実的だが切分けの根拠を ADR へ明記する必要がある |
| **—（F4: (a) 採用）** | F4 は独立に決定した。（a）粒度低下を D2a-R5 の「同等」の範囲として D6 へ明示承認追記する。（b）`DesignFailureCodeV2` の拡張は不採用 — D4 の凍結識別子であり、契約変更と docs／test 同期の費用が運用上の影響に見合わない。元 code は例外の cause chain に残り管理者ログで追跡できる | rev.4 の D6 へ記録済み |

いずれの案でも、決定後に F2（`ops retention` 等の未接続 backend）の command 接続を `V2-12-05` 再開時の Scope に含める。

## 8. 参照

* [ADR 0035 v1 retirement governance](../../adr/0035-v1-retirement-governance.md) — D2a／D3／D5／D6／D10
* [Task index V2-12-05](../task-index.md)
* [V2-12-03 v2 command path evidence](v2-12-03-v2-command-path-evidence.md)
* `src/main/java/com/github/nankotsu029/landformcraft/core/v2/command/V2CommandVerbV2.java` — v2 verb 表
* `src/main/java/com/github/nankotsu029/landformcraft/core/PaperWorkflowService.java` — F1／F3 の v1 backend
