# 0014: `hydrology-plan`を`surface-2_5d`依存のRelease 2 capabilityとして固定する

- Status: Accepted
- Date: 2026-07-17
- Decision scope: V2-3-14

## Context

ADR 0013は`surface-2_5d`だけを非empty Release 2 capabilityとして固定した。V2-3ではhydrology plan、global routing field、bounded reconciliation、独立validation、固定12枚のdiagnostic previewがそれぞれsealed artifactとして完成している。これらをsurface Releaseへ任意追加すると、plan／graph／reconciliation／previewのどれが欠けてもreaderが推測する余地が残る。一方でformat 2 coreの空capability契約や`surface-2_5d` exact setを書き換えると、直前Releaseのstrict回帰を壊す。

## Decision

V2-3-14で`hydrology-plan`を有効化する。このcapabilityは単独では受理せず、必ず`surface-2_5d`と併用する。manifestの`requiredCapabilities[]`はcanonical sortにより`["hydrology-plan","surface-2_5d"]`だけを許す。

hydrology側のversion 1 artifact type／pathは次に固定する。

- `hydrology-plan-v2` → `hydrology/plan.json`（plan semantic checksum）
- `hydrology-routing-artifact-v2` → `hydrology/routing/index.json`
- indexが列挙する全`hydrology-field-grid-v1` → `hydrology/routing/<relativePath>`
- `hydrology-reconciliation-plan-v2` → `hydrology/reconciliation-plan.json`
- `hydrology-reconciliation-artifact-v2` → `hydrology/reconciliation-artifact.json`
- `hydrology-validation-artifact-v2` → `hydrology/validation.json`
- `hydrology-preview-index-v2` → `hydrology/previews/index.json`
- 固定12枚の`hydrology-preview-png-v1` → `hydrology/previews/<layer>.png`

surface側の必須集合とpathはADR 0013のまま不変である。strict verifierはsurface payloadを先に照合し、その後hydrology plan＝Blueprint内plan、routing graph／field＝plan／Blueprint寸法、reconciliation＝Blueprint、validation／preview＝Blueprint寸法を検査する。missing／extra／future type/version、graph checksum改変、`hydrology-plan`単独、未知capabilityは拒否する。

publisherはsealed sourceだけをstagingへcopyし、directory／ZIPをstrict read-backしてからatomic publishする。raw pathはportable manifestへ保存しない。feature lifecycleの`SUPPORTED`昇格、environment／volume capability、Paper applyは対象外である。

## Consequences

- `surface-2_5d`単独Releaseは従来どおりstrict verifyできる。
- hydrology payloadが揃わなければ`hydrology-plan` Releaseをpublishできない。
- このADRの導入時点ではHydrology featureを`EXPERIMENTAL`のままにし、`SUPPORTED`判定を`V2-3-15`へ残した。
- format 2 coreの空capability意味は変更しない。

## Implementation status

`V2-3-15`の統合監査はdirectory／ZIP、tampering、resource／cancel、v1／V2-2回帰を再検証し、`hydrology-plan`をoffline `SUPPORTED`とした。feature lifecycleは別判定であり、full completionしたRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDだけを`SUPPORTED`とした。WATERFALL、ALPINE／GLACIAL mountain、VOLCANIC_ARCHIPELAGOとPaper applyはこのADRの外で未完のままである。

## Alternatives considered

### hydrology artifactだけを単独capabilityにする

surface Blueprint／tile／constraint bindingなしでは最終地形contextが不足し、受信側が推測する必要があるため不採用。

### surface exact setへhydrology fileを暗黙追加する

直前`surface-2_5d`の必須集合と回帰を壊すため不採用。

### format 2 coreへhydrologyを混在させる

空capability契約とADR 0012のcontainer分離を壊すため不採用。
