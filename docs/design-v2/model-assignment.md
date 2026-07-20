# Task実行モデル割当（Model Assignment）

> Status: 実行体制の正本。Task ScopeとAcceptanceの正本は [Task Index](task-index.md)、進捗は [docs/roadmap.md](../roadmap.md)。利用可能な契約: Codex（ChatGPT Plus）、Cursor Pro、Antigravity CLI（Gemini Pro系）、Claude Code（Claude Pro）、OpenAI API（少量）。

## 1. 原則

1. **通常Taskの二本柱**は Antigravity Gemini Pro と Claude Sonnet 5 High。Codexは高リスク領域（数学基盤・決定性保証・transaction・rollback・recovery・最終監査）専用とする。
2. **計画だけ難しいTask**は Claude Code `opusplan`（OpusでPlan、Sonnetで実装）。
3. **レビューは実装者と別系統**で行う。同じモデルに自分の実装を最終承認させない。
4. **昇格は失敗ベース**で固定する。最初から強いモデルを使わず、条件を満たしたときだけ上げる。
5. **1 Task = 1 Task capsule（`.task-context/V2-x-NN.md`、Git管理外）= 1新規セッション**。モデルとEffortはセッション開始時に決める。
6. **full suiteはPhase gate Task（V2-4-15 / V2-5-18 / V2-6-19 / V2-7-07 / V2-8-08 / V2-9-14 / V2-10-09 / V2-12-07）だけ**で実行する。通常Taskは対象テスト＋Task close前の`./gradlew test`/`build`まで。
7. **Claude Pro枠とAPI課金を分離**する。`ANTHROPIC_API_KEY`が設定されたままだとAPI課金になり得るため、セッション開始時に`/status`で認証元を確認する。
8. **使用量枯渇時**は上位モデルを全Taskへ広げず、高リスクTaskだけ従量課金（OpenAI API／追加クレジット）で継続する。

### 昇格条件（このラダー以外の飛び級をしない）

```text
Cursor／軽量モデル（Haiku・Sonnet Low）
  ↓ 仕様解釈が必要／同一原因で2回失敗
Antigravity Gemini Pro ／ Claude Sonnet 5 High
  ↓ contract・Schema互換・memory・concurrencyが争点
Claude Opus 4.8 High
  ↓ 複数不変条件・並行処理・Recovery設計
Claude Opus 4.8 XHigh
  ↓ 数学的保証・transaction・rollback実装
Codex Terra Extra High
  ↓ world transaction・journal・crash recovery
Codex Sol Max
  ↓ PhaseまたはRelease全体の最終監査
Codex Sol Ultra
```

### レビューマトリクス

| 実装者 | 第一レビュー | 高リスク時の第二レビュー |
|---|---|---|
| Antigravity Gemini Pro | Claude Sonnet 5 High | Codex Terra Extra High |
| Claude Sonnet 5 | Antigravity Gemini Pro | Codex Terra Extra High |
| Claude Opus 4.8 | Antigravity Gemini Pro | Codex Sol |
| Cursor Pro | Claude Sonnet 5 High | AntigravityまたはCodex |
| Codex Terra／Sol | Claude Opus 4.8（独立レビュー） | Antigravity（Acceptance確認）→人間 |

実機依存Task（smoke・実測）はCursor Pro＋人間を主担当とし、モデルにmock完了を許さない。

## 2. Task別割当表

Effort列はClaude Codeの`--effort`相当（Antigravity/Codexは各ツールの同等段階）。「主担当／レビュー」が原則で、停止条件・Acceptanceは[Task Index](task-index.md)が優先する。

### Track A: V2-4 Environment（残り10）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-4-06 | Snow/snowline | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-4-07 | Semantic material profile | Claude Sonnet 5 | High | Antigravity |
| V2-4-08 | Minecraft palette adapter | Claude Code `opusplan` | Plan=Opus/実装=Sonnet | Antigravity＋（palette checksum部）Codex Terra XH |
| V2-4-09 | Mangrove regional shaping | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-4-10 | Coral reef bathymetry | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-4-11 | Ecology placement | Claude Sonnet 5 | High | Antigravity |
| V2-4-12 | Volcanic/canyon material | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-4-13 | Environment validators/previews | Claude Sonnet 5 | High | Antigravity |
| V2-4-14 | Release 2 `environment-fields` | Claude Opus 4.8 | High | Codex Terra Extra High |
| V2-4-15 | Phase gate audit（full suite） | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋人間 |

