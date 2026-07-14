# Quick Start

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

## 2. Manual JSONでReleaseを作る

同梱fixtureを使う最短経路です。

```bash
./gradlew run --args="validate examples/sandy-coast/request.yml examples/sandy-coast/terrain-intent.json"
./gradlew run --args="generate examples/sandy-coast/request.yml examples/sandy-coast/terrain-intent.json build/quickstart-preview"
./gradlew run --args="export examples/sandy-coast/request.yml examples/sandy-coast/terrain-intent.json build/quickstart-exports"
./gradlew run --args="verify build/quickstart-exports/sandy-coast-001/<release-id>"
./gradlew run --args="verify build/quickstart-exports/sandy-coast-001/<release-id>.zip"
```

`build/quickstart-preview`の8 PNGとwarningを確認してからReleaseを使います。

## 3. Paperへ移す

Release directoryを `plugins/LandformCraft/data/exports/rocky-coast-001/<release-id>` にchecksumを保ったままコピーします。別サーバーへZIPで移した場合はCLI `verify`を再実行してから展開してください。

```text
/lfc apply plan rocky-coast-001/<release-id> world 0 64 0
```

planはworldを変更しません。表示されたplacement ID、範囲、disk見積もりを確認し、同じplayerまたはCONSOLEで表示tokenを使います。クリックは入力欄へcopyするだけで自動実行されません。

```text
/lfc apply execute <placement-id> <token>
/lfc apply status <placement-id>
```

## 4. Undo

配置後に人がblockを変更しているとworld driftとしてUndoを拒否します。

```text
/lfc apply undo <placement-id>
/lfc undo execute <placement-id> <token>
```

`RECOVERY_REQUIRED`になった場合は推測でjournalを書き換えず、[Troubleshooting](troubleshooting.md) と [Admin Guide](admin-guide.md) のRecovery手順へ進んでください。
