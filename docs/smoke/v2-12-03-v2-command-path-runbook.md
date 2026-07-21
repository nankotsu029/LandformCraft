# V2-12-03 v2 command path smoke runbook

`/lfc v2 <verb>` の正式経路を、Paper 1.21.11 + WorldEdit 7.3.19 単独（FAWE 禁止）の実機で
64×64 まで通す手順の正本である。自動化runnerは `scripts/smoke/v2-12-03-run.sh`（RCON）である。

**状態: 2026-07-20に実行・合格。** 証拠は
[v2-12-03-v2-command-path-evidence.md](../design-v2/audits/v2-12-03-v2-command-path-evidence.md)
を正本とする。`APPLIED` → `UNDONE` までをWorldEdit 7.3.19単独・64×64で通した。
同auditの§4に、本実行で判明したrunnerの欠陥3件（fill guardのfalse positive、RCON応答を
証拠化していなかった点、deprecation警告の二重スラッシュ）と修正内容を記録している。
再実行時は修正後のrunnerが空fillを失敗として扱う。

## 0. Preconditions

- Java 21 / Paper 1.21.11（`build(132)`。`V2-6-14`と同一build）
- WorldEdit **7.3.19** + LandformCraft のみ。`run/plugins/FastAsyncWorldEdit*.jar` が存在しないこと
- `run/` のtest worldが使い捨てであること（runnerは `placement-v2` state と `releases-v2` を消す）
- 空きdisk 2 GiB以上
- `V2-12-02` の production export経路と `V2-12-03` の command routingがbuild済みであること

寸法は64×64固定である。WorldEdit単独runtimeの実測上限
（`PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM`、ADR 0035 D6で明示承認された劣化）であり、
500／1000はFAWE evidenceに限られるため本smokeでは扱わない。

runnerは起動前に `run/plugins/LandformCraft/config.yml` の
`placement.release2.measured-candidate-max-*` を **64** へ固定する。FAWE測定後に残った
500／1000のままWorldEdit単独で起動すると `onEnable` が失敗しpluginがdisabledになる。
また coastal 64×64のGRAVITY effect（約50万block）向けに
`LANDFORMCRAFT_RUNSERVER_XMX`（既定 **4G**）でheapを上げ、`runServer --no-daemon` で起動する。

## 1. Fixture

本smokeは **production export経路** を使う。test専用のfixture exporterは使わない。

```bash
./gradlew -q run --args="v2 export \
  examples/v2/diagnostic/harbor-cove-64.request-v2.json \
  examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json \
  build/smoke/v2-12-03/fixture harbor-cove-64 water 54 46"
```

`placementEligible: true` と `requiredCapabilities: [surface-2_5d]` を確認する。
`manifestChecksum` を証拠へ残す。

## 2. Server sequence

```bash
bash scripts/smoke/v2-12-03-run.sh
```

runnerが発行するcommand列は次のとおりである。

1. `lfc version` / `lfc doctor` / `lfc help` — WorldEdit 7.3.19、writable、v2 verbのhelp掲載
2. `forceload add`（**block** column座標）＋ Y方向7層ずつの `fill`
   （effect padding込み66×66のため、8層だと32768上限超過）
3. `lfc v2 place plan harbor-cove-64 world 0 64 0`
   （coastal砂のGRAVITY半径64のため、`Y=-60`ではeffectがworld minHeightを割る）
4. `lfc v2 place confirm <placement-id> <token>` — snapshot-all完了
5. `lfc v2 place execute <placement-id>` — apply → settle → effect envelope全体のexact verify → `APPLIED`
6. `lfc v2 status <placement-id>`
7. `lfc r2 status <placement-id>` — **deprecated alias** の警告が出ること
8. `lfc v2 undo plan` → `lfc v2 undo execute` — `UNDONE`
9. v1 command回帰（`lfc help` / `lfc doctor` / `lfc apply status …` が従来どおり）

CONSOLE／RCONではconfirmation tokenをchatへ出さず、owner-onlyの
`data/confirmations/<key>.command`（10分・1回限り）へ保存しpathだけを表示する（`V2-11-03`）。
runnerは **confirmation file** からcommandを読む（server logにtokenは出ない）。
`v2 place plan` → `r2-confirm-<placement-id>.command`、
`v2 undo plan` → `r2-undo-<placement-id>.command`。

### forceload 注意

Paper 1.21 では `forceload` は **block** column座標を取る（chunk indexではない）。

```text
forceload add <blockX> <blockZ> [<toBlockX> <toBlockZ>]
```

64×64 envelope（anchor `0,64,0`、FLUID半径2込み）は
`forceload add -2 -2 65 65` である。chunk indexを渡すと極小範囲だけがmarkされ、
`fill` が `That position is not loaded` で失敗する。

## 3. 収集する証拠

`build/smoke/v2-12-03/evidence/` に出力される。

- `cli-export.txt` — production export経路の出力（manifest checksum、placement eligibility）
- `summary.txt` — jar sha256、release manifest sha256、placement ID、anchor、寸法
- `plan-line.txt` / `confirm-snippet.log` / `execute-snippet.log` / `status-line.txt`
- `alias-snippet.log` — `/lfc r2` のdeprecation警告
- `undo-snippet.log` — `UNDONE`
- `v1-regression.log` — v1 commandが不変であること
- `checksums/{journals,operations,snapshots}.sha256`

server world、log全文、secretはGitへ入れない。checksumと相対pathだけを監査へ書く。

## 4. Acceptance

- `lfc v2 export` が placement-eligible な `surface-2_5d` Releaseを作る
- `/lfc v2 place plan|confirm|execute` が `APPLIED` へ到達し、effect envelope全体のexact verifyが通る
- `/lfc v2 undo plan|execute` が `UNDONE` へ到達する
- `/lfc r2 …` が同一semanticで動き、deprecation警告を出す
- v1 commandのrouting・出力が不変である
- 上記をevidence fileで再現的に示せる

## 5. 失敗時

未完を完了と書かない。失敗した段階、log抜粋、環境（Paper build、WorldEdit version、heap、disk）を
記録し、`V2-12-03`を`BLOCKED_EXTERNAL`のまま残す。`V2-12-04`以降へ進まない。

典型的な失敗:

- `plugin is disabled` — `measured-candidate-max-*` がWorldEdit天井（64）超。runnerは64へ固定する
- `That position is not loaded` — `forceload` にchunk indexを渡している。block座標を使う
- `effect envelope exceeds allowed world bounds` — coastal砂のGRAVITY半径64に対してanchor Yが低すぎる。`0 64 0`を使う
- `Too many blocks in the specified area` — fillのY sliceが厚すぎる。68×68 footprintでは7層以下
- Gradle daemon disappeared / mid-undo crash — 既定2G heap不足。`LANDFORMCRAFT_RUNSERVER_XMX=4G`と`--no-daemon`を使う
