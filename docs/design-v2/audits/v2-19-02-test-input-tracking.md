# V2-19-02 Gradle test input tracking（filesystem inventory test）

> Status: **PASS（2026-07-23）**。[横断監査 rev.2](../../audits/cross-cutting-audit-2026-07-23.md) §6-1／§7末行が確定した「filesystem inventory testの走査対象がGradle test taskのinputに登録されておらず、up-to-date／build cache再利用が失敗を隠す」欠陥を修正し、cache有効／無効で同一判定になることを実測で固定した。**testの判定規則（orphan判定、link解決、schema inventory）は一切変更していない**。production code、Schema、example、artifact、checksumへの変更は0件である。

## 1. 欠陥（修正前の実測）

`SchemaContractTest`は実行時に`src`／`docs`／`README.md`／`schemas`／`examples`を走査し、`DocsLinkConsistencyTest`は`docs`＋`README.md`／`AGENTS.md`／`CHANGELOG.md`を走査する。しかし`build.gradle.kts`の`test` taskが宣言していたinputは`pluginJar`＋registry 4ファイル（`V2-15-02`／`V2-15-04`）だけだった。Gradleは宣言されないファイルの変更を検知しないため、同一の作業tree（`org.gradle.caching=true`）で判定が分岐する。

| # | 操作 | 結果 |
|---|---|---|
| 1 | `./gradlew test --tests '*SchemaContractTest'`（2回目） | `Task :test UP-TO-DATE`／BUILD SUCCESSFUL |
| 2 | 未追跡のorphan example（`examples/v2/diagnostic/scenarios/…probe…json`）を作成し、同じcommandを再実行 | `Task :test UP-TO-DATE`／**BUILD SUCCESSFUL（失敗が隠れる）** |
| 3 | 同じ作業treeで`--rerun-tasks`を付与 | `SchemaContractTest > everyExampleDocumentIsReferencedBySourceOrDocs() FAILED`／BUILD FAILED |

2と3は**同じbyteの作業tree**に対する相反する判定であり、監査が記録した時点依存（rev.1で2件orphan → rev.2で1件）と同じ機構である。

## 2. 修正

### 2.1 `build.gradle.kts`

走査rootを単一のlist（`filesystemInventoryRoots`）へ集約し、`test` taskの入力として宣言した。normalizationは`PathSensitivity.RELATIVE`とし、cache keyをproject位置から独立させる（test自身も相対pathで読む）。

```text
AGENTS.md, CHANGELOG.md, README.md, build.gradle.kts, docs, examples, schemas, src
```

`V2-15-02`／`V2-15-04`が個別宣言していた4ファイル（`schemas/terrain-intent-v2*.schema.json`、`docs/design-v2/*-registry.md`）は`schemas`／`docs`に包含されるため、宣言をこのlistへ統合した（追跡範囲は縮小せず拡大している）。`build.gradle.kts`自身をrootへ含めるのは、`GradleTestInputContractV2Test`がこのscriptを読んで判定するためである。

### 2.2 走査rootの単一正本と双方向のdrift検出

- `buildcontract.FilesystemInventoryRootsV2`（test scope）が走査rootの正本を持ち、`SchemaContractTest`／`DocsLinkConsistencyTest`はrootをこの定数から取る。判定規則そのもの（≥2 segmentのpath suffix一致、heading slug解決、schema inventory比較）は未変更である。
- `buildcontract.GradleTestInputContractV2Test`が3方向を固定する。
  1. `build.gradle.kts`の`filesystemInventoryRoots`＝`FilesystemInventoryRootsV2.SCANNED_ROOTS`（順序含む）。
  2. `test` taskが実際にそのlistを`inputs.files(...)`＋`withPropertyName`＋`PathSensitivity.RELATIVE`で消費している。
  3. **新規test向けdrift guard**: `src/test/java`配下の全`Path.of("…")` literalの先頭segmentを走査し、repository root直下に実在するものが宣言rootへ含まれていることを要求する。`@TempDir`相対のbare filenameはroot直下に実在しないため対象外、`build`だけは許可rootとして明示する（verdictを左右する成果物は`pluginJar` inputで別途宣言済み、その他はtest自身が作るscratch）。

この3のguardは実装中に実効性を示した — 導入直後に`GradleTestInputContractV2Test`自身の`build.gradle.kts`読取りを未宣言rootとして検出し、rootへ追加した。

### 2.3 再現scriptとcache有効／無効の同値性

`scripts/ci/v2-19-02-inventory-input-check.sh`が§1の実測をpass/fail checkとして再実行する。probeは実行後に必ず削除する（`trap`）。

## 3. 検証記録（2026-07-23）

### 3.1 `scripts/ci/v2-19-02-inventory-input-check.sh`（8/8 期待どおり、21秒）

```text
== baseline: clean working tree ==
OK   clean --build-cache: pass
OK   clean --no-build-cache: pass
== untracked orphan example must be seen even by a warm cache ==
OK   orphan-example --build-cache: fail
OK   orphan-example --no-build-cache: fail
== untracked broken docs link must be seen even by a warm cache ==
OK   broken-docs-link --build-cache: fail
OK   broken-docs-link --no-build-cache: fail
== restored working tree ==
OK   restored --build-cache: pass
OK   restored --no-build-cache: pass
V2-19-02 inventory input check PASSED
```

§1の表の行2は、修正後は`--build-cache`／`--no-build-cache`のいずれでもFAILとなり、行3（`--rerun-tasks`）と一致する。

### 3.2 suite

- `./gradlew test`: **PASS**（219 class／1256 test／9 skip／0 failure／0 error、7分8秒）。
- 直後の`./gradlew test`: `Task :test UP-TO-DATE`。example fixtureを書き戻すtest（`Foundation*SliceCompilerV2Test`等の`Files.copy`）はcommit済みbyteと同一を書くため、input宣言後もtaskが自己無効化しないことを確認した。
- `./gradlew build`: **PASS**。
- `git status --short`: 意図した差分のみ（生成物・probe・`.task-context/`の混入なし）。

## 4. Scope外／不変

- testの判定規則、Schema、example、artifact、Release format、terrain field／tile／block semantic checksum、v1 goldenはいずれも不変（この監査で触れていない）。
- CI設定ファイルは本repositoryに存在しないため、cache有効／無効の同値性は§2.2のtestと§2.3のscriptで固定した。CIを導入する際は`./gradlew test`に加えてこのscriptを実行する。
- 既知のcost: `docs`配下の編集は`test` task全体を再実行させる。これは判定の正しさと引き換えの意図した挙動であり、build cacheがrevert時の再実行を吸収する。
