# 0010: version分離したTerrain Generation v2 roadmapを採用する

- Status: Accepted
- Date: 2026-07-14

## Context

現行のTerrainIntent v1はtopology、sea side、land ratio、relief、矩形に近いzone希望を中心とし、featureの正確な形、接続、局所scale、hard／soft constraintを十分に表せません。現行generatorも2次元height／water／material gridと汎用zone処理が中心で、fjord、delta、専用coast、volcano、cave、overhang、arch、sky island等を安定して生成する境界を持ちません。

一方、Phase 0〜6で確立したstrict Schema、Provider分離、global coordinate tile、Sponge stream、Release検証、確認付き配置、snapshot／rollback、bounded executorは維持する価値があります。v1を直接拡張すると、generator checksum、Release format 1、既存配置journal、Provider契約の意味を壊します。

## Decision

現行Beta安定化と並行する長期trackとして、[Terrain Generation v2](../design-v2/implementation-roadmap.md) のV2-0〜V2-6をroadmapへ採用します。採用は実装開始または機能提供を意味せず、全Phaseを未開始から追跡します。

- v1 Schema、generator `3.0.0-phase6`、Release format 1、既存配置／Undoを凍結し、v2をversion dispatchで並設する。
- TerrainIntent v2はfeature、geometry、relation、hard／soft constraint、canonical map bindingを表す。
- WorldBlueprint v2はfield ownership、stage graph、named seed、budget、validation targetをfreezeしたcompile済みIRとする。
- 地表の大部分は2.5D fieldを維持し、局所3DはAABB限定のsparse volume overlayにする。
- hydrologyはtile生成前に全域solveし、geology／climate／ecology／materialを意味fieldとして段階導入する。
- 初期moduleはcompile-time built-in catalogに限定し、任意code、script、外部JARを実行しない。
- Release format 2とPaper配置はoffline生成／検証の後段に置き、format 1 verifierを緩めない。
- 各featureはgeneratorだけでなくvalidator、preview、fixture、resource budget testが揃うまでsupportedとしない。

最初の実装はV2-0のさらに小さいcontract／diagnostic compiler changeとし、新地形やRelease 2を同時に導入しません。

## Security and safety consequences

- image constraint、field sidecar、volume、ZIPを信頼しない入力として寸法、byte、entry、path、checksum、decode量を検査する。
- global retained memory、並列task peak、cache、decode、queue、preview、schematic bufferを合算してadmissionを行う。
- fluid、gravity、neighbor updateを含むRelease 2配置はmutation／effect envelopeを事前上限化し、全snapshot後にだけapplyする。上限化できない場合は拒否する。
- v1／v2の曖昧なauto-upgradeやfallbackを禁止し、unknown version／capabilityはstrict rejectする。
- checksumや分岐へ影響する数値kernel、merge順、seed派生をversion化し、whole／tile／thread差をgolden testで検出する。
- dynamic module実行を採用しないため、初期v2で新しい任意code実行面は増やさない。

## Consequences

- `docs/roadmap.md` はBeta hardeningとv2を別trackで追跡する。
- caveや高度な植生は「永続的な非目標」ではなく「現行v1では未対応で、v2 gate通過までunsupported」と整理する。
- Phaseごとに新Schema、ADR、format、docs、examples、脅威分析が必要になる。
- v1とv2の並設期間は型、codec、test、publisher／verifierの保守負担が増える。
- Paperでv2を配置できる時期はV2-6まで遅れるが、offline artifactと安全境界を先に検証できる。

## Implementation note

2026-07-14にV2-0を完了した。v1と分離したTerrainIntent v2／WorldBlueprint v2、strict Schema、exact version dispatch、canonical JSON／checksum、compile-time diagnostic module catalog、named seed、validation model、地形を生成しないdiagnostic compiler、Azure Coast＋10 scenario fixture、pure `TerrainQuery`／`TerrainBlockResolver`、v1 adapter、v1 golden回帰を追加した。

同日にV2-1も完了した。AI referenceとは別契約の3種類のnumeric constraint map、strict PNG U8／U16 decoder、fixed-point sampling、`LFC_GRID_V1` sidecar、bounded reader、manual generation path、固定8 diagnostic previewを追加した。新地形generator、Release 2、schematic、Paper v2配置には着手していない。進捗の正本は [docs/roadmap.md](../roadmap.md) とする。

2026-07-17にV2-4-02を完了した。V2-4-01の`GeologyPlanV2`／4 sidecarをin-place変更せず、source geology checksumへ結合する`LithologyPlanV2`を追加した。catalogはcompile-time built-inの9種semantic lithology、8-bit compact code、hardness、permeability、erosion responseに固定し、province assignmentはformationとscalar descriptorの完全一致を要求する。strict Schema/read-back、canonical checksum、catalog／plan budget、bounded sidecar scanで外部preset、任意class、future version、checksum不一致を拒否する。Minecraft palette、strata、Release capability、v1 contractは追加・変更していない。

同日にV2-4-03／V2-4-04を順に完了した。`StrataPlanV2`はgeology／lithology checksumへ結合したordered profileとsurface-exposed derived scalar、V2-3 hydrology geology-inputの明示version handoffを追加する。`ClimatePlanV2`は32-cell coarse precipitation／runoff priorとfinal temperature／moistureを別phaseへ分離し、V2-3 `HydrologyPlan`／fixed runoff prior／source generatorからtarget generatorへのchecksum付き`EXPLICIT_VERSION_TRANSITION`を別planへfreezeする。いずれもV2-3 artifact、v1、Release capabilityをin-placeで変更せず、environment lifecycleは`EXPERIMENTAL`のままである。

## Alternatives

### v1を破壊的に置換する

既存checksum、Release、Provider、journalの意味を壊すため採用しません。

### zone enumと汎用noiseを追加し続ける

形状、接続、3D、feature別validationを表せないためv1保守以外には採用しません。

### 全域full voxel engineへ一括移行する

1000×1000のmemory、tile、preview、Release、配置を同時に危険にするため採用しません。

### 外部runtime pluginを先に公開する

任意code、supply chain、version、決定性、resource制御が未確立なため採用しません。
