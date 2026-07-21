# 地形・構造物・画像入力ガイド

この文書は「現在の `0.9.0-beta.1` で実際に生成できるもの」と、promptに書けてもまだ生成できないものを区別します。長いpromptを書くだけでは、`TerrainIntent v1` に存在しない表現は実装されません。

## 構造物の定義

構造物は `terrain-intent.json` の `structures` で、種類、個数、配置を希望するzoneを指定します。絶対座標やblock一覧は指定しません。

```json
{
  "zones": [
    {
      "id": "southern-beach",
      "type": "SANDY_BEACH",
      "preferredArea": "SOUTH",
      "areaShare": 0.35
    }
  ],
  "structures": [
    {
      "type": "SMALL_PIER",
      "count": 2,
      "preferredZone": "southern-beach"
    }
  ]
}
```

`preferredZone` は同じIntent内のzone IDである必要があります。generatorはseedからanchorと90度単位のrotationを決め、水、崖、傾斜、範囲、他の構造物との間隔を検査します。希望zoneに置けない場合は安全な別zoneを探索し、それでも置けなければ構造物を省略してwarningを残します。

現在のbuilt-in typeは次の8種です。

| type | 内容 | 主な配置条件 |
|---|---|---|
| `SMALL_PIER` | 小型桟橋 | 陸と水の境界 |
| `SMALL_BRIDGE` | 小型橋 | 両端が乾地で中央が水域 |
| `FISHING_HUT` | 小屋 | 平坦な乾地 |
| `STONE_RUIN` | 石造遺跡 | 平坦な乾地 |
| `PATH` | 砂利道 | 乾地、地形追従 |
| `RETAINING_WALL` | 石積み擁壁 | 乾地、地形追従 |
| `STONE_STEPS` | 石段 | 乾地、地形追従 |
| `FENCE` | 木柵 | 乾地、地形追従 |

独自schematicは `asset validate/import` で安全にcatalogへ保存できます。ただしTerrainIntent v1はcustom asset IDを選べないため、現時点ではbuilt-inの置換や生成への自動統合はされません。

## 対応地形

現在のgeneratorは「各X/Zに地表Yが1つあるheightfield」です。地表素材は `GRASS`、`SAND`、`STONE`、`GRAVEL`、`MUD`、`SNOW`、水系は海、川、湖を扱います。

| 地形 | 対応状況 | 現在の表現 |
|---|---|---|
| 山、谷、崖 | 対応 | `MOUNTAINS`、`VALLEY`、`CLIFFS` とreliefで大域形状を作る |
| 砂浜、岩場の海 | 対応 | `SANDY_BEACH`、`ROCKY_COAST`、海側、深さ、遠浅幅を指定 |
| 平原、湿地、通常の森 | 部分対応 | 草・泥の地表とzone/vegetation maskまで。樹木や草花は配置しない |
| 川、湖、海岸、島、群島 | 対応 | topology、水系個数、sea sideから決定論的に生成 |
| 桜の森 | 未対応 | `FOREST`には近似できるが桜の木、専用palette、biomeは生成しない |
| マングローブ湿地 | 未対応 | `WETLAND`の泥地には近似できるがマングローブ、水没根、biomeは生成しない |
| 滝、段瀑、滝壺 | 未対応 | 川と高低差は作れるが、崖を落ちる立体的な水流は表現しない |
| 洞窟、繁茂した洞窟 | 未対応 | 地下は列規則で埋めるため空洞、鍾乳石、苔、glow berriesは作らない |
| 空島、overhang、natural arch | 未対応 | 1列1地表のheightfieldでは上下に分離した地形を表現できない |

同じ理由で、火山の火口と溶岩、氷河、珊瑚礁、mesaの層状地質、峡谷のoverhang、地下河川、巨大樹、密林、海中洞窟も専用生成としては未対応です。砂丘、fjord、delta、雪山、渓谷、潮だまりは既存のzoneとtopologyで外形を近似できますが、固有の地質・植生・流体挙動までは再現しません。

`src/main/resources/legacy/v1/fixtures/mountain-stream` はv1移行回帰用のpackaged fixtureです。新規生成のexampleではなく、滝、洞窟、樹木、水色、白波、魚、particleなどがv1で変換不能だったことをmigration reportで固定するために維持しています。新規設計は`examples/v2/`を使ってください。

