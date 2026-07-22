# Canonical feature target registry

この文書はADR 0036で承認された`LEGACY_V2`互換source 60値と、`CANONICAL_V2`
authoring projection 46値の機械検証可能なprojectionである。generator support registryではなく、
Schema／model／migrationのidentifier dispositionだけを表す。

<!-- canonical-feature-target-registry-v1:start -->
- Contract: `canonical-feature-target-registry-v1`
- Projection: `CANONICAL_V2`; intentVersion=2
- Compatibility sources: 60; canonical authoring kinds: 46
- Dispositions: direct=46, parent-subtype=7, parent-alias=1, parent-child=5, parent-overlay=1
- Lifecycle: 14×`CURRENT_PUBLIC` (tagged release window not started)
- Approved mappings: `ALPINE_MOUNTAIN_RANGE`→`MOUNTAIN_RANGE.profile=ALPINE`, `BACKSHORE_PLAINS`→`PLAIN.context=BACKSHORE`, `BEDROCK_RIVER`→`RIVER.channelSubtype=BEDROCK`, `FLOODED_CAVE`→`CAVE_OWNER.children.FLOODED_CAVE`, `GLACIAL_CIRQUE_FIELD`→`MOUNTAIN_RANGE.profile=GLACIAL.children.GLACIAL_CIRQUE_FIELD`, `GLACIAL_MOUNTAIN_RANGE`→`MOUNTAIN_RANGE.profile=GLACIAL`, `LAGOON`→`CORAL_REEF.children.LAGOON`, `LAVA_FLOW_FIELD`→`VOLCANIC_OWNER.children.LAVA_FLOW_FIELD`, `MANGROVE_WETLAND`→`MARSH.wetlandType=MANGROVE`, `MEANDERING_RIVER`→`RIVER.morphology=MEANDERING`, `OXBOW_LAKE`→`LAKE.origin=RIVER_CUTOFF`, `REEF_PASS`→`CORAL_REEF.children.REEF_PASS`, `VOLCANIC_ARCHIPELAGO`→`ARCHIPELAGO.origin=VOLCANIC`, `VOLCANIC_CALDERA`→`VOLCANIC_OWNER.children.VOLCANIC_CALDERA`
- Canonical projection SHA-256: `cbce7cda3ea117dcaa75f1e0b80ad3351b17283e94faca084d85ae62f1b15dcc`
<!-- canonical-feature-target-registry-v1:end -->

14値のlifecycleは全て`CURRENT_PUBLIC`であり、V2-15-04完了だけではdeprecation windowを
開始しない。lifecycleは`CURRENT_PUBLIC → DEPRECATED_AUTHORING → LEGACY_READABLE_ONLY`の
一方向である。実装停止・再有効化は別のoperational modeであり、lifecycle rollbackではない。

`CANONICAL_V2`はSchema／codec／migration contractであり、このTaskはgenerator、plan、
Release capability、Paper routingへ接続しない。
