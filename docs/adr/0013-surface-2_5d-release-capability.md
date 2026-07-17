# 0013: `surface-2_5d`をstrictなRelease 2 capabilityとして固定する

- Status: Accepted
- Date: 2026-07-15
- Decision scope: V2-2-11

## Context

ADR 0012はRelease format 2のcontainerだけを導入し、空のcapability／artifact indexしか許可しなかった。V2-2ではcoastal field、独立validation、11枚のdiagnostic preview、offline Sponge v3 tileがそれぞれ別artifactとして完成している。しかし、これらを任意の組合せでZIPへ入れると、Blueprintとの対応、field sidecarの意味、preview、tile block streamのどれが欠けてもreaderが推測してしまう。

## Decision

V2-2-11で`surface-2_5d`を唯一の非empty Release 2 capabilityとして有効化する。capabilityを持つmanifestは、次のversion 1 artifact typeをexactに持つ。

- `generation-request-v2`、`terrain-intent-v2`、`world-blueprint-v2`
- `constraint-field-index-v2`とindexが列挙する全`constraint-field-grid-v1`
- `coastal-validation-artifact-v2`
- `coastal-preview-index-v2`と固定11枚の`coastal-preview-png-v1`
- 1個以上の`offline-tile-artifact-v2`と対応する`sponge-schematic-v3`

file pathも固定する。request／intent／Blueprintは`source/`と`blueprint/`、fieldは`constraints/`、validationは`validation/coastal-validation.json`、previewは`previews/`、tile metadata／schematicは`tiles/`へ置く。field／preview／tileの可変集合はそれぞれのsealed index／metadataが唯一の正本であり、manifestにはそれらの物理fileも個別に列挙する。

strict verifierは、core indexのpath／checksum／budget検査後に、request→intent→Blueprint、Intent binding→field index、Blueprint→validation／preview／tile、tile metadata→Sponge v3 canonical block streamを検査する。field／preview／tile sidecarに未列挙／余分なfileを許さず、tileはBlueprint X/Z全体を重複・穴なしで覆う。hard validation error、future capability/type/version、semantic checksum不一致は拒否する。

publisherは既にsealedなsource artifactだけをstagingへcopyし、staging directoryとZIPをstrict read-backしてからatomic publishする。raw source pathはportable manifestへ保存しない。Release format 1のreader／writer、v1 Schema、generator `3.0.0-phase6`、Paper applyへは接続しない。

## Consequences

- field／preview／tileの開始時点で十分なartifactが揃っていなければ`surface-2_5d` Releaseをpublishできない。
- capability単体の追加時点では4 coastal featureを`SUPPORTED`へ昇格させない。後続のV2-2-12統合監査が全gateを再確認したため、現在はoffline生成／検証／export経路に限って4 featureとcapabilityを`SUPPORTED`としている。Paper applyは引き続き対象外である。
- `hydrology-plan`は[ADR 0014](0014-hydrology-plan-release-capability.md)で`surface-2_5d`依存の別capabilityとして有効化した。`environment-fields`、`sparse-volume`は未定義のまま拒否する。

## Alternatives considered

### tileまたはpreviewだけを任意に収容する

Blueprint／validation／fieldとのsemantic bindingを失い、受信側が不足物を推測する必要があるため不採用。

### Release format 1へfieldとtileを追加する

v1 strict allowlistと既存checksumを変更し、互換境界を壊すため不採用。

### Paper applyも同時に有効化する

world mutation、snapshot、effect envelope、Undoの安全gateはV2-6の別Taskであるため不採用。
