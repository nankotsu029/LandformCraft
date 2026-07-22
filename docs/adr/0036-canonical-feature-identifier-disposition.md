# 0036: canonical Feature identifierの処置と互換移行を固定する

- Status: Accepted
- Date: 2026-07-22
- Revised: 2026-07-22（pre-acceptance review 1）
- Accepted: 2026-07-22
- Decision scope: V2-15-03
- Implementation scope after acceptance: V2-15-04 and the dependent V2-15 leaf Tasks only
- Depends on: V2-15-01, V2-15-02

## Context

2026-07-22の[全地形カタログ監査](../audits/terrain-catalog-full-audit-2026-07-22.md)は、
現行`TerrainIntentV2.FeatureKind`／Schemaの60値のうち14値を、target canonical inventoryでは
独立kindではなくalias、subtype、relation-bound child、またはparent-owned overlayとして扱う案を示した。
この案を採るとtarget public independent inventoryは、現行60値から14値を除き、別Taskで扱う
plan-level volume 3値と新規独立8値を加えた57値となる。

14値は既にstrict Schema、Java enum、example、named seed、semantic checksum、plan hook、support
catalogのいずれかへ現れている。既存tokenを通常のenum renameやparser aliasとして置換すると、同じ
`intentVersion: 2` artifactを別の意味へ暗黙変換し、再serialization、seed、checksum、relation ownershipを
壊し得る。[ADR 0035](0035-v1-retirement-governance.md) D4も、この14 tokenの変更を承認していない。

V2-15-03は処置と移行policyだけを決める。Schema、enum、codec、writer、generator、example、catalog、
capabilityを変更する実装はV2-15-04以降であり、本ADRがAcceptedになっただけでは開始・完了しない。

## Decision

### D0. 正本と承認境界

本ADRは、監査§12 DR-1の**推奨案**を採用する。14値をtarget canonical authoring inventoryから外し、
1 release cycleのdeprecated read／explicit migrate期間を設けた後、新規writerのauthoring対象から除外する。
既存artifactのstrict readerは維持する。

本ADRが`Proposed`の間は次を禁止する。

- 14値のenum／Schema削除、rename、parser alias化、意味変更
- target表現へのread-time自動normalization
- writer、provider prompt、example、catalog、named seed、checksum、capabilityの変更
- V2-15-04またはdependent leafのmigration実装開始

人間が本ADRの全文を明示承認し、Statusと承認記録が`Accepted`へ同期された時点だけで、
V2-15-04の実装を開始できる。Accepted後の処置表、checksum policy、window条件の実質変更は
本ADRのamendmentと再承認を要する。Task ScopeとAcceptanceはTask Indexを正本とし、より厳しい
制約を適用する。

### D1. 14値のcanonical disposition

表中の`target authoring`はcanonical semanticsを固定する。V2-15-04が追加できるserialized field、
field位置、closed tokenはD4.1のallowlistだけであり、表記を理由に別名field、自由文字列、unknown fallbackを
追加してはならない。generator／planへの接続は対応leaf Taskが担当し、V2-15-04はSchema／model／codec／registry／
migration projectionを超えて実装しない。

