# Task実行モデル割当（Model Assignment）

> Status: 実行体制の正本（2026-07-22全面改訂）。Task ScopeとAcceptanceの正本は [Task Index](task-index.md)、進捗の正本は [docs/roadmap.md](../roadmap.md)、実行規約は [Task Execution Guide](task-execution-guide.md)。本文書はどのモデルがどのTaskを実行・レビューするかだけを定め、Task Scope・Acceptance・依存・状態を変更しない。矛盾時はTask Index／roadmapが優先する。

## 1. 文書の目的と正本関係

- 本文書は、現在の契約（§2）の範囲内で、品質を落とさずに費用対効果を最大化するTask実行体制の正本である。
- Task完了の判定・記録は本文書では行わない。完了済みTaskの割当行は正規表から除去し、進捗はroadmapを参照する（§14・§15）。
- 本文書に存在してTask Indexに存在しないTask IDは正規Taskではない。

## 2. 現在の契約・利用可能モデル

利用可能な契約は次の2つだけである。いずれも**無制限ではない**。

| 契約 | 提供モデル | 用途 |
|---|---|---|
| Claude Max 5x | Claude Fable 5／Claude Opus 4.8／Claude Sonnet 5／Claude Haiku 4.5 | Claude Code（サブスクリプション認証） |
| Cursor Pro（$20） | Grok 4.5 | Cursor（通常実装・独立レビュー） |

次は利用不能であり、現在・将来の通常Taskの主担当・レビュー・昇格先として割り当ててはならない: Codex、GPT-5.6、Terra、Sol、Luna、Antigravity、Gemini Pro、Claude Pro、OpenAI API、Codex追加クレジット、Anthropic API従量課金（§15の履歴付録だけが非規範的な例外）。

### Effort段階の表記

本文書はツール非依存の意味的段階だけを使う。

```text
Low / Medium / High / Highest available
```

| 意味的段階 | Claude Code（`--effort`、確認済み: low/medium/high/max） | Cursor |
|---|---|---|
| Low | `low` | セッション開始時にUIで確認 |
| Medium | `medium` | 同上 |
| High | `high` | 同上 |
| Highest available | `max` | 同上（UIに表示される最上位の推論設定） |

Cursor側には確認済みの固有effort段階名がないため、本文書へ固有名を記載しない。セッション開始時に実UIの表示へ従う。

## 3. モデル別の役割

### Claude Haiku 4.5 — 低リスク軽作業

使用対象: docsのみの軽微な修正、リンク修正、表記統一、Task capsule作成、grep結果整理、既存情報の要約、diff一次分類、単純なfixture追加。

使用禁止: 公開Schema変更、migration、security判断、並行処理、決定性保証、memory admission、Release形式、world mutation、rollback／Undo／Recovery、Phase gate、最終承認。Haiku単独で完了扱いにできるのは、production・Schema・公開contractを変更しない低リスクTaskだけ。

### Grok 4.5（Cursor Pro） — 通常実装の主力

使用対象: 既存パターンに沿った通常実装、局所的なFeature wiring、generator dispatch、validator／preview追加、テスト追加、コンパイルエラー修正、対象限定リファクタ、CLI／docs局所整合、テスト失敗の反復修正、実機測定用harness修正。加えて**Claude実装の独立レビュー**（モデルファミリーが異なる第一レビュー）を担う。

単独最終決定の禁止: 公開contract設計、複数Schema migration、transaction、rollback、crash recovery、複雑な並行処理、数学的境界保証、最大入力memory保証、Phase gate、Release capability昇格。Grok実装は原則Sonnet 5がレビューする。

### Claude Sonnet 5 — 品質と使用量のバランス中心

使用対象: 通常〜中高リスク実装、複数ファイルにまたがる既存設計の拡張、局所的contract変更、E2Eテスト、非破壊的・局所的Schema変更、Grok実装のコードレビュー、Acceptance evidence確認、Task Scopeと差分の照合、ログ／telemetry解析。

最初からSonnetで開始してよい場合: 既存パターンだけでは方針が確定しない、Task Scopeの解釈が必要、Grokではコンテキスト不足になりやすい、複数module間の接続が必要。高リスクcontract・transaction・Recovery・複雑な数学／並行処理はOpusへ昇格する（§6）。

### Claude Opus 4.8 — 高リスク実装・高品質レビュー限定

使用対象: 公開contract、複数Schema整合、互換migration、決定性、fixed-point数学、graph ordering、memory admission、concurrency、cancel semantics、security、Release strict read-back、world mutation、Undo／rollback／Recovery、capability promotion、重大なADR。

```text
Opus 4.8 High:              高リスク設計、中規模contract実装、Sonnet／Grok実装の高リスクレビュー
Opus 4.8 Highest available:  複数不変条件が結合する実装、transaction／Recovery、複雑なmigration、
                            最大入力保証、Phase gate前の詳細監査
```

禁止: 通常のFeature wiring、単純なvalidator追加、docs同期への大量投入。

### Claude Fable 5 — 最上位の希少リソース

