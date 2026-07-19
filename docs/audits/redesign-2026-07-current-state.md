# 2026-07-17 再設計調査: 現行システムの実態

> Status: 調査記録（2026-07-17の作業ツリー、branch `fix/all`、HEAD `5d2df6c`）。再設計判断は [ADR 0015](../adr/0015-adopt-v2-foundation-three-track-roadmap.md)〜[0017](../adr/0017-deterministic-image-mask-extraction.md) を正本とする。

## 1. 調査範囲と方法

- `src/main` 515 file、`src/test` 74 file、`schemas/` 27件、`examples/` 60件超、`docs/` 全体、`AGENTS.md`、`.agents/skills/` 7 Skill、Gradle設定を確認した。
- 判断の根拠は実行コード・テスト・監査文書の順とし、文書の完了主張はテスト実行で裏を取った。
- 調査開始時に`git status`はclean、`./gradlew build -x test`と`./gradlew test`はともに成功（exit 0）である。

## 2. 実装済みと確認できた事実

| 領域 | 実態 | 根拠 |
|---|---|---|
| v1製品 | 0.9.0-beta.1 RC。generator `3.0.0-phase6`、Release format 1、snapshot/rollback/Undo/Recovery、WorldEdit 7.3.19 smoke済（FAWE単独・500角実測は未完） | `docs/roadmap.md` Beta hardening、`docs/audits/phase-6-beta-audit.md`、`PlacementApplicationServiceTest` |
| V2-0/V2-1 | version dispatch、TerrainIntent v2/WorldBlueprint v2、strict constraint map（3 role）、`LFC_GRID_V1` sidecar＋bounded window、manual path | `format/v2/`、`core/v2/`、`NumericConstraintMapInputTest`、`LfcGridFormatV1Test` |
| V2-2 | 4 coastal feature、transition compositor、独立validator/11 preview、offline Sponge v3 tile、Release 2 core＋`surface-2_5d`。Phase gate監査済 | `generator/v2/coast/`、`docs/design-v2/audits/v2-2-phase-gate.md`、`AzureCoastPhaseGateV2Test` |
| V2-3 | Hydrology IR/固定prior、全域basin/D8 routing、river/lake/canyon/waterfall/delta/tidal/fjord/mountain/volcanic骨格、固定3 pass reconciliation、12 preview、`hydrology-plan` capability。Phase gate監査済 | `generator/v2/hydrology/`、`docs/design-v2/audits/v2-3-phase-gate.md`、`HydrologyPhaseGateV2Test` |
| V2-4（進行中 5/15） | geology/lithology/strata/climate/water-conditionの各PlanV2とcompiler。`EXPERIMENTAL` | `model/v2/environment/`、`core/v2/*PlanCompilerV2Test` |
| 品質基盤 | named seed（SHA-256 tagged）、integer-only、whole/tile/thread/locale/timezone不変テスト、trusted ceiling方式のadmission、staging→strict read-back→atomic publish | `NamedSeedDeriverV2`、各`*V2Test` |

## 3. 再設計目標に対する不足（2026-07-17時点）

1. **スケール**: 水平上限`1..1_000`が`GenerationBounds`のほか`ConstraintMapSamplerV2`、`HydrologyRoutingRequestV2`、`CoastalRasterKernelV2`、各coast generator、preview index等15箇所以上へ分散。予算検証も1000角のみ。3000×3000の実行方式（coarse大域計画→streaming tile→部分再生成→分割export）は未定義だった。constraint map decodeは1枚4Mピクセル上限で3072²を収容できない。
2. **画像忠実性**: 通常画像・スケッチはAI要約（reference image role）でのみ利用され、決定論的にmask/height/zoneを抽出する経路がなかった。V2-1の数値PNGは外部準備が前提。
3. **実行体制**: roadmapは単一直列トラックで、`task-execution-guide.md`は単一モデル（GPT-5.6 Sol）前提だった。モデル割当・費用最適化・並行実行規約は未定義だった。
4. **v1既知の限界（継続）**: 集約値TerrainIntent v1、単一water level、column-only 2.5D等は [current-limitations.md](../design-v2/current-limitations.md) が正確に記録済みで、V2-4〜V2-6が解消経路である。

## 4. 再設計での対応（本調査と同時に実施した変更）

- **維持**: v2契約・module catalog・sidecar・Release 2・placement設計・全既存ID/監査（ADR 0015）。
- **追加（EXPERIMENTAL, 未接続）**:
  - scale契約 `model.v2.scale`（`ScaleClassV2`/`ScaleProfileV2`/`TilePlanV2`）＋ `core.v2.scale.ScaleAdmissionV2`（ADR 0016、テスト: `TilePlanV2Test`、`ScaleAdmissionV2Test`）
  - 画像抽出契約 `format.v2.constraint.extract`（`ImageLandWaterExtractorV2`/`ExtractedMaskDraftV2`、ADR 0017、テスト: `ImageLandWaterExtractorV2Test`）
- **roadmap再構築**: Track A（V2-4→V2-5→V2-6）/ Track B（V2-7 画像忠実性）/ Track C（V2-8 スケール）の3トラック依存グラフと、Taskごとのモデル割当（[model-assignment.md](../design-v2/model-assignment.md)）。
- **廃止・置換**: なし（削除したコード・Schemaはない）。`task-execution-guide.md`の単一モデル前提の記述をモデル割当正本への参照に置換した。

## 5. 残存リスク

- LARGE（3000×3000）は契約のみで、生成・予算実測・export分割は未実装（V2-8-02以降）。
- 画像抽出はcoreのみで、secure入力封筒・draft artifact・昇格経路は未実装（V2-7-02以降）。
- Beta hardeningのFAWE単独smoke・500角実測は引き続き`BLOCKED_EXTERNAL`。
- 現行の分散寸法検査はV2-8-02で統一するまで二重管理であり、それまでLARGE値を受理する経路は存在しない（安全側）。
