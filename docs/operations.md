# 運用と復旧

この文書には将来コマンドを含む目標運用も記載します。現在、Paper側はplugin起動、config読込、Executor初期化／停止までです。CLI側はSchema検証済みのPhase 1 preview生成まで実装しています。

## 導入予定構成

```text
Paper 1.21.11 / Java 21
├── LandformCraft.jar
└── WorldEdit 7.3.19 または対応するFAWE
```

WorldEditとFAWEは同時に導入しません。現在のpluginは `softdepend: [WorldEdit]` のため、どちらもない状態でも起動します。schematic／選択／配置機能はWorldEdit互換pluginを実行時に検査してから有効化します。

## Server data directory予定

```text
plugins/LandformCraft/
├── config.yml
└── data/
    ├── requests/
    ├── designs/
    ├── candidates/
    ├── jobs/
    ├── exports/
    ├── placements/
    ├── snapshots/
    ├── assets/
    └── logs/
```

大量artifactをplugin JAR内やworld directoryへ混在させません。各request／job／release／placementにIDを付けます。

## 初期設定

`config.yml`の現在の項目:

```yaml
storage:
  root: data

limits:
  max-width: 1000
  max-length: 1000
  default-tile-size: 128

executors:
  generation-parallelism: 0
  io-concurrency: 32
  generation-queue-capacity: 128

providers:
  openai:
    enabled: false
    api-key-env: OPENAI_API_KEY
  anthropic:
    enabled: false
    api-key-env: ANTHROPIC_API_KEY

worldedit:
  enabled: false
```

`generation-parallelism: 0` は `max(1, min(4, CPU数 - 1))` です。I/O同時数は1〜256、生成threadは1〜64、CPU queueは1〜4096のhard rangeです。上限到達時は無制限に滞留させずfailed futureとしてjob層へ返し、job層がbackpressure／retryを判断します。

ProviderはPhase 4まで未実装です。`enabled: true` にしても現在はAPIを呼びません。

## Secret設定予定

Paper processを起動するservice manager、container、shellで環境変数を渡します。

```text
OPENAI_API_KEY=<secret>
ANTHROPIC_API_KEY=<secret>
```

値はMinecraft console、chat、commandへ貼り付けません。設定確認コマンドも値そのものを表示せず、present／missingだけを返します。

## 標準運用フロー

```text
request作成
  → validate
  → design/import
  → candidates生成
  → previewとvalidation確認
  → Release export/verify
  → placement dry-run
  → confirm
  → snapshot付きapply
  → verify
```

設計と生成をPaper server内に閉じず、CLI／外部WorkerでReleaseを作ってPaperがverify／applyする運用を最終推奨とします。

## 再起動復旧

- job stage変更はatomicに永続化する
- stage開始前に入力checksum、stage完了後に出力checksumを保存する
- process停止時、実行中jobを黙ってQUEUEDへ戻さない
- 安全な完了checkpointがあればそこから再開候補にする
- 外部API requestの結果不明時は重複課金を避け、`RECOVERY_REQUIRED`にする
- apply中断時はplacement journalを読み、未検証tileを確認してrollbackまたは手動復旧する

## SnapshotとUndo

- snapshot容量をapply前に見積もる
- tile適用前にそのtileのsnapshotを閉じてchecksumを確定する
- applyとverify成功後もretention期間中はsnapshotを残す
- undoは適用順の逆順で実行する
- world ID、seed、target origin、release checksumが一致しないsnapshotを自動適用しない
- snapshot削除は配置journalと監査logを残した別operationにする

## Backup

- plugin dataとworld backupを別storageへ取得する
- Releaseは再生成可能でも、placement journalとsnapshotはworld復旧に必要
- secretを通常backupへ含める場合は暗号化とaccess controlを設定する
- restore手順を定期的にtestする

## 監視項目

- queue length、stage duration、failure／retry率
- Paper tick timeとwatchdog警告
- CPU、heap、native memory、virtual thread数
- artifact／snapshot disk使用量
- provider latency、429／5xx、token／cost quota
- apply／rollback時間と失敗tile

## 障害別対応

### Provider失敗

worldは変更しません。retry上限後はFAILEDまたはRECOVERY_REQUIREDにし、manual JSON importへ切り替えられるようにします。

### 生成／validation失敗

候補を不採用にし、diagnostic previewとissueを残します。ReleaseをREADYにしません。

### Export途中停止

temporary releaseを公開せず、次回startup時にageとjob IDを確認してcleanupまたはdiagnostic保存します。

### Apply途中停止

新規applyを停止し、placement journal、world identity、snapshot checksumを検査します。自動判断できない場合はRECOVERY_REQUIREDにして管理者へ提示します。

### Disk不足

生成開始前とsnapshot前に余裕を検査します。書込途中のartifactをREADYにせず、world apply前ならworldへ影響させません。