使用対象: Phase gate監査、Release全体監査、複数Phase横断の設計判断、大規模migrationの最終設計、長時間・大規模リポジトリ監査、Opusで解消できない設計矛盾、高品質レビューが対立した場合の裁定、transaction／Recovery全体の最終監査、false promotion防止監査。

使用禁止: 通常Feature実装、単純なdocs修正、局所テスト修正、既存パターンwiring、機械的rename、最初の実装試行。**Phase gateだからという理由だけでFableを自動使用しない** — 単純な証拠集約ならOpus 4.8で足りるかを先に評価する（本文書ではV2-16-19をOpus主担当とした例がそれに当たる）。Fable主担当Taskでも、同一Fableセッションに自己レビューと最終承認をさせない。

## 4. 費用対効果の基本方針（既定経路）

```text
低リスクdocs・整理:      Haiku 4.5 → 必要ならGrok 4.5またはSonnet 5レビュー
通常実装:                Grok 4.5 → Sonnet 5レビュー
通常だが設計解釈が必要:  Sonnet 5 → Grok 4.5独立レビュー
高リスク実装:            Opus 4.8 → Grok 4.5独立レビュー → 必要ならSonnet 5 Acceptance確認
最高リスク／Phase全体:   Opus 4.8またはFable 5 → Grok 4.5独立レビュー
                         → 別Claudeモデルによる第二レビュー → 人間承認
```

- 同一モデルの別セッションだけでは「独立レビュー」にならない。高リスクTaskのレビュー鎖には**モデルファミリーが異なるレビュー（実務上はGrok 4.5）を最低1つ**含める。
- 最初から強いモデルを使わない。昇格は§6の明示条件だけで行う。
- レビューは削らない。使用枠が不足したら低優先度Taskを保留する（§12）。

## 5. リスク分類

| リスク | 定義 | 開始モデル |
|---|---|---|
| 低 | production・Schema・公開contractに触れないdocs／fixture／整理 | Haiku 4.5 |
| 中（通常） | 既存パターンに沿うproduction実装・wiring・テスト | Grok 4.5 |
| 中高 | Scope解釈・複数module接続・局所contract／Schema変更を含む | Sonnet 5 |
| 高 | 公開contract／Schema・migration・決定性・memory・concurrency・security・Release read-back | Opus 4.8 |
| 最高 | Phase／Release横断、複数高リスク不変条件の結合、false promotionが製品保証へ直結 | Opus 4.8 Highest availableまたはFable 5 |

## 6. 昇格条件

曖昧な「難しそう」では昇格しない。**単なるテスト失敗、環境不足、実機未確保は昇格理由にならない**（環境不足は`BLOCKED_EXTERNAL`、§8）。

| 昇格 | 条件（いずれか） |
|---|---|
| Haiku → Grok／Sonnet | production codeを変更する／Schemaに触れる／Task Scopeの解釈が必要／転記だけでは完了しない |
| Grok → Sonnet | 同一原因で2回修正失敗／複数moduleのcontract解釈／既存実装とTask本文の矛盾／局所修正でAcceptanceを満たせない／公開API・CLI意味論に影響 |
| Sonnet → Opus | 公開Schema・保存形式／migration／決定性不変条件／memory上限保証／concurrency・race・ordering／security境界／transaction／rollback／Undo／Recovery／capability promotion |
| Opus → Fable | Phase・Release全体を横断／複数高リスク不変条件の結合／Opusと独立レビューが重大点で対立／正本・ADR・実装間の矛盾を解消できない／false promotionが製品保証へ直結／大規模migrationの最終go・no-go |

## 7. レビューマトリクス

| 主担当 | 第一レビュー | 高リスク時の第二レビュー |
|---|---|---|
| Haiku 4.5 | Grok 4.5またはSonnet 5 | Opus 4.8 |
| Grok 4.5 | Sonnet 5 | Opus 4.8 |
| Sonnet 5 | Grok 4.5 | Opus 4.8 |
| Opus 4.8 | Grok 4.5 | Fable 5または人間 |
| Fable 5 | Grok 4.5 | Opus 4.8＋人間 |
| Cursor＋人間による実機Task | Sonnet 5（ログ／telemetry解析） | Opus 4.8＋人間 |

この表は機械的に適用せず、Taskリスクへ応じて§9〜§10の個別行が優先する。高リスクTaskでは、同じモデルに実装・第一レビュー・最終承認をすべて担当させない。人間承認がAcceptanceへ含まれるTaskは、モデルレビュー完了だけで完了扱いにしない。

## 8. 実機依存Taskの扱い

Paper実機smoke、WorldEdit／FAWE測定、MSPT／tick stall測定、Recovery drill、watchdog確認、専用ホストmemory測定、実world配置、Undo／rollback実測は**Cursor＋人間が主担当**である。モデルの担当はharness実装・確認、runbook作成、ログ／telemetry分析、evidence Schema確認、Acceptance照合だけに限る。

