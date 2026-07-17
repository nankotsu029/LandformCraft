# Limitations

## 継続する製品境界

- LandformCraft独自Web UI、browser UI、REST／WebSocket serverは提供しない
- 巨大都市、複雑な建築群の完全自動生成はしない
- AIによる全block座標列挙、AI生成Javaコードの実行はしない
- entity、任意block entity、高度なblock entityを生成・importしない
- biome書換えはしない
- 任意codeを実行する地形module／script／外部JARを読み込まない
- 全域をdense voxel worldとして生成・保持しない
- 最大水平範囲は1000×1000、最大vertical spanは512
- Minecraft／Paper 1.21.11とSponge Schematic v3 DataVersion 4671以外を推測変換しない
- custom asset paletteはbeta allowlistに限定し、entity／block entity／biomeを拒否する
- 全server構成での1000×1000実world apply TPS／所要時間は保証しない
- 実OpenAI／Anthropic APIのmodel availability、価格、将来互換、Provider側exactly-onceを保証しない
- CLIはMinecraft worldを直接変更しない。apply／Undo／Recovery mutationはPaperだけ
- betaから古いversionへのdata downgradeは保証しない

## 現行0.9／v1の未対応機能

- direct constraint mapを利用した地形生成（V2-1ではmapのcanonical field化とdiagnostic previewまで）
- 専用のfjord、delta、volcano、canyon、waterfall、coral reef generator
- 完成したgeology／climate／ecology field、高度な植生（V2-4-02まではcatalog/assignmentのみ）
- cave、lush cave、overhang、natural arch、sky island等の局所3D地形
- Release format 2とv2地形のPaper配置

これらの一部は [Terrain Generation v2 roadmap](roadmap.md#terrain-generation-v2) に含まれます。V2-0〜V2-3 gateは完了し、strict v2 contract、v1 query adapter、direct map field、4 coastal kind、Hydrology IR／routing、regional plan／bounded field generator、固定3 pass reconciliation、独立validator／preview、V2専用offline tile schematic、strictな`surface-2_5d`／`hydrology-plan` Release 2 capabilityまで実装済みです。V2-4-01〜V2-4-04ではtyped geology／lithology／strataとcoarse climate prior／final temperature・moistureを`EXPERIMENTAL`で追加しましたが、regional wetness／salinity／hydroperiod、snow、ecology／materialとenvironment Release capabilityは未実装です。統合監査を通過したcoastal 4 kindとofflineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDは`SUPPORTED`ですが、CLI／Paper command、配置には接続していません。WATERFALLはvolume、mountainはsnowline／material、volcanicはmaterial／lava tube／ecologyが未完成なため`EXPERIMENTAL`です。次は`V2-4-05`で、V2-5〜V2-6は前提待ちです。

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
