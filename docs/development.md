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

PaperやWorldEditを起動せず高速に実行します。

### 2. Application contract test

Provider、artifact repository、clock、executorをfakeへ差し替え、成功、timeout、retry、cancel、checkpoint recoveryを検証します。

### 3. Real-server smoke test

`run-paper`でPaper 1.21.11を起動し、plugin load、command、Scheduler、WorldEdit／FAWE adapterを検証します。WorldEdit環境とFAWE環境は別server profileにし、同時導入しません。

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
- manifestの正本は `docs/artifact-format.md` と将来のschema
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