- 実機証拠がない状態で、モデルに`COMPLETE`／`SUPPORTED`／`PROMOTED`と判断させない。
- mock・unit test・offline fixtureを実機証拠の代替にしない。
- ホスト・環境が確保できないTaskは`BLOCKED_EXTERNAL`とし、強いモデルへの変更で解決を図らない（[Task Execution Guide §8](task-execution-guide.md)）。

## 9. 現在の未完了Task割当

現在実行可能なPhaseはV2-19（Track A主担当・横断）とV2-15（Track E。ただし公開配線leafは`V2-19-01` stage gate待ち）である。V2-18は2026-07-23の人間承認でPhase gate `V2-18-12`が完了し、全13 Task完了で閉鎖した。V2-19は2026-07-23の[横断監査](../audits/cross-cutting-audit-2026-07-23.md)人間承認で登録した（§9.3）。V2-16／V2-17は依存待ち、V2-8-03〜08はHOLDで§10に置く。完了済みTask（V2-0〜V2-7、V2-9〜V2-14、V2-15-01〜10、V2-18全13）は本表へ掲載しない（履歴は§15とroadmap）。

### 9.1 V2-18 Macro foundation and intent conformance（Track A、完了）

進捗: **全13 Task完了・親Phase閉鎖（2026-07-23）**。`V2-18-10`のfail-closed昇格は2026-07-23の人間承認で完了、`V2-18-11`のconformance portfolioが確定した`coastal-honored-400`東arm landfall非適合は`V2-18-13`が是正した。`V2-18-12`（Phase gate）は2026-07-23に実装・検証（`MacroFoundationPhaseGateV2Test`によるconformance portfolioのgate化＋全leaf再検証＋full clean suite PASS、[audit](audits/v2-18-12-phase-gate.md)）ののち、同日の人間承認（atomic decision: `V2-15-47`／`V2-16-19` Acceptance追記・V2-15 stage gate解除・親Phase閉鎖）で完了した。Scope正本は[Task Index §18](task-index.md)。

| Task | 状態 | 内容 | リスク | 主担当 | Effort | 第一レビュー | 第二レビュー／人間gate | 昇格条件 |
|---|---|---|---|---|---|---|---|---|
| V2-18-13 | 完了（2026-07-23） | `coastal-honored-400`東arm landfall非適合の是正（rocky cape西端をNW 0.62→0.60／SW 0.72→0.685へ拡張しfixture geometryを是正、mask再生成でarmをmainlandへ接続） | 中（mask再生成でcoastal容器checksumが動く） | Sonnet 5 | — | Grok 4.5 | — | 再生成手順が`V2-18-09`の合成規則と一致しない場合はOpus |
| V2-18-12 | 完了（2026-07-23、人間承認済み） | Phase gate（full suite、V2-15 stage gate解除判断） | 最高（Phase横断、`V2-15-47`／`V2-16-19` Acceptance追記） | **Fable 5** | Highest available | Grok 4.5 | Opus 4.8＋**人間承認** | — |

割当根拠: `V2-18-12`は単なる証拠集約でなく、V2-15／V2-16のAcceptanceへ遡ってintent-conformanceを追記しstage gateの解除可否を裁定する複数Phase横断判断のためFableとする。

### 9.2 V2-15 Canonical catalog／existing-generator wiring（Track E、残り38）

進捗: `V2-15-01`〜`10`完了（10/47）。次のTaskは`V2-15-11`だが、**2026-07-23横断監査により公開配線leafは`V2-19-01`（semantic materialization gate、§9.3）完了を待つ**。**[ADR 0039](../adr/0039-offline-production-route-eligibility.md) Gate 0 は2026-07-23の人間承認で`Accepted`（候補A採択: offline production route は dispatch `production-dispatch-registry-v1→v2` contract bump で追加）となり、`V2-15-10`が候補Aパターンの最初の適用leafとして完了した**。`PRODUCTION_CONNECTED`（Paper込み完全接続＝coastal 4）の意味・Paper `SUPPORTED` exact setは不変（`RIVER`／`MEANDERING_RIVER`はexport-SUPPORTEDのまま`OFFLINE_PRODUCTION`route、Paper `paper_apply`はEXPERIMENTALで昇格なし）。後続`V2-15-11`〜`46`は同一パターンをコピーする。

**Stage gate（全行共通、2026-07-23解除済み）:** 一律保留（`V2-18-09`完了までの待機）は`V2-18-12`の裁定と2026-07-23の人間承認で解除された。以後、production export／placement昇格系の作業・Acceptanceは各配線leafの**per-leaf義務**として次を負う: (1) 対象kindのcomposition profileをfield監査でPROVISIONAL→確定する（ADR 0038 D4。`CompositionProfileRegistryV2`にNORMATIVE登録済みなのは現状coastal 4種＋`PLAIN`／`HILL_RANGE`のみ。対応表の変更が必要ならADR 0038 amendmentを先行させ、Phase gate期待値はregistry変更と同一commitで更新する — test期待値だけの変更は禁止）、(2) 新規接続kindのintent-conformance portfolio caseを追加し、portfolio全case適合（登録済み非適合ゼロ）を維持する。registry／Schema／codec／offline plan／determinism系の作業は従来どおり制約なし。加えて ADR 0039 Accepted 後は、承認された offline production route パターンだけを 10〜46 でコピーする（A/B 混在禁止）。**さらに2026-07-23の[横断監査](../audits/cross-cutting-audit-2026-07-23.md)人間承認により、(3) 公開配線leaf（production wiring系）は`V2-19-01`が確立するsemantic materialization義務 — 対象Featureのfinal canonical block streamからの非空効果・形状conformance測定、plan-only metricとblock metricの別欄化 — をAcceptanceへ含み、`V2-19-01`完了までは着手しない**（registry／Schema／codec／offline plan／determinism系leafは従来どおり制約なし）。