| Existing Schema value | Disposition | Target authoring and ownership | Required migration behavior |
|---|---|---|---|
| `MEANDERING_RIVER` | `RIVER` subtype | `RIVER`＋`morphology=MEANDERING` | feature ID、geometry、relation、meander parameter、legacy named-seed namespaceを保持する |
| `ALPINE_MOUNTAIN_RANGE` | `MOUNTAIN_RANGE` subtype | `MOUNTAIN_RANGE`＋`profile=ALPINE` | ridge ownershipとmountain parameterを保持し、material／environmentを推測追加しない |
| `GLACIAL_MOUNTAIN_RANGE` | `MOUNTAIN_RANGE` subtype | `MOUNTAIN_RANGE`＋`profile=GLACIAL` | ridge ownershipとmountain parameterを保持し、glacier childを暗黙生成しない |
| `VOLCANIC_ARCHIPELAGO` | `ARCHIPELAGO` subtype | `ARCHIPELAGO`＋`origin=VOLCANIC`。caldera／lavaは下記typed child | island point、volcanic parameter、明示relationを保持し、cone／childを推測追加しない |
| `MANGROVE_WETLAND` | `MARSH` subtype | `MARSH`＋`wetlandType=MANGROVE`＋明示environment profile | tidal relation、hydrology、environment parameterを保持する |
| `BACKSHORE_PLAINS` | `PLAIN` profile alias | `PLAIN`＋`context=BACKSHORE` | polygonと明示coast relationを保持する。coastが欠ける場合は補完せず拒否する |
| `BEDROCK_RIVER` | `RIVER` subtype | `RIVER`＋`channelSubtype=BEDROCK` | river graph／geometryと明示bedrock semanticsを保持する。通常riverへ弱めない |
| `OXBOW_LAKE` | `LAKE` subtype／relation | `LAKE`＋`origin=RIVER_CUTOFF`＋parent river cutoff relation | cutoff reach ownershipを保持する。parent／reachが一意でなければ拒否する |
| `LAGOON` | parent-owned child plan | V2-15では`CORAL_REEF`のtyped lagoon child。`ENCLOSED_BY` relationが正本。V2-16 composition ownerは別契約 | standalone kindを作らない。ownerが一意でなければ拒否する |
| `REEF_PASS` | parent-owned child plan | `CORAL_REEF`のtyped pass child。`CARVES_THROUGH`＋`CONNECTS_TO` relationが正本 | centerlineと両relationを保持し、単独passへ昇格しない |
| `VOLCANIC_CALDERA` | parent-owned child plan | relationで指定された`VOLCANIC_CONE`またはvolcanic `ARCHIPELAGO`のtyped caldera child | rim／floor geometryとowner bindingを保持する。nearest volcanoを選ばない |
| `LAVA_FLOW_FIELD` | parent-owned child plan | relationで指定された`VOLCANIC_CONE`またはvolcanic `ARCHIPELAGO`のtyped lava-flow child | flow path、ordinal、owner bindingを保持する。単独generatorを作らない |
| `GLACIAL_CIRQUE_FIELD` | parent-owned child plan | `profile=GLACIAL`の`MOUNTAIN_RANGE`が所有するtyped cirque collection | point順序ではなくstable child IDで正規化し、hostが無ければ拒否する |
| `FLOODED_CAVE` | parent-owned fluid overlay | relationで指定された`CAVE_NETWORK`／`UNDERGROUND_RIVER` planのtyped fluid-region hook | host AABB、fluid body、water level、CSG順を保持し、cave kindやfluidを推測しない |

`LAGOON`のV2-15 migration ownerは入力relationで一意に決まる`CORAL_REEF`だけとする。barrier-island等の
composition ownerはV2-16のcomposition contractまで移行せず、V2-15-04では`unsupported owner`として拒否する。
全14値について複数owner、欠落relation、unsupported owner、parameter lossがあればdocument全体をfail closedで
拒否し、部分migrationやnearest／priority fallbackを行わない。

### D2. 互換lifecycle state machine（単調・rollback不可）

14値の**公開lifecycle state**は次の順序だけを一方向に遷移する。

```text
CURRENT_PUBLIC
  -- R_mでtarget writer・strict migrator・docsを同時公開 -->
DEPRECATED_AUTHORING
  -- deprecation window完了＋別の人間close承認 -->
LEGACY_READABLE_ONLY
```

逆向きedgeは存在しない。implementation rollback、release rollback、feature flag、writer停止を理由に
`LEGACY_READABLE_ONLY`→`DEPRECATED_AUTHORING`または`DEPRECATED_AUTHORING`→`CURRENT_PUBLIC`へ戻してはならない。
本stateを戻す必要がある場合は、本ADR amendmentと人間承認を得て新しいforward stateを定義する。

- `CURRENT_PUBLIC`: 現行状態。本ADRのAcceptedだけではこのstateを変えない。
- `DEPRECATED_AUTHORING`: canonical writerはtarget表現を生成する。compatibility authoring APIは14 tokenを
  一時受理してstable deprecation diagnosticとmigration案内を返せる。legacy documentのno-op read→writeは
  byte／canonical checksumを変えず旧tokenを保持するが、target documentから旧tokenを新規合成しない。
