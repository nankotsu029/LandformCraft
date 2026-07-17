# Release 2 `surface-2_5d` layout

このcapabilityは入力JSONへraw pathを埋め込まない。`ReleaseSurfacePublisherV2`へsealed artifactのpathを渡すと、次のportable layoutをstagingでstrict verifyしてからpublishする。

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
```

`manifest.json.artifacts[]`にはmanifest以外の全fileを個別に列挙する。`constraints/index.json`、`previews/index.json`、tile metadataはそれぞれsidecarのsemantic checksumとversionを拘束するため、任意のfileを追加・削除・置換できない。実行可能なfixtureとtampering例は`ReleaseSurfacePublisherVerifierV2Test`にある。これはoffline release layoutだけであり、CLI／Paper applyやRelease format 1の例ではない。
