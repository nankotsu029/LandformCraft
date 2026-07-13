# 0005: 配置を確認トークン付きタイルcheckpoint transactionにする

- Status: Accepted
- Date: 2026-07-13

## Context

Releaseは検証済みでも、対象world、world border、server停止、WorldEdit失敗は配置時にだけ確定します。1000×1000を単一編集として扱うと、途中失敗の位置と復旧可能性を永続化できません。また、非同期plan完了をそのままworld変更へ接続すると、人間の確認前に本番worldを変更する危険があります。

## Decision

- `PlacementApplicationService`をPaperとWorldEditから独立した配置state machineの正本にする。
- `plan`はRelease checksum、world identity、height／border boundsを検査し、10分期限の一回用確認トークンを発行する。保存するのはトークンのSHA-256だけとする。
- `execute`はReleaseを再検証し、確認状態を消費・永続化してから初めてworld操作をSchedulerへ渡す。
- 各tileを `snapshot → checkpoint → apply → checkpoint → verify → checkpoint` の順で処理する。
- 失敗時とundo時はchecksum検証済みsnapshotを適用順の逆順で復元する。
- world変更中の再起動は自動推測せず `RECOVERY_REQUIRED` にする。
- Bukkit／WorldEdit worldアクセスは`PaperWorldEditPlacementGateway`だけに置き、`PaperMainThreadDispatcher`を必須にする。schematic／journal I/OはVirtual Thread executorへ置く。

## Consequences

- confirmなしのplan、無効／期限切れtoken、改変Releaseではworld操作が呼ばれない。
- tileごとのJSON journalとsnapshotが追加のdisk容量を使用する。
- crash直前にWorldEditが完了したかを完全には判定できないため、再起動後に自動再開しない。
- 現在は同一world上の複数placement間の重なり予約を行わない。管理者は同じ領域へ同時にplan／executeしない。

## Alternatives

- WorldEditのundo historyだけへ依存する案は、server再起動と外部配置の永続復旧要件を満たさないため不採用。
- 全tileを一度にsnapshot／pasteする案は、memory、進捗、部分失敗の観測性が悪いため不採用。
- 再起動後に自動でapplyを継続する案は、最後の編集完了が不明な状態で二重適用し得るため不採用。
