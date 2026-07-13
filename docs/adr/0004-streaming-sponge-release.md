# 0004: TileをSponge v3へstream出力して検証後に公開する

- Status: Accepted
- Date: 2026-07-13

## Context

最大1000×1000、縦512の全blockをobjectまたは単一配列として保持すると、Phase 1の2次元grid設計を損ない、heap上限と失敗時の復旧が難しくなります。またCLIはPaper／WorldEdit plugin runtimeなしでもReleaseを作る必要があります。

## Decision

- Phase 1のcolumn dataからsurface、subsoil、stone、water、airを要求時にmaterializeする
- tileごとにSponge Schematic v3のNBT byte arrayへ直接stream出力する
- Minecraft 1.21.11の公式DataVersion 4671を固定し、WorldEdit本体を配布JARへshadeしない
- schematic Offsetはzero、tile相対位置はmanifest originを正本にする
- release directoryの全file checksum、Schema、tile coverage、NBTを自己検証する
- fsync後のatomic moveが成功したものだけを公開し、ZIPは別のsibling artifactとして検証する
- WorldEdit 7.3.19のreaderと公開paste operationをtest依存で実行し、形式互換と相対配置を確認する

## Consequences

- block数に比例するdisk I/Oは必要だが、tile全blockのheap保持を避けられる
- CLIはWorldEditの実行環境なしでexport／strict verifyできる
- Paper／FAWEへの実配置はmanifest originを加算するだけの明示的なplacement planへできる
- Phase 2はvanillaの限定paletteで、洞窟、biome、entity、block entityは未対応
- atomic move非対応filesystemでは安全性を優先してexportが失敗する

## Alternatives

### WorldEditをCLIへ同梱してClipboardを生成する

platform registry初期化が必要で、Paper pluginとCLIの依存境界が曖昧になり、WorldEdit本体をshadeしない方針とも衝突するため不採用です。

### 全block配列を先に構築する

実装は単純ですが、最大サイズのmemory budgetに適さないため不採用です。
