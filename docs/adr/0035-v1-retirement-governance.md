# 0035: v1退役のgovernanceと互換性policyの段階的解除

- Status: **Accepted**（2026-07-21、rev.5を承認。初回承認は2026-07-20のrev.3）
- Date: 2026-07-20（rev.4／rev.5: 2026-07-21）
- Decision scope: V2-12-01（適用範囲は`V2-12-05`／`V2-12-06`。rev.4で`V2-12-08`〜`V2-12-10`を追加、rev.5で未公開期間中のdeprecation window免除条件を追加）
- Supersedes: なし（AGENTS.md §6のv1凍結を**置換せず、解除条件を追加する**）

## Context

`V2-0`〜`V2-11`はv1を一切変更せず、version dispatchでv2を並設してきた。AGENTS.md §6はv1 Schema、generator `3.0.0-phase6`、Release format 1、v1 placement／Undo、既存checksum／goldenを無条件に凍結している。この凍結は、v2が未成熟な間に利用者資産と回帰基盤を守るために正しかった。

2026-07-20の全体再監査は次を確認した。

- 通常のCLI／Paper経路は依然v1である。
- v2のrequest→design→generate→preview/export→Release 2 placementはproduction経路として接続されていない（`V2-12-02`／`V2-12-03`）。
- v1→v2 migration toolが存在しない（`V2-12-04`）。
- `V2-11-01`／`V2-11-06`により`surface-2_5d`の4 featureは`paper_apply` `SUPPORTED`、published dimension limitは1000×1000（FAWE 2.15.2 evidence）である。

したがってv1の削除・改名は「いつか行う」ではなく、**どの証拠が揃った時にどの順序で何を消すか**を先に正本化しなければならない。順序を決めずに削除を始めると、凍結解除の判断がTask単位の裁量になり、利用者資産の非可逆な破壊とcanonical checksumの意図しない変化を招く。

本ADRはコードを変更しない。`V2-12-05`以降のTaskが参照する削除・改名・deprecationの**唯一の正本**を定める。

## Decision

### D0. 正本関係

本ADRは削除・改名・deprecationを**許可するgovernance境界**の正本である。[Task Index](../design-v2/task-index.md) は具体的な**実行ScopeとAcceptance gate**の正本である。

両者が矛盾する場合は、**より厳しい制約を適用して作業を停止し、両方を同期するまで削除・改名を実行しない**。「ADRでは許可されているがTaskでは禁止」「Taskには書かれているがADRでは禁止」のいずれも、実行の根拠にしない。

### D1. 凍結は本ADRのAcceptだけでは解除されない

AGENTS.md §6のv1凍結は、次の**両方**が成立した時にのみ、かつ本ADRが列挙した範囲に限って解除される。

1. 本ADRが`Accepted`である（人間承認）。
2. 対象v1機能について、D2の表が定める**カバレッジ前提Task**が完了している。

`V2-12-02`〜`V2-12-04`はv1を変更しないため、本ADRのAcceptを待たずに実行できる。v1契約に触れるTaskは`V2-12-05`（deprecation）と`V2-12-06`（削除）だけであり、両者は人間承認を必須とする。

### D2. 削除対象v1機能の一覧と根拠

各行は、`V2-12-06`が当該資産を削除してよい条件である。前提Taskと追加条件が未完なら削除しない。

**「書けなくする」と「読めなくする」は別の決定である。** v1 artifactの新規生成は削除するが、既存v1 artifactのread／verify／migrateはD2bのlegacy compatibility境界として残す。

#### D2a. 削除対象（writer／実行経路）

| # | 削除対象 | 主な資産 | v2カバレッジ | 前提Task | 追加条件 |
|---|---|---|---|---|---|
| R1 | v1 generator `3.0.0-phase6` | `generator/{TerrainGenerator, LogicalLayoutGenerator, LogicalTerrainLayout, DeterministicNoise, TerrainChecksum, StructurePlanner}` | `generator.v2.*`（V2-2〜V2-5、V2-9／V2-10） | `V2-12-02` | v1 golden corpusがD3-K1のimmutable archiveへ変換済みで、v2 equivalence／migration regression testが同等scenarioをカバーしていること |
| R2 | Release format 1／Design package v1 の **publisher** | `format/{ReleasePublisher, ReleaseArtifacts, DesignArtifactPublisher}`、`examples/*/release-manifest.json`のうちfixture用途以外 | Release format 2 core＋4 capability（ADR 0012／0013／0014／0019）、`format.v2.design.DesignArtifactPublisherV2` | `V2-12-02`＋`V2-12-04` | D2bのlegacy readerがmigration tool内で自己完結していること。`ReleaseVerifier`／`ReleaseVerification`／`DesignArtifacts`／`DesignArtifactVerifier`／`DesignVerification`は**削除せずD2bへ縮退**する |
| R3 | v1 placement／Undo／recovery | `core/{PlacementApplicationService, PlacementWorldGateway, PolicyPlacementWorldGateway, FilePlacementJournalRepository, FilePlacementSafetyStore, SnapshotCleanupService, PreparedPlacement, PreparedRecovery, PreparedCleanup, PlacementDiskEstimator, PlacementDiskEstimate}`、`paper/PaperWorldEditPlacementGateway` | Release 2 placement lifecycle（ADR 0020〜0028、0034） | `V2-12-03`＋`V2-12-05` | D8のdrain gate通過（v1 journal／Undo／recovery／cleanup stateが残っていないこと）＋**D11の`V2-12-10`完了**（cleanup経路のv2等価物） |
| R4 | v1 design／generation orchestration | `core/{TerrainDesignApplicationService, GenerationApplicationService, ReleaseApplicationService, PaperWorkflowService, BlueprintCompiler, DesignResponseCache, GenerationJobRepository, FileGenerationJobRepository, GenerationTask, GenerationOutcome, DesignJobHandle, TerrainDesignProviderFactory}` | `core.v2.design.TerrainDesignApplicationServiceV2`＋`V2-12-02`のproduction export経路 | `V2-12-02` | D8のdrain gate通過（未完了・再開可能なv1 generation jobがゼロ）＋**D11の`V2-12-08`＋`V2-12-09`完了**（request authoringとjob／candidate／export lifecycleのv2等価物） |
| R5 | v1 provider adapter | `ai/openai/OpenAiTerrainDesignProvider`、`ai/anthropic/AnthropicTerrainDesignProvider`、`ai/http/AbstractHttpTerrainDesignProvider`、`ai/spi`のv1専用型 | `ai/*/v2`（`V2-6-11`が実装、ADR 0029のcapability negotiation） | `V2-6-11`＋`V2-12-03` | provider設定key、`ProviderFailureCode`へのerror mapping、retry／quota、audit記録がv2側で同等であること。**この同等性検査は`V2-12-05`のカバレッジ監査で実施し証拠を残す**（`V2-12-03`はcommand routingのみ） |
| R6 | v1 CLI／Paper command | `cli/LandformCraftCli`のv1 verb、`paper/LandformCraftCommand`のv1 branch、対応permission node | `V2-12-03`のv2 command経路 | `V2-12-03`＋`V2-12-05` | D5のdeprecation window通過。未公開プロジェクトのためtelemetryは不要だが、削除するverbごとにv2代替verbを1対1で対応付けた表を`V2-12-06`の証拠に残す |
| R7 | v1専用Schema／example の**active化解除**（削除ではなく移設） | `schemas/{terrain-intent, world-blueprint, generation-request, generation-job, design-audit, export-manifest, placement-journal, placement-safety-state, snapshot-cleanup-plan, structure-placements}.schema.json`と対応`examples/` | 各`-v2.schema.json`＋`examples/v2/` | `V2-12-04` | active Schema inventoryから除外し、**migration専用のimmutable legacy contract resourceへ移設**する（下記R7詳細）。packaged JARだけでv1 artifactをstrict readできること |
| R8 | V2-0互換spine | `generator/v2/V1TerrainQueryAdapter`とそのtest | 不要（v1 layoutを読むためだけの型。production未使用） | R1と同時 | なし |

