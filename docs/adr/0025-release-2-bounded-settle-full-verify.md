# 0025: Release 2 bounded settleとeffect envelope全体のexact verifyをterminal APPLIED条件にする

- Status: Accepted
- Date: 2026-07-18
- Decision scope: V2-6-07

## Context

ADR 0024はcanonical apply transactionを`APPLYING`＋tile `APPLIED` checkpointまで固定し、terminal `APPLIED`をsettle／full verifyへ委ねた。tile checkpointだけを成功とみなすと、delayed fluid／gravity／neighbor updateやeffect envelope外副作用を見逃し、AGENTS.mdの配置順序（settle → full verify）を破る。sampled verifyはexact stream比較を弱めるため採用できない。

## Decision

V2-6-07で次を固定する。

- `PlacementSettleVerifyPolicyV2`（`release-2-placement-settle-verify-policy-v1`）— settle tick／quiescence／timeout、verify slice／queue、effect外update拒否、sampled verify拒否、scan／continuity／canonical byte budget。
- `PlacementSettleVerifyServiceV2` — apply完了journal（`APPLYING`かつ全tile `APPLIED`）だけから`SETTLING → VERIFYING → APPLIED`へ進め、scheduler-sliced settle tickとeffect envelope全体のX→Z→Y exact block-state stream比較を行う。
- `PlacementVerifyEvidenceV2`（`release-2-placement-settle-verify-v1`）— `VERIFIED`だけをsealし、plan／envelope／snapshot／apply-complete journalへchecksum bindingする。expected／observed stream checksum一致とcontinuity metrics（surface foundation／marine underwater column／surface-volume entrance／underground fluid／overlay）を記録する。
- `PlacementWorldGatewayV2.advanceSettleTick`／`readVerifySlice` — Paper main-threadでbounded settle／verify-readを実行し、observer timeout／cancel後も受理済みoperationをreconcileする。

failure（timeout、effect外update、mismatch、cancel、shutdown、slice／queue／resource budget）は`RECOVERY_REQUIRED`へ分類する。rollback／Undo／Recovery実行とproduction `SUPPORTED`は後続Taskに委ねる。

## Consequences

- tile `APPLIED` checkpointは中間状態であり、placement成功ではない。
- effect envelope全体のexact verifyを必須にし、sample verifyへ弱めない。
- v1 placement／Undo／Release 1意味は変更しない。

## Alternatives

- apply直後にterminal `APPLIED`へ進める案は、settle前checkpointをexact verifyと誤認するため不採用。
- effect envelopeのsampled／tile-only verify案は、seam連続性とdelayed updateを証明できないため不採用。
- snapshot外副作用を成功扱いし事後rollbackへ委ねる案は、rollback不能副作用を許容するため不採用。
