# Release 2 `hydrology-plan` layout

このcapabilityは`surface-2_5d`を必須依存とする。`ReleaseHydrologyPublisherV2`へsealed surface／hydrology artifactのpathを渡すと、次のportable layoutをstagingでstrict verifyしてからpublishする。

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
```

`requiredCapabilities[]`は`["hydrology-plan","surface-2_5d"]`だけである。`manifest.json.artifacts[]`にはmanifest以外の全fileを個別に列挙する。routing index／preview indexはsidecarのsemantic checksumとversionを拘束するため、任意のfileを追加・削除・置換できない。実行可能なfixtureとtampering例は`ReleaseHydrologyPublisherVerifierV2Test`にある。V2-3 Phase gate完了により、このoffline capabilityは`SUPPORTED`である。CLI／Paper applyやRelease format 1の例ではない。