R4とR3の追加条件はrev.4のD11で強化した。削除対象集合そのものは不変である。

**R1〜R8以外のv1資産を`V2-12-06`が削除することは本ADRで承認していない。** 一覧外の削除が必要になった場合は`V2-12-06`を停止し、本ADRのamendmentを取得する。

#### D2b. legacy compatibility境界（削除しない）

既存利用者資産をread・verify・migrateする経路は、writer削除後も残す。production workflowからは呼ばず、migration専用経路だけが使用する。

| 対象 | 扱い |
|---|---|
| v1 intent reader | `terrain-intent` を strict read するmodel／codec／validator |
| v1 design package reader／verifier | `format/{DesignArtifacts, DesignArtifactVerifier, DesignVerification}` をmigration専用のread-only経路へ縮退する。publisherは R2 で削除する |
| Release 1 reader／verifier | `format/{ReleaseVerifier, ReleaseVerification}` をmigration専用のread-only legacy verifierへ縮退する。publishとplacementからの参照は削除する |
| v1 canonical digest計算 | `format/Sha256`（D3-K2、version中立）と、v1 artifactのdigest算出に必要な正規化処理 |
| migration mapping | v1→v2 contract mapping本体（`V2-12-04`） |
| legacy contract resource | R7で移設するimmutable Schema／fixture（R7詳細） |
| 想定される配置 | `legacy.v1.reader` ／ `legacy.v1.verifier` ／ `legacy.v1.migration` 相当の隔離package。正確なpackage名は`V2-12-04`／`V2-12-06`の実装時に確定してよい（D4により内部識別子は自由） |

**削除対象からの除外規則（重要）**

> v1 intent／design package／Release 1 のmigration専用readerに必要な model、codec、strict validator、version dispatch は**削除対象外**である。R4またはR7に含まれる既存型がこれに該当する場合、R4／R7の削除**前**にlegacy migration moduleへ移設する。移設せずに削除してはならない。

R4は`DesignResponseCache`、`TerrainDesignApplicationService`、`BlueprintCompiler`、job repository、provider factoryを削除するため、この中にv1 design package読取に必要なcodec／model／repositoryが混在していないかを、R4着手時に依存グラフで確認する。混在していれば先に移設する。

**R7詳細: Schemaは削除ではなく移設する**

v1 Schemaを完全削除してfield名・必須集合をJavaコードへ再実装すると、二重実装・転記ミス・required集合の欠落を招き、D2b Acceptance（strict read、fail closed拒否）を満たしにくくなる。したがってR7は次を意味する。

- `schemas/`のactive inventory（`V2-11-03`が固定したdisk＝bundle＝packaged resource一致検査の対象）から除外する。
- 同じ内容を`legacy/v1/contracts/`相当の隔離領域へ**immutable resource**として移設し、packaged JARへ含める。
- migration専用readerはこのlegacy resourceを使ってstrict readする。公開Schema inventoryやuser docsのSchema一覧には現れない。
- 移設後、legacy contract resourceの内容を変更しない。変更が必要ならADR amendmentを要する。

D2bのAcceptance条件は次のとおりである。

1. packaged JARだけでv1 intent、v1 design package、v1 Releaseを読める。
2. active `schemas/`ディレクトリを実行時に参照しない（legacy contract resourceのみ参照する）。
3. offline環境で動作する。
4. unknown version、strict v1 contractに定義されないfield、破損artifactを、migration reportへ記録したうえで**拒否**する。黙って破棄、推測変換、部分的成功として扱わない（D9と同一規則）。
5. migration fixture testが`V2-12-06`完了後も通る。
6. `V2-12-04`は、**R4／R7削除後を模した状態**（active schemasとv1 orchestrationが存在しない構成）でのpackaged-JAR migration testを持つ。

#### D2c. 削除前に解消が必要な既知の構造問題

