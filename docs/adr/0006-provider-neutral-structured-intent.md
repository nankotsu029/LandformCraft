# 0006: AI Providerを構造化Intent境界へ閉じ込める

- Status: Accepted
- Date: 2026-07-13

## Context

OpenAI Responses API、Claude Messages API、manual JSONはrequest／response形式、認証、usage、error contractが異なります。一方、generatorへprovider型や不安定な応答を渡すと、provider切替で再現性と安全性が壊れます。外部Structured Outputが対応するJSON Schema subsetも完全なLandformCraft契約とは一致しません。

## Decision

すべての設計元を非同期`TerrainDesignProvider` SPIへ接続し、返却型を検証済み`TerrainIntent`と秘密を除いたmetadataへ固定します。

- OpenAI／Anthropic固有mappingは専用adapterへ隔離する
- HTTP、timeout、429／5xx retry、`Retry-After`、local quotaは共通transportで扱う
- APIへは共通対応subsetのSchemaを送り、完全Schemaとrecord不変条件をローカルで再検証する
- manual import／fixtureも同じSPIを通す
- 成功結果はchecksum付きDesign Package、状態はatomic job JSONへ分離する
- APIキー、prompt本文、error本文を監査artifactへ保存しない
- model IDは呼出側が明示し、自動でlatest modelを選ばない

## Consequences

Providerを替えても`BlueprintCompiler`以降は変わらず、同じIntent／seedは同じgenerator checksumになります。外部APIがSchema制約を一部扱えなくても範囲外値は生成前に拒否されます。HTTP API変更はadapterの更新が必要です。ローカルtoken budgetはprocess内であり、永続的な課金上限にはprovider側billing controlが別途必要です。

## Alternatives

- AI SDK型をcoreへ渡す案はprovider切替と依存隔離を壊すため不採用
- AI応答をそのままBlueprintへ変換する案はSchema／semantic errorを生成処理へ持ち込むため不採用
- provider価格表を埋め込み通貨上限を計算する案は価格・model変動が大きいため不採用
