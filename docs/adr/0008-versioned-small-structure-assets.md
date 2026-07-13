# 0008: 小規模構造物をversion付きassetと決定論的配置へ限定する

- Status: Accepted
- Date: 2026-07-13

## Context

Phase 6では地形を壊さず少量の人工物を追加し、同じBlueprint、seed、generator versionから同じ結果を再生成する必要があります。AIにblock座標を列挙させる方式、任意schematicを無検証で読む方式、全tileを3次元配列へ展開する方式は、安全性、再現性、メモリ上限を満たしません。

Phase 6開始gateでは、別placement間の領域予約、確認tokenのactor binding、snapshot disk事前見積もりも優先度決定が必要でした。

## Decision

- Java内のversion付きbuilt-in catalogを正本とし、Phase 6では8種類の小規模assetだけを許可する。
- assetはMinecraft version、寸法、配置種別、傾斜上限、terrain-following、block templateを含み、canonical内容のSHA-256を持つ。
- `StructurePlanner`は全体座標で決定論的にanchorとrotationを探索し、水、崖、傾斜、bounds、他structureとの間隔を検査する。`preferredZone`は第1探索の優先条件であり、安全な候補がなければ全zoneへfallbackする。
- 配置できないstructureは警告として省略し、検証済み地形を失敗させない。不正な配置やasset不一致を含むPlanはexport前にエラーとする。
- tile schematicは従来の列streamへ小さなstructure override indexを重ねる。全block配列は作らない。
- Releaseは`structures.json`、`assets/required-assets.json`、利用assetのstandalone `.schem`を保存し、外側artifact checksumとasset semantic checksumの両方を検証する。
- generator versionを`3.0.0-phase6`へ更新する。

運用hardeningの優先度は次のように決定する。

1. world領域予約／overlap拒否: 高。異なるplacement IDの同時実行を許可する前、遅くともPhase 7の複数利用者UIより先に実装する。
2. actor-bound confirmation token: 高。現在は権限で保護されたconsole／command sender向けbearer tokenであり、Web／複数player操作を公開するPhase 7より先に実装する。
3. snapshot disk事前見積もりと予約: 高。500〜1000角をproduction apply可能と宣言する前、Phase 7 worker運用より先に実装する。

## Consequences

- 同じasset checksumとseedで配置を再現でき、Release単体で必要assetを監査できる。
- WorldEdit／FAWEは生成判断に関与せず、最終的なschematic読込とworld配置だけを担当する。
- complex building、custom user asset、block entity、entityはPhase 6の対象外となる。
- preferred zoneへの配置はbest effortであり、fallback配置はpreviewのmagenta marker、validation warning、`structures.json`の`preferredZoneFallback`で人間が確認する必要がある。
- 3項目の運用hardeningが完了するまでは、重複領域へ同時配置せず、権限を限定し、disk空き容量を管理者が監視する。

## Alternatives

- AIが全blockを生成する案: サイズ、検証、再現性の理由で不採用。
- 任意schematicをそのままimportする案: version、危険なblock entity、checksum policyが未定のため後続Phaseへ延期。
- structureごとに別tileを貼る案: terrainとのatomicな検証とUndoが複雑になるため不採用。
