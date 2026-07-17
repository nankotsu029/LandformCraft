# 0011: v2の2次元fieldをLFC_GRID_V1 sidecarへ保存する

- Status: Accepted
- Date: 2026-07-14
- Amended: 2026-07-17（V2-4-01 geology semantic code 12〜15）

## Context

Terrain Generation v2ではland-water、height、zone、desired／actual／residual等の2次元fieldを扱う。最大1000×1000の値を`WorldBlueprintV2` JSONへ直接埋めると、JSON解析時のobject／文字列overhead、全域保持、部分読出し、checksum検証が不安定になる。一方、Release format 2はV2-2まで導入しないため、V2-1にはcontainerから独立して検査できるcompact sidecarが必要である。

fieldは数値、寸法、座標、sampling、no-dataの意味を曖昧にせず、whole scanとtile／window scanで同じ結果を返さなければならない。cancelまたは検証失敗した途中fileをcanonical artifactとして公開してはならない。

## Decision

v2の2次元field sidecarとして、非圧縮row-major binary形式`LFC_GRID_V1`を採用する。Java APIは`FieldArtifactDescriptorV2`、`LfcGridWriterV1`、`LfcGridReaderV1`を正本とする。

### Descriptor

artifact descriptorは次を必須とする。

- canonical relative `.lfgrid` path
- field ID
- semantic
- value type
- width／length
- coordinate space
- sampling
- scale／offset（millionths fixed-point）
- no-dataの有無とraw sentinel
- encoding version `LFC_GRID_V1`
- artifact SHA-256
- semantic SHA-256
- source kind／canonical source ID／source checksum／decoder ID・version／transform ID

absolute path、`..`、URL、raw image metadataはdescriptorへ保存しない。constraint source IDは`constraint-source:<slug>`、manual／derivedはそれぞれ`manual-source:<slug>`／`derived-source:<slug>`とする。

V2-1のland-waterとzone categorical fieldはraw IDを意味値の整数単位として扱うため`scaleMillionths=1_000_000`、heightとresidualはpayload自体がmillionthsなので`scaleMillionths=1`とする。`transformId`は変換algorithmのversionであり、個々のrotation／flip／crop parameterではない。manual constraint bundleでは具体的parameterを含むcanonical Requestをindexの`sourceRequestChecksum`でbindingし、descriptor単体から補完推測しない。

V2-3-02のrouting fieldも同じbinary layoutを再利用する。`HYDROLOGY_FLOW_DIRECTION`はU8、scale 1、offset 0、no-data 255、`HYDROLOGY_FLOW_ACCUMULATION`はI32、scale 1、offset 0、no-data 0とし、どちらも`NEAREST`だけを許可する。routing固有のD8 code、downstream reachability、basin coverageはHydrology routing index／readerが検査し、constraint bundleのbinding意味へ混在させない。

V2-4-01のgeology foundationもlayoutを再利用する。province／formation IDはU16、scale 1,000,000、hardness／permeabilityはU16、scale 1,000とし、すべてoffset 0、no-data 65535、`NEAREST`に固定する。ID codeとscalar値の対応はchecksum付き`GeologyPlanV2`を正本とし、4 field間の整合はgeology bounded readerが検査する。semantic lithologyの意味やMinecraft block stateはsidecarへ推測追加しない。

### Bundle index

`fields/index.json`はsidecar値を埋め込まないstrict indexである。source Request／Intent checksum、適用binding、canonical artifact ID、role別に完全一致するfield集合、label辞書、全descriptorに加え、`canonicalChecksum`自身を除いたcanonical JSONのSHA-256を持つ。pending checksum、shared／orphan field、roleとsemantic／value type／sampling／scaleの不一致、source provenance不一致、categorical fieldに辞書外値があるbundleをread-back時に拒否する。

### Binary layout

すべてbig-endianである。payloadはpadding、compression、row strideを持たない。

| Offset | 型 | 内容 |
|---:|---|---|
| 0 | 8 byte | ASCII magic `LFCGRID1` |
| 8 | I32 | format version `1` |
| 12 | I32 | header全長 |
| 16 | I64 | payload byte長 |
| 24 | I32 | width |
| 28 | I32 | length |
| 32 | U8 | value type code |
| 33 | U8 | semantic code |
| 34 | U8 | coordinate-space code |
| 35 | U8 | sampling code |
| 36 | I64 | scaleMillionths |
| 44 | I64 | offsetMillionths |
| 52 | U8 | no-data present `0`／`1` |
| 53 | 3 byte | zero reserved |
| 56 | I32 | no-data raw value。無効時はzero |
| 60 | U8 | source-kind code |
| 61 | 3 byte | zero reserved |
| 64 | 6×U16 | 後続UTF-8文字列のbyte長 |
| 76 | 32 byte | semantic SHA-256 |
| 108 | variable | field ID、source ID、source checksum、decoder ID、decoder version、transform IDのstrict UTF-8 |
| headerLength | variable | row-major payload |

binary codeは次へ固定し、enum ordinalを使用しない。

