# 0018: semantic materialをMinecraft 1.21.11 palette adapterへ隔離する

- Status: Accepted
- Date: 2026-07-17

## Context

V2-4-07はgeology／wetness／snowから閉じた6 semantic material classを解決するが、Minecraft block stateをmodelへ持ち込まない。offline Sponge writer（V2-2-09）はcanonical block-state文字列と辞書順paletteを要求するため、semantic classからversion固定のblock stateへ変換するadapter境界が必要である。v1の`MinecraftBlockPalette`を拡張するとRelease format 1とgolden checksumへ波及する。

## Decision

1. **`model.v2.minecraft.MinecraftPalettePlanV2`をadapter契約の正本とする。** `material-profile-contract` checksumへ直接bindingし、Minecraft `1.21.11`／DataVersion `4671`／`minecraft-palette-resolver-v1`のcompatibility tupleを凍結する。
2. **閉じた18 mapping（6 class × SURFACE/CEILING/FLOOR）だけを受理する。** last-write-wins、未知semantic ID、future resolver／Minecraft version、NBT付きstateは拒否する。built-in targetは環境export allowlist内のvanilla識別子に限定する。
3. **解決とallowlistは`format.v2.minecraft`に置く。** `MinecraftPaletteResolverV2`はcompact code→canonical stateを返し、`EnvironmentBlockStateCatalogV2`はV2-2 coastal setのstrict超集合としてoffline writer／inspectorの共有gateになる。palette ID 127／128のVarInt境界を同じallowlistで検証する。
4. **v1 `worldedit.MinecraftBlockPalette`は変更しない。** Paper apply、biome書換え、feature shapingは本ADRの範囲外である。

## Consequences

- offline Sponge write→strict inspect→WorldEdit 7.3.19 read-backが、palette adapter経由のcanonical block checksumで一致する。
- Release 2 `environment-fields` capability（V2-4-14）は本palette checksumをindexできるが、本Taskではcapabilityを有効化しない。
- coastal tileは従来どおり通る。環境paletteが追加するcolored wool等はcoastal generatorが使わない限りartifactへ混入しない。

## Alternatives

- **v1 paletteへsemantic classを追加する:** Release 1／golden回帰を壊すため棄却。
- **WorldEdit BlockState APIをgeneratorへ持ち込む:** package境界とoffline決定性に反するため棄却。
- **aspectごとに別catalog versionを持つ:** 初期6 classでは過剰。変更時はresolver versionを上げる。
