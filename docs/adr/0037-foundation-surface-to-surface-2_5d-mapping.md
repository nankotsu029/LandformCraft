# 0037: V2-9／V2-10 foundation 2.5D surfaceを既存`surface-2_5d`へ写像する

- Status: Accepted
- Date: 2026-07-22
- Decision scope: V2-15-09

## Context

[全地形カタログ監査 DR-2](../audits/terrain-catalog-full-audit-2026-07-22.md)は、foundation artifactをfeature名ごとの新capabilityへ分けず、block streamの意味に応じて既存`surface-2_5d`／`hydrology-plan`／`environment-fields`／`sparse-volume`へ格納することを推奨した。代替の`foundation-plan`新capabilityは新artifact formatと全verifierを要し、V2-15-09の停止条件に触れる。

V2-9／V2-10はplan-level generate／validation／previewを閉じたが、exportは`PARTIAL`のままRelease capabilityへ未接続である。一方[ADR 0013](0013-surface-2_5d-release-capability.md)は`surface-2_5d`のexact artifact setをcoastal-named validation／preview typeで固定しており、[ADR 0030](0030-release-2-cross-capability-hardening.md)はfoundation／bathymetryをFeature別placement typeなしで同一canonical block streamへbindする。`ProductionDispatchRegistryV2`はexact capability listごとにpipelineを1つだけ許すため、coastalが既に`["surface-2_5d"]`を占有している。

## Decision

1. **capability写像:** V2-9／V2-10の2.5D foundation surface（plain／hill代表を含む）は新capabilityを作らず、既存`surface-2_5d`へ写像する。volume／surface-volume connectionは本ADRの対象外とし、既存`sparse-volume`写像を後続Taskへ残す。
2. **artifact exact set:** ADR 0013の必須type／path（`coastal-validation-artifact-v2`、固定11枚`coastal-preview-*`、constraint field、offline tile）を変更しない。foundation adapterはmerge結果からland-water／surface-heightを投影し、Blueprintにcoastal planが無いときcoastal overlay previewは空値、`CoastalValidatorV2`は空metricsのhard-passとしてhonestに実行する。
3. **dispatch境界:** foundation adapterは`ProductionDispatchRegistryV2`へ`["surface-2_5d"]`第二pipelineとして登録しない。coastal production routeとcapability keyを衝突させず、`Release2FoundationSurfaceExportApplicationServiceV2`が`ReleaseSurfacePublisherV2`／`ReleaseSurfaceVerifierV2`を直接使う。
4. **非昇格:** Feature Support Catalog、Paper能力、CLI既定、個別foundation kindの`PRODUCTION_CONNECTED`化は本Decisionの範囲外である。本adapterはV2-18-08／09の前提資材であり、macro foundation spineやowner coverage fail-closedは行わない。

## Consequences

- plain-hill等のfoundation surfaceはdirectory／ZIPの`surface-2_5d` strict verifyとplacement eligibilityまで到達できる。
- coastal-named artifact typeはcontainer契約として残り、foundation固有validation／preview SchemaはRelease payloadへ埋め込まない（plan-level foundation artifactは従来どおり別経路）。
- coastal／hydrology／environment／sparse-volumeの既存production checksumとcapability listは不変。
- 新capabilityやADR 0013 exact setの破壊的変更が将来必要になった場合は、本ADRをamendしてから実装する。

## Alternatives considered

### `foundation-plan`新capability

strict containerは明確だが新format／verifier／catalog／dependency matrixが必要で、DR-2停止条件に触れるため不採用。

### ADR 0013のvalidation／preview typeをsurface汎用名へ改名

既存coastal Releaseとchecksum回帰を壊すため本Taskでは不採用。必要なら別ADRとmigration Taskとする。

### coastal pipelineを置き換えてfoundationを同一registry keyへ載せる

現行唯一のproduction経路を破壊し、V2-18-09のcoastal再接続前に時期尚早であるため不採用。