静的依存の実測（2026-07-20）で次を確認した。`V2-12-06`はこれを解消してから該当段階へ進む。

- `format/v2/design/DesignArtifactPublisherV2` が `format.ReleaseVerifier.deleteTree(...)` を使用している。**v2 classがR2分類のclassへ依存している。** `deleteTree`はRelease format 1の意味を持たない汎用file utilityであるため、R2着手前にD3-K2の共通基盤（`format`のversion中立utility）へ抽出する。抽出はcanonical checksumに影響しない。
- `ai/*` が `core.GenerationExecutors` を参照している。これはD3-K2であり退役対象ではない。R5削除時に巻き込まない。

### D3. 維持するv1資産（削除対象外）

| # | 維持対象 | 扱い | 理由 |
|---|---|---|---|
| K1 | v1 golden corpus（`V1CompatibilityGoldenTest`のfixtureと期待checksum） | **R1削除前にimmutable archiveと等価回帰testへ変換する**（下記K1詳細）。fixture dataは恒久維持し、削除するのはv1 generatorを実行するtest harnessだけである | 削除順序の途中でv1回帰を失うと、R1〜R6の削除が意図せぬcanonical変更を伴ったか検証できない |
| K2 | version中立の共通基盤 | **v1退役の対象ではない**。改名（D4）のみ適用可 | `core/{CancellationToken, LandformException, LandformErrorCode, GenerationExecutors, WorldAccessPolicy, DiskBudgetPolicy, ArtifactRepository, DoctorService, DoctorReport, ProviderSettings}`、`format/{Sha256, LandformDataCodec}`、`worldedit/`の共通adapter、`model/`のv2が参照するprimitiveはv1契約ではない |
| K3 | custom asset catalog | **削除対象から明示的に除外する** | `structure/{StructureAsset, StructureBlock, StructurePlacementKind, BuiltInStructureAssetCatalog}`、`core/CustomAssetService`、`schemas/custom-asset-*.schema.json`、`examples/custom-asset/`、`examples/phase6-structures/`、`asset` commandに**v2等価物が存在しない**。削除するならv2カバレッジTaskの新規追加が先に必要であり、本ADRはそれを承認しない |
| K4 | 運用verb（**verb名のみversion中立**） | verb名を維持し、`V2-12-05`でv2経路へ再接続する。backendはartifact version dispatchを持つ | `doctor`／`version`／`help`はversion非依存だが、`recovery`はjournal・snapshot・safety stateを、`job`はgeneration job stateを読むため**backendはversion依存**である。verb名の維持を「実装もversion中立」と読み替えない |
| K5 | 参照用のv1 ADR（0001〜0009等） | 維持する | 決定履歴であり、実装が消えても記録は消さない |

K3は、`V2-12-01`のGate停止条件「v2が未カバーのv1機能が削除対象に含まれる場合は停止」に該当し得た項目として、**2026-07-20監査時点で明示的に確認された**ものである。削除対象から除外することで停止条件を成立させない。将来custom assetをv2へ移す場合は、新Task ID（`V2-12`系の未使用ID）を追加してから本ADRをamendする。

全機能カバレッジの確認は`V2-12-05`のv1→v2カバレッジ監査が改めて行う設計であるため、K3を「唯一の未カバー項目」と断定しない。**`V2-12-05`で他の未カバー機能が判明した場合は停止し、本ADRをamendする。**

**K1詳細: v1 goldenの変換方針**

`V1CompatibilityGoldenTest`は`generator.TerrainGenerator`（R1）を直接実行するため、R1削除でtestは検証対象を失う。「fileを残す」と「実行可能なtestとして残す」を分離し、R1削除**前**に次の2種類へ変換する。

1. **Immutable archive**（恒久維持）— v1入力request、期待checksum、期待manifest、生成元のgenerator version文字列、生成済みcanonical block-state出力を、再生成不能な固定資料として保存する。以後いかなるTaskもこの値を書き換えない。
2. **v2 equivalence／migration regression test**（恒久維持）— archiveのv1 fixtureをmigration tool（`V2-12-04`）へ入力してv2 artifactへ変換し、下記の資産別基準で検証する。

すなわち「v1 generatorを実行するgolden test」を残すのではなく、「過去のv1出力を固定資料として保存し、migrationとv2側の回帰検査に使う」形へ移す。この変換完了はD2aのR1追加条件であり、未完ならR1を削除しない。

**equivalenceの定義は資産別に分ける。** 「意味的同等性」を全資産へ一律適用しない。

*Release 1 → Release 2（完全一致を要求する）*

- canonical block-state streamの完全一致
- bounds／dimensionの完全一致
- placement対象座標の完全一致
- checksumが形式差（format 1／2のcanonicalization差）を除いて1対1に対応付くこと

*v1 intent／design package → v2（構造対応を要求し、生成結果一致は要求しない）*

- v1で明示されていた制約を失わない
- hard constraintを新規に推測しない（欠落はdraft／未指定のまま。AGENTS.md §6と同じ規則）
- 未表現情報をdraft／unspecifiedとして残す
- feature、bounds、seed、関係のmappingが一致する
- **生成後block完全一致は保証対象にしない**（v2 generatorはv1と異なるため）

この2種を同一のequivalence testで扱わない。

### D4. 名称変更方針

**自由に変更してよい（`V2-12-06`のScope内）**

- Javaのclass名、interface名、record名、method名、field名、local変数名、package名。`V2`／`v2` suffixの除去を含む。
- test class名、test method名、fixture変数名。
- docs、CHANGELOG、コメント、log message本文の表記。

**ADRが明示承認した場合のみ変更してよい（既定は不変）**

次はcanonical checksum、strict verifier、artifact互換のいずれかに影響するため、識別子として凍結する。

