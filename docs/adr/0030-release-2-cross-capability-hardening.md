# 0030: Release 2 capability dependency matrix と placement eligibility を固定する

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-12

## Context

Release format 2 は `surface-2_5d`／`hydrology-plan`／`environment-fields`／`sparse-volume` を capability 分離で収容してきたが、valid prefix・cross-version reader・artifact limit・placement eligibility が publisher／verifier／PlacementPlan に分散し、不完全依存や unknown version の拒否根拠が単一の正本になっていなかった。V2-9／V2-10 の foundation／bathymetry は Feature 別 placement type を増やさず、既存 canonical block stream へ収容する契約が必要だった。world mutation と Release format 3 は非 Scope である。

## Decision

1. **`ReleaseCapabilityDependencyMatrixV2`**（`model.v2.release`）を valid capability prefix の正本とする。許容集合は次の5個だけである。
   - `[]`
   - `[surface-2_5d]`
   - `[hydrology-plan, surface-2_5d]`
   - `[environment-fields, hydrology-plan, surface-2_5d]`
   - `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`
2. **`ReleaseArtifactCatalogV2`** と **`PlacementPlanV2`** は同じ matrix を参照し、未知 capability／不完全依存を拒否する。Release 1 allowlist とは共有しない。
3. **`ReleaseCrossVersionReaderPolicyV2`** は format 2／manifest 1 だけを受理し、future version を forward-read しない。codec／catalog／eligibility で強制する。
4. **`ReleaseArtifactLimitsCatalogV2`** が core limit と payload-adjacent ceiling を集約する。trusted ceiling を超える limits を受理しない。
5. **`ReleasePlacementEligibilityVerifierV2`** が directory／ZIP の strict verify＋sealed checksum＋valid prefix を placement 前 gate とする。plan の Release binding／capability 一致を要求する。
6. **`ReleasePlacementInputContractV2`** が surface／hydrology／environment／sparse-volume の共通 overlay ordinal stream を定義し、foundation／bathymetry host kind も Feature 別 placement type なしで同じ stream へ bind する。

## Consequences

- 全 valid prefix は directory／ZIP 双方で placement-eligible になり、missing／extra／unknown／version／checksum／path／ZIP bomb は拒否される。
- foundation／bathymetry 追加時に新しい placement type を導入しない契約が test で固定される。
- 次は `V2-6-13`（operational metrics／diagnostics／retention）。world mutation の変更は本 ADR の範囲外である。

## Alternatives

- PlacementPlan と Catalog で別々の capability 集合を維持する案は drift を招くため不採用。
- future Release format を silent upgrade する案は cross-version policy に反するため不採用。
- Feature 別 placement type を foundation／bathymetry 用に追加する案は V2-6-12 Scope と非 Scope に反するため不採用。
