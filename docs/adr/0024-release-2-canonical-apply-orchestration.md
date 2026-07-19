# 0024: Release 2 applyをcanonical streamのbounded transactionとして実行する

- Status: Accepted
- Date: 2026-07-18
- Decision scope: V2-6-06

## Context

V2-6-01〜V2-6-05で、placement plan、effect envelope、reservation-bound confirmation、snapshot-all、containment evidenceまでをchecksum-boundにした。次のworld mutationでは、この前段を迂回できず、surface、cave、sky solid、waterfall、underground fluidをFeatureKind別のPaper実装へ分岐させずに同じ最終block streamから適用する必要がある。またPaper schedulerへ受理されたoperationは、observer側のtimeoutやcancelだけで取消済みとみなせない。

## Decision

`PlacementApplyTransactionServiceV2`をRelease 2専用application serviceとし、次を固定する。

- 最初のscheduler submission前にRelease directory、plan binding chain、reservation ownership、consumed confirmation、published snapshot、containment evidence、canonical source bindingをstrict再検証する。
- 入力はpreview／exportと同じfeature-neutralな`PlacementCanonicalBlockSourceV2`とする。各mutation AABBをX最速→Z→Yでexact coverageし、閉じたblock physics catalog、effect-envelope physics宣言、immutable fingerprintを検査する。
- tileはplanのcanonical index順、passは明示値`SOLID(10) → AIR_CARVE(20) → FLUID(30)`、同一pass内は明示overlay ordinal昇順で適用する。Java enum ordinalやFeatureKind分岐を順序根拠にしない。
- worker数、transaction queue、overlay数、block数、scheduler sliceを事前admissionし、全blockや全tileを常駐させずrestartable cursorをboundedに再走査する。
- Paper adapterはbounded sliceだけをschedulerへ渡し、WorldEdit公開APIをmain threadで実行する。completion receiptはscheduler受理、main-thread実行、mutation数、WorldEdit resource closeをすべて証明する。
- observerへは取消不能な観測stageを返す。observer timeout／cancel後もscheduler受理済みoperationの実完了をreconcileする。
- 最初のsubmission前にjournalを`APPLYING`へatomic publishし、完了tileだけをcanonical `APPLIED` prefixとしてcheckpointする。submissionを試みた後のfailure、cancel、shutdown、invalid receipt、source driftは`RECOVERY_REQUIRED`へ進める。
- 全tileのblock apply後もjournalは`APPLYING`に留める。`SETTLING`、effect envelope全体のexact verify、rollback、Undo、Recovery、`SUPPORTED`昇格は後続Taskに委ねる。

## Consequences

- V2-2〜V2-10の出力は同じcanonical block streamへ適合すればよく、新featureごとのPaper adapterを増やさない。
- pass／overlayごとの再走査でCPUは増えるが、working setをscheduler slice上限へ閉じ、source driftを各走査で検出できる。
- apply成功はplacement成功を意味しない。V2-6-07のsettle／full verifyが完了するまでterminal `APPLIED`へ進めない。
- Release 1、v1 placement service、v1 Undo、既存golden checksumは変更しない。

## Alternatives

- FeatureKindごとにPaper placement adapterを作る案は、順序と安全規則が分岐しcross-feature seamを壊すため採用しない。
- observer Futureのtimeoutをscheduler operationのcancelとみなす案は、world stateとjournalを不一致にするため採用しない。
- apply完了直後にterminal `APPLIED`へ進める案は、settle前のtile checkpointをeffect envelope全体のexact verifyと誤認するため採用しない。