- Schema `$id`と`schemas/`のファイル名（`-v2` suffixを含む）
- `manifest.json`の`requiredCapabilities[]`名（`surface-2_5d`、`hydrology-plan`、`environment-fields`、`sparse-volume`）
- artifact typeとversion token
- built-in module ID、stage ID、named seed
- contract ID、generator version文字列
- `LandformErrorCode`の安定error code値
- operational metricのlabel名とaudit JSONLのfield名
- canonicalization contract — すなわち、canonical serializationの**field名、定数token、順序規則**として各contractのcanonicalization仕様に定義され、checksum golden testで固定されている識別子

最後の項目は「canonical checksum入力に含まれる全文字列」ではない。utf-8化されるuser入力値やpathまで凍結すると実行不能な規則になるため、凍結対象はcanonicalization仕様に列挙されstable tokenとしてgolden testで固定されているものに限る。ある識別子が凍結対象か不明な場合は、変更してchecksum golden testが落ちるかで判定するのではなく、**変更せずADR amendmentを求める**（fail closed）。

**Java API境界**

LandformCraftのJava packageおよびpublic classは、現時点で外部互換性を保証するpublic APIでは**ない**。外部統合の安定境界はCLI、Paper command、permission node、Schema、artifact contractに限定する。この前提が変わり外部plugin向けSPIを公開する場合は、当該SPIをD4の自由変更対象から除外するamendmentが必要である。

**本ADRが明示承認する識別子変更**

| 対象 | 変更 | 影響 |
|---|---|---|
| Paper permission node | `landformcraft.r2.*` → `landformcraft.v2.*` | checksum非影響。`V2-12-05`で新nodeを追加し旧nodeをdeprecated aliasとして維持、`V2-12-06`で旧nodeを削除する |
| Paper／CLI command名 | D5のとおり | checksum非影響 |

上記2件以外に、凍結識別子の変更は**一切承認していない**。特に`V2` suffixはJava識別子でのみ除去でき、Schema `$id`・ファイル名・capability名からは除去しない。これらの`-v2`はversion世代ではなく**契約の同一性**を表すためである。

### D5. `/lfc r2` の正式command名とdeprecation window

`/lfc r2`はADR 0034が評価用にv1と明示分離するために導入した暫定名である。正式名を次の順序で確定する。

| Task | Paper | CLI | v1経路 |
|---|---|---|---|
| `V2-12-03` | `/lfc v2 <request\|design\|generate\|preview\|export\|place\|undo\|status>` を正式名として追加。`/lfc r2 <...>` は同一semanticのdeprecated aliasとして維持し、実行時にdeprecation警告を出す | `lfc v2 <verb>` を追加 | 不変（凍結継続） |
| `V2-12-05` | `/lfc <verb>` の既定をv2へ切替。v1は `/lfc v1 <verb>` の明示opt-inのみ。v1実行時にdeprecation警告を出す | `lfc --v1 <verb>` でのみv1（下記CLI構文） | deprecated（動作は不変） |
| `V2-12-06` | `/lfc r2` alias、`/lfc v1`、`landformcraft.r2.*` node、`--v1` を削除。`/lfc v2 <verb>` は**恒久的に**`/lfc <verb>`のexplicit-version aliasとして残す | `lfc v2 <verb>` を恒久維持 | 削除（D2の範囲） |

`/lfc v2`を恒久alias として残すのは、`V2-12-03`時点で書かれた運用scriptを`V2-12-06`が壊さないためである。

**CLI構文**: `--v1`は既存parserに合わせ、**位置に依存しないglobal option**とする（現行`LandformCraftCli`は`--data-dir`／`--json`／`--quiet`／`--verbose`を同方式でparseする）。正規表記は`lfc --v1 <verb> ...`である。Paper側の`/lfc v1 <verb>`とは表記が揃わないが、CLIのsubcommand位置にversion tokenを差し込む方式は既存parserの構造変更を伴い、`V2-12-05`のScope（default routing切替）を超えるため採らない。`lfc v2 <verb>`はPaperと揃えたsubcommand形式で追加し、`--v1`との非対称は`docs/commands.md`へ明記する。

**deprecation window**（`V2-12-05`完了から`V2-12-06`開始まで）は次を**すべて**満たす期間とする。

1. v2既定・v1 opt-inの構成を含むtagged releaseが1回以上存在する。
2. `V2-12-05`完了日から30日以上経過している。ただし未公開（0.9系、`Unreleased`）の間に限り、repository ownerが残存riskを受諾し、`V2-12-06`の開始と30日条件の免除を同じ承認記録で明示した場合はこの条件を免除できる。
3. `V2-12-05`のv1→v2カバレッジ監査に未承認の劣化が残っていない。
4. `V2-12-06`開始について人間承認が記録されている。

本プロジェクトは未公開（0.9系、`Unreleased`）であるため、条件1を満たすtagが存在しない場合に限り、**`V2-12-05`のAcceptance gate**が「v1経路にしか存在しない利用可能機能はゼロ」を証拠付きで確認することで条件1を代替できる。条件2は上記のrepository ownerによる明示免除だけで代替でき、条件3〜4は代替できない。公開releaseまたはtagged releaseの配布後は条件2の免除を使用できない。

この代替判定を`V2-12-07`に置かないのは、通常のTask順が`V2-12-05` → `V2-12-06` → `V2-12-07`であり、`V2-12-06`の開始条件を後続の`V2-12-07`で満たすと循環するためである。`V2-12-07`は削除**後**の最終Phase gateとして残す。

### D6. 寸法カバレッジ劣化の明示承認

`V2-12-05`のカバレッジ監査は、v1で可能だった操作・寸法がv2で同等以上であることを要求する。現時点で既知の唯一の劣化は次であり、本ADRはこれを**明示承認する**。

- WorldEdit 7.3.19単独runtimeにおけるv2 Paper applyのpublished limitは64×64である（`V2-11-06`）。1000×1000のevidenceはFAWE 2.15.2単独（`V2-11-04`／`V2-11-05`）であり、WorldEdit単独runtimeでの64超は未実測である。
- 承認理由: 未実測寸法を`SUPPORTED`にしない原則（task-execution-guide §8）を寸法evidenceより優先する。運用者はFAWE 2.15.2を導入することで1000×1000まで利用できる。

