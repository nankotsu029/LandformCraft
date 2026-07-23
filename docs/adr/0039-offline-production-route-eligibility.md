# 0039: offline production route eligibility（Paper非昇格の公開 export／CLI 配線）

- Status: **Accepted**（2026-07-23 人間承認、**候補 A 採択**）
- Date: 2026-07-23（起草・採択同日）
- Decision scope: V2-15-10 着手前の Gate 0。採用後の適用範囲は V2-15-10〜45 の公開配線 leaf（`V2-15-46` catalog cleanup は offline production route に触れる場合のみ）、およびそれらが共有する dispatch／（採否により）current-state 契約
- Depends on: V2-15-02（current-state projection）、V2-15-05（production-dispatch-registry-v1）、V2-15-06〜09（shared pipelines／foundation adapter）、[全地形カタログ監査](../audits/terrain-catalog-full-audit-2026-07-22.md)、[ADR 0038](0038-macro-foundation-contract.md) D4 per-leaf 確定義務
- Blocks（解消済み）: V2-15-10 `RIVER`＋meandering public wiring は2026-07-23の採択・完了により本ブロックを解消した。`V2-15-11`〜`46`の同一 offline production route パターンに依存する後続配線 leaf は、`V2-15-10`が確立したパターンをコピーする形で引き続き本 ADR の Accepted 契約に従う

## Context

V2-15 の公開配線 leaf は、既存 plan／generator を **公開入力→validation→plan→generator→preview→Release strict directory／ZIP→CLI／Paper status** へ接続する。監査§4 は hydrology／environment 等について「production export dispatch／CLI。**Paperは`EXPERIMENTAL`**」と明示し、Task Index §15.1 は **Paper `SUPPORTED` 昇格を V2-17 専管**とする。

一方、現行実装は次の結合になっている。

1. [`CurrentFeatureStateRegistryV2`](../../src/main/java/com/github/nankotsu029/landformcraft/core/v2/catalog/CurrentFeatureStateRegistryV2.java) の `PRODUCTION_CONNECTED` は `export==SUPPORTED` **かつ** `paper_apply==SUPPORTED` のときだけ成立する（現状 coastal 4 種のみ）。
2. [`ProductionDispatchRegistryV2`](../../src/main/java/com/github/nankotsu029/landformcraft/core/v2/export/ProductionDispatchRegistryV2.java)（`production-dispatch-registry-v1`）は route を `PRODUCTION_CONNECTED` に限定し、それ以外の kind への route 追加を fail closed で拒否する。

このままでは、Paper を昇格せずに V2-15-10 以降を production dispatch へ載せられない。`PRODUCTION_CONNECTED` の意味を「export だけで足りる」へ書き換える案は、V2-15-02／05 が固定した **Paper 込みの完全接続** と、V2-18-12 gate が pin する coastal Paper exact set を曖昧にする。

したがって、**既存 `PRODUCTION_CONNECTED` の意味は変えない**前提で、offline（Paper 非 `SUPPORTED`）な production export／CLI 配線を許す route eligibility を、次のどちらか一方で導入する必要がある。どちらを採るか自体が本 ADR の Decision である。

## 凍結（両候補共通・本 ADR が Accepted でも変更しない）

1. **`PRODUCTION_CONNECTED` の意味を変えない。** export と `paper_apply` がともに `SUPPORTED` である Paper 込みの完全接続を指す。現状の coastal 4（`SANDY_BEACH`／`BREAKWATER_HARBOR`／`HARBOR_BASIN`／`ROCKY_CAPE`）がこの集合である。
2. **Paper `SUPPORTED` 昇格はしない。** V2-17 と実機証拠専管。`MacroFoundationPhaseGateV2Test` が pin する `paper_apply: SUPPORTED` exact set を本 ADR および V2-15 配線 leaf で広げない。
3. **既存 current-state enum 値の意味変更・削除・統合をしない。** 候補 B を採る場合も **新値の追加** のみとし、既存値の再定義は禁止する。
4. **後続 leaf は本 ADR が採った一方のパターンだけをコピーする。** A と B の混在、leaf ごとの ad-hoc side door 増殖は禁止する（ADR 0037 の foundation adapter は既採決の例外として維持し、本 ADR の offline production route とは別経路のままとする）。
5. **本 ADR が `Proposed` の間は、V2-15-10 以降の production wiring 実装（dispatch route 追加、catalog export 昇格を伴う公開縦経路、CLI capability 接続、composition PROVISIONAL→NORMATIVE、intent-conformance portfolio case 追加）を開始しない。**

## Decision（人間承認で確定する）

