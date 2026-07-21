# V2-12-03 v2 command path smoke evidence

`/lfc v2 <verb>` 正式経路の実機smoke証拠である。手順の正本は
[runbook](../../smoke/v2-12-03-v2-command-path-runbook.md)、runnerは `scripts/smoke/v2-12-03-run.sh`。

- **実施日:** 2026-07-20
- **結果:** 合格（`APPLIED` → `UNDONE`、full effect-envelope exact verify通過）
- **profile:** Paper 1.21.11（build 132）＋ WorldEdit **7.3.19**（`7.3.19+7378-cb4171a`）単独。FAWE不使用
- **寸法:** 64×64（WorldEdit単独runtimeの実測上限。ADR 0035 D6）
- **heap:** `LANDFORMCRAFT_RUNSERVER_XMX=4G`、`runServer --no-daemon`

## 1. Fixture（production export経路）

test専用exporterではなく `lfc v2 export`（`V2-12-02`）で作成した。

```text
releaseId: harbor-cove-64
requiredCapabilities: [surface-2_5d]
tiles: 1
placementEligible: true
verifiedFiles: 23
blueprintChecksum: 8b06b28499e349cf4948f16a6c8766098b06db0a4d23a2f0a491823f5b17adf3
manifestChecksum:  9a0fd9fecabf386e7187af6b098e21d5e6bd0ecc921bf9c0646db59b6b5ccc82
```

| 項目 | 値 |
|---|---|
| jar sha256 | `bfd4419538953efdd047dc2cc41456fc24378d2ea4e5fab0979a3bfd0c65b46d` |
| staged manifest.json sha256 | `063af15a8f355d4f04ddf65431d8c3b35416d0e08ad58a28192d1684b627ca1b` |
| placement ID | `d7d082bc-a414-4ec8-858c-f8c75c978b6f` |
| anchor | `0, 64, 0`（world） |

anchor Yは64である。coastal砂のGRAVITY effect半径（64）とFLUID support（2）を含む
effect envelopeがworld minHeight（−64）を割らないためで、`V2-6-14`のsolid-stone smokeが使う
`Y=-60`はcoastal fixtureでは成立しない。

## 2. Lifecycle（すべて `/lfc v2 <verb>`）

| 段階 | command | 結果 | 時刻 |
|---|---|---|---|
| plan | `lfc v2 place plan harbor-cove-64 world 0 64 0` | Placement ID発行、world未変更 | 21:38:07 |
| confirm | `lfc v2 place confirm <id> <token>` | `SNAPSHOT_COMPLETE` | 21:38:57 |
| execute | `lfc v2 place execute <id>` | `state: APPLIED` / `outcome: APPLIED` / `Release 2 settle and full effect-envelope verify succeeded` | 21:43:29 |
| status | `lfc v2 status <id>` | `APPLIED` | 21:43:34 |
| status（alias） | `lfc r2 status <id>` | `APPLIED` ＋ deprecation警告 | 21:43:35 |
| undo plan | `lfc v2 undo plan <id>` | confirmation file発行 | 21:43:37 |
| undo execute | `lfc v2 undo execute <id> <token>` | `state: UNDONE` | 21:44:33 |

confirmation tokenはCONSOLE／RCONではchatへ出さず、owner-onlyの
`data/confirmations/r2-confirm-<id>.command`／`r2-undo-<id>.command` に保存され、
runnerはそのfileからcommandを読む（server logにtokenは出ない、`V2-11-03`）。

deprecated aliasのRCON応答（`evidence/alias-rcon.txt`）:

```text
[LandformCraft] ! '/landformcraft r2' は '/landformcraft v2' の deprecated alias です（V2-12-06で削除）。
```

server logにERROR／SEVERE／Exceptionは無く、`Disabling LandformCraft` まで正常終了した。

## 3. 収集したartifact checksum

`build/smoke/v2-12-03/evidence/checksums/`（Gitへは入れない）。

| 対象 | 件数 |
|---|---|
| `placement-v2/journals` | 2 |
| `placement-v2/operations` | 22 |
| `placement-v2/snapshots` | 4 |

本smokeのjournal: `d7d082bc-a414-4ec8-858c-f8c75c978b6f.json`
sha256 `a0745d25f4e394346990fb23db7dbb7c4d534367510f85169f430805bd7fb890`。

## 4. 本実行で判明したrunnerの欠陥（修正済み・再実行時に有効）