**rev.4で追加承認した劣化（provider failure codeの粒度）**

`V2-12-05`の監査（2026-07-21）が検出したF4を、D2a-R5の追加条件「`ProviderFailureCode`へのerror mappingがv2側で同等であること」の範囲内として**明示承認する**。

- v1の`ProviderFailureCode`は9値（`AUTHENTICATION`／`INVALID_REQUEST`／`RATE_LIMITED`／`SERVER_ERROR`／`TIMEOUT`／`INVALID_RESPONSE`／`LOCAL_RATE_LIMIT`／`TOKEN_BUDGET_EXCEEDED`／`TRANSPORT_ERROR`）である。v2の`DesignFailureCodeV2`はこれを4値へcollapseする（認証失敗とrequest不正→`INVALID_REQUEST`、rate limitとlocal rate limitとtoken budget超過→`BUDGET_EXCEEDED`、timeoutとserver errorとtransport error→`PROVIDER_TRANSPORT`、`INVALID_RESPONSE`は1対1）。運用者に見えるcodeとmessageからは元の区別を復元できない。
- 承認理由: 元の`ProviderFailureCode`は例外のcause chainに保持されており、管理者ログでは追跡できる。`DesignFailureCodeV2`はD4が凍結対象に挙げたerror codeであり、拡張はcanonical契約変更とdocs／test同期を伴う。粒度低下の運用上の影響（原因分類が管理者ログ参照を要する）は、凍結識別子を変更する費用に見合わない。
- 本承認は**surface されるerror codeの粒度**に限る。設定key、retry、quota、audit記録の同等性は`V2-12-05`の監査で確認済みであり、これらに劣化が生じた場合は本承認の範囲外である。
- 将来`DesignFailureCodeV2`の粒度を上げる場合は、本ADRのamendmentを要する（D4）。

**承認範囲の限定**

本節が承認した劣化は、（1）WorldEdit単独runtimeの寸法、（2）provider failure codeの粒度、の2件だけである。これ以外の機能・寸法劣化が見つかった場合、`V2-12-05`は停止し本ADRのamendmentを取得する。rev.4時点で未承認のまま残っている劣化はF1／F2／F3であり、これらは承認ではなく**D11のv2実装**によって解消する。

### D7. 削除順序

依存している側から消す。2026-07-20の静的依存実測で、`core`のv1 orchestration（R4: `TerrainDesignApplicationService`、`TerrainDesignProviderFactory`、`PaperWorkflowService`、`DesignResponseCache`）が`ai.spi`のv1型（R5）を**import している**ことを確認した。したがってR5をR4より先に削除するとcompileできない。既定順序は次とする。

```text
D8 drain gate
→ D2c 構造問題の解消（ReleaseVerifier.deleteTree の抽出）
→ R6（command／permission）
→ R4（design／generation orchestration）
→ R5（provider adapter）
→ R3（placement／Undo／recovery）
→ R2（Release format 1 publisher。verifierはD2bへ縮退）
→ R1＋R8（generator 3.0.0-phase6と互換spine）
→ R7（v1専用Schema／example。legacy readerの自己完結を確認後）
```

各段階の直後に`./gradlew test`と`./gradlew build`を通す。R7はSchema inventory検査（`V2-11-03`が固定したdisk＝bundle＝packaged resource一致と`examples/`参照必須）を同時に更新する。

**順序の例外条件**: 上記は論理的な既定順序である。`V2-12-06`は着手時に実際の静的依存グラフ（`jdeps`、IDE参照検索、Gradle compilation のいずれか）を取得し、削除計画へ添付する。グラフが既定順序と逆方向の依存を示した場合、各段階でbuild可能な範囲に限り、**同一削除トランザクション内の順序（R4とR5など）を変更してよい**。ただし削除対象集合そのものは変更してはならない。集合の変更にはADR amendmentが必要である。

### D8. v1 operational stateのdrainと移行

`V2-12-06`は、削除開始前に次を満たさなければならない。

drain対象は**未完了stateに限らない**。現在のretention policy上、Undo、Recovery、status表示、cleanup、監査参照の対象であるすべてのv1 operational stateをdrain対象とする。

実測（2026-07-20）で次を確認した。`PlacementApplicationService`のUndoは`PlacementState.APPLIED`、すなわち**正常完了したplacement**に対して実行される。`SnapshotCleanupService`は`retentionDays`（1〜36500）のretention windowを持ち、cleanupは明示実行である。したがってR3を削除すると、**完了済みv1 placementの正当なUndo権とretention期間内のsnapshotを失う。**

対象stateは次を含む。

1. 実行中または再開可能なv1 generation job
2. 未完了のv1 placement journal、Undo、recovery、cleanup state
3. **完了（`APPLIED`）済みだがretention期間内でUndo可能なv1 placement**
4. **`job`で参照可能なv1 job history**
5. **cleanup期限前のsnapshot**
6. **recovery操作は不要だが監査目的で保持されているjournal**
7. **operatorが後からmigrationする予定のRelease 1 artifact**

各stateの処理方法は次のいずれかへ固定する。実行者の裁量で「無視して削除」を選べない。

1. v2 stateへ移行する。
2. version中立なneutral archiveへ変換する。
3. retention期限までv1 read-only backendを維持する（D2b境界内）。
4. operator承認付きで期限切れとし、事前通知とbackupを残す。

手続き条件は次のとおりである。

- 検査は`doctor`または専用preflight commandにより機械的に実施する。
- unresolved stateが1件でも存在する場合、削除を**fail closed**で停止する。
- 削除前に、対象stateとartifactのbackup inventoryを出力する。
- preflight結果はmachine-readable形式（JSON等）で保存し、`V2-12-06`のAcceptance証拠とする。

この検査は形式的手続きではなく削除の前提条件である。D3-K4が「backendはversion dispatch」とする一方、R3／R4はv1 repositoryを削除するため、dispatch先が消える前にstateを解消しなければならない。