### Track A: V2-5 Sparse volume（18）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-5-01 | Fixed-point SDF primitives | Claude Opus 4.8 | XHigh | Codex Terra Extra High |
| V2-5-02 | Ordered CSG | Claude Opus 4.8 | High | Antigravity |
| V2-5-03 | AABB spatial index | Claude Sonnet 5 | High | Antigravity |
| V2-5-04 | 3D tile cache（memory/concurrency） | Codex Terra Extra High | XHigh | Claude Opus 4.8 High |
| V2-5-05 | TerrainQuery volume support | Claude Opus 4.8 | High | Antigravity |
| V2-5-06 | Cave network | Claude Sonnet 5 | High | Antigravity |
| V2-5-07 | Lush cave | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-5-08 | Underground lake | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-5-09 | Sea cave | Claude Sonnet 5 | High | Antigravity |
| V2-5-10 | Overhang | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-5-11 | Natural arch | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-5-12 | Sky island group | Claude Sonnet 5 | High | Antigravity |
| V2-5-13 | Waterfall volume integration | Claude Code `opusplan` | Plan=Opus/実装=Sonnet | Antigravity |
| V2-5-14 | Post-volume environment/material | Claude Sonnet 5 | High | Antigravity |
| V2-5-15 | Volume validators/previews | Claude Sonnet 5 | High | Antigravity |
| V2-5-16 | Offline 3D schematic read-back | Claude Opus 4.8 | High | Codex Terra Extra High |
| V2-5-17 | Release 2 `sparse-volume` | Claude Opus 4.8 | High | Codex Terra Extra High |
| V2-5-18 | Phase gate audit（full suite） | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋人間 |

### Track A: V2-6 Placement（21）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-6-01 | Placement contract | Claude Opus 4.8 | XHigh | Antigravity |
| V2-6-02 | Mutation/effect envelope | Codex Terra Extra High | XHigh | Claude Opus 4.8 XHigh |
| V2-6-03 | Reservation/bound confirmation | Claude Opus 4.8 | XHigh | Codex Terra Extra High |
| V2-6-04 | Snapshot-all | Codex Sol Max | Max | Claude Opus 4.8 XHigh |
| V2-6-05 | Fluid/gravity containment preflight | Codex Terra Extra High | XHigh | Claude Opus 4.8 High |
| V2-6-06 | Apply transaction orchestration | Codex Sol Max | Max | Claude Opus 4.8 XHigh |
| V2-6-07 | Bounded settle / full verify | Codex Terra Extra High | XHigh | Claude Opus 4.8 High |
| V2-6-08 | Rollback | Codex Sol Max | Max | Claude Opus 4.8 XHigh |
| V2-6-09 | Undo | Codex Terra Extra High | XHigh | Claude Opus 4.8 High |
| V2-6-10 | Recovery | Codex Sol Max | Max | Claude Opus 4.8 XHigh |
| V2-6-11 | Provider/manual/image統合 | Claude Sonnet 5 | High | Antigravity |
| V2-6-12 | Cross-capability hardening | Codex Terra Extra High | XHigh | Claude Opus 4.8 High |
| V2-6-13 | Metrics/diagnostics/retention | Claude Sonnet 5 | High | Antigravity |
| V2-6-14 | WorldEdit smoke（実機） | Cursor Pro＋人間 | — | Claude Sonnet 5（ログ分析） |
| V2-6-15 | FAWE smoke（実機） | Cursor Pro＋人間 | — | Claude Sonnet 5（ログ分析） |
| V2-6-16 | 500×500実測 | —（無効化） | — | — |
| V2-6-17 | 1000×1000実測 | —（無効化） | — | — |
| V2-6-18 | Final supported catalog | Claude Sonnet 5 | High | Antigravity |
| V2-6-19 | RC audit（full suite） | Codex Sol Ultra | Max | 人間最終承認 |
| V2-6-20 | Verified Release 2 canonical block source | Claude Opus 4.8 | XHigh | Codex Terra Extra High＋Antigravity |
| V2-6-21 | Release 2 Paper application／command lifecycle | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋Antigravity＋人間 |
| V2-11-01 | Paper apply capability promotion（`V2-6-19`監査で追加） | Claude Sonnet 5 | High | Antigravity＋人間 |

