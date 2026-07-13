# 0001: LandformCraftの境界と正式名称

- Status: Superseded in part by ADR-0002
- Date: 2026-07-13

## Context

初期案では`AIWorldComposer`という仮称とGyoSai由来の設計がありました。一方、正式なPaper Plugin名、repository directory、main classとしてLandformCraftが指定されています。また、AIに直接blockを生成させる方式はresponse size、品質、再現性、安全性に問題があります。

## Decision

- 正式な製品／plugin名を`LandformCraft`とする
- base packageを`com.github.nankotsu029.landformcraft`とする
- 指定されたPaper main `com.github.nankotsu029.landformcraft.Landformcraft`を外部契約として維持する
- GyoSaiとはコード、model、設定、用語を分離する
- AIはstructured `TerrainIntent`だけを生成する
- Javaの決定論的generatorがblockへ変換する
- core、provider、format、Paper、CLIの責務を分離する（物理的なGradle module分割はADR-0002でpackage分離へ変更）

## Consequences

- ProviderをOpenAIからAnthropic／manual importへ変更してもgeneratorを再実装しない
- Minecraft serverなしでgeneratorとformatをtestできる
- Paper pluginは薄く保てる
- 責務境界とcontract管理の初期costが増える
- `Landformcraft`というclass名は通常のJava命名慣例とは異なるが、明示契約を優先する

## Alternatives

### GyoSaiWorldComposerを直接汎用化

ゲーム固有model、validation、zone設定が汎用coreへ混入するため不採用です。

### 単一の巨大Paper plugin

server起動なしのtest、外部Worker、CLI、provider交換が難しくなるため不採用です。

### AIに全blockまたはJavaコードを生成させる

token量、非決定性、validation、任意コード実行riskのため不採用です。