### D9. migration toolの品質条件

`V2-12-04`が存在するだけではv1削除の根拠として不十分である。R2・R7の削除は、migration toolが次を**すべて**満たすことを前提とする。これは実装詳細ではなく、v1削除を許可するためのgovernance条件である。

1. source artifactを変更しない（非破壊）。
2. dry-runを提供する。
3. 既存の出力先をoverwriteしない。
4. deterministicである（同入力→同checksum）。
5. migration reportを出力する。
6. 元version、変換後versionを記録する。checksumは、canonical checksumが定義されている資産（Release 1等）についてはそのcanonical checksumを、定義されていないsource（`terrain-intent.schema.json`にはchecksum fieldが存在しない等）については元ファイルのbyte digestまたは規定されたcanonical source digestを記録する。
7. 一部失敗時に中途半端な成果物をpublishしない（staging→strict read-back→atomic publish）。
8. unknown version、およびstrict v1 contractに定義されないfieldを、migration reportへ記録したうえで**拒否**する。黙って破棄、推測変換、部分的成功として扱わない（D2b Acceptance 4と同一規則。reportは続行の許可を意味しない）。
9. corrupted artifactをfail closedで拒否する。
10. migration結果をRelease 2 strict verifierで検証する。

### D10. command inventory

`V2-12-05`は、**削除するverbと削除しないverbの両方**を含むcommand inventoryを証拠として残す。削除対象verbの1対1対応表だけでは、K3／K4のverbが`V2-12-06`の「v1 commandの削除」に巻き込まれる危険が残る。

| Verb | `V2-12-05`後 | `V2-12-06`後 |
|---|---|---|
| `request`／`design`／`generate`／`preview`／`export`／`place`／`undo`／`status` | v2既定、v1 opt-in（`/lfc v1`、`lfc --v1`） | v2のみ |
| `asset` | 現行実装を維持（K3） | **維持。R6の削除対象外** |
| `doctor` | v1／v2双方のstateを診断 | v2＋legacy migration診断 |
| `job` | v1／v2 dispatch | D8のstate drain後はv2、またはarchive表示 |
| `recovery` | v1／v2 dispatch | D8通過後はv2のみ |
| `version`／`help` | version非依存 | 維持 |

`V2-12-06`のR6は、この表で「維持」とされたverbを削除してはならない。

`V2-12-05`の監査（2026-07-21）が作成した**完全版inventory**（Paper／CLIの全verbを対象とし、本節の代表verb表を包含する）は [v1→v2カバレッジ監査 §3](../design-v2/audits/v2-12-05-v1-to-v2-coverage-audit.md) にある。`V2-12-06`のR6判定は本節ではなく完全版inventoryを参照する。

### D11. `V2-12-05`監査で判明した未カバー機能の解消方針（rev.4）

`V2-12-05`は2026-07-21にカバレッジ監査を実施し、D6の承認範囲外の劣化を4件検出して停止した。D3-K3が定めた停止規定（「`V2-12-05`で他の未カバー機能が判明した場合は停止し、本ADRをamendする」）に従い、本節を追加する。

**決定: 劣化を承認するのではなく、v2側へ不足機能を実装する（監査§7の案A）。**

F4のみD6へ明示承認として追記した。F1／F2／F3は次の3 Taskで解消する。Task定義の正本は [task-index.md](../design-v2/task-index.md) であり、本節はgovernance上の位置づけと削除条件への影響だけを定める。

| 新Task | 解消する劣化 | 内容 |
|---|---|---|
| `V2-12-08` | **F1**（重大） | v2 request authoring。`generation-request-v2.json`のcreate／bounds編集／WorldEdit selectionからのbounds取込／chat prompt取込／list をPaperとCLIへ実装する |
| `V2-12-09` | **F3**（重大） | v2 job／candidate／export lifecycle。非同期job store、`job status\|cancel`のv2 backend、request単位のcandidate列挙、確認token付きexportを実装する |
| `V2-12-10` | **F2**（中） | 実装済みだがcommand未接続のv2運用機能の接続。retention cleanup、v2 journal検証、CLIからのv2 recovery read-only参照 |

**削除条件への影響**

- D2a **R4**（v1 design／generation orchestration）の追加条件へ`V2-12-08`＋`V2-12-09`の完了を加える。`PaperWorkflowService`／`TerrainDesignApplicationService`／`ReleaseApplicationService`はF1／F3の唯一の実装であり、v2等価物が無い状態で削除すると機能が失われる。
- D2a **R3**（v1 placement／Undo／recovery）の追加条件へ`V2-12-10`の完了を加える。`SnapshotCleanupService`はF2のcleanup経路の唯一の実装である。
- D2a **R6**（v1 command）の追加条件は変更しない。ただしD5のdeprecation window条件1の代替判定（「v1経路にしか存在しない利用可能機能はゼロ」）は、`V2-12-08`〜`V2-12-10`完了後に`V2-12-05`が再判定する。
- 上記は削除**対象集合**の変更ではなく追加条件の付与であるため、D2aのR1〜R8一覧は不変である。

**Task順序**

`V2-12-08` → `V2-12-09` → `V2-12-10` → `V2-12-05`（再開） → `V2-12-06` → `V2-12-07`。`V2-12-05`の再開時は本ADRとTask Indexが要求する監査を最初からやり直し、F1〜F3の解消をevidenceで確認する。前回監査の結果を再利用して「解消済み」と記録しない。

**停止条件**

`V2-12-08`〜`V2-12-10`の実装中に、v2契約（`GenerationRequestV2`、`TerrainIntentV2`、Release format 2、error code、capability名）の変更が必要になった場合は停止する。これらはD4の凍結識別子または既存canonical契約であり、本節はその変更を承認していない。新機能はv2の既存契約の上に構築する。

## Consequences

