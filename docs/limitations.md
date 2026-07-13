# Limitations

## 明示的な非目標

- LandformCraft独自Web UI、browser UI、REST／WebSocket serverは提供しない
- 巨大都市、複雑な建築群の完全自動生成はしない
- AIによる全block座標列挙、AI生成Javaコードの実行はしない
- entity、任意block entity、高度なblock entityを生成・importしない
- biome書換え、洞窟、高度な植生シミュレーションはしない
- 最大水平範囲は1000×1000、最大vertical spanは512
- Minecraft／Paper 1.21.11とSponge Schematic v3 DataVersion 4671以外を推測変換しない
- custom asset paletteはbeta allowlistに限定し、entity／block entity／biomeを拒否する
- 全server構成での1000×1000実world apply TPS／所要時間は保証しない
- 実OpenAI／Anthropic APIのmodel availability、価格、将来互換、Provider側exactly-onceを保証しない
- CLIはMinecraft worldを直接変更しない。apply／Undo／Recovery mutationはPaperだけ
- betaから古いversionへのdata downgradeは保証しない

## Release candidateで残るbeta blocker

- custom asset catalogは安全な保管・検証・移送用までで、TerrainIntent v1からasset IDを選んで生成へ統合できない
- 500×500／1000×1000はCLI generate／export／verifyだけを再計測済みで、Paper plan／snapshot estimate／applyは未計測
- 成功したProvider responseのdurable cacheはなく、新しいdesign jobはProviderを再呼出しする
- error JSONのcontext IDはbeta candidateでは共通`resourceId`で、resource種別ごとの名前付きfieldではない
- CLI jobの別process cancelは永続intentまでで、実行ownerへ通知するIPCはない
- CONSOLEへ表示したconfirmation commandは標準Paper logにも平文で記録される
- OpenAI相関IDとlocal job／Design checksumは調査・成功結果再利用を助けるが、response受信前のnetwork断で二重処理を完全には防げない
- FAWE current apply smoke、最新sourceのclean build、crash Recovery E2Eが未完了

ブラウザ版ChatGPT／ClaudeでJSONを作る手動運用は独自Web UIではなく、引き続き対応します。
