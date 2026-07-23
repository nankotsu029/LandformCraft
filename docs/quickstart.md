# Quick Start

> CLI／Paperはv2のみです。`/lfc <verb>`と`lfc <verb>`は恒久的な明示形`v2 <verb>`と同じ経路です。既存v1 artifactは`migrate`またはread-only legacy verifierで扱います。

## 1. Install

Java 21とPaper 1.21.11を用意し、`LandformCraft-0.9.0-beta.1.jar`とWorldEdit 7.3.19または対応FAWEのどちらか一方を `plugins/` へ置きます。最初は必ずtest worldとserver backupを使ってください。

```bash
./gradlew clean build
```

起動後に確認します。

```text
/lfc version
/lfc doctor
```

## 2. v2 requestからReleaseを作る

同梱のv2 fixture（`harbor-cove-64-honored`）を使う最短経路です。CLIは既定でv2経路を通ります。このfixtureはHARD要件がすべて充足可能（`LAND_WATER_MASK`は実在mask PNGを解決し、HARD relationはproduction接続済みkind間のみ）なため、V2-18-03のHARD preflight gateを通過します。fixtureは`foundationBaseLevels`を宣言しているため、V2-18-09のmacro foundation経路（maskが全cellのland-waterを確定し、coastal featureはその上のmodifier）で生成されます。V2-18-10以降、この明示foundation入力は**必須**です。持たないrequestはfoundation owner被覆が0のまま`v2.export.foundation-owner-coverage-incomplete`で拒否され、baseline引数では回避できません。V2-18-11以降、このfixtureはHARDな`EDGE_CLASSIFICATION`（北端は陸 ≥0.85、南端は海 ≥0.90）も宣言します。生成後にtarget-driven validationが公開前に実測し、満たさないexportは拒否されます。

```bash
# requestを厳密に検証
./gradlew run --args="request validate examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json"

# Release 2 directory＋ZIPを公開し、placement適格性まで検証
#   末尾の baseline: <land|water> <land-surface-y> <water-bed-y> は引数としては必須のまま受理されますが、
#   明示foundation入力（HARD mask＋foundationBaseLevels）を持つrequestでは無視され、
#   summaryへ v2.cli.surface-baseline-deprecated 警告が出ます（V2-18-09／V2-18-10）
./gradlew run --args="export \
  examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json \
  examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json \
  build/quickstart-exports harbor-cove-64 water 54 46"

# 検証済みReleaseのpreview indexを表示
./gradlew run --args="preview build/quickstart-exports/harbor-cove-64"
```

自分でrequestを起こす場合は authoring verb を使います（V2-12-08）。

```bash
./gradlew run --args="--data-dir build/data request create my-cove"
./gradlew run --args="--data-dir build/data request bounds my-cove 64 64 32 72 50"
./gradlew run --args="--data-dir build/data request constraint-map my-cove coast-mask maps/coast-u8.png <sha256> 64 64"
./gradlew run --args="--data-dir build/data request prompt my-cove A sheltered cove."
# 保存先: build/data/v2/requests/my-cove.request-v2.json
```

## 3. Paperへ移す

§2で作ったRelease directoryを `plugins/LandformCraft/data/releases-v2/harbor-cove-64` にchecksumを保ったままコピーします。別サーバーへZIPで移した場合はCLI `preview`（strict verify込み）を再実行してから展開してください。

```text
/lfc place plan harbor-cove-64 world 0 64 0
```

planはworldを変更しません。表示されたplacement ID、範囲、disk見積もりを確認し、発行されたconfirmation tokenで実行します。

confirmation tokenはactor、placement、reservationに紐づく1回用の値です。

* tokenはTab補完しません。playerのchatに出るtokenのクリックは入力欄へcopyするだけで、自動実行されません。
* CONSOLEとRCONの出力はPaperのserver logへ複製されるため、非playerには**tokenをchatへ表示しません**。確認コマンド全文をowner-onlyのfile（`plugins/LandformCraft/data/confirmations/<key>.command`）へ保存し、console上ではそのpathだけを表示します。10分有効・1回用で、消費に成功するとfileは破棄されます。
* CONSOLE／RCONで実行するときは、このfileを読んで中の確認コマンドをそのまま実行します。tokenをlogやchatへ貼り直さないでください。

```text
/lfc place confirm <placement-id> <token>
/lfc place execute <placement-id>
/lfc status <placement-id>
```

## 4. Undo

配置後に人がblockを変更しているとworld driftとしてUndoを拒否します。

```text
/lfc undo plan <placement-id>
/lfc undo execute <placement-id> <token>
```

`RECOVERY_REQUIRED`になった場合は推測でjournalを書き換えず、[Troubleshooting](troubleshooting.md) と [Admin Guide](admin-guide.md) のRecovery手順へ進んでください。

## 5. 既存v1 artifactの移行（任意）

v1 production commandは削除済みです。保持しているv1 intent／design package／Release 1は、CLIの`migrate inspect`でlossを確認し、`migrate apply ... strict|accept-lossy`で新しいv2 bundleへ非破壊変換してください。元artifactは上書きされません。
