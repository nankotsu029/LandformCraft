# Release 2 `sparse-volume` layout

このcapabilityは`environment-fields`、`hydrology-plan`、`surface-2_5d`を必須依存とする。`ReleaseSparseVolumePublisherV2`へsealed environment（surface／hydrology／environmentを含む）artifactと、volume plan／index／validation／3D tileのpathを渡すと、次のportable layoutをstagingでstrict verifyしてからpublishする。environment以下の階層は`environment-fields` layoutと同一で、`volume/`配下だけが追加される。

```text
manifest.json
source/…  blueprint/…  constraints/…  validation/…  previews/…  tiles/…   (surface-2_5d)
hydrology/…                                                                (hydrology-plan)
environment/…                                                              (environment-fields)
volume/sdf-primitive-plan.json
volume/csg-plan.json
volume/aabb-index-plan.json
volume/validation.json
volume/tiles/<tile-id>.json
volume/tiles/<tile-id>.schem
```

`requiredCapabilities[]`は`["environment-fields","hydrology-plan","sparse-volume","surface-2_5d"]`（natural order）だけである。`manifest.json.artifacts[]`にはmanifest以外の全fileを個別に列挙する。

Volume payloadのbindingは次をstrictに拘束する。

- `volume/csg-plan.json`のprimitive plan bindingが`volume/sdf-primitive-plan.json`のcanonical checksumと一致する（operator plan → SDF）。
- `volume/aabb-index-plan.json`のCSG plan bindingが`volume/csg-plan.json`のcanonical checksumと一致する（AABB → operator plan）。
- `volume/validation.json`の`sourcePlanChecksum`が`volume/csg-plan.json`のcanonical checksumと一致し、hard validationを通過する。
- `volume/tiles/<id>.schem`はSponge v3の3D volume tile（air cavity／fluid／independent solid）で、strict read-backのsemantic checksumがmetadataと一致し、`sourceBlueprintChecksum`が公開Blueprintと一致する。

3D volume tileは`offline-tile-artifact-v2`とは別の`volume-offline-tile-artifact-v2`／`volume-sponge-schematic-v3` artifact typeを使い、surface tile集合と混ざらない。実行可能なfixtureとmissing/extra/version/binding/tile tampering例は`ReleaseSparseVolumePublisherVerifierV2Test`にある。これは`V2-5-17`のoffline capabilityであり、V2-5親Phase統合監査（`V2-5-18`、2026-07-18完了）でoffline `SUPPORTED`へ昇格した。CLI／Paper applyやRelease format 1の例ではない。
