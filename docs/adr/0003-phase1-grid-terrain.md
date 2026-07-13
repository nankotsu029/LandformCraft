# 0003: Phase 1地形データを2次元gridとして生成する

- Status: Accepted
- Date: 2026-07-13

## Context

最大1000×1000の全blockを高さ方向まで展開すると、memory使用量とschematic生成前の保持時間が大きくなります。一方、Phase 1のpreviewと検証には列ごとの表面高、水深、素材、featureがあれば十分です。

## Decision

- height、水深、featureをimmutableなrow-major `IntGrid`で保持する
- materialをbyte ordinalのimmutable gridで保持する
- noiseと水系はtile内座標ではなくrelease全体のglobal X/Zで計算する
- tileは範囲、margin、checksumを持つ`TilePlan`として表し、block listを保持しない
- terrain checksumはgenerator version、seed、bounds、grid内容からSHA-256で計算する
- Phase 1 CLIは`.schem`を作らず、PNGと検証結果だけを出力する

## Consequences

- 1000×1000でも主要mapは数十MB以内に収めやすい
- 同じBlueprintとseedの結果をchecksumで比較できる
- Phase 2で各列をblockへmaterializeする処理が別途必要になる
- tile単独生成は16 block marginを計算し、中央gridだけを返す。全体生成との全セル一致をtestする

## Alternatives

### 全blockを一括配列にする

処理は単純ですが、空気や同じ岩層まで保持するため不採用です。

### `List<StructureBlock>`を正本にする

object overheadが大きく、1000×1000に適さないため不採用です。
