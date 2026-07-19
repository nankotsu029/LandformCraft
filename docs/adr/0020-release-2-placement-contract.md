# 0020: Release 2 placement plan／journal契約をv1と分離して固定する

- Status: Accepted
- Date: 2026-07-18
- Decision scope: V2-6-01

## Context

ADR 0005はRelease format 1向けの確認トークン付きtile checkpoint transactionを固定した。V2-5 gate完了後、Release format 2をworld mutationから分離したplacement契約が必要になった。v1 journalをin-placeで再解釈すると、capability set、effect envelope、snapshot-all、format 2 artifact bindingを表現できず、既存checksumと復旧意味を壊す。

## Decision

V2-6-01で`model.v2.placement`へRelease 2専用のimmutable契約を追加する。

- `PlacementPlanV2`（`release-2-placement-contract-v1`）はplacement／operation ID、actor、world target／bounds／`MINIMUM_CORNER` anchor、Release format 2 manifest checksum binding、canonical capability set、`tile-xN-zN` canonical tile order、mutation／effect envelope checksum参照、reservation／confirmation binding slot、resource budget、`canonicalChecksum`を持つ。
- `PlacementJournalV2`（`release-2-placement-journal-v1`）はsealed planを埋め込み、format 2 journal states（`PLANNED`〜`RECOVERY_REQUIRED`）とtile entry statesを持つ。V2-6-01は`PLANNED`＋`PENDING`だけをemitする。
- `PlacementPlanCompilerV2`はplan／journalをsealするだけで、Release verify、envelope算出、reservation、snapshot、applyを行わない。
- Schema／codecは`LandformV2DataCodec`へ隔離する。v1 `placement-journal.schema.json`、`LandformDataCodec`、`FilePlacementJournalRepository`は変更しない。

未知version／capability／state、unsafe path、target mismatch、checksum改変、tile／journal budget超過はfallbackせず拒否する。

## Consequences

- Release 2 placementはv1 journalと並設され、version dispatchで読取できる。
- envelope算出（V2-6-02）、reservation／confirm（V2-6-03）、snapshot-all／apply以降は本契約のstate／binding slotを埋めるだけで進められる。
- Paper world mutationはこの契約だけでは有効化されない。

## Alternatives considered

### v1 journalへcapability／envelope欄を追加する

既存Release 1 journalの意味とstrict Schemaを壊すため不採用。

### placement契約をRelease manifest artifactへ混ぜる

world mutation stateとportable Release正本を混同し、offline Release verifyと配置lifecycleの境界が崩れるため不採用。