- `LEGACY_READABLE_ONLY`: default writer、provider prompt、manual authoring、new examplesは14 tokenを生成・受理
  しない。legacy readerとexplicit migratorだけが受理する。unknown tokenとして扱わず、legacyであることを
  stable diagnosticで識別する。

`LEGACY_READABLE_ONLY`になっても、現行14値を含むstrict compatibility Schema／enum decode tableを削除しない。
active authoring projectionとlegacy read projectionを分離し、同じtokenをcontextなしに別semanticへdispatchしない。

公開lifecycle stateとは別に、runtimeはcanonical writer／migratorを**有効／停止**できる。これは可逆な運用modeであり、
lifecycle stateを変更しない。停止中に許可される操作は現在のlifecycle state以下に限定する。

- `CURRENT_PUBLIC`: 現行writer／readerだけを継続できる。
- `DEPRECATED_AUTHORING`: last-known-good compatibility writer／legacy readerへ切替できる。
- `LEGACY_READABLE_ONLY`: legacy read／verify／explicit migrateだけを継続し、旧token authoringを再有効化しない。
  canonical writerが停止した場合、新規authoringを一時停止して修正版を待つ。

### D3. deprecation windowの定義

windowは、target writer、strict migrator、compatibility reader、migration docsをすべて含む最初のtagged release
`R_m`が公開された時に開始する。`R_m`より前の`Unreleased`期間、Task完了日、ADR承認日だけでは開始しない。

14 tokenのauthoring除外は、deprecated authoring経路を含む`R_m`が少なくとも1回公開され、その次のrelease
cycleまで移行機会が存在し、次をすべて満たした場合だけ行える。

1. committed fixture corpusの全legacy documentをstrict readできる。
2. migratorのpositive／negative／corruption／idempotence testが成功する。
3. source checksum→target checksumのmigration reportを機械検証できる。
4. legacy-only operational usageが残る場合、そのriskと代替経路が文書化されている。
5. repository ownerが`LEGACY_READABLE_ONLY`への遷移を別途明示承認する。

tagged releaseが存在しない間はwindowを満了扱いにしない。期間短縮や条件免除には本ADR amendmentと人間承認を
要する。

### D4. reader、writer、Schema projection

- **Reader:** legacy artifactを現行契約のままstrict validateし、14 tokenを保持したcompatibility modelとして読む。
  読取成功をmigration成功とみなさず、read-timeにtarget kindへ置換しない。
- **Canonical writer:** V2-15-04以降の新規documentはtarget authoring表現だけを出力する。配列順、module順、locale、
  timezoneに依存しない。
- **Compatibility writer:** `DEPRECATED_AUTHORING`中だけ、既存legacy documentのlossless round-tripと明示legacy
  authoringを提供できる。出力にはdeprecation diagnosticを付けるがcanonical documentへ未定義fieldを混入しない。
- **Schema projection:** read vocabulary、authoring vocabulary、migration targetを機械的に区別する。既存
  `terrain-intent-v2` documentを同じ`intentVersion`のまま別semanticとして再解釈しない。projection選択は必ず
  D4.1の明示discriminatorまたは明示reader引数で行い、field欠落、Schema検証失敗、token集合からprojectionを
  推測しない。
- **Examples:** existing legacy examplesは互換fixtureとして凍結し、上書き再生成しない。target exampleは別fileへ
  追加し、source↔target migration pairとする。

#### D4.1. V2-15-04へ許可するtyped contract追加

既存`schemas/terrain-intent-v2.schema.json`とその`$id`は`LEGACY_V2` projectionのimmutable compatibility
contractとして維持する。V2-15-04は、次の**1 Schemaだけ**をactive canonical authoring projectionとして追加できる。

| Item | Approved stable identifier |
|---|---|
| Schema file | `schemas/terrain-intent-v2-canonical.schema.json` |
| Schema `$id` | `https://github.com/nankotsu029/LandformCraft/schemas/v2/terrain-intent-v2-canonical.schema.json` |
| Existing version field | `intentVersion: 2`（意味を変更しない） |
| Required projection field | top-level `featureProjection`、const `CANONICAL_V2` |
| Closed projection enum used by reader／registry／migrator | `LEGACY_V2`, `CANONICAL_V2` |

