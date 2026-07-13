# 開発ロードマップ

このファイルを進捗の正本とします。過去のPhase 0〜5監査は [phase-0-5-audit.md](audits/phase-0-5-audit.md)、現在の実装監査は [phase-6-beta-audit.md](audits/phase-6-beta-audit.md) です。

## 現在地

**現在: 0.9.0-beta.1 release candidate / Beta stabilization**

独自Web UIは製品範囲から削除しました。CLIとPaperを利用者向けUIとし、ブラウザ版ChatGPT／Claudeによる手動JSON作成は外部AI利用方法として維持します。

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

## 継続する非目標

- 独自Web UI／browser UI／frontend／HTTP server
- 巨大都市の完全自動生成
- AIによる全block座標列挙、AI生成Javaコード実行
- entity、高度なblock entity、biome書換え
- 無制限サイズ、洞窟、高度な植生

これらは将来予定ではなく、現在の製品境界です。変更する場合は新しいADRと脅威分析を必要とします。