| Task | 状態 | 内容 | リスク | 主担当 | Effort | 第一レビュー | 第二レビュー／人間gate | 昇格条件 |
|---|---|---|---|---|---|---|---|---|
| V2-15-10 | **完了（2026-07-23）** | `RIVER`＋meandering subtype wiring＋dispatch `v1→v2` bump（ADR 0039候補Aの最初の適用leaf） | 中高（alias互換／meander checksum不変） | Sonnet 5 | High | Grok 4.5 | — | checksum互換の設計判断が必要ならOpus |
| V2-15-11 | 依存03,06,`V2-19-01` | `LAKE`＋oxbow subtype wiring（materialization義務込み） | 中高（hydrology relation＋block実体化） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-12 | 依存06 | `CANYON` wiring | 中（通常public wiring） | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-13 | 依存06,08 | `WATERFALL`＋volume overlay wiring | 中高（graph／CSG ordering接続） | Sonnet 5 | High | Grok 4.5 | — | ordering不変条件が争点ならOpus |
| V2-15-14 | 依存06 | `DELTA` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-15 | 依存06 | `TIDAL_CHANNEL_NETWORK` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-16 | 依存06 | `FJORD` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-17 | 依存03,07,09 | mountain＋alpine／glacial profiles | 中高（subtype互換／shared kernel） | Sonnet 5 | High | Grok 4.5 | — | kernel互換が争点ならOpus |
| V2-15-18 | 依存03,07,09 | archipelago＋volcanic profile | 中高（composition profile） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-19 | 依存03,07,09 | marsh＋mangrove profile | 中高（environment subtype） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-20 | 依存07 | `CORAL_REEF` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-21 | 依存03,09 | `PLAIN`＋backshore alias | 中高（alias／Schema互換、ADR 0036準拠） | Sonnet 5 | High | Grok 4.5 | — | disposition互換が争点ならOpus |
| V2-15-22 | 依存09 | `HILL_RANGE` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-23 | 依存09 | `VALLEY` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-24 | 依存09 | `FLOODPLAIN` wiring | 中（river relation必須） | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-25 | 依存09 | `ROCKY_COAST` wiring | 中高（production coastal checksum不変） | Sonnet 5 | High | Grok 4.5 | — | coastal回帰が崩れたらOpus |
| V2-15-26 | 依存09 | `SEA_CLIFF` wiring | 中高（surface-volume host handoff） | Sonnet 5 | High | Grok 4.5 | — | host contract設計が必要ならOpus |
| V2-15-27 | 依存09 | `SINGLE_ISLAND` wiring | 中 | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-28 | 依存09 | `VOLCANIC_CONE` wiring | 中（caldera／lava childのみ） | Grok 4.5 | — | Sonnet 5 | — | child ownership解釈が必要ならSonnet |
| V2-15-29 | 依存09 | basin／shelf／slope marine trio | 高（bathymetry数学／shared kernel／memory） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-30 | 依存29 | `SUBMARINE_CANYON` wiring | 中高（bathymetry owner分離） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-31 | 依存29 | abyssal plain＋seamount variants | 中高（basin containment） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-32 | 依存08,09 | `CAVE_ENTRANCE` wiring | 中高（frozen cave checksum bind） | Sonnet 5 | High | Grok 4.5 | — | binding設計が必要ならOpus |
| V2-15-33 | 依存08,09 | `UNDERGROUND_RIVER` wiring | 高（graph／fluid／CSG ordering） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-34 | 依存07,09 | glacier／ice cap／ice sheet common kernel | 高（glacial数学／volume／memory） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-35 | 依存34 | moraine／outwash wiring＋preview | 中（parent bounds／preview整合） | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-15-36 | 依存08,09 | sinkhole／karst spring graph wiring | 高（karst graph／surface-volume-fluid） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-37 | 依存09 | escarpment／plateau transition pair | 中高（transition contract） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-38 | 依存08,09 | `LAVA_TUBE` wiring | 中高（`CARVE_SOLID` ownershipのみ） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-39 | 依存06,09 | `SPRING` wiring | 中高（hydrology graph specialization不変） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-40 | 依存08 | cave network／lush cave graph wiring | 高（sparse graph／volume／memory） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-41 | 依存08 | `OVERHANG` wiring | 中高（gravity containmentはPaper gate残置） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-42 | 依存08 | `SKY_ISLAND_GROUP` wiring | 中高（sparse solid／group bounds） | Sonnet 5 | High | Grok 4.5 | — | §6既定 |
| V2-15-43 | 依存03,08 | `UNDERGROUND_LAKE` public vertical slice | 高（新public kind＋public Schema＋cave fluid bounds） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-44 | 依存03,08 | `SEA_CAVE` public vertical slice | 高（public Schema＋coast-volume host） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-45 | 依存03,08 | `NATURAL_ARCH` public vertical slice | 高（public Schema＋support invariant） | Opus 4.8 | High | Grok 4.5 | — | §6既定 |
| V2-15-46 | 依存03,04 | child／overlay／compatibility catalog cleanup | 最高（identifier migration／checksum／削除はADR承認範囲のみ） | Opus 4.8 | Highest available | Grok 4.5 | Sonnet 5（Acceptance確認）＋**人間確認**（ADR承認範囲逸脱の有無） | §6既定 |
| V2-15-47 | 依存10..46 | Phase gate（full suite、既存20＋全leaf E2E） | 最高（47 Task Phaseの最終監査／false promotion防止） | **Fable 5** | Highest available | Grok 4.5 | Opus 4.8＋**人間承認** | — |

