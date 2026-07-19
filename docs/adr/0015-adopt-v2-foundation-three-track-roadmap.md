# 0015: 2026-07再設計はv2基盤を維持し、3トラック依存グラフへroadmapを再構築する

- Status: Accepted
- Date: 2026-07-17

## Context

2026-07-17の全面再設計指示は、自然言語・画像・マスク・スケッチ・構造化データから、小規模〜約3000×3000のMinecraft地形を高忠実・高再現・安全に生成する基盤を要求した。指示は既存Design v2の維持を目的とせず、必要なら全面置換も許可している。

再設計に先立つ調査（[redesign-2026-07-current-state.md](../audits/redesign-2026-07-current-state.md)）で、現行リポジトリの実態を確認した。

- V2-0〜V2-3のPhase gateは監査付きで完了し、V2-4は5/15まで完了している。
- 決定論（named seed、integer-only、whole/tile/thread/locale/timezone不変）、有界メモリ（bounded window、事前admission）、セキュリティ（strict decode、path/link/TOCTOU、checksum、atomic publish）、安全配置（reservation/confirm/snapshot/rollback/Recovery）が既にコードとテストで強制されている。
- 全テストとclean buildは緑である。

一方、再設計指示の目標に対して次の不足が事実として存在する。

1. 水平上限1000×1000が15箇所以上へ分散hard-codeされ、3000×3000への実行方式（低解像度大域計画、streaming、部分再生成、disk-backed中間データの上限拡大）が未定義。
2. 通常画像・スケッチから制約データへの決定論的抽出経路がない。V2-1のconstraint mapは外部で用意した数値PNGのみ受理し、通常画像はAI要約（reference image）でのみ利用される。
3. roadmapが単一直列トラックで、実行エージェント（モデル）割当と費用最適化の規約がない。

## Decision

1. **v2アーキテクチャ（TerrainIntent v2 / WorldBlueprint v2 / built-in module catalog / LFC_GRID_V1 sidecar / Release format 2 / placement状態機械）を再設計の基盤として維持する。** 全面置換は、監査済み完了資産と決定論・安全性のテスト網を根拠なく破棄することになり、再設計指示自身の判断優先順位（リポジトリの事実 > 構想）に反するため棄却する。
2. **roadmapを3トラックの依存グラフへ再構築する。**
   - Track A（コア地形）: V2-4 Environment → V2-5 Sparse volume → V2-6 Placement（既存ID・完了証拠を凍結維持）
   - Track B（画像忠実性）: 新Phase V2-7。決定論的抽出、draft/confidence、明示昇格、多入力競合解決、source-to-result差分
   - Track C（スケール）: 新Phase V2-8。scale class契約、寸法policy統一、LARGE予算、coarse計画、streaming/resume、export分割
   - Track B/CはTrack Aの完了を前提とせず、依存が実際にあるTaskだけ個別に前提を宣言する。
3. **完了済みTask ID・Phase番号・監査証拠は再採番しない。** 新規作業は新Phase ID（V2-7、V2-8）と新Taskとして追加する。
4. **並行実行の規約を追加する。** 各トラックの次Taskは別エージェントが並行実行できる。同一ファイルを複数エージェントが同時編集することは禁止し、`format.v2.release`等の共有領域を変更するTaskは直列にする。
5. **Task実行のモデル割当を [model-assignment.md](../design-v2/model-assignment.md) を正本として規定する。**

## Consequences

- 既存のv1凍結、v2不変条件、Phase gate規則（`V2-x`末尾監査だけが親Phaseを閉じる）は変更されない。
- 新境界の初期実装として、scale契約（ADR 0016）と画像抽出契約（ADR 0017）を`EXPERIMENTAL`で導入した。
- AGENTS.md、task-execution-guide、skillsは3トラック並行とモデル割当を反映して改訂する。
- 「roadmapを1から」は、依存関係の再導出と前進計画の再構築として実施し、完了記録の改竄は行わない。完了主張はすべて監査文書とテストへの参照を持つ。

## Alternatives

- **全面置換（新アーキテクチャをゼロから実装）**: 完了済み32 Taskとテスト網を失い、コンパイル不能・二重Schema期間が長期化するため棄却。
- **既存roadmapの単純継続（V2-4→V2-5→V2-6のみ）**: 3000×3000と画像忠実性という再設計指示の中心目標に対する経路が存在しないままになるため棄却。
- **画像忠実性・スケールをV2-6完了後の後置Phaseとする**: 両者はplacementへ依存しない部分が大きく、直列化は並行リソース（複数エージェント運用）を浪費するため棄却。