V2-15-04後に書くcanonical documentは`featureProjection: CANONICAL_V2`を必須とする。既存legacy documentは
byte／checksum維持のためfieldを追加せず、`readLegacy(..., LEGACY_V2)`または同等の明示projection引数を持つ
専用readerからだけ読む。generic dispatcherは`featureProjection`欠落を`LEGACY_V2`と推測せず拒否する。
migratorもsource projectionを明示引数として要求し、canonical documentのfield値との不一致を拒否する。
Schemaを順に試すfallback、14 tokenの有無による判定、`intentVersion: 2`だけによるprojection dispatchは禁止する。

canonical parentの`parameters`へ追加できるfieldは次だけである。discriminatorとpayloadは組で検査し、
discriminatorが`DEFAULT`ならcompatibility payloadを禁止する。payloadは表の既存legacy `$defs`をそのままstrict
reuseし、fieldのcopy、rename、自由形式mapへ変換しない。

| Parent kind | Approved discriminator field／closed values | Approved typed payload field |
|---|---|---|
| `RIVER` | `morphology: DEFAULT|MEANDERING` | `meanderingParameters` → existing `meanderingRiverParameters` |
| `RIVER` | `channelSubtype: DEFAULT|BEDROCK` | none（legacy `BEDROCK_RIVER`は`NoParameters`） |
| `MOUNTAIN_RANGE` | `profile: DEFAULT|ALPINE|GLACIAL` | `profileParameters` → existing `mountainParameters`（non-default時必須） |
| `ARCHIPELAGO` | `origin: DEFAULT|VOLCANIC` | `volcanicParameters` → existing `volcanicArchipelagoParameters` |
| `MARSH` | `wetlandType: DEFAULT|MANGROVE` | `mangroveParameters` → existing `mangroveWetlandParameters` |
| `PLAIN` | `context: DEFAULT|BACKSHORE` | `backshoreParameters` → existing `backshoreParameters` |
| `LAKE` | `origin: DEFAULT|RIVER_CUTOFF` | `riverCutoffParameters` → existing `oxbowLakeParameters` |

parent-owned child／overlayのlossless carrierとして、canonical `Feature`へ`children`を追加できる。
`children`はchild `id`のUTF-8 byte順でcanonicalizeするbounded arrayで、各elementが持てるfieldは
`id`、`childKind`、`geometry`、`parameters`、`priority`、`provenance`、`legacySeedBinding`だけである。
`geometry`／`priority`／`provenance`は既存Feature定義を、`parameters`は`childKind`に対応する既存legacy
parameter `$defs`をstrict reuseする。`childKind`は次の6 tokenだけを受理する。

```text
LAGOON
REEF_PASS
VOLCANIC_CALDERA
LAVA_FLOW_FIELD
GLACIAL_CIRQUE_FIELD
FLOODED_CAVE
```

owner matrixはD1の処置をstrictにする。V2-15-04で許可するのは、`LAGOON`／`REEF_PASS`→`CORAL_REEF`、
`VOLCANIC_CALDERA`／`LAVA_FLOW_FIELD`→`VOLCANIC_CONE`または`origin=VOLCANIC`の`ARCHIPELAGO`、
`GLACIAL_CIRQUE_FIELD`→`profile=GLACIAL`の`MOUNTAIN_RANGE`、`FLOODED_CAVE`→`CAVE_NETWORK`または
`UNDERGROUND_RIVER`だけである。barrier-island等のV2-16 composition ownerは本Taskで先取りせず、該当する
legacy inputはowner contract未実装としてfail closedにする。global `relations`はnested child IDをendpointとして
参照できるが、owner nestingの代替にせずD1の必須relationを併せて検証する。

V2-15-04はcanonical `Feature`のoptional propertyとcanonical child elementのpropertyとして
`legacySeedBinding`を追加できる。migrated canonical parent／childでは必須、migration由来でないcanonical elementでは
禁止する。このfieldはprovenanceやmigration reportではなく、canonical JSON、semantic checksum、Blueprint compileへ
参加する**生成semantic**である。持てるfieldは次だけである。