割当根拠: 旧割当はwiring系の大半をOpus相当以上としていたが、`V2-15-05`〜`09`でdispatch spine・shared pipeline・export adapterが完了済みのため、確立パターンへ載せるだけのwiringはGrok（通常）／Sonnet（互換・checksum・subtype解釈あり）へ引き下げた。Opusは数学・memory・ordering・public Schema新設を含む行だけに残す。`V2-15-47`は公開カタログ全面の最終監査でfalse promotionが製品保証へ直結するためFableとする。

### 9.3 V2-19 Input integrity and block materialization（Track A主担当・横断、16 Task）

進捗: 未着手（2026-07-23登録、[横断監査](../audits/cross-cutting-audit-2026-07-23.md)人間承認済み）。次のTaskは`V2-19-01`。Scope正本は[Task Index §19](task-index.md)。`V2-19-01`はV2-15公開配線leafのstage gateを兼ねるため最優先で実行する。`V2-19-09`／`12`／`14`はADR＋人間承認必須（承認前に実装しない）。

| Task | 状態 | 内容 | リスク | 主担当 | Effort | 第一レビュー | 第二レビュー／人間gate | 昇格条件 |
|---|---|---|---|---|---|---|---|---|
| V2-19-01 | 未着手 | Semantic materialization gate（V2-15／16 per-leaf義務へfinal block stream実体化・形状conformanceを追加、plan-only／block metric別欄化、catalog支持と公開到達性の別表示） | 最高（Phase横断Acceptance遡及変更） | Opus 4.8 | High | Grok 4.5 | **人間承認** | 裁定が割れたらFable |
| V2-19-02 | 未着手 | Gradle test input修正（inventory testの走査対象をtest inputへ登録、cache無効化のCI固定） | 低中（build設定） | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-19-03 | 未着手 | Reference-image public design修理（secure prepare／budget／EXIF非漏洩／provider handoff／実E2E） | 中高（画像decode security） | Sonnet 5 | High | Grok 4.5 | — | security境界の設計争点はOpus |
| V2-19-04 | 未着手 | Generic constraint source＋Intent binding authoring（3 role共通宣言・binding生成verb・E2E） | 中高（Schema／binding契約） | Sonnet 5 | High | Grok 4.5 | — | binding契約の設計争点はOpus |
| V2-19-05 | 未着手 | RIVER block materialization（bed carve＋water fill、responsibility分離、final tile検査） | 高（production spine＋checksum影響） | Opus 4.8 | High | Grok 4.5 | Sonnet 5 | — |
| V2-19-06 | 未着手 | HEIGHT_GUIDE macro foundation consumer（D2-2範囲、1枚制約のrole別緩和、RasterResidual有効化） | 高（foundation契約＋決定性） | Opus 4.8 | High | Grok 4.5 | Sonnet 5 | — |
| V2-19-07 | 未着手 | Foundation producer tier＋PLAIN vertical slice（ProducerLayer初接続、candidate→effective、dispatch到達） | 高（merge kernel＋composition） | Opus 4.8 | High | Grok 4.5 | Sonnet 5 | — |
| V2-19-08 | 未着手 | Design-time support lint（reachable集合提示＋dispatch dry-run、report-only） | 中（通常実装） | Grok 4.5 | — | Sonnet 5 | — | §6既定 |
| V2-19-09 | 未着手 | Coastal 4種必須の緩和ADR＋実装（部分集合許可条件・checksum影響） | 高（V2-2契約変更） | Opus 4.8 | High | Grok 4.5 | **ADR人間承認** | — |
| V2-19-10 | 未着手 | Material／palette block反映（resolver接続、11 state→profile allowlist、実field validation） | 中高（semantic checksum計画） | Sonnet 5 | High | Grok 4.5 | ADR承認範囲確認 | checksum戦略が争点ならOpus |
| V2-19-11 | 未着手 | Docs current-state同期（stale表記4文書＋README、公開到達性列追加） | 低（docs-only） | Haiku 4.5 | — | Grok 4.5 | — | §6既定 |
| V2-19-12 | 未着手 | Coherent detail kernel設計ADR＋実装（連続multi-scale detail、budget固定、erosion分離） | 高（新表現契約） | Opus 4.8 | High | Grok 4.5 | **ADR人間承認** | 設計裁定が割れたらFable |
| V2-19-13 | 未着手 | Blueprint拡張面積縮小のADR検討（typed plan envelope案、Proposed著述まで） | 中高（設計文書） | Opus 4.8 | High | Sonnet 5 | — | — |
| V2-19-14 | 未着手 | mask⇔feature SOFT reconcile pre-pass ADR＋実装（決定論的snap、tolerance超拒否） | 高（HARD不発明原則との整合） | Opus 4.8 | High | Grok 4.5 | **ADR人間承認** | — |
| V2-19-15 | 未着手 | Commit message規約（Task ID必須等、docs-only） | 低 | Haiku 4.5 | — | Grok 4.5 | — | §6既定 |
| V2-19-16 | 依存01..15 | Phase gate（全leaf再検証、materialization gate発効確認、full clean suite） | 最高（Phase横断） | **Fable 5** | Highest available | Grok 4.5 | Opus 4.8＋**人間承認** | — |

