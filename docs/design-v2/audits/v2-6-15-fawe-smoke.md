# V2-6-15 FAWE 2.15.2 standalone Release 2 placement smoke

- Status: **COMPLETE**（2026-07-19）
- Decision scope: Track A `V2-6-15`
- Related: ADR 0032、[evidence](v2-6-15-fawe-smoke-evidence.md)、[runbook](../../smoke/v2-6-15-fawe-2.15.2-runbook.md)、前提 `V2-6-14`

## 1. Environment probe（final）

| Check | Result |
|---|---|
| Isolated directory `run-fawe/` | **present** |
| FAWE 2.15.2 only（no WorldEdit jar） | **PASS** |
| LandformCraft plugin via `runFaweServer` `-add-plugin` | **PASS**（今回 shadowJar） |
| `./gradlew runFaweServer`（`runDirectory=run-fawe`） | **PASS** — Paper 1.21.11-132、`Done` 到達 |
| Direct `versions/*/paper-*.jar` launch | not used（paperclip経路を使用） |
| Release 2 Paper apply／Undo／Recovery injection | **present**（V2-6-21、`/lfc r2`） |
| Past FAWE init-only (2026-07-14) | **not accepted** |

## 2. Decision

**Status: COMPLETE**

`runFaweServer`（`xyz.jpenilla.run-paper` の `runDirectory=run-fawe`、Paper build 132）で FAWE 単独 profile を起動し、`/lfc r2 plan→confirm→execute→undo` を `APPLIED` 後 `UNDONE` まで実機確認した。canonical expected stream checksum は V2-6-14 と同値。WorldEdit plugin 併用なし。

## 3. Acceptance

- [x] FAWE-only generate→…→Undo（Recovery path available; Undo closed the operation）
- [x] gateway close／async／main-thread（clean stop；no main-thread join errors）
- [x] canonical checksum／queue／memory／disk（expected=observed stream；snapshot disk 12288 bytes）
- [x] exact FAWE 2.15.2+e9ed0d1＋build；no secret／server artifacts committed