- `V2-12-05`／`V2-12-06`は裁量ではなくD2／D3／D4の一覧に対する適合検査になる。一覧外の削除・改名はADR amendmentなしに実行できない。
- K3（custom asset catalog）はv2へ移行しないまま残る。v2単独運用時もassetsはv1由来の実装で提供され続ける。これは既知の技術的負債であり、`V2-12-07`の監査で明記する。
- K1のv1 goldenはR1削除前にimmutable archive＋v2 equivalence testへ変換されるため、v1 generatorが消えた後も過去のcanonical出力に対する回帰は失われない。fixture dataは恒久維持であり、削除されるのはv1 generatorを実行するharnessだけである。
- 既存利用者のv1 artifactは、writer削除後もD2bのlegacy readerでread・verify・migrateできる。「v1で新しく作れない」と「v1資産を読めない」を分離したため、削除が利用者資産の孤立を招かない。
- D2bとD9の維持コストが発生する。legacy reader、migration fixture、archive、legacy contract resourceは`V2-12-06`以降も保守対象として残る。これは利用者資産保護の対価として受け入れる。
- **`V2-12`完了後の構成は「v1コードが一切ない構成」ではない。** D2b legacy reader／verifier／migration、K1 v1 fixture archive、K3 v1由来custom asset実装、K5 v1 ADR、R7 legacy contract resourceが残る。到達目標は「v2が唯一のproduction writer／generator／placement／通常command経路であり、v1はmigration専用read-only compatibility境界と明示的維持対象だけに隔離された構成」である。`V2-12-07`のPhase gateはこの定義で監査する。
- Schema `$id`とcapability名に`-v2`が恒久的に残る。v3世代が必要になった場合はv2とは別のcontract IDが必要になる。v3の並設方針そのものは本ADRのDecision scope外であり、必要時に別ADRで判断する。
- 本ADRがAcceptされるまで、AGENTS.md §6のv1凍結は全Taskで無条件に有効である。

## Alternatives

- **v1を即時削除する案**: `V2-12-02`〜`V2-12-04`の完了前にv1を消すと、production経路が存在しない期間が生じ、既存Release 1資産の変換手段も失われるため不採用。
- **v1を恒久維持する案**: 二重のgenerator、Release format、placement、command、Schemaを維持し続けるコストが継続し、再監査が指摘した「通常経路がv1のまま」という状態を固定するため不採用。
- **削除一覧をTask側（task-index）だけに書く案**: Task Indexは実行単位の正本でありgovernance決定の履歴を残さない。承認・amendmentの記録場所として不適当なため不採用。
- **Schema `$id`から`-v2`を除去する案**: 全canonical checksumとstrict verifierの再基準化が必要になり、`V2-12-06`が「削除と改名」ではなく「契約変更」になるため不採用。
- **deprecation windowを無条件の人間承認だけで定義する案**: 期間・release・監査結果の客観条件がないと承認が形式化するため不採用。rev.5の免除は、未公開であること、V2-12-05のカバレッジ監査PASS、repository ownerによるrisk受諾、V2-12-06開始の独立承認を同時に要求する限定例外である。
- **Release format 1 verifierをpublisherと同時に削除する案**: 既存Release 1資産を読む唯一の手段を失い、migration toolがv1 contractを再実装する必要が生じる。利用者資産の孤立リスクに対して削減できる保守コストが小さいため不採用（D2b）。
- **v1 goldenをそのままv1 generatorごと残す案**: R1を削除できず、二重generatorの保守が恒久化する。archive＋equivalence testで同じ回帰価値を得られるため不採用（K1詳細）。
- **CLIを`lfc v1 <verb>`のsubcommand形式へ揃える案**: Paperとの表記統一は魅力的だが、既存CLI parserはversion tokenをsubcommand位置に持たないglobal option方式であり、`V2-12-05`のScopeを超える構造変更になるため不採用（D5）。

## Approval

- **承認日:** 2026-07-20
- **承認者:** nankotsu029（repository owner）
- **承認対象:** rev.3
- **Status:** `Proposed` → `Accepted`
- **同意範囲:** D2a削除一覧、D2b legacy compatibility境界、D3維持対象（特にK3 custom assetの削除対象からの除外）、D6のWorldEdit単独runtime 64×64カバレッジ劣化、D10 command inventory。

**rev.4の承認**

- **承認日:** 2026-07-21
- **承認者:** nankotsu029（repository owner）
- **承認対象:** rev.4
- **Status:** `Accepted`（維持）
- **同意範囲:** （1）`V2-12-05`監査のF1／F2／F3を承認ではなく**v2実装で解消する**方針（D11、監査§7の案A）と、そのための新Task `V2-12-08`〜`V2-12-10`の追加。（2）F4（provider failure codeの粒度低下）のD6への明示承認追記。（3）D2a R3／R4の追加条件強化。
- **同意しない範囲:** `V2-12-08`〜`V2-12-10`の個別Task実装の開始承認ではない。各TaskはTask Indexの契約に従って個別に実行する。`V2-12-05`の再開承認、`V2-12-06`の開始承認でもない。

**rev.5の承認**

- **承認日:** 2026-07-21
- **承認者:** repository owner（本Taskのユーザー指示。最終決定権者）
- **承認対象:** rev.5
- **Status:** `Accepted`（維持）
- **同意範囲:** 未公開（0.9系、`Unreleased`）であり、`V2-12-05`のカバレッジ監査がPASS済みであることを前提に、D5条件2の30日経過を免除する。あわせて`V2-12-06`の開始を独立承認し、短縮したdeprecation windowに伴う残存riskを受諾する。
- **同意しない範囲:** D8 drain gate、D2aのR1〜R8限定、D2b／D3維持境界、D4の識別子凍結、Acceptance testを免除しない。

### 承認が意味すること／意味しないこと

**意味すること**

- 本ADRが削除・改名・deprecationのgovernance境界の正本として発効した（D0）。
- `V2-12-05`／`V2-12-06`は、D1の第2条件（対象行のD2a前提Taskと追加条件の完了）を満たした範囲で、v1契約へ触れることが許可され得る。
- D6の寸法劣化（WorldEdit単独runtimeの64×64）は承認済みであり、`V2-12-05`のカバレッジ監査はこれを未承認劣化として扱わない。