割当根拠: `V2-19-01`はV2-15／V2-16のAcceptanceへ遡及する契約変更のためOpus＋人間承認とし、`V2-18-12`と同型の複数Phase横断裁定が必要になった場合のみFableへ昇格する。`V2-19-05`〜`07`はproduction export spine（`core.v2.export`）とmerge kernelへ触れる高リスク実装のためOpus主担当・二重レビューとする。共有領域（`core.v2.export`等）を触るTask（05／06／07／09／10）はV2-15配線leafと直列にする。

## 10. HOLD／BLOCKED／承認待ちTask

### 10.1 HOLD: V2-8-03〜08（Track C、LARGE scale-up）

2026-07-21のS2決定で保留。LARGE契約は凍結保持し、1024完成後にS3退役を独立migration Phaseで再審査する。

| Task | 状態 | 主担当 | 暫定候補 | 再評価条件 |
|---|---|---|---|---|
| V2-8-03〜V2-8-08 | HOLD | **再開時再評価** | Sonnet 5（予算・preview系）／Opus 4.8（streaming・resume・分割export系） | S3再審査の結論、Scope変更、1024実測結果、その時点のモデル性能・利用枠 |

HOLD Taskへモデルを確定割当しない。再開時に本文書を改訂してから着手する。

### 10.2 BLOCKED（依存待ち）: V2-16（19 Task、`V2-15-47`待ち）

以下は**暫定割当**であり、`V2-16-01`着手前に再評価する。composition engine（`V2-16-01`）の成立を前提に、presetは通常実装として扱う。

| Task | 内容 | リスク | 暫定主担当 | Effort | 第一レビュー | 第二レビュー／人間gate |
|---|---|---|---|---|---|---|
| V2-16-01 | composition engine（bounds／seed／ordering／manifest contract） | 最高 | Opus 4.8 | Highest available | Grok 4.5 | Sonnet 5（Acceptance確認） |
| V2-16-02 | `WATERFALL_CHAIN` preset | 中 | Grok 4.5 | — | Sonnet 5 | — |
| V2-16-03 | `ICE_FJORD` preset | 中高 | Sonnet 5 | High | Grok 4.5 | — |
| V2-16-04 | `CENOTE` preset | 中高（surface-volume-fluid） | Sonnet 5 | High | Grok 4.5 | 争点化でOpus |
| V2-16-05 | `BARRIER_ISLAND` preset | 中 | Grok 4.5 | — | Sonnet 5 | — |
| V2-16-06 | `ATOLL` preset | 中（child ordering） | Grok 4.5 | — | Sonnet 5 | — |
| V2-16-07 | `ESTUARY` composition | 高（river-mouth／tidal graph coupling） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-08 | `FLOATING_REEF` composition | 中高（aerial host／sparse support） | Sonnet 5 | High | Grok 4.5 | — |
| V2-16-09 | braided／bedrock river＋terrace | 高（river graph数学／subtype互換） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-10 | `DAM_RESERVOIR` vertical slice | 高（barrier／spillway ownership） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-11 | `ALLUVIAL_FAN` vertical slice | 中高（drainage／deposition数学） | Sonnet 5 | High | Grok 4.5 | 数学的境界が争点ならOpus |
| V2-16-12 | `OCEAN_TRENCH` vertical slice | 高（global continuity／memory。LARGEはHOLD policy準拠） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-13 | ridge＋submarine volcano subtype | 高（ridge数学／provenance） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-14 | `DUNE_FIELD` vertical slice | 中高（directional morphology） | Sonnet 5 | High | Grok 4.5 | — |
| V2-16-15 | `BADLANDS` vertical slice | 高（drainage-aware erosion／strata） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-16 | `TOWER_KARST` vertical slice | 高（tower spacing／cave graph／bounds） | Opus 4.8 | High | Grok 4.5 | — |
| V2-16-17 | `SALT_FLAT` vertical slice | 中高 | Sonnet 5 | High | Grok 4.5 | — |
| V2-16-18 | `PEAT_BOG` subtype | 中（new kindなし） | Grok 4.5 | — | Sonnet 5 | — |
| V2-16-19 | Phase gate（full suite、canonical count再確認） | 最高 | Opus 4.8 | Highest available | Grok 4.5 | Sonnet 5＋**人間承認**。監査対立・横断矛盾が出たらFableへ昇格 |