> **採択（確定）: 候補 A を採用する。** 2026-07-23 の人間承認（ADR 全文レビュー＋候補 A の明示採択）により Status を `Accepted` とする。候補 B は不採択であり、契約骨子は記録として本節に残す（Alternatives 相当）。V2-15-10 以降の配線実装はこの採択をもって開始可能となった。

### 推奨: 候補 A — dispatch 側で offline production route を追加

1. **`production-dispatch-registry-v1` → `production-dispatch-registry-v2` へ contract bump する。**
2. route を二クラスに分ける。
   - **`PRODUCTION_CONNECTED` route（既存）:** current-state の `PRODUCTION_CONNECTED` と exact cover を維持。Paper 込み完全接続。意味・coastal 4 不変。
   - **`OFFLINE_PRODUCTION` route（新規）:** Paper 非 `SUPPORTED` のまま production export／CLI に載せる kind。資格条件（Accepted 後の実装で test 固定）:
     - dedicated module binding（diagnostic のみは不可。未登録なら同一 leaf で dedicated binding を登録する — 例: `RIVER` は現状 diagnostic binding）
     - Feature Support Catalog の `export==SUPPORTED`（必要なら同一 leaf で export 列を `SUPPORTED` へ上げる — 例: `RIVER` は現状 `PARTIAL`）
     - pipeline が完全な generator／validator／preview／export handler chain を宣言
     - unknown／partial／未登録は artifact 生成前に fail closed
   - **catalog 列は必要条件であって十分条件ではない。** offline route への追加は各配線 leaf の dispatch registry への**明示 handler chain 登録によってのみ**行われる。現時点で catalog が `export==SUPPORTED` かつ `paper_apply!=SUPPORTED` を満たす既存16 kind（`ALPINE_MOUNTAIN_RANGE`／`GLACIAL_MOUNTAIN_RANGE`／`CANYON`／`CAVE_NETWORK`／`CORAL_REEF`／`DELTA`／`FJORD`／`LAKE`／`LUSH_CAVE`／`MANGROVE_WETLAND`／`MEANDERING_RIVER`／`OVERHANG`／`SKY_ISLAND_GROUP`／`TIDAL_CHANNEL_NETWORK`／`VOLCANIC_ARCHIPELAGO`／`WATERFALL`）が、契約 bump の時点で自動的に route へ編入されることはない。
   - **`paper_apply` の非 `SUPPORTED` 域内の遷移（`UNSUPPORTED`↔`EXPERIMENTAL`）は本 ADR の対象外**で、各 leaf の Scope 判断とする。禁止されるのは `SUPPORTED` への昇格のみ（凍結 2）。
3. **`CurrentFeatureStateRegistryV2` の `PRODUCTION_CONNECTED` 定義・分類規則・contract version は維持する。** offline 配線 kind は current-state 上は従来どおり `OFFLINE_OR_PLAN_LEVEL`（または現行分類）のままでよい。dispatch が offline production 資格を独自に判定する。ただし [current-feature-state-machine-registry.md](../design-v2/current-feature-state-machine-registry.md) の projection block（binding 件数・投影 SHA-256）は、各 leaf の catalog／binding 変更（dedicated 登録、export 昇格）に伴い**同一 commit で更新される**。これは同 registry が要求する正常な doc 同期であり、不変なのは enum 値の意味・分類規則・contract version である。
4. **CLI／Paper status smoke** は offline production route を選択できるが、Paper placement lifecycle の `SUPPORTED` 経路や capability 昇格には接続しない。
5. **V2-15-10** が本 Decision の最初の適用 leaf である。承認後、契約 bump と `RIVER`／meander wiring を同じ Task 完了単位で整合させる（ユーザーが契約だけ先行コミットを指示した場合を除く）。

### 不採用推奨: 候補 B — state 側に新分類を追加

候補 B は次を行う案である（本推奨では採用しないが、人間が B を選んだ場合の契約骨子）。

1. `CurrentFeatureStateRegistryV2.CurrentState` に **新 enum 値**を追加する（例: `OFFLINE_PRODUCTION_ROUTE`）。既存値の意味は変更しない。
2. 分類規則例: dedicated module + `export==SUPPORTED` + `paper_apply != SUPPORTED`（および child／enum-only 除外）。
3. current-state **contract version bump**、[`current-feature-state-machine-registry.md`](../design-v2/current-feature-state-machine-registry.md) projection と SHA を同一変更で同期。
4. `ProductionDispatchRegistryV2` は `PRODUCTION_CONNECTED` **または** 新分類を route 許可集合とする（必要なら dispatch も version bump）。

候補 B を不採用推奨とする理由:

- **分類規則例は「実際に配線済みか」を原理的に表現できない。** catalog 実測で `export==SUPPORTED` かつ `paper_apply!=SUPPORTED` の kind は既に16種あり（候補 A 節に列挙）、いずれも production 未配線である。B の規則はこれらを**配線ゼロのまま契約 bump の初日に新分類へ移してしまう**。かつ current-state registry は dispatch の handler chain を参照できない（依存方向は dispatch→state であり、逆参照は循環）ため、「配線済み」を state 側の分類規則として書く方法が存在しない。
- current-state registry は V2-15-02 で **catalog／module／Schema の read-only projection** として固定されており、application の route 政策をここに持ち込むと「現状の事実投影」と「公開 wiring 政策」が同一 enum に混ざる。
- offline production の資格は本来 V2-15-05 が持つ **production-dispatch application registry** の関心事である。
- 候補 B は state contract と（多くの場合）dispatch contract の二重 bump になり、投影 SHA／CI の変更面が広い。

人間が候補 B を明示採択した場合は、本節を B の確定文に差し替え、A を Alternatives へ移す。

## Consequences

### 候補 A 採用時

- V2-15 配線 leaf は Paper を上げずに offline production route へ kind を追加できる。
- `PRODUCTION_CONNECTED` と Paper exact set の回帰ピンは維持できる。
- current-state 文書の「production-connected=4」は、Paper 完全接続の意味のまま残り、offline 配線数は dispatch registry／docs 側で数える。ただし projection block の binding 件数（dedicated／diagnostic）と投影 SHA-256 は各 leaf の catalog／binding 変更に伴い同一 commit で更新される（Decision A-3）。
- dispatch contract bump に伴い、既存 `production-dispatch-registry-v1` を前提にした test／docs（registry checksum pin を含む）を v2 へ同期する必要がある。

### 候補 B 採用時（人間が選んだ場合）

- current-state 投影が offline production 集合を一等公民として見えるようになる（監査・CI での数え上げが容易）。
- state／projection／SHA／（多くの場合）dispatch の同時変更が必須で、V2-15-02 契約の解釈を拡張する。

### 共通

- ADR 0037 foundation adapter（registry に第二 `["surface-2_5d"]` を載せない）は維持する。foundation leaf（例: V2-15-21〜）が offline production route へ載るか、adapter 経路を拡張するかは各 leaf Scope と本 ADR の採用パターンに従い、第二 capability key は作らない。
- composition PROVISIONAL→NORMATIVE と intent-conformance portfolio case は、本 ADR とは独立した V2-18-12 per-leaf 義務として各配線 leaf が負う。

## Alternatives considered

### `PRODUCTION_CONNECTED` の定義から `paper_apply==SUPPORTED` を外す

既存値の意味変更であり、本 ADR の凍結条件と V2-15-02／18 gate の用語を壊す。**禁止。**

### Paper `SUPPORTED` を V2-15 で昇格して現状 dispatch に載せる

Task Index §15.1 および V2-17 専管に反する。実機証拠なしの昇格。**禁止。**

### 家族ごとの side-door Application Service だけを増やす（registry に載せない）

ADR 0037 は capability key 衝突回避の例外として正当化済みだが、V2-15-10 以降の多数 leaf に同型 side door を増やすと「公開配線」の単一 dispatch 正本が崩れる。本 ADR で offline production route を一箇所に固定する。**原則不採用**（0037 既存経路は維持）。

### 新 Release capability や新 artifact format

監査 DR-2／V2-15 停止条件に触れる。**不採用。**

## Acceptance / 実装開始条件

1. 人間が本 ADR 全文をレビューし、**候補 A または候補 B** を明示採択する。
2. Status を `Accepted` に更新し、採択日・採択候補を Decision 節へ記録する。
3. その後に限り `V2-15-10` の配線実装を開始する。11–28 は 10 で確立した同一パターンのみをコピーする。

**充足記録（2026-07-23）:** 条件 1・2 は同日の人間承認（候補 A の明示採択）で充足した。`V2-15-10` の配線実装は開始可能である。

## References

- [terrain-catalog-full-audit-2026-07-22](../audits/terrain-catalog-full-audit-2026-07-22.md) §4／§10.1
- [current-feature-state-machine-registry](../design-v2/current-feature-state-machine-registry.md)
- [task-index §15](../design-v2/task-index.md)
- [ADR 0037](0037-foundation-surface-to-surface-2_5d-mapping.md)
- [ADR 0038](0038-macro-foundation-contract.md)
- [V2-18-12 phase gate](../design-v2/audits/v2-18-12-phase-gate.md)