### Track A後続: V2-11 Capability promotion追加分（2026-07-20再監査で追加）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-11-02 | R2 dimension guard／measurement profile（P0） | Claude Opus 4.8 | High | Antigravity＋（admission/security部）Codex Terra XH |
| V2-11-03 | Docs／Schema／example consistency sync | Claude Sonnet 5 | High | Antigravity |
| V2-11-04 | 500×500実測（専用高memoryホスト、FAWE 2.15.2／`run-fawe`単独） | Cursor Pro＋人間 | — | Claude Sonnet 5（telemetry／ログ分析） |
| V2-11-05 | 1000×1000実測（専用高memoryホスト、FAWE 2.15.2／`run-fawe`単独） | Cursor Pro＋人間 | — | Claude Sonnet 5（telemetry／ログ分析） |
| V2-11-06 | Measured dimension catalog promotion | Claude Sonnet 5 | High | Antigravity＋人間 |

### Track A後続: V2-12 V2 production path／v1 migration（7、2026-07-20再監査で追加）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-12-01 | v1 retirement governance ADR／policy改訂 | Claude Opus 4.8 | High | Antigravity＋人間承認必須 |
| V2-12-02 | Production Release 2 export path | Claude Opus 4.8 | High | Codex Terra Extra High |
| V2-12-03 | CLI／Paper v2 command routing／E2E | Claude Sonnet 5 | High | Antigravity＋（permission/security部）Codex Terra XH |
| V2-12-04 | v1→v2 migration tool | Claude Opus 4.8 | XHigh | Codex Terra Extra High |
| V2-12-05 | v2 default切替／v1 deprecation | Claude Sonnet 5 | High | Antigravity＋人間承認必須 |
| V2-12-06 | v1削除・名称正規化 | Codex Terra Extra High | XHigh | Claude Opus 4.8 XHigh＋人間承認必須 |
| V2-12-07 | Migration Phase gate（full suite） | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋人間 |

### Track B: V2-7 画像忠実性（残り6）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-7-02 | Secure抽出入力封筒 | Claude Sonnet 5 | High | Antigravity |
| V2-7-03 | Draft artifact／confidence preview | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-7-04 | 明示昇格経路（draft→constraint map） | Claude Opus 4.8 | High | Antigravity |
| V2-7-05 | Height guide抽出 | Claude Sonnet 5 | High | Antigravity |
| V2-7-06 | Zone/スケッチlabel抽出 | Claude Code `opusplan` | Plan=Opus/実装=Sonnet | Antigravity |
| V2-7-07 | 多入力競合解決＋差分preview＋Phase gate | Claude Opus 4.8 | XHigh | Codex Terra Extra High＋人間 |

### Track C: V2-8 スケール（残り7）

| Task | 内容 | 主担当 | Effort | レビュー |
|---|---|---|---|---|
| V2-8-02 | 寸法policyのscale契約への統一 | Claude Code `opusplan` | Plan=Opus/実装=Sonnet | Antigravity＋v1 golden回帰必須 |
| V2-8-03 | Constraint map／field storeのLARGE予算 | Claude Opus 4.8 | High | Codex Terra Extra High |
| V2-8-04 | Coarse大域計画＋hydrology LARGE実測 | Codex Terra Extra High | XHigh | Claude Opus 4.8 XHigh |
| V2-8-05 | Streaming生成・resume・部分再生成 | Codex Terra Extra High | XHigh | Claude Opus 4.8 High |
| V2-8-06 | Preview pyramid（段階preview） | Antigravity Gemini Pro | High | Claude Sonnet 5 High |
| V2-8-07 | Export／Release分割 | Claude Opus 4.8 | High | Claude Sonnet 5 High |
| V2-8-08 | LARGE offline統合＋Phase gate（full suite） | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋人間 |

### Track A extension: V2-9 Terrain foundation（14）

| Task | 内容 | 主担当 | Effort | レビュー | リスク種別 |
|---|---|---|---|---|---|
| V2-9-01 | Surface foundation contract | Claude Opus 4.8 | High | Codex Terra Extra High | contract・Schema互換／memory |
| V2-9-02 | PLAIN/HILL_RANGE slices | Antigravity Gemini Pro | High | Claude Sonnet 5 High | 通常実装 |
| V2-9-03 | MOUNTAIN_RANGE/VALLEY foundation | Claude Opus 4.8 | High | Antigravity | 計画が難しい通常実装／互換 |
| V2-9-04 | General RIVER contract | Claude Opus 4.8 | XHigh | Codex Terra Extra High | graph数学／決定性 |
| V2-9-05 | FLOODPLAIN/MARSH foundation | Claude Sonnet 5 | High | Antigravity | 通常実装／environment境界 |
| V2-9-06 | ROCKY_COAST/SEA_CLIFF | Claude Opus 4.8 | High | Codex Terra Extra High | surface-volume contract |
| V2-9-07 | Island/cone foundation | Claude Sonnet 5 | High | Antigravity | 計画が難しい通常実装 |
| V2-9-08 | Marine bathymetry core | Claude Opus 4.8 | XHigh | Codex Terra Extra High | 数学的保証／memory |
| V2-9-09 | SUBMARINE_CANYON | Claude Opus 4.8 | XHigh | Antigravity | 数学的profile／決定性 |
| V2-9-10 | CAVE_ENTRANCE connector | Claude Opus 4.8 | High | Codex Terra Extra High | surface-volume contract |
| V2-9-11 | UNDERGROUND_RIVER/fluid connection | Claude Opus 4.8 | XHigh | Codex Terra Extra High | graph／fluid ordering |
| V2-9-12 | Macro land-water topology | Claude Opus 4.8 | High | Antigravity | contract・Schema互換 |
| V2-9-13 | River graph roles/composites | Claude Opus 4.8 | High | Codex Terra Extra High | graph contract／互換 |
| V2-9-14 | Foundation Phase gate（full suite） | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋人間 | 監査 |