割当根拠: `V2-16-19`はV2-15-47と異なり、確立済みcanonical catalog上のcomposition Phaseを閉じる監査であり、まずOpus Highest availableで足りるかを評価する（Fable自動投入をしない例）。対立や横断矛盾が出た場合だけFableへ昇格する。

### 10.3 BLOCKED（依存＋実機）: V2-17（7 Task、`V2-15-47`／`V2-16-19`待ち）

実機ホスト未確保の実測Taskは着手時点で`BLOCKED_EXTERNAL`とする。以下は暫定割当で、着手前に再評価する。

| Task | 内容 | 暫定主担当 | Effort | レビュー／解析 | 人間gate |
|---|---|---|---|---|---|
| V2-17-01 | measurement harness／runbook／evidence Schema | Opus 4.8 | High | Grok 4.5 | 人間（手順実行可能性確認） |
| V2-17-02 | hydrology-plan実機matrix | Cursor＋人間（hostなしは`BLOCKED_EXTERNAL`） | — | Sonnet 5（ログ／telemetry解析） | 人間 |
| V2-17-03 | environment-fields実機matrix | Cursor＋人間（同上） | — | Sonnet 5（ログ／telemetry解析） | 人間 |
| V2-17-04 | sparse-volume実機matrix（fluid／gravity／containment） | Cursor＋人間（同上） | — | Sonnet 5（解析）＋Opus 4.8（evidence第二確認） | 人間 |
| V2-17-05 | foundation／new／preset実機matrix | Cursor＋人間（同上） | — | Sonnet 5（ログ／telemetry解析） | 人間 |
| V2-17-06 | evidence-bound capability promotion | Sonnet 5 | High | Grok 4.5＋Opus 4.8（false promotion監査） | **人間承認必須** |
| V2-17-07 | Paper Phase gate（full runtime suite、Recovery drill） | **Fable 5**（監査）＋Cursor＋人間（実機drill） | Highest available | Grok 4.5 | Opus 4.8＋**人間承認** |

モデルはharness／evidence分析のみを担当し、実機証拠のない`SUPPORTED`／`PROMOTED`判断をしない（§8）。`V2-17-07`はRelease全体のcapability保証を閉じる最終監査でfalse promotionが製品保証へ直結するためFableとする。

### 10.4 人間承認待ち（モデル追加投入で解決しない項目）

| 項目 | 状態 | モデルの役割 |
|---|---|---|
| V2-13親Phase完了宣言／1024 catalog昇格 | 全6 Task完了済み、**いずれも別の人間承認待ち** | Sonnet 5によるevidence整理のみ。承認判断はしない |
| V2-14親Phase完了宣言 | 3/3 Task完了済み、**人間承認待ち** | 同上 |

## 11. 種類別の既定割当（表にない新規Taskが発生した場合）

| 作業種類 | 開始モデル | 標準レビュー | 昇格条件 | 使用禁止 | 人間gate |
|---|---|---|---|---|---|
| docs | Haiku 4.5 | Grok 4.5またはSonnet 5 | Scope解釈が必要→Sonnet | Fable／Opus | 不要 |
| fixture／test | Haiku 4.5（単純）／Grok 4.5 | Sonnet 5 | 同一原因2回失敗→Sonnet | Fable | 不要 |
| 通常Feature実装 | Grok 4.5 | Sonnet 5 | §6 Grok→Sonnet | Fable | 不要 |
| 既存generator wiring | Grok 4.5 | Sonnet 5 | 互換・checksum解釈→Sonnet | Fable | 不要 |
| CLI／Paper routing | Grok 4.5 | Sonnet 5 | permission／security接点→Opus | Haiku | 不要 |
| Schema局所変更（非破壊） | Sonnet 5 | Grok 4.5 | 保存形式・公開contract→Opus | Haiku | 不要 |
| 複数Schema／migration | Opus 4.8 High | Grok 4.5 | 最終go/no-go→Fable | Haiku／Grok単独 | migration実施時 |
| 数学／fixed-point | Opus 4.8 High〜Highest available | Grok 4.5 | 決定性保証の最終監査→Fable | Haiku | 不要 |
| hydrology graph | Sonnet 5（既存パターン）／Opus 4.8（ordering・決定性） | Grok 4.5 | §6既定 | Haiku | 不要 |
| memory／performance | Sonnet 5（allocation改善）／Opus 4.8（bounded設計・最大入力保証） | Grok 4.5 | §6既定 | Haiku | 実測を伴う場合 |
| concurrency／cancel | Opus 4.8 High〜Highest available | Grok 4.5 | transaction連鎖→Fable監査 | Haiku／Grok単独 | 不要 |
| security | Opus 4.8 High | Grok 4.5 | 境界設計対立→Fable | Haiku／Grok単独 | 境界変更時 |
| Release／strict read-back | Opus 4.8 High | Grok 4.5 | capability昇格→＋Sonnet Acceptance | Haiku | capability昇格時 |
| transaction／rollback／Recovery | Opus 4.8 Highest available | Grok 4.5 | 全体最終監査→Fable | Haiku／Grok単独／Sonnet単独 | Recovery drill時 |
| 実機測定 | Cursor＋人間 | Sonnet 5（解析） | — | 全モデル単独完了 | **必須** |
| ADR／governance | Opus 4.8 Highest available | Grok 4.5 | 複数Phase横断→Fable | Haiku／Grok単独 | **必須** |
| Phase gate | Opus 4.8 Highest available（証拠集約中心）／Fable 5（横断裁定・false promotion） | Grok 4.5 | Opus監査で対立→Fable | Haiku／Grok単独 | **必須** |

