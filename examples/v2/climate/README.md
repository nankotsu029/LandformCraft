# V2-4 climate field example

`climate-plan-v2.json` はV2-4-04の400×400最小contract例です。32-cell coarse grid上の降水／runoff priorと、標高・緯度相当・exposure・V2-3 flow accumulationを読む最終temperature／moisture fieldを別phaseとしてfreezeします。

`hydrologyHandoff` は既存V2-3 `HydrologyPlan` のcanonical checksum、固定 `CONSTANT_RUNOFF_PRIOR` checksum、source generator `hydrology-priority-flood-v1` を保持したまま、`hydrology-priority-flood-climate-prior-v1` への明示version transitionを宣言します。V2-3 artifactの意味をin-placeで変更しません。

field値はinteger-only kernelでglobal X/Zから再現し、plan JSONへdense arrayを埋め込みません。final fieldはpriorの正本ではなく、hydrology graphを逆向きに変更しません。実payloadのsidecar化とRelease 2 capabilityは後続Taskの範囲です。