### Track A extension: V2-10 Deferred terrain families（9）

| Task | 内容 | 主担当 | Effort | レビュー | リスク種別 |
|---|---|---|---|---|---|
| V2-10-01 | Glacial ice foundation | Claude Opus 4.8 | XHigh | Codex Terra Extra High | volume／memory／数学 |
| V2-10-02 | Glacial deposition/profiles | Claude Sonnet 5 | High | Antigravity | 通常実装／classification |
| V2-10-03 | Karst hydrology graph | Claude Opus 4.8 | XHigh | Codex Terra Extra High | graph／surface-volume-fluid |
| V2-10-04 | Additional marine landforms | Claude Opus 4.8 | High | Antigravity | bathymetry contract |
| V2-10-05 | Advanced river/lake contract split | Claude Opus 4.8 | High | Codex Terra Extra High | 計画／contract分割 |
| V2-10-06 | Escarpment/dry-land foundation | Claude Sonnet 5 | High | Antigravity | 通常実装／transition |
| V2-10-07 | LAVA_TUBE volume slice | Claude Opus 4.8 | High | Codex Terra Extra High | sparse volume／互換 |
| V2-10-08 | Advanced island/reef | Claude Sonnet 5 | High | Antigravity | composition／通常実装 |
| V2-10-09 | Deferred terrain Phase gate（full suite） | Codex Sol Max | Max | Claude Opus 4.8 XHigh＋人間 | 監査 |
| V2-10-10 | Surface SPRING graph-node slice | Claude Opus 4.8 | High | Codex Terra Extra High | graph／hydrology |
| V2-10-11 | OXBOW_LAKE reach-cutoff basin slice | Claude Opus 4.8 | High | Antigravity | lake／reach relation |

## 3. 種類別の既定割当（表にないTaskが発生した場合）

| 作業種類 | 既定 |
|---|---|
| docs修正・fixture追加・リンク修正・diff要約 | Cursor軽量／Claude Haiku（禁止: Schema変更・並行処理・決定性・rollback） |
| テスト失敗の反復 | 1回目Cursor → 原因不明ならSonnet High → 同一原因2回失敗でOpus High → contract/memory/concurrencyならCodex Terra XH |
| Schema変更 | 非破壊・局所=Sonnet High/Antigravity → 複数Schema・migration=Opus High → 保存形式・公開contract=Codex Terra XH |
| 数学的Task | 既存式実装=Sonnet High → 数式・境界・誤差設計=Opus XHigh → 決定性保証が重大=Codex Sol Max → 最終数学監査=Codex Sol Ultra |
| Memory/性能 | 計測=Cursor → allocation改善=Sonnet High → bounded設計=Opus High/XHigh → 最大入力保証=Codex Terra XH/Sol Max |
| Concurrency | 既存パターン=Sonnet High → race/ordering/cancel=Opus XHigh → 決定性含む並列=Codex Terra XH → transaction/recovery連携=Codex Sol Max |

## 4. コンテキスト節約

- Task capsuleへ入れるのは: 対象Task全文、Execution Guide必須事項、関連ADR/Schema抜粋、変更候補ファイル、現在のgit diff、直近関連テスト結果、凍結対象、Acceptanceチェックリスト、停止条件、推奨モデル/Effort、のみ。
- リポジトリ全体・全docs・全テストログをレビューへ渡さない。渡すのはcapsule＋`git diff --stat`＋関連diff＋実行テスト結果＋新規/変更Schema・ADR。
- ルート指示ファイル（AGENTS.md）へ全仕様を詰め込まない。詳細はdocs/ADR/Schemaへ置き、`[[正本]]`参照で辿らせる。
