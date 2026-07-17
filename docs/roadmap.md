# 開発ロードマップ

このファイルを進捗の正本とします。過去のPhase 0〜5監査は [phase-0-5-audit.md](audits/phase-0-5-audit.md)、現在の実装監査は [phase-6-beta-audit.md](audits/phase-6-beta-audit.md) です。v2の詳細設計とPhase別gateは [Implementation Roadmap v2](design-v2/implementation-roadmap.md)、採用判断は [ADR 0010](adr/0010-adopt-versioned-terrain-v2-roadmap.md) を参照してください。

## 現在地

**現在: 0.9.0-beta.1 release candidate / Beta stabilization。V2-0〜V2-3のPhase gateと`V2-4-01`〜`V2-4-05`完了、V2-4進行中、次は`V2-4-06`**

独自Web UIは製品範囲から削除しました。CLIとPaperを利用者向けUIとし、ブラウザ版ChatGPT／Claudeによる手動JSON作成は外部AI利用方法として維持します。

現行v1のBeta安定化とv2開発は別trackです。v2の設計文書やroadmap登録は実装完了を意味せず、現行Schema、generator `3.0.0-phase6`、Release format 1、CLI／Paperの既定挙動を変更しません。

## 完了済みマイルストーン

| マイルストーン | 状態 | 完了内容 |
|---|---|---|
| M0 Contract | 完了 | record、Schema、strict JSON／YAML、package境界 |
| M1 Terrain Preview | 完了 | seed再現地形、tile margin、8 PNG、500／1000 performance test |
| M2 Portable Release | 完了 | Sponge v3 tile、manifest、checksum、ZIP、strict verify |
| M3 Safe Placement | 完了 | snapshot、apply、verify、rollback、Undo、journal |
| M4 Text to Intent | 完了 | manual JSON、OpenAI、Anthropic、Design Package |
| M5 Image Intent | 完了 | 6 role、画像security、座標正規化、evidence |
| M6 Structures | 完了 | 8 built-in asset、決定論的配置、Release統合 |

## Beta hardening

- [x] Paper request／design／generate／candidate／export／job／version／doctor command
- [x] command単位permissionと権限対応Tab root
- [x] placement領域予約と同時overlap拒否
- [x] actor-bound、一回用、期限付きconfirmation
- [x] snapshot／temporary／rollback／marginのdisk見積もりと永続予約
- [x] snapshot retention dry-run／確認付きcleanup／audit
- [x] recovery status／diagnose／rollback／acceptと保守的分類
- [x] Paper Provider config wiring、環境変数key、model明示、hard range
- [x] CLI common option、stable error code、correlation ID、job操作
- [x] 制限付きcustom asset validate／import／list／info／remove
- [x] User／Admin／仕組み／障害対応／制限／release checklist文書
- [x] WorldEdit 7.3.19でrequest→fixture design→generate→export→plan→apply→verify→Undo smoke
- [ ] FAWE単独環境で今回buildの完全Paper smoke
- [ ] 500×500実world apply／Undo計測
- [ ] release checklistの全必須項目を実機で閉じる

未チェック項目を成功済みとは扱いません。公開可否は監査のBeta decisionとrelease checklistで決定します。

## 継続する製品境界

- 独自Web UI／browser UI／frontend／HTTP server
- 巨大都市の完全自動生成
- AIによる全block座標列挙、AI生成Javaコード実行
- entity、高度なblock entity、biome書換え
- 無制限サイズ、任意codeを実行する外部地形plugin、全域dense voxel world

これらはv2でも自動的には範囲へ入りません。変更する場合は新しいADRと脅威分析を必要とします。

## 現行0.9／v1で未対応

- direct constraint map、正確なcurve／polygon／feature relation
- feature別coast、fjord、delta、volcano、canyon、waterfall generator
- geology、climate、ecology fieldと高度な植生
- cave、overhang、arch、sky island等の局所3D地形
- Release format 2とv2地形のPaper配置

これらは下記v2 Phaseの対象ですが、該当Phaseのgateを満たすまでは利用可能、Beta対応、supportedと表現しません。

## Terrain Generation v2

依存順は `V2-0 → V2-1 → V2-2 → V2-3 → V2-4 → V2-5 → V2-6` です。先行Phaseのgateを満たさずに後続機能をsupportedへしません。Beta hardeningの未完項目もv2着手を理由に閉じません。