| Field | Contract |
|---|---|
| `sourceKind` | D1のexisting 14 tokenのいずれか。target parent／childとの対応一致を必須とする |
| `derivationVersion` | sourceで使用したnamed-seed derivation token。現行値は`sha256-tagged-v1` |
| `seedNamespace` | source FeaturePlanのlegacy namespace。qualified ID、空文字不可 |
| `moduleId` | source seed derivationへ入力したlegacy module ID |
| `moduleVersion` | source seed derivationへ入力したlegacy module version |
| `generatorVersion` | source seed derivationへ入力したlegacy generator version |

global seedとfeature／child `id`は既存document／requestの値を使う。compilerはmigrated elementについて
`legacySeedBinding`のtupleからnamed seedを導出し、canonical kindのdefault module tupleで置換しない。
bindingはmigration reportだけへ置かずtarget documentに残し、後続read→write、再compile、Release再生成でも
同じcanonical semanticsとして維持する。new canonical authoringがmigration sourceなしに`legacySeedBinding`を
指定することは拒否する。

上記以外のTerrainIntent field、token、Schema `$id`、generic extension map、nullable fallback、child kind、
seed policyはV2-15-04に許可しない。必要なら実装を停止し、本ADR amendmentと人間承認を得る。V2-15-04は
これらのtyped contractをround-trip／migrateできるが、domain generator、plan ownership実行、Release capability、
Paper routingを接続しない。

### D5. explicit migrationとchecksum／seed

Migrationは明示operationであり、入力を上書きしない。stagingへtarget documentとmigration reportを書き、両方を
strict read-backした後にatomic publishする。reportは少なくともmigration contract version、source artifact identity、
source／target projection、source canonical checksum、target canonical checksum、各featureの旧ID／旧token／target
owner／target disposition、D4.1のlegacy seed binding、diagnosticをstable順序で記録する。document discriminator、
explicit reader／migrator projection引数、report projectionが一致しなければ拒否する。missing、extra、duplicate、
unknown version、ambiguous owner、lossy mappingを拒否する。

- 未移行legacy artifactのJSON、semantic checksum、artifact checksum、named seed、生成済みblock checksumは不変とする。
- 移行後documentのcanonical checksumは表現変更により変わってよい。本ADRはその変更を、reportに旧新checksumを
  1対1で記録し、strict verifierが対応を検証する場合に限り承認する。
- alias／subtype移行は既存feature ID、geometry、relation、明示parameterを保持し、source seed derivation tupleを
  targetの`legacySeedBinding`へcanonical semanticsとして埋め込む。`FeatureKind.name()`、canonical module default、
  migration reportだけのout-of-band metadataからseedを再導出してterrainを変えない。
- 既存generator／planがあるmappingは、migration前後のcanonical block-state streamを同一にする。意味的に同じ
  mappingでblock checksumが変わる場合はV2-15-04／leafを停止し、本ADR amendmentを得る。
- child／overlay internalizationでplan artifactのsemantic checksum contract自体をversion化する必要がある場合、
  source planを引き続きverifyできるlegacy verifierを残し、新旧checksum対応と最終block checksum一致をfixtureで示す。
- target documentを再度migrateするとbyte-identical targetと`ALREADY_CANONICAL`結果を返し、二重child化しない。

### D6. capability、support、v1／legacy境界

本Decisionはfeatureの実装完了や昇格ではない。migration後も各feature／parentの現行実装stateを超えて
`SUPPORTED`へせず、Release capability名、artifact type/version、generator version、Paper placement eligibilityを
変更しない。V2-17の実機証拠なしにPaper capabilityを昇格しない。

本DecisionはADR 0035をamendせず、v1 Schema、legacy v1 read／verify／migrate、immutable golden、Release format 1、
v1由来checksumを変更しない。14値はTerrainIntent v2の処置であり、「v1退役」を理由にcompatibility readerを
削除しない。

### D7. rollbackと停止