Acceptanceに影響しないが、証拠の質に影響したため記録する。**いずれもrunner側の欠陥であり、
製品側の不具合ではない。**

1. **fill guardのfalse positive** — Paperの応答 `No blocks were filled` は
   `blocks filled` を含むため、旧guardの `rg -qi 'Filled|blocks filled'` を通過した。
   本実行では全16 sliceが `No blocks were filled` で、意図した stone baseline は
   確立されていない（対象領域が先行smokeの結果すでにstoneだったためと考えられる）。
   `forceload` も `No chunks were marked for force loading` を返している（既にmark済み）。
   placementはfull effect-envelope exact verifyを通過しているため lifecycle の証明は
   成立するが、baselineが「意図して置いたstone」であることは本実行の証拠からは言えない。
   guardを `No blocks were filled` の明示拒否＋`Filled <n>` の要求へ修正した。
2. **`doctor-snippet.log`／`v1-regression.log` が空** — RCONのcommand応答は
   RCON channelへ返り `latest.log` には出ない。server logをgrepする実装だったため空になった。
   `doctor-rcon.txt`／`v1-regression-rcon.txt` としてRCON応答自体を保存し、
   `lfc help` にv2 verbが載ること、v1 commandが v2 router へ吸われていないこと
   （`V2_UNKNOWN_VERB`／`V2_PAPER_ONLY` が出ないこと）をrunner内で検査するよう修正した。
   したがって本実行では**v1 command不変のlive証拠は取得できていない**。v1不変は自動test
   （`LandformCraftCliV2Test.v1CommandDispatchIsUnchanged` と full suite）で担保している。
3. **deprecation警告の二重スラッシュ** — `'//landformcraft r2'` と表示されていた
   （`ROOT` が既に先頭スラッシュを含む）。表示のみの欠陥で、routingには影響しない。修正済み。

## 4b. 併せて解消したtest provisioning欠陥

本Task中のfull suiteで `java.util.concurrent.RejectedExecutionException: I/O concurrency limit reached`
が間欠的に発生した。原因は production ではなく test 側のprovisioningである。
`GenerationExecutors.create(ioConcurrency, generationParallelism, generationQueueCapacity)` の
第1引数はI/O admissionであり、次の2件が過小だった。

| test | 変更前 | 変更後 | 根拠 |
|---|---|---|---|
| `DesignResponseCacheTest` | `create(2, 2, 8)`（I/O 2） | `create(4, 2, 8)` | 同じserviceを駆動する`TerrainDesignApplicationServiceTest`は4。design flowはrequest read／image load／複数のjob-state write／cache lookupが重なる |
| `PlacementPhaseGateV2Test` run-b | `runLifecycle(…, 1, 1, 8)`（I/O 1） | `runLifecycle(…, 4, 1, 8)` | runtime profileの軸はgeneration parallelism（1 vs 4）であり、I/O admissionは determinism 次元ではない。I/O 1ではlifecycle中の一時的な2本目の投入が即座に拒否される |

`supplyIo` のfail-fast admissionはAGENTS.md §11の事前admission要件どおりであり、production側は変更していない。
determinism assertion（locale／timezone／worker数を変えて同一block-state mapになること）も弱めていない。
紛らわしかった`runLifecycle(root, cpu, io, queue)`の引数名を実際の並び
（`ioConcurrency, generationParallelism, queue`）へ改めた。
修正後、full suiteを2回連続で969 tests／0 failuresで通した。

## 5. Acceptance判定

| 条件 | 判定 | 根拠 |
|---|---|---|
| `lfc v2 export` が placement-eligible な `surface-2_5d` Releaseを作る | 合格 | `evidence/cli-export.txt` |
| `/lfc v2 place plan\|confirm\|execute` が `APPLIED` に到達し effect envelope全体のexact verifyが通る | 合格 | `evidence/execute-snippet.log` |
| `/lfc v2 undo plan\|execute` が `UNDONE` に到達する | 合格 | `evidence/undo-snippet.log` |
| `/lfc r2 …` が同一semanticで動き deprecation警告を出す | 合格 | `evidence/alias-rcon.txt` |
| v1 commandのrouting・出力が不変 | live証拠なし（自動testで担保） | §4-2 |

Gate（「v2正規経路のE2Eが自動test＋実機smokeで固定される」）は満たした。
catalogの能力昇格・寸法昇格は本Taskでは行っていない（`V2-11-06`の範囲のまま）。