| Phase | 状態 | 目的 | 主な完了判定 |
|---|---|---|---|
| V2-0 Compatibility spine | 完了 | v1を変えずv2 contract、diagnostic compiler、共通queryを通す | v1 checksum／全block stream不変、11 scenario、query whole／tile決定性 |
| V2-1 Constraint maps | 完了 | mask／height guide／zone labelをAIを介さずfieldへcompileする | strict decoder、fixed-point、sidecar、8 preview、1000角memory、whole／tile一致 |
| V2-2 Coastal 2.5D | 完了 | beach＋breakwater＋rockycapeを縦に完成する | Azure Coast統合監査、Release 2 capability strict verify、WorldEdit read-back |
| V2-3 Hydrology | 完了 | global river／lake／delta／tidal／fjord／waterfall graphを作る | 接続・勾配・流量・分流・海接続、順序／thread決定性 |
| V2-4 Environment | 進行中（5/15） | geology／climate／ecology／semantic materialを統合する | snow、mangrove、coral、volcanic／strataの環境条件とbudget |
| V2-5 Sparse volume | 未開始 | cave／lush cave／overhang／arch／sky islandを局所3D化する | connectivity、roof／support、3D seam、dense volume非使用、offline read-back |
| V2-6 Placement | 未開始 | Release 2を安全に配置・復旧可能にする | effect envelope、snapshot-all、settle/full verify、実world計測、v1回帰 |

### Task tracking

V2-0とV2-1のAcceptance gateは完了済みで、完了状態を戻さない。V2-2の証拠は [V2-2 Phase gate audit](design-v2/audits/v2-2-phase-gate.md) を正本とする。`V2-3-01`〜`V2-3-14`ではHydrology IR／routing、river／lake／canyon／waterfall／delta／tidal／fjord／mountain／volcanic skeleton、固定3 pass reconciliation、独立validator／12 preview、Release 2 `hydrology-plan` strict capabilityを順に実装した。`V2-3-15`では9 scenarioのcanonical compileを順序／1・4 thread／locale／timezoneで統合し、positive／corruption／tampering、1000角budget、cancel／non-convergence、v1／Release 1／V2-2回帰を再検証した。[V2-3 Phase gate audit](design-v2/audits/v2-3-phase-gate.md) を根拠に、offlineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDと`hydrology-plan`を`SUPPORTED`とした。WATERFALL、ALPINE／GLACIAL mountain、VOLCANIC_ARCHIPELAGOは後続のvolume／environment／material gateまで`EXPERIMENTAL`のままである。

`V2-4-01`ではchecksum付き`GeologyPlanV2`、V2-3 fixed priorの明示replacement、named seed、Stage 3 built-in module、province／opaque formation／hardness／permeabilityの4 field、strict `LFC_GRID_V1` atomic bundle、bounded readerとCPU／retained／working／artifact budgetを実装した。empty/minimal round-trip、whole／tile順／thread／locale／timezone、1000角admission、unknown ID／checksum／future version／cancelを検証した。Hydrology planとv1の意味は変更せず、moduleは`EXPERIMENTAL`、V2-4親Phase gateは未完了である。次は`V2-4-02`である。

`V2-4-02`では`LithologyPlanV2`を追加し、V2-4-01の`GeologyPlanV2` checksumへ厳密に結合したcompile-time built-inの9種 lithology catalog、固定hardness／permeability／erosion response、provinceごとの8-bit compact code assignmentをfreezeした。catalog／plan checksum、version、budget、assignmentのprovince／formation／scalar一致をstrictに検証し、既存4 fieldをbounded windowで読んでassignmentの適用可能性を検査する。新しいsidecar field、Minecraft block state、strata、climate、Release capabilityは導入していない。whole／tile順／thread／locale／timezone、future version／unknown ID／任意class／checksum corruptionを検証し、v1とHydrology planは不変である。environmentは引き続き`EXPERIMENTAL`、親Phase gateは未完了である。次は`V2-4-03`である。