target writer／migratorの不具合時に行うrollbackは、D2の公開lifecycle stateを戻す操作ではなく、現在stateで許可された
last-known-good implementationへruntime／release routingを切り替える**運用rollback**である。

- `CURRENT_PUBLIC`では現行legacy implementationを継続する。
- `DEPRECATED_AUTHORING`ではcanonical writerを停止し、同stateで許可済みのcompatibility writer／legacy readerへ
  切り替えられる。stateは`DEPRECATED_AUTHORING`のままである。
- `LEGACY_READABLE_ONLY`ではcanonical writerを停止できるが、legacy authoringを再有効化しない。legacy
  read／verify／explicit migrateだけを維持し、新規authoringは修正版公開までfail closedで停止する。

legacy reader、legacy Schema fixture、source artifactを削除しないため、未移行artifactは現在stateで許可された範囲で
利用できる。公開済みtarget artifactをlate rollbackで削除・自動reverse migrateしない。reverse mappingがlosslessで
あることをversion付きcontractとfixtureで証明できる場合だけ、別の明示operationとして扱い、実行してもD2 lifecycle
stateは変えない。

次のいずれかが必要なら該当Taskを停止し、Task Index追加または本ADR amendmentを行う。

- 新artifact formatまたはRelease capability
- 既存v1／legacy境界の変更
- 旧新block checksum不一致の受諾
- 表にないtokenの削除／rename／意味変更
- relationやownerを推測するmigration
- 1 Taskで複数の独立generator実装

## Consequences

- target canonical inventoryは重複する14の独立authoring kindを持たず、parent ownershipとrelationを正本にできる。
- 既存artifactは暗黙変換されず、readerとchecksumを維持したままexplicit migrationを選べる。
- Java enumまたはcompatibility decode tableにはlegacy tokenが残り得るため、enum件数とcanonical authoring kind件数は
  同義ではなくなる。registry／Schema projection／docsは両方を明示する必要がある。
- canonical documentは`featureProjection: CANONICAL_V2`で自己識別し、fieldを持たないhistoric legacy documentは
  明示`LEGACY_V2` readerだけから扱う。generic dispatcherのguess／schema fallbackは利用できない。
- V2-15-04のTerrainIntent contract追加はD4.1のdiscriminator、7 parent specialization、6 child carrier、
  `legacySeedBinding`に限定され、追加fieldが必要なら再承認を要する。
- Migration後documentのcanonical checksumは変わるが、旧新対応はversion付きreportで監査できる。生成意味が同じ
  経路のfinal block checksumは変えない。legacy derivation tupleはtarget documentの生成semanticとして残る。
- deprecation windowはrelease実績と別のhuman close approvalを要するため、未公開状態だけでlegacy authoringを
  早期削除できない。
- lifecycle stateは一方向で、rollbackは現在stateの許可範囲内でimplementationを停止／切替するだけである。

## Alternatives

### Alternative A: 現行60値を永久にcanonical public spellingとして保持する

不採用。read compatibilityは最大だが、`RIVER`／`MOUNTAIN_RANGE`／`ARCHIPELAGO`／`MARSH`等とownershipが重複し、
child planをstandalone generatorへ誤昇格させる余地が残る。本Decisionはspellingのlegacy readを維持しつつ、
new authoringだけをcanonical parentへ収束させる。

### Alternative B: 新Schema majorで14値を即時削除する

不採用。既存TerrainIntent、example、provider output、seed namespace、semantic checksum、plan hookを一度に破壊し、
migration opportunityを与えない。versionを増やすだけではlossless migrationやreader維持の代替にならない。

### Alternative C: parser aliasでread-timeにtarget kindへ自動置換する

不採用。同じsource bytesがreader versionによって別semantic／checksumになり、owner relationの欠落を推測で補うことに
なる。readとmigrateを分離し、変換は明示operationだけに限定する。

## Human approval record

- Decision: **APPROVED**
- Required approver: repository owner or explicitly delegated human reviewer
- Approval date: 2026-07-22
- Approval reference: user message `adr0036を承認します。`
- Approval applies to: this complete text including pre-acceptance review 1; any material post-approval change requires an amendment and renewed approval