## 画像から生成する

画像はrequestファイルと同じdirectory以下へ置き、安全な相対pathとroleを `request.yml` に記述します。

```yaml
images:
  - file: images/layout.png
    role: TOP_DOWN_SKETCH
  - file: images/height.png
    role: HEIGHT_REFERENCE
  - file: images/zones.png
    role: ZONE_REFERENCE
  - file: images/mood.jpg
    role: MOOD_REFERENCE
```

`TOP_DOWN_SKETCH` はnorth-upで、画像の右が `+X/EAST`、下が `+Z/SOUTH` です。PNG/JPEGだけを使い、最大16枚です。入力はsymlink、magic、容量、寸法、pixel数などを検査し、向きを補正してmetadataなしPNGへ正規化してからProviderへ送ります。

画像から直接heightmapやblockを作るわけではありません。OpenAI/Anthropicが画像とpromptを `TerrainIntent` へ要約し、その後はJava generatorが生成します。そのため画像を使う場合は `design openai` または `design anthropic` を実行します。

```bash
export OPENAI_API_KEY='...'
./gradlew run --args="design openai path/to/request.yml <model-id> build/designs build/jobs"
./gradlew run --args="design-verify build/designs/<request-id>/<design-id>"
```

生成されたDesign Package内の `terrain-intent.json` を確認してから `generate` / `export` へ渡します。画像binary自体はDesign Packageへ保存されません。

## 指定への一致度を上げる手法

現行版で最も安定するのは、文章だけに任せず、次の情報を組み合わせる方法です。

1. `TOP_DOWN_SKETCH` で海陸、山、川、zoneの大域配置を示す。
2. `HEIGHT_REFERENCE` で高低差を示す。
3. `ZONE_REFERENCE` で砂浜、岩場、森などの領域を分ける。
4. promptには方角、面積比、個数、優先順位、禁止事項を数値で書く。
5. Providerが作った `TerrainIntent` を人が検査・修正してimportする。
6. `candidates`を複数生成し、8枚のpreviewで比較する。

最終Intentを人が確定してmanual importする方法が、API応答の揺らぎを除くうえでは最も再現性があります。ただし、現在のSchemaは曲線の制御点、pixel単位heightmap、正確な川筋、構造物座標を持たないため、「画像と同じ輪郭」や「block単位の完全一致」は保証できません。

## 生成幅を増やすv2計画

拡張は可能ですが、1つのenum追加では足りません。正式な依存順と進捗は [roadmap](roadmap.md#terrain-generation-v2)、詳細設計は [Implementation Roadmap v2](design-v2/implementation-roadmap.md) を正本とします。概要は次の順です。

1. v1互換を固定したv2 contract／diagnostic compiler／query adapterを追加する。V2-0 gateは完了済み。
2. raster constraint入力を追加し、正規化済みheight／zone／land-water mapをAIを介さずfieldへcompileする。V2-1 gateは完了済み。
3. beach＋breakwater＋rockycapeを2.5D vertical sliceとしてgenerator／validator／preview／Release 2 offline exportまで完成する。
4. global hydrology、geology／climate／ecology、semantic materialを段階導入する。
5. cave、overhang、arch、sky islandをAABB限定のsparse local volumeとして追加する。
6. effect envelopeとsnapshot-allを備えたRelease 2配置を最後に有効化する。

各Phaseには新しいSchema／generator version、artifact、memory上限、WorldEdit export、snapshot見積、rollback、脅威分析が必要です。現在あるv2コードはcompatibility spine、direct constraint field、4 coastal kind、Hydrology pipeline、geology／climate／water／snow／material／ecology environment pipelineとstrictな`surface-2_5d`／`hydrology-plan`／`environment-fields` capabilityまでです。V2-2〜V2-4統合監査を通過したcoastal 4 kind、offlineのriver／lake／canyon／delta／tidal／fjord、ALPINE／GLACIAL mountain、mangrove、coral、volcanic archipelagoは`SUPPORTED`ですが、CLI／Paper配置を持ちません。waterfall、capeのoverhang／sea cave／full 3D stack、cave-local ecology、entity／実block配置と未完成child kindは`EXPERIMENTAL`です。Track Aの次は`V2-5-01`で、V2-5は未着手、V2-6は前提待ちです。
