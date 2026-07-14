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

`examples/mountain-stream` は山、谷、川、湖、岩礁海岸、小規模構造物を組み合わせる入力例です。滝、洞窟、樹木、水色、白波、魚、particleなど、現在のIntent/生成器にない要求は実現されないため、完成品質のfixtureではなく拡張要件の例として扱ってください。

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

## 生成幅を増やすには

拡張は可能ですが、1つのenum追加では足りません。安全性と再現性を維持するなら、次の順が現実的です。

1. palette/vegetation descriptorを追加し、桜、針葉樹、マングローブ、砂漠、雪原を決定論的に配置する。
2. raster constraint入力を追加し、正規化済みheight/zone/water mapをAIを介さずgeneratorへ渡す。
3. waterfall/river graphを追加し、水源から河口まで標高が単調に下がる制約を検証する。
4. cave maskと3D density fieldをtile/margin方式で追加し、洞窟とoverhangを扱う。
5. biome書換え、地下・海中palette、custom asset ID参照をversion付きSchemaで追加する。

2は指定への一致度を最も直接改善します。3〜5はartifact、memory上限、WorldEdit export、snapshot見積、rollback、脅威分析へ影響するため、新しいSchema/generator versionとADRが必要です。これらは現在の実装済み機能や確定マイルストーンではありません。
