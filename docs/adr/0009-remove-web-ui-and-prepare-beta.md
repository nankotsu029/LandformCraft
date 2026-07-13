# 0009: 独自Web UIを削除しCLI／Paper betaへ集中する

- Status: Accepted
- Date: 2026-07-14

## Context

Phase 7には独自Web UI、HTTP endpoint、認証、CSRF、WebSocket、外部Worker案が記載されていました。一方、Phase 0〜6の利用経路にはCLIとPaperの未完成command、複数配置のoverlap、bearer token、disk不足、snapshot retention、明示Recoveryというbeta前の安全課題が残っていました。

ブラウザ版ChatGPT／ClaudeでTerrainIntent JSONを作る手動運用と、OpenAI／Anthropic HTTP API clientは独自Web UIではありません。

## Decision

独自Web UIを製品範囲とroadmapから削除し、frontend、embedded HTTP server、REST／WebSocket、Web認証、CSRF、Web専用DTO／config／workerを追加しません。リポジトリ検索時点で該当する実装code、依存、asset、npm設定は存在せず、削除対象は計画文書でした。

CLIとPaperを正規UIとし、共通Application Serviceを使います。job abstractionはCLI／Paperの非同期処理、cancel、永続状態に必要なので維持します。beta hardeningとしてcommand、permission、stable error、actor-bound confirmation、region／disk reservation、cleanup、Recovery、管理文書を優先します。

## Consequences

- HTTP server／frontend由来のattack surfaceと運用負担を持たない
- browserでの候補比較は8 PNGとfilesystem artifactを利用する
- ブラウザ版AIからの手動JSON importは維持する
- 外部Workerは現在のroadmap項目ではない。必要性が発生した場合は新ADRで独立runtime、secret、再開protocolを設計する
- 0002／0008や旧監査にあるWeb／Phase 7の記述は歴史的背景であり、現在仕様は本ADRとroadmapを優先する
