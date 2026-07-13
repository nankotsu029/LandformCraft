# 開発ガイド

## 前提

- JDK 21
- macOS／Linux／Windows上のGradle Wrapper
- Paper機能を試す場合は2GB以上の空きmemoryを推奨

ローカルGradleを直接使わず、`./gradlew`／`gradlew.bat`を使います。wrapperはGradle 9.6.1へ固定されています。

## よく使うコマンド

```bash
./gradlew clean build
./gradlew test
./gradlew shadowJar
./gradlew run --args="--help"
./gradlew run --args="validate examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json"
./gradlew run --args="generate examples/phase6-structures/request.yml examples/phase6-structures/terrain-intent.json build/phase6-preview"
./gradlew run --args="export examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/phase2-exports"
./gradlew run --args="verify build/phase2-exports/rocky-coast-001/<release-id>.zip"
./gradlew run --args="journal-verify examples/placement-journal.json"
./gradlew run --args="design import examples/rocky-coast/request.yml examples/rocky-coast/terrain-intent.json build/phase5-designs build/phase5-jobs"
./gradlew run --args="design-verify build/phase5-designs/rocky-coast-001/<job-id>"
./gradlew runServer
```

Paper JAR:

```text
build/libs/LandformCraft-<version>.jar
```

これはPaper用の配布JARです。CLI用JARには`-cli` classifierが付きます。`PluginArtifactTest`がmain classとplugin metadataの存在、core classの同梱、Paper／WorldEdit本体の非同梱を検査します。

## 依存バージョン

| 対象 | 設定 | 根拠 |
|---|---|---|
| Java | 21 | Paper 1.21.11要件とVirtual Threads |
| Paper API | `1.21.11-R0.1-SNAPSHOT` | Paper公式の1.21.11座標 |
| plugin api-version | `1.21.11` | 古いserverへの誤loadを防ぐ |
| WorldEdit | `7.3.19` | Minecraft 1.21.11系の公開API |
| JUnit | BOM `6.1.2` | 純粋Javaテスト |
| Jackson | BOM `2.21.3` | strict JSON／YAML mapping |
| NetworkNT JSON Schema Validator | `2.0.4` | Draft 2020-12入力検証 |

参照:

- [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/)
- [Paper Java requirements](https://docs.papermc.io/paper/getting-started/)
- [WorldEdit 7.3.19 API](https://worldedit.enginehub.org/en/7.3.19/api/)
- [Gradle Java toolchains](https://docs.gradle.org/current/userguide/toolchains.html)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Claude Structured Outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)
- [Claude API errors／retry](https://platform.claude.com/docs/en/api/errors)

## コーディング規約

- Java packageは全小文字、型名は通常UpperCamelCase。Paper mainの `Landformcraft` は明示された外部契約なので例外。
- domain dataはrecordを優先し、compact constructorで単純な局所不変条件を守る。
- nullを意味のある状態として使わない。optional状態は明示的な型で表現する。
- collectionはdefensive copyを作り、公開後に変更できないようにする。
- 時刻は`Instant`、識別子は意味のあるvalue type／UUID、距離・座標は単位を名前に含める。
- seedの派生手順は順序とhash algorithmを固定し、`HashMap`のiteration orderなど非決定要素へ依存しない。
- 外部adapterはdomain型へ変換してからcoreへ渡す。

compilerは `--release 21`、UTF-8、`-Xlint:all -Werror` です。warningを抑制するときは理由を最小範囲に記載します。

## テスト戦略

### 1. Pure Java unit test

対象:

- recordの不変条件
- schema mapping
- seed再現性
- global座標とtile境界
- height、水系、material、structure collision
- manifest／checksum
- Sponge v3 NBT bounds／palette／block count
- Release directory／ZIPのread-back、改変、欠損、重複tile
- WorldEdit readerと公開paste APIによるtile相対位置
- placement confirm gate、tile checkpoint、途中失敗の逆順rollback、snapshot改変／欠損拒否
- OpenAI／Claude request shapeとstructured response mapping
- 429／5xx retry、timeout、cancel interrupt、ローカルRPM／token budget
- Design Packageのread-back、改変／欠損／未知file拒否、監査のsecret非保持
- 画像のmagic／拡張子／byte／dimension／pixel／single-frame／symlink検査
- EXIF orientation、metadata除去、PNG再符号化、上面図座標と文章／画像矛盾検査
- OpenAI／Claudeの画像payload shape、元path非送信、画像あり／なしの共通pipeline
- `image-evidence.json`のSchema round-trip、checksum改変／欠損拒否
- 8種類のbuilt-in asset、semantic checksum、rotation、terrain-following
- 水／崖／structure衝突と、配置不能時のterrain-only fallback
- structure込みtile／standalone asset `.schem`のread-back
- required asset／placement Schema、Releaseのasset改変／欠損／version拒否
- world mutation Futureのcancel拒否、停止時の新規mutation拒否、journal recovery

PaperやWorldEditを起動せず高速に実行します。

### 2. Application contract test

Provider、artifact repository、clock、executorをfakeへ差し替え、成功、timeout、retry、cancel、checkpoint recoveryを検証します。

### 3. Real-server smoke test

`run-paper`でPaper 1.21.11を起動し、plugin load、command、Scheduler、WorldEdit／FAWE adapterを検証します。WorldEdit 7.3.19環境とFAWE 2.15.2環境は分け、同時導入しません。2026-07-14に0.9.0-beta.1をWorldEdit 7.3.19で64×64／1 tile／4 structure Releaseへplan（293,621,754 bytes予約）→snapshot→apply→full verify→別token Undoし、`UNDONE` journal read-backと正常停止を確認しました。最初の試行で柵connection state差分をexact verifyが検出して自動rollbackし、生成paletteを修正後に再成功しています。FAWE 2.15.2のcurrent smokeは独立`run-fawe`でplugin initializationまで確認しましたが、実行sandboxのport bind制限によりload／applyへ到達していないため未確認です。

`JavaPlugin`を直接constructorで生成するテストは禁止します。

## 新しいpackageを追加するとき

1. `src/main/java/com/github/nankotsu029/landformcraft/` 配下へ置く
2. 依存方向を `docs/architecture.md` と照合する
3. packageの責務をREADME表へ追加する
4. `src/test/java`へ最小のunit testまたはsmoke testを用意する
5. `./gradlew build` とconfiguration cache再利用を確認する

Gradle subprojectの追加は通常行いません。独立配布、別runtime、依存隔離などpackageだけでは解決できない理由がある場合にADRを追加してから分割します。

## 文書とスキーマ

- 進捗状態は `docs/roadmap.md` のみを正本にする
- 外部データの正本は `schemas/`、JSON/YAMLは `examples/` に置く
- manifestの正本は `docs/artifact-format.md` と対応する`schemas/`内のschema
- commandの正本は `docs/commands.md`
- 設定と運用は `docs/operations.md`
- 理由はADR、現在の仕様は通常docsへ記載する

## リリース前確認

- 全testとbuildが成功
- Paper 1.21.11でloadできる
- secret scanner／手動確認でキーがない
- schema version、generator version、plugin versionが適切
- exampleがschemaに合う
- release artifactにsource-only依存やWorldEdit本体をshadeしていない
- roadmapが実態に一致する
