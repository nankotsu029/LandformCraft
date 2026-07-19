# 0031: Release 2 operational metrics／diagnostics／retention を bounded audit 契約で固定する

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-13

## Context

Release 2 の generation／placement／recovery は個別 service として存在するが、operator が stage／queue／memory／disk／settle／verify を同一の運用証拠として読み、failure を correlation ID から辿り、snapshot retention を dry-run／confirm／audit 付きで実行する `core.v2.operations` が欠けていた。v1 の `SnapshotCleanupService`／`DoctorService` は残すが、Release 2 と共有 allowlist や自動削除 default は禁止である。external telemetry SaaS と Web UI は非 Scope である。

## Decision

1. **`OperationalMetricsSnapshotV2`** は closed enum label と固定 unit（COUNT／BYTES／TICKS）だけを許す。未知 label・自由文字列 cardinality を拒否する。
2. **`OperationalAuditEventV2`** は append-only JSONL（`operations-audit-v2.jsonl`）へ correlation ID・actor・operation／stage／decision を記録する。absolute path、Authorization、API key、raw payload を拒否する。
3. **`OperationalDiagnosticsServiceV2`** は correlation から redacted findings を返す。secret 有無は boolean だけ。
4. **`Release2RetentionServiceV2`** は `PlacementRecoveryServiceV2` cleanup を actor-bound plan／TTL token／confirm／audit で包む。startup 自動削除は行わない。
5. Paper は `PaperOperationalOperationsServiceV2` と `/lfc ops metrics|diagnose` を提供する。retention の production 配線は recovery service 注入を前提とし、未注入時は metrics／diagnostics のみ有効とする。

## Consequences

- Operator は correlation ID から failure を診断でき、cleanup は dry-run／confirm／audit 付きになる。
- v1 cleanup／doctor の意味は変更しない。
- 次は `V2-6-15`（FAWE 2.15.2 standalone smoke；ADR 0032）。

## Alternatives

- 自由 label の metrics map は unbounded cardinality のため不採用。
- startup 自動 cleanup は非 Scope（自動削除 default 禁止）のため不採用。
- external telemetry SaaS は非 Scope のため不採用。