**意味しないこと**

- v1凍結の解除は、D1の前提Taskと対象行ごとの追加条件を満たしたR1〜R8に限る。rev.5は一覧外のv1資産へ解除範囲を広げない。
- rev.5の開始承認は`V2-12-06`だけに適用し、後続`V2-12-07`の開始承認ではない。
- D2a一覧外の削除、D3維持対象の削除、凍結識別子の変更を許可するものではない。これらには本ADRのamendmentを要する。

### 改訂履歴

- rev.1（2026-07-20）: 初版。
- rev.5（2026-07-21）: repository ownerの最終決定を反映。未公開期間中に限り、V2-12-05監査PASSとrisk受諾を伴う明示承認でD5条件2の30日経過を免除できる限定例外を追加し、本Taskの免除と`V2-12-06`開始承認を記録した。D8、R1〜R8限定、legacy維持境界、識別子凍結、test gateは不変。
- rev.2（2026-07-20）: 第1回レビューのChanges Requested 5点と補足6点を反映。
  1. D5のdeprecation window条件1の代替判定を`V2-12-07`から`V2-12-05`のAcceptance gateへ移し、`V2-12-06`開始条件の循環を解消。
  2. D3-K1へv1 goldenのimmutable archive＋v2 equivalence test変換方針を追加し、R1追加条件へ組み込み。
  3. D7の削除順序をR6→**R4→R5**へ修正（静的依存実測で`core`のv1 orchestrationが`ai.spi`をimportすることを確認）。順序の例外条件と依存グラフ添付義務を追加。
  4. D2をD2a（writer削除）／D2b（legacy reader維持）／D2c（構造問題）へ分割。Release format 1 verifierを削除対象から外しmigration専用readerへ縮退。
  5. D8としてv1 job／journal／Undo／recovery／cleanup stateのdrain gate（fail closed）を追加。
  6. D2cへ、`DesignArtifactPublisherV2`（v2）が`ReleaseVerifier.deleteTree`（R2）へ依存している実測結果と、その事前抽出義務を追加。
  7. D3-K4を「verb名はversion中立、backendはversion dispatch」へ訂正。
  8. D4へJava API境界の非保証を明記。
  9. D4の凍結対象を「canonical checksum入力に含まれる全文字列」からcanonicalization contractのstable tokenへ限定し、判定不能時はfail closed。
  10. D5へCLI `--v1`の構文（位置非依存global option）を確定。
  11. D9としてmigration toolの品質条件10項目を追加。Consequencesのv3並設方針への言及を撤回。
- rev.4（2026-07-21）: `V2-12-05`のカバレッジ監査結果を反映。
  1. D11を追加し、F1（v2 request authoring欠落）／F2（v2運用機能のcommand未接続）／F3（v2 job・candidate・export lifecycle欠落）を承認ではなくv2実装で解消する方針（監査§7の案A）と、新Task `V2-12-08`〜`V2-12-10`を決定。
  2. D6へF4（`ProviderFailureCode` 9値→`DesignFailureCodeV2` 4値のcollapse）を明示承認として追記し、承認済み劣化が寸法とprovider failure code粒度の2件であることを明記。
  3. D2a R4の追加条件へ`V2-12-08`＋`V2-12-09`完了を、R3の追加条件へ`V2-12-10`完了を追加。削除対象集合R1〜R8は不変。
  4. D10へ、`V2-12-06`のR6判定が参照すべき完全版command inventory（監査§3）への参照を追加。
- rev.3（2026-07-20）: 第2回レビュー反映。承認阻害の中心は設計判断ではなくADR・Task Index間の正本不整合だった。
  1. D0として正本関係（ADRはgovernance境界、Task Indexは実行Scope、矛盾時は厳しい側を適用し停止）を追加。
  2. Task Index §1の無条件v1凍結へADR例外を追加（同期実施）。
  3. Task Index `V2-12-06`をD2a／D2b／K1へ同期。verifierとgolden fixtureを削除対象から外した（同期実施）。
  4. D10としてcommand inventoryを追加。削除しないverb（`asset`／`doctor`／`job`／`recovery`／`version`／`help`）を明示し、R6の対象外とした。
  5. D8のdrain対象へ「完了済みだがretention期間内でUndo可能なplacement」「job history」「cleanup期限前snapshot」「監査保持journal」「未migration Release 1」を追加し、処理方法4種を固定。実測で`PlacementApplicationService`のUndoが`APPLIED` stateを対象とし、`SnapshotCleanupService`が`retentionDays`を持つことを確認した。
  6. D2bへv1 intent／design package readerを明示追加。実測で`format/{DesignArtifacts, DesignArtifactVerifier, DesignVerification}`がrev.2でどの分類にも属していなかったため、D2bへ、`DesignArtifactPublisher`をR2へ分類。削除対象からの除外規則（reader必要型はR4／R7削除前に移設）を追加。
  7. R7を「削除」から「active inventoryからの除外＋immutable legacy contract resourceへの移設」へ変更。
  8. K1のequivalenceをRelease 1→Release 2（完全一致）とv1 intent／design→v2（構造対応、生成結果一致は非保証）へ分割。
  9. D9-8とD2b-4のunknown field規則を「report したうえで拒否」へ統一。
  10. D9-6のchecksum記録を、canonical checksum非定義資産（`terrain-intent`等）向けにbyte digest fallbackを認める表現へ変更。
  11. R5の前提を`V2-6-11`＋`V2-12-03`へ訂正し、同等性検査の実施Taskを`V2-12-05`と明記。
  12. K3の「唯一の未カバー項目」を「2026-07-20監査時点で明示的に確認された項目」へ弱め、`V2-12-05`での新規発見時は停止＋amendとした。
  13. Consequencesへ「v1コードゼロではない」到達構成の定義を追加し、Task Index `V2-12-07`の「v2単独構成」を同期。
