# 0012: Release format 2をcapability分離したstrict artifact indexとして導入する

- Status: Accepted
- Date: 2026-07-15

## Context

v1 Release formatは、固定file allowlist、v1 TerrainIntent／WorldBlueprint、Sponge tile、Paperのcheckpoint placementに結び付いている。V2-1のfield sidecar、V2-2のcoastal validation preview、standalone offline tileはこのv1 containerへ追加できない。v1 verifierを緩めたり、manifestへ任意fileを許したりすると、既存Release／placement／Undoの互換境界を壊す。

一方、V2-2-10時点では`surface-2_5d`の必須artifact集合をまだ固定していない。containerとpayload capabilityを同時に有効化すると、未定義のfield、preview、tile、semantic checksumを曖昧なfallbackで受理する危険がある。

## Decision

- v1 `ReleasePublisher`／`ReleaseVerifier`とは別に、`format.v2.release`へRelease format 2 coreを追加する。
- `release-manifest-v2.schema.json`の`manifest.json`はformat version 2、manifest version 1、release ID、`requiredCapabilities[]`、`artifacts[]`、exclude-self canonical SHA-256を持つ。
- `artifacts[]`はmanifest以外のfileをexactに列挙し、descriptorはartifact ID／type／version、safe relative path、byte length、artifact／semantic SHA-256を持つ。pathとIDはcanonicalに正規化し、case collisionを許さない。
- `ReleaseArtifactCatalogV2`はcompile-time immutable catalogである。V2-2-10ではcore-only releaseだけを許可し、`requiredCapabilities[]`と`artifacts[]`が空でなければ拒否する。`surface-2_5d`はcatalog上の予約であり、manifestにはV2-2-11まで出現できない。
- publisherはstaging directoryをstrict self-verifyし、fsyncとatomic move後にだけdirectoryを公開する。ZIPも別staging fileでstrict read-backしてからatomic publishする。cancel／failureで未公開targetを残さない。
- verifierはdirectory／ZIPのnon-symbolic regular file、canonical path、duplicate／case collision、strict index、checksum、unknown capability／type/version、entry、compressed／expanded byte、resident buffer、disk budgetを検査する。ZIP directory entryとnon-atomic fallbackは拒否する。

## Consequences

- V2 artifactをv1 containerへ混在させず、v1 Schema、generator `3.0.0-phase6`、Release format 1、placement、Undoの意味とchecksumを凍結できる。
- core-only releaseには地形payloadがなく、CLI／Paper／world mutationへ接続しない。これは利用可能なcoastal Releaseを意味しない。
- `surface-2_5d`、`hydrology-plan`、`environment-fields`、`sparse-volume`は、それぞれのTaskで必須artifact集合、semantic validator、directory／ZIP tampering testを追加してから有効化する。
- strict indexとbounded extractorを先に共通化するため、後続capabilityがindex外fileやunknown versionをfallbackする余地を作らない。

## Alternatives

### v1 manifestにv2 fileを任意追加する

v1 allowlistと既存verifierの意味を緩め、既存配置の信頼境界を曖昧にするため採用しない。

### 最初から`surface-2_5d`を有効化する

field、preview、offline tileの必須集合とsemantic bindingを同じTaskで確定することになり、V2-2-10の単一container scopeを超えるため採用しない。

### ZIPだけを正本にする

directoryのstrict read-backやatomic publishを省けず、展開時のpath／budget検査も必要なため採用しない。directoryとZIPを同じverifierで検査する。
