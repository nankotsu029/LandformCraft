# 0019: `environment-fields`を`hydrology-plan`＋`surface-2_5d`依存のRelease 2 capabilityとして固定する

- Status: Accepted
- Date: 2026-07-17
- Decision scope: V2-4-14

## Context

ADR 0014は`hydrology-plan`を`surface-2_5d`依存のRelease 2 capabilityとして固定した。V2-4ではgeology／lithology／strata、climate／water-condition／snow、semantic material／Minecraft palette、ecology、feature material、独立environment validation／10-layer previewがそれぞれsealed artifactとして完成している。これらをhydrology Releaseへ任意追加すると、plan／palette／ecology／previewのどれが欠けてもreaderが推測する余地が残る。一方で直前の`hydrology-plan`／`surface-2_5d` exact setを書き換えるとstrict回帰を壊す。

## Decision

V2-4-14で`environment-fields`を有効化する。このcapabilityは単独では受理せず、必ず`hydrology-plan`と`surface-2_5d`と併用する。manifestの`requiredCapabilities[]`はcanonical sortにより`["environment-fields","hydrology-plan","surface-2_5d"]`だけを許す。

environment側のversion 1 artifact type／pathは次に固定する。

- `geology-plan-v2` → `environment/geology-plan.json`（Blueprint geologyと一致）
- `lithology-plan-v2` → `environment/lithology-plan.json`
- `strata-plan-v2` → `environment/strata-plan.json`
- `climate-plan-v2` → `environment/climate-plan.json`
- `water-condition-plan-v2` → `environment/water-condition-plan.json`
- `snow-plan-v2` → `environment/snow-plan.json`（climate checksum binding）
- `material-profile-plan-v2` → `environment/material-profile-plan.json`
- `minecraft-palette-plan-v2` → `environment/minecraft-palette-plan.json`（material checksum binding）
- `ecology-plan-v2` → `environment/ecology-plan.json`
- `feature-material-profile-plan-v2` → `environment/feature-material-profile-plan.json`
- `environment-validation-artifact-v2` → `environment/validation.json`
- `environment-preview-index-v2` → `environment/previews/index.json`
- 固定10枚の`environment-preview-png-v1` → `environment/previews/<layer>.png`

surface／hydrology側の必須集合とpathはADR 0013／0014のまま不変である。strict verifierはsurface→hydrology→environmentの順で照合し、Blueprint一致、palette／material／snow／ecologyのchecksum binding、validation hard-pass、preview寸法を検査する。missing／extra／future type/version、palette checksum改変、`environment-fields`単独、未知capabilityは拒否する。

publisherはsealed sourceだけをstagingへcopyし、directory／ZIPをstrict read-backしてからatomic publishする。raw pathはportable manifestへ保存しない。feature lifecycleの`SUPPORTED`昇格、sparse-volume、Paper applyは対象外である。

## Consequences

- `surface-2_5d`単独および`hydrology-plan`＋`surface-2_5d` Releaseは従来どおりstrict verifyできる。
- environment payloadが揃わなければ`environment-fields` Releaseをpublishできない。
- このADRの導入時点ではenvironment featureを`EXPERIMENTAL`のままにし、`SUPPORTED`判定を`V2-4-15`へ残す。
- format 2 coreの空capability意味は変更しない。

## Alternatives considered

### environment artifactだけを単独capabilityにする

hydrology／surface Blueprint／tile contextなしでは最終地形contextが不足し、受信側が推測する必要があるため不採用。

### hydrology exact setへenvironment fileを暗黙追加する

直前`hydrology-plan`の必須集合と回帰を壊すため不採用。
