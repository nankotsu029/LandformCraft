# Changelog

## 0.9.0-beta.1 — Unreleased release candidate (2026-07-14 audit)

### Added

- Paper request、design、generate、job、candidate、export、version、doctor、Recovery、cleanup、asset commands
- 機能単位permissionと権限対応completion
- stable public error code、correlation ID、safe message契約
- actor domain identityとactor-bound／一回用confirmation
- world UUID＋inclusive boundsのatomic placement reservationとoverlap拒否
- snapshot worst-case disk見積もり、FileStore別永続予約、実使用量journal
- retention dry-run／確認付きcleanup／checksum再検査／audit
- Recovery診断分類、rollback、full-match限定accept、audit
- Paper OpenAI／Anthropic設定、環境変数key、model／policy hard range
- CLI common option、doctor、request、job、candidate、asset、read-only recovery
- 制限付きSponge v3 custom asset catalogとsecurity検査
- beta向けQuick Start、User／Admin Guide、仕組み、障害対応、制限、release checklist

### Changed

- versionを `0.9.0-beta.1`へ更新
- WorldEdit／FAWEが無い場合も非placement Paper commandを利用可能に変更
- OpenAI requestへ決定論的client request IDを付加
- placement journal／Schemaへactor、confirmation時刻、disk reservation／usageを追加
- READMEとroadmapをBeta stabilizationへ再構成
- Phase 3 terminal journalを`SYSTEM:LEGACY`として保守的に読めるupgrade互換loaderを追加
- preferred zone fallbackをmanifest／warning／previewへ明示し、柵の4方向connection stateを決定論的にmaterialize
- Paper jobの即時statusと単調な永続化、request別candidate一覧／完全性検証へ強化

### Removed

- 独自Web UI／Phase 7 UI／Web専用workerを将来計画から削除
- broad apply／undo permissionをplan／execute／status／recoveryへ分割

### Security

- 別actor、overlap領域、disk不足、symlink asset、RECOVERY_REQUIRED snapshot削除をworld変更／削除前に拒否
- API key値、Provider response本文、internal exception、absolute pathを利用者向けerrorへ出さない契約を追加

### Compatibility

placement journalとpermission名がbeta前snapshotから変わります。upgrade前にworldとdata rootをbackupしてください。downgradeは保証しません。