`V2-4-03`では`StrataPlanV2`を追加し、lithology／geology checksumへ結合したprovince strata profile、`BOTTOM_TO_TOP` layer order、bounded cardinal dip／fold、surface-exposed derived hardness／permeability、およびV2-3 `UNIFORM_GEOLOGY_PRIOR`／`hydrology-reconcile-fixed-v1`からの明示 hydrology geology-input handoffをfreezeした。dense 3D strata配列は作らず、`StrataExposureResolverV2`がdescriptorからinteger-onlyで露出層を解決する。zero/thin/inverted strata、unknown lithology、非cardinal azimuth、layer×tile budget、future version／checksum corruptionを拒否し、whole／tile／seam／thread／locale／timezoneとv1／Hydrology plan不変を確認した。climate、material、Release capabilityは導入していない。environmentは引き続き`EXPERIMENTAL`、親Phase gateは未完了である。次は`V2-4-04`である。

`V2-4-04`では`ClimatePlanV2`を追加し、32-cell coarse precipitation／runoff priorと、標高減率・緯度相当・exposure・Hydrology flow accumulationを読むfinal temperature／moisture fieldを別phase／別ownerとしてfreezeした。V2-3 `HydrologyPlan` canonical checksum、固定`CONSTANT_RUNOFF_PRIOR` checksum、source generator versionを保持し、`hydrology-priority-flood-climate-prior-v1`への置換を`EXPLICIT_VERSION_TRANSITION`として宣言する。既存V2-3 artifactを再解釈せず、integer-only global X/Z sampling、whole／tile正逆／1・4 thread／locale／timezone、same prior／same graph、1000角coarse／window budget、implicit／unknown climate、future／mismatch／checksum corruptionを検証した。wetness／salinity／hydroperiod、snow、ecology、material、sidecar／Release capabilityは導入していない。environmentは引き続き`EXPERIMENTAL`、親Phase gateは未完了である。次は`V2-4-05`である。

`V2-4-05`では`WaterConditionPlanV2`を追加し、最終地形／水系接続とclimate moistureからdrainage/water distance、groundwater proxy、tidal influence、salinity、hydroperiod、wetness、wetness residualの7 fieldをinteger-onlyで導出する。距離は最大64 blockにboundし、marine connectivityが無い場合はsalinity／tidal influenceを0にする。implicit ocean fallback、unbounded diffusion、no-data、hard range超過、future／checksum corruptionを拒否し、whole／tile正逆／1・4 thread／locale／timezoneと1000角window budgetを検証した。mangrove／coral／ecology／snow、sidecar／Release capabilityは導入していない。environmentは引き続き`EXPERIMENTAL`、親Phase gateは未完了である。次は`V2-4-06`である。

V2-2〜V2-6の詳細なTask Scope、非Scope、test、決定性／memory／security条件、Acceptance、停止条件は [Task Index](design-v2/task-index.md) を正本とし、共通実行規則は [Task Execution Guide](design-v2/task-execution-guide.md) を参照する。

| Phase | Task | 状態 | 最初／次 | 親Phase gate |
|---|---:|---|---|---|
| V2-2 | 12 | 完了（12/12） | 完了 | `V2-2-12` |
| V2-3 | 15 | 完了（15/15） | 完了 | `V2-3-15` |
| V2-4 | 15 | 進行中（5/15） | `V2-4-06` | `V2-4-15` |
| V2-5 | 18 | 前提待ち | `V2-5-01` | `V2-5-18` |
| V2-6 | 19 | 前提待ち | `V2-6-01` | `V2-6-19` |

個別Taskが完了しても親Phaseを完了にしない。各Phase末尾の統合監査Taskが、全前提、Phase fixture、capability strict verify、determinism／memory／security portfolio、v1回帰、clean buildを確認した場合だけPhase gateを閉じる。WorldEdit／FAWE smokeと500／1000実world計測は、必要環境がなければ`BLOCKED_EXTERNAL`のまま残し、mockやoffline testで完了扱いにしない。

## v2共通gate

- `SUPPORTED` featureにはIntent contract、generator、validator、preview、positive／corruption fixture、resource budget testを揃える。
- v1→v2は新artifactを作る明示操作とし、欠落した形状や接続を推測してhard constraintへしない。
- whole／tile、tile順序、thread数、module登録順、locale、timezone、対応runtimeでfield／metric／final block checksumを一致させる。
- Release format 1 reader／verifier／placement／Undoを維持し、format 2はversion別のstrict publisher／verifierへ隔離する。
- v2 Paper applyはoffline generation、preview、Release 2 self-verify、corruption validationが完了するまで有効化しない。
- Schema、ADR、production docs、examplesは各Phaseの実装と同時に更新し、設計例だけを実装済みと表現しない。
