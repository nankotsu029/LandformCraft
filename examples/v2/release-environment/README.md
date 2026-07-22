# Release 2 `environment-fields` layout

このcapabilityは`hydrology-plan`と`surface-2_5d`を必須依存とする。`ReleaseEnvironmentPublisherV2`へsealed surface／hydrology／environment artifactのpathを渡すと、次のportable layoutをstagingでstrict verifyしてからpublishする。

```text
manifest.json
source/generation-request.json
source/terrain-intent.json
blueprint/world-blueprint.json
constraints/index.json
constraints/<FieldArtifactDescriptorV2.relativePath>
validation/coastal-validation.json
previews/index.json
previews/<fixed 11 PNG names>
tiles/<tile-id>.json
tiles/<tile-id>.schem
hydrology/plan.json
hydrology/routing/index.json
hydrology/routing/fields/*.lfgrid
hydrology/reconciliation-plan.json
hydrology/reconciliation-artifact.json
hydrology/validation.json
hydrology/previews/index.json
hydrology/previews/<fixed 12 PNG names>
environment/geology-plan.json
environment/lithology-plan.json
environment/strata-plan.json
environment/climate-plan.json
environment/water-condition-plan.json
environment/snow-plan.json
environment/material-profile-plan.json
environment/minecraft-palette-plan.json
environment/ecology-plan.json
environment/feature-material-profile-plan.json
environment/validation.json
environment/previews/index.json
environment/previews/<fixed 10 PNG names>
```

`requiredCapabilities[]`は`["environment-fields","hydrology-plan","surface-2_5d"]`だけである。`manifest.json.artifacts[]`にはmanifest以外の全fileを個別に列挙する。palette／material／snow／ecology／preview indexはsemantic checksumとversionを拘束するため、任意のfileを追加・削除・置換できない。実行可能なfixtureとtampering例は`ReleaseEnvironmentPublisherVerifierV2Test`にある。`V2-15-07`以降、coastal production feature向けのshared Application Serviceは`Release2EnvironmentExportApplicationServiceV2`（`EnvironmentFieldsExportPipelineV2`）であり、hydrology dependencyを同一Releaseでstrict verifyする。個別environment Feature leafの公開配線、CLI既定切替、Paper applyは含まない。`V2-4-15`統合監査を通過したoffline `SUPPORTED` layoutであるが、Release format 1の例ではない。
