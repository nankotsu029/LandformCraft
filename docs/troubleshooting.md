# Troubleshooting

各errorの `LFC-...` codeとcorrelation IDを保存し、secretを含めず管理者へ渡してください。

| 症状 | 確認と対処 |
|---|---|
| WorldEditが見つからない | `/lfc doctor`。WorldEdit 7.3.19または対応FAWEを一方だけ導入し、`worldedit.enabled: true`でrestart |
| WorldEditとFAWEを同時導入 | 起動を拒否します。一方を削除してrestart |
| API key missing | configの`api-key-env`名とPaper process environmentを確認。値はconfig／chatへ貼らない |
| Provider 401／403 | keyの権限・organization・endpointをProvider管理画面で確認。response本文はLandformCraft logへ出さない |
| Provider 429 | `Retry-After`と上限付きretry後に失敗。RPM／token budgetを下げ、時間を置く |
| Provider 5xx／timeout | job statusを確認。曖昧な失敗は二重課金を完全には排除できないため、成功済みDesignを先に探す |
| 画像拒否 | PNG/JPEG、magic、8 MiB source、寸法／pixel、single frame、relative path、symlinkでないことを確認 |
| promptと上面図の方角矛盾 | `TOP_DOWN_SKETCH`は上=北、右=東、下=南、左=西。promptは「北側を陸地、南側を海」のように直接指定し、画像四辺の色と一致させる |
| Schema error | JSONだけを保存し、未知field、enum、request-id、範囲、重複keyを修正 |
| structure-not-placed | preferred zone、水、崖、傾斜、bounds、間隔を満たさない。structures previewとvalidation warningを確認 |
| atomic move unsupported | Design／Release／reservation／cleanup planの安全publishを拒否。data rootをatomic rename対応filesystemへ移す |
| disk不足 | `LFC-SNAPSHOT-NO-SPACE`。cleanup plan、backup後のretention、Release縮小、同時placement減少を検討 |
| Release verify failure | 配置しない。移送前のdirectory／ZIPとchecksumを比較し、改変fileを個別上書きせずReleaseを再生成 |
| confirmation expired | 新しいplanを作る。tokenを延長／再利用しない |
| actor mismatch | planを作った同じplayer UUIDまたはCONSOLEで実行。権限があっても別actor tokenは使えない |
| placement overlap | 同worldの既存予約がterminalになるのを待つか、接触しない別boundsへ変更 |
| world drift | 人の変更を保護するためUndoを拒否。backupとjournalを比較し、勝手にforceしない |
| RECOVERY_REQUIRED | `apply recover diagnose`。tokenが出ない分類はmanual intervention。snapshotをcleanupしない |
| Undo失敗 | journal、snapshot checksum、world identityを保持。再試行前にdiagnoseし、別placementを重ねない |

CLIで詳細が必要な場合だけ `--verbose`を付けます。通常出力やPaper chatにstack trace、absolute path、API responseを貼らないでください。
