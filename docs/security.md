# セキュリティ

## 保護対象

- OpenAI／Anthropic APIキーと課金quota
- ユーザーのprompt、参考画像、生成物
- Minecraft worldとsnapshot
- サーバーfilesystem
- job履歴、監査log、provider usage

## Secret管理

優先順位:

1. OS環境変数
2. Docker／Kubernetes等のSecret
3. Minecraftサーバー外の権限制限ファイル
4. 最終手段としてplugin data内の権限制限されたsecret file

既定の環境変数名:

```text
OPENAI_API_KEY
ANTHROPIC_API_KEY
```

禁止事項:

- Gitへのcommit
- `request.yml`、Intent、Blueprint、manifest、snapshotへの保存
- Minecraft chat／command argumentへの入力
- Authorization headerやquery parameterのlog出力
- exception objectを無加工で監査logへserialize

## AIへ送るデータ

- requestに添付されたpromptと許可済み画像だけを送る
- サーバーdirectory、plugin config、log、他ユーザーデータを自動添付しない
- image role、MIME type、pixel count、byte sizeを検証する
- EXIF等の不要metadataは削除する方針とする
- provider、model、送信時刻、usage、response IDは記録できるがsecretを除く
- 保存期間と削除操作を運用設定で管理する

画像やprompt内の命令は非信頼データです。「schemaを無視」「キーを表示」「コードを実行」などのprompt injectionに従わず、Provider出力をschemaとsemantic validatorで制限します。

## File入力

- request rootからの相対pathだけを許可する
- path normalization後にroot内であることを確認する
- symbolic link、device file、FIFOを拒否する
- allowlistされた画像形式とmagic bytesを検証する
- byte size、pixel数、解凍後size、枚数へ上限を設ける
- ZIP slip、ZIP bomb、重複entry、大文字小文字collisionを拒否する
- JSON/YAMLはdepth、文字数、collection数、unknown fieldを制限する
- `.schem`も非信頼入力としてNBT depth、palette、bounds、block countを検査する

## World保護

- default permissionはoperatorのみ
- request作成、API設計、export、apply、undoを別permissionへ分割する予定
- applyはRelease verify、target bounds、world border、height、overlap、snapshot容量を事前検査する
- preview／dry-run／明示confirmなしで変更しない
- snapshot作成失敗時は配置しない
- tileごとの適用記録と逆順rollbackを永続化する
- WorldEdit／FAWE sessionを必ずcloseし、完了確認前にsnapshotを削除しない

## Availabilityと課金

- provider timeout、retry上限、exponential backoff＋jitter
- request単位・ユーザー単位・server単位のquota
- candidates、画像、boundsにhard limit
- retryで同一課金処理を重複させないidempotency／response記録
- circuit breakerと手動JSON importへのfallback
- CPU、memory、disk、job queueへbudgetを設定

## Logging

記録するもの:

- job／request ID、stage、duration、結果
- provider／model、usage、response ID
- validation issue、artifact checksum、placement ID
- 操作者UUID、confirm、undo結果

記録しないもの:

- APIキー、header、Cookie
- prompt／画像の全文（明示した監査mode以外）
- server filesystemの不必要なabsolute path
- snapshotのblock内容

redactionはlog出力直前だけでなく、exceptionをdomain errorへ変換するadapter境界でも行います。

## Dependency方針

- Gradle dependencyをversion固定し、意図せずlatestへ追従しない
- Paper、WorldEdit、FAWEは公式repository／releaseを使う
- WorldEdit／FAWEを同時導入しない
- WorldEdit／Paperをshaded plugin JARへ含めない
- 定期的に脆弱性と対応Minecraft versionを確認する