- value type: `U8=1`、`U16=2`、`I32=3`
- semantic: `LAND_WATER_MASK=1`、`HEIGHT_GUIDE=2`、`ZONE_LABEL_MAP=3`、`DESIRED_LAND_WATER=4`、`ACTUAL_LAND_WATER=5`、`RESIDUAL_LAND_WATER=6`、`DESIRED_HEIGHT=7`、`ACTUAL_HEIGHT=8`、`RESIDUAL_HEIGHT=9`、`HYDROLOGY_FLOW_DIRECTION=10`、`HYDROLOGY_FLOW_ACCUMULATION=11`、`GEOLOGY_PROVINCE_ID=12`、`GEOLOGY_FORMATION_ID=13`、`GEOLOGY_HARDNESS=14`、`GEOLOGY_PERMEABILITY=15`
- coordinate space: `RELEASE_LOCAL_XZ=1`
- sampling: `NEAREST=1`、`BILINEAR_FIXED=2`
- source kind: `MANUAL=1`、`CONSTRAINT_MAP=2`、`DERIVED=3`

header上限は2048 byte、水平寸法上限は各1000である。未知code、future version、非zero reserved byte、不正UTF-8、payload長不一致、trailing／truncated byteを拒否する。

payload encodingは次のとおりである。

- `U8`: 1 byte unsigned
- `U16`: 2 byte unsigned big-endian
- `I32`: 4 byte signed big-endian
- cell順: `z=0`の`x=0..width-1`、次に`z=1`。つまり`index=z*width+x`

version 1のpayloadは単一の非圧縮配列であり、chunk、chunk table、chunk checksum、row paddingを持たない。

### Checksum

artifact checksumはheaderとpayloadを含むfile byte全体のSHA-256であり、自己参照を避けるため外部descriptorだけに置く。

semantic checksumはdomain tag `LFC_GRID_SEMANTIC_V1`、field ID、semantic、value type、寸法、coordinate space、sampling、scale、offset、no-data設定、および全raw値をcanonical I32 big-endian row-major順でhashする。path、source provenance、物理的なtemporary filenameは含めない。readerはartifact checksum、descriptorとheaderの完全一致、semantic checksumをすべて確認してからwindow APIを公開する。

### Samplingとwindow

固定点座標のscaleは1,000,000で、`0`はcell 0のcenter、`1,000,000`はcell 1のcenterを表す。categorical fieldとrouting fieldは`NEAREST`だけを許可する。nearestのhalf cell tieは正方向のcellへ丸める。

`BILINEAR_FIXED`は4 sampleをinteger／fixed-pointだけで補間し、各linear interpolationをnearest millionthへ丸める。負数もzeroから離れる対称なhalf roundingを使う。4 sampleのいずれかがno-dataなら結果もno-dataとし、holeを暗黙補間しない。範囲外座標をclampせず拒否する。

readerは要求windowの`int[]`と1行分のencoded bufferだけを確保する。window寸法と`4*cellCount + encodedRowBytes`をalloc前にbudget検査する。writerは1 encoded rowだけを保持し、strict read-backは固定64 KiB bufferでstream検証する。1000×1000の全値配列を要求しない。

### Publication

writerはcanonical targetと同じdirectoryのtemporary fileへstreamし、各rowでcancelを確認する。payload完了後にsemantic checksumをheaderへ確定し、fileをforceし、temporary fileをstrict read-backする。artifact checksum、semantic checksum、header、payload長が一致し、最後のcancel checkを通った場合だけ`ATOMIC_MOVE`で公開する。atomic moveをcommit pointとし、その後に届いたcancelを理由にcommitted targetを削除しない。atomic move非対応filesystemでは失敗する。move前のcancel／failure時はtemporary fileを削除し、targetを作らない。既存canonical targetを暗黙上書きしない。

manual constraint bundleは個別sidecarの公開先自体をbundle staging内に限定する。全sidecar、sealed index、preview、budget、strict read-backが完了した後、最後のcancel checkとbundle targetへの`ATOMIC_MOVE`の間に追加処理を挟まない。この外側moveがcanonical bundleのcommit pointである。

## Consequences

- 1000×1000のI32 fieldは約4 MiBで、writerは全域配列を持たずに作成できる。
- tile、preview、validatorは同じsidecarをbounded windowで読める。
- endianness、fixed-point、sampling、no-data、checksumがversion 1として固定される。
- V2-3-02は既存code 1〜9とbinary layoutを変えず、追加semantic code 10／11でrouting fieldを同じbounded readerへ載せられる。古いreaderが未知codeを拒否するfail-closed挙動は維持する。
- V2-4-01も既存code 1〜11とbinary layoutを変えず、追加semantic code 12〜15で4 geology fieldを収容する。geology bundleは専用のstrict集合／cross-field検査を持ち、constraint field indexやRelease capabilityの必須集合を変更しない。
- 非圧縮なのでrandom row accessと事前展開上限は単純だが、圧縮形式よりdiskを使う。V2-1の最大4 MiB／I32 fieldでは安全性と単純性を優先する。
- Release format 2へ収容する際もartifact indexとpath policyは別途定義する必要がある。今回のADRはRelease format 1を変更しない。

## Alternatives

### Blueprint JSONへ配列を埋め込む

1000角のparse memory、partial read、checksum、unknown number表現が不利なため採用しない。

### PNGをcanonical fieldとして使い続ける

palette、gamma、sample model、metadata、decoder差をfieldの正本へ持ち込むため採用しない。PNGは入力／previewであり、decode後はLFC_GRID_V1へcanonical化する。

### gzip／chunk圧縮をversion 1へ入れる

random window読出し、展開budget、chunk indexとchecksumを同時に複雑化する。現Phaseの最大payloadに対して必要性が低いため採用しない。必要なら別encoding versionで追加する。

### platform floatでbilinear補間する

rounding、NaN、runtime差をchecksumとconstraint判定へ持ち込むため採用しない。