## 12. 使用量・課金管理

### Claude（Claude Max 5x）

1. セッション開始時に`/status`で**サブスクリプション認証**であることを確認する。
2. `ANTHROPIC_API_KEY`が環境変数・設定に残っているとAPI従量課金になり得るため確認する。**ユーザーの明示承認なしにAPI従量課金へ切り替えない。**
3. Max 5x利用枠が不足した場合の順序:
   1. Haiku／Sonnetで可能な作業を先へ進める
   2. 通常実装をCursor／Grokへ移す
   3. Opus／Fableが必要な高リスクTaskを保留する
   4. 新たな従量課金は人間の明示承認を得る

### Cursor（Cursor Pro $20）

1. usage-based billing／BYOK／Max Mode／追加従量課金の設定が有効になっていないかを確認する。**ユーザーの明示承認なしに月額プラン外の従量課金を前提にしない。**
2. Cursor利用枠が不足しても品質上必要なレビューを削除しない。低優先度Taskを保留し、Grok第一レビュー分は一時的にSonnet 5レビューへ振り替えてよい（高リスクTaskの「モデルファミリーが異なるレビュー」要件は、枠回復後の追認レビューで満たす）。

## 13. Task capsule／コンテキスト節約

1 Task＝1 Task capsule（`.task-context/V2-x-NN.md`、Git管理外）＝1新規セッション。モデルとEffortはセッション開始時に本文書から決める。

capsuleへ含めるのは原則次だけ: 対象Task全文、Task Execution Guideの必須事項、関連ADR／Schemaの必要箇所、依存TaskのAcceptance結果、変更候補ファイル、現在のgit diff、直近の関連テスト結果、凍結対象、Acceptance checklist、停止条件、推奨モデル／Effort、レビュー担当。

毎回無差別に渡さないもの: リポジトリ全体、すべてのdocs、すべてのADR、全テストログ、無関係な過去Task。

モデル別の調整: Grok／Haikuへは上記最小構成を厳守する。Opus／Fableが担当するPhase gate・横断監査・migration設計では、必要な依存（対象Phase全Taskの完了証拠、関連ADR全文、stage gate条件）を狭めすぎず、Taskを正しく完了できる**最小十分**な情報量にする。コンテキスト削減自体を目的にしない。

## 14. 文書更新ルール

- 本文書を更新するのは次の場合だけ: 契約・利用可能モデルの変更、新Phase／Taskの登録、HOLD Taskの再開、昇格条件・レビュー体制の変更、Phase gate通過による表の整理。
- Taskが完了したら、該当行を正規表から削除する（進捗記録はroadmapが正本。本文書へ完了履歴を蓄積しない）。実行モデルの履歴は本文書のGit履歴とroadmapに残る。
- 表にないTaskは§11の種類別既定で開始し、恒常化するなら次回改訂で表へ追加する。
- Task Scope・Acceptance・依存・状態・IDは本文書で変更しない。正本間の矛盾を発見したら本文書で独自解決せず、roadmap／Task Index側の修正Taskとして報告する。

## 15. 履歴付録（非規範的）

> Historical execution record — Non-normative — Not an available current assignment

2026-07-22の本改訂以前、本文書はCodex（ChatGPT Plus）、Antigravity（Gemini Pro系）、Claude Pro、OpenAI API、GPT-5.6を含む旧契約前提の割当を記載していた。完了済みTask（V2-0〜V2-7、V2-9〜V2-14、V2-15-01〜09、V2-18-01ほか）は当時の割当・当時のモデルで実行されており、その実行記録は遡及的に書き換えない。旧割当表の全文は本ファイルのGit履歴（2026-07-22以前版）を、各Taskの完了evidenceは[docs/roadmap.md](../roadmap.md)と`docs/design-v2/audits/`を参照する。例: `V2-13-06`はGPT-5.6系モデルが主担当として完了し人間承認済みである（歴史記録であり、現在の割当先ではない）。
