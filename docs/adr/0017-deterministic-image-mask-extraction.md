# 0017: 通常画像からの決定論的land-water抽出をdraft境界として導入する

- Status: Accepted
- Date: 2026-07-17

## Context

v1の画像経路は「画像 → AI要約 → TerrainIntent v1」であり、位置・形状・比率は保存されない。V2-1は厳密な数値PNG（`LAND_WATER_MASK`／`HEIGHT_GUIDE`／`ZONE_LABEL_MAP`）を直接fieldへcompileする経路を完成させたが、その数値PNGは利用者が外部ツールで用意する前提である。再設計目標「画像・スケッチが示す位置・形状・水系を可能な限り忠実に再構築する」には、通常のRGB画像・スケッチから制約データ候補を**AIを介さず決定論的に**取り出す境界が欠けている。

同時に、既存の設計制約（V2-6-11の非Scope）は「画像からhard geometryの暗黙生成」を禁止している。抽出結果が無確認でhard constraintになる設計は採用できない。

## Decision

1. **`format.v2.constraint.extract`を新しい抽出境界とする。** 初期実装は`ImageLandWaterExtractorV2`（`image-land-water-extract-v1`）で、sanitized ARGBピクセルからinteger-onlyの固定規則でWATER/LAND/UNKNOWNと信頼度0..255を分類する。
   - alpha < 128 → UNKNOWN（信頼度0）
   - `2*blue - red - green >= 32` → WATER（信頼度 = min(255, dominance)）
   - `2*blue - red - green <= 0` → LAND（信頼度 = min(255, 1 - dominance)）
   - 中間帯 → UNKNOWN（信頼度0）
   閾値はALGORITHM_VERSIONの下で凍結し、変更は新version文字列を必須とする。
2. **出力は`ExtractedMaskDraftV2`（draft）であり、SOFT提案に限る。** draftはsource checksumとtagged SHA-256 semantic checksumを持ち、同一入力から常に同一checksumを再生する。
3. **hard constraintへの昇格は明示操作のみとする。** draftはV2-1の厳密な数値PNG経路（strict decoder、fixed-point、provenance）を再経由して初めてconstraint mapになる（V2-7-04）。暗黙昇格・fallback昇格は禁止する。
4. **admissionを割当前に行う。** trusted ceilingは4096 px/辺、16Mピクセル、aspect 32、working 64 MiB（class+confidenceで2 byte/pixel）で、呼び出し側から拡大できない。cancelは行単位で観測する。
5. **AI提案との関係**: 将来AIがmask候補を提案する場合も、同じstrict decoder・同じdraft/昇格契約を通す。AI出力を直接fieldへ書く経路は作らない。
6. **secure入力封筒との接続はV2-7-02の別Taskとする。** 本ADR時点の抽出coreは純関数（ピクセル配列入力）であり、ファイル入力はv1 `ReferenceImageProcessor`同等のpath/link/decode検査を持つ専用envelopeを通してのみ接続する。

## Consequences

- 通常画像からの抽出はcoreのみ`EXPERIMENTAL`で存在し、CLI/Paper/Requestへは未接続である。接続・draft artifact・preview・昇格・height/zone抽出・多入力競合解決はV2-7-02〜07で段階導入する。
- 抽出規則は単純な色規則であり、航空写真・スケッチの様式差で誤分類し得る。これはdraft+confidence+明示昇格の契約で吸収し、規則の改良は新algorithm versionとして追加する（既存draftの再現性を壊さない）。
- 決定論・budget・checksumのテスト様式は既存規約（thread/locale/timezone不変、trusted ceiling、CancellationException）へ従う。

## Alternatives

- **AI(vision)へ直接mask生成を依頼**: 再現性がなく、同一入力・同一seedで同一結果という不変条件を満たせないため、正本経路としては棄却（提案入力としては上記5で許容）。
- **抽出結果を直接hard constraint化**: 暗黙のhard geometry生成であり既存の安全境界に反するため棄却。
- **k-meansなど反復クラスタリング**: 反復回数・初期値・浮動小数で決定論が壊れやすく、初期版としては固定閾値規則を採用。zone抽出（V2-7-06）では有界・整数のみの量子化を別途設計する。
