# 0022: Release 2 region／disk reservationとbound confirmationをv1と分離して固定する

- Status: Accepted
- Date: 2026-07-18
- Decision scope: V2-6-03

## Context

ADR 0020／0021はRelease 2 placement plan／journalとmutation／effect envelopeを固定したが、world mutation前に必要なatomic multi-region／disk reservationとactor-bound confirmationは未実装だった。v1の`FilePlacementSafetyStore`／confirmation hash意味を変えると既存配置経路が壊れるため、Release 2専用契約が必要である。

## Decision

V2-6-03で次を追加する。

- `model.v2.placement.PlacementReservationPlanV2` — effect AABBごとのregion lease、disk lease、placement／envelope binding、`canonicalChecksum`
- `model.v2.placement.PlacementSafetyStateV2` — overlap／disk ledgerとconsumed confirmation hash（平文tokenは保存しない）
- `core.v2.placement.reservation.FilePlacementSafetyStoreV2` — atomic write、prune、overlap拒否、injectable disk probe、restart rebuild
- `PlacementConfirmationBinderV2` — Release／envelope／reservation／operation／actor／expiry／nonceへ結合したSHA-256、TTL 10分、constant-time比較
- `PlacementReservationConfirmCompilerV2` — `RESERVATION_BOUND`→`CONFIRMATION_ISSUED`、失敗時lease全解放

confirmation hashはplacement planの可変`canonicalChecksum`を含めず、envelopeのsource placement checksumとenvelope／reservation checksumを正本とする。

snapshot、apply、settle、v1 reservation意味変更は対象外である。

## Consequences

- snapshot-all（V2-6-04）は予約済みeffect envelopeとissued confirmationを前提にできる。
- overlap／disk不足／expiry／replay／actor mismatchはapply前にhard rejectされる。
- v1 `FilePlacementSafetyStore`とconfirmation契約は不変のまま並存する。

## Alternatives considered

### v1 safety storeをRelease 2へ拡張する

v1 journal／bounds意味とeffect multi-region契約が衝突するため不採用。

### confirmationへplacement plan canonicalChecksumを含める

reservation／confirmation bindingのたびにplan checksumが変わるため循環し、verify不能になる。
