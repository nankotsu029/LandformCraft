# How It Works

```text
自然言語／役割付き画像
  → TerrainIntent
  → WorldBlueprint
  → low-resolution layout
  → height／water／material
  → structure planning
  → margin付きtile
  → 8 PNG preview
  → Sponge v3 schematic
  → Release Package
  → PlacementPlan
  → snapshot
  → apply
  → verify
  → Undo／Recovery
```

AIはtheme、zone、地形feature、素材、少数のstructureといった「何を作るか」をTerrainIntentとして返します。AIに1000×1000×高さ分のblockを列挙させると、巨大、不安定、検証不能、prompt injectionやコード実行の危険が増えるため行いません。Java generatorがSchema検証済みIntentを決定論的にblockへ変換します。

WorldBlueprintは正規化したrequest、Intent、candidate seed、generator versionを固定します。同じBlueprint、seed、generator versionならthread数、locale、timezoneによらず同じterrain checksumとstructure placementになります。generator versionが違えば同じseedでも同一とはみなしません。

全worldの3次元配列や巨大block Listは作りません。低解像度layoutを補間し、global X/Zでnoiseをsampleします。河川、侵食など近傍が必要なtileは16 blockのmargin付きで計算し、中央だけを採用するため境界seamを防ぎます。structure overrideもglobal placementから各tileへ投影します。

Previewはoverview、height、water、slope、materials、features、structures、validationの8枚です。structure layerは実際のanchorとfootprintを示し、配置不能はterrain-only warningにします。

Release Packageはrequest、Intent、Blueprint、validation、preview、tile schematic、structure manifest、required asset schematic、manifest、全file checksumを含む持ち運び可能な正本です。directoryでもZIPでもstrict verifierが欠損、改変、危険path、重複tile、NBT上限、version不一致を拒否します。

PlacementPlanはRelease checksum、world UUID、target origin、inclusive bounds、actorを固定します。plan時に領域とdiskをatomic stateへ予約し、token平文ではなく結合情報のhashだけをjournalへ保存します。execute直前に同じ条件を再検査します。

各tileはworldをsnapshotしてchecksumをjournalへ書き、schematicをapplyし、worldが期待値と一致するかverifyします。途中失敗は適用済みtileを逆順復元します。Undoも先に全tileのworld driftを検査します。crashで状態が不確定なら `RECOVERY_REQUIRED`とし、証拠が揃うまでAPPLIEDを推測しません。
