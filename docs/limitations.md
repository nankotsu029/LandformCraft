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

## 現行0.9の未対応機能

- direct constraint mapを利用した地形生成（V2-1ではmapのcanonical field化とdiagnostic previewまで）
- 専用のfjord、delta、volcano、canyon、waterfall、coral reef generator
- 完成したgeology／climate／ecology field、高度な植生（v1には未接続。V2-4 offline pathはPhase gate完了）
- cave、lush cave、overhang、natural arch、sky island等の局所3D地形（offline v2は下記のとおり）
- `hydrology-plan`／`environment-fields`／`sparse-volume`のproduction export／Paper配置接続（既定v2 commandと`surface-2_5d`配置は接続済み）

これらの一部は [Terrain Generation v2 roadmap](roadmap.md#terrain-generation-v2) に含まれます。V2-0〜V2-5のoffline gateは完了し、対象featureとRelease 2 capabilityをoffline `SUPPORTED`にしています。V2-6は`V2-6-19`のRelease candidate audit／Phase gateまで完了し（[audit](design-v2/audits/v2-6-phase-gate.md)）、placement safety、rollback／Undo／Recovery、cross-capability hardening、運用metrics、strict canonical source、Release 2 Paper lifecycle、WorldEdit 7.3.19（`V2-6-14`）およびFAWE 2.15.2単独（`V2-6-15`）の実機smokeまで実装・検証済みです。`V2-12-05`以降、このlifecycleは既定`/lfc place`（明示形`/lfc v2 place`）から使います。`V2-11-01`（2026-07-20）で実機evidenceの範囲だけをcatalog昇格し、`surface-2_5d` capabilityのSANDY_BEACH／BREAKWATER_HARBOR／HARBOR_BASIN／ROCKY_CAPEの5 Paper能力列（`paper_apply`／`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`）が`SUPPORTED`です（寸法は後述の`V2-11-06`で昇格）。`hydrology-plan`／`environment-fields`／`sparse-volume`のfeatureはprefixごとの実機smokeが無いため`EXPERIMENTAL`、V2-9／V2-10 foundation featureはRelease capability未接続のため`UNSUPPORTED`のままです。無効化済み`V2-6-16`／`17`の後継として`V2-11-04`／`V2-11-05`がFAWE単独で500×500／1000×1000 Paper lifecycleを実測完走し、`V2-11-06`（2026-07-20完了）がその実測範囲だけをcatalogへ昇格しました。published dimension limitは1000×1000（FAWE 2.15.2 evidence）で、WorldEdit 7.3.19単独runtimeは64×64のまま、1000超は未実測として拒否されます。Track D（V2-9）／E（V2-10）は全Task完了済みですが、offline plan-level能力だけが昇格しており、公開Intent dispatch、Release 2 capability、CLI／Paper、配置には接続していません。

## Release candidateで残るbeta blocker

- custom asset catalogは安全な保管・検証・移送用までで、TerrainIntent v1からasset IDを選んで生成へ統合できない
- Paper 500／1000実測Task（`V2-6-16`／`17`）は無効化済み。後継`V2-11-04`／`V2-11-05`がFAWE単独で500×500／1000×1000 Paper lifecycleを実測完走し、`V2-11-06`がcatalog寸法を1000×1000へ昇格した。この寸法evidenceはFAWE 2.15.2のみで、WorldEdit 7.3.19単独runtimeは64×64、1000超（LARGE含む）は未実測
- 通常運用のRelease 2配置寸法は、検出runtimeの実測上限（FAWE 1000×1000／WorldEdit 64×64）を設定で超えられない（`V2-11-02`のクランプ＋`V2-11-06`の実測昇格。上限超の設定値は起動時に拒否し、上限超のlayoutはworld変更前に拒否する）。配布configの既定は64×64で、引き上げは運用者の明示設定。再実測専用の`measurement-profile`だけが例外で、明示flag＋隔離world＋CONSOLE／RCON operatorの3条件が揃った時にだけ有効になり、能力昇格は伴わない
- 成功したProvider responseのdurable cacheはなく、新しいdesign jobはProviderを再呼出しする
- error JSONのcontext IDはbeta candidateでは共通`resourceId`で、resource種別ごとの名前付きfieldではない
- CLI jobの別process cancelは永続intentまでで、実行ownerへ通知するIPCはない
- CONSOLEへ表示したconfirmation commandは標準Paper logにも平文で記録される
- OpenAI相関IDとlocal job／Design checksumは調査・成功結果再利用を助けるが、response受信前のnetwork断で二重処理を完全には防げない
- FAWE current crash Recovery E2E／最新source clean buildの一部が未完了（Release 2 FAWE placement smoke `V2-6-15`は完了）

ブラウザ版ChatGPT／ClaudeでJSONを作る手動運用は独自Web UIではなく、引き続き対応します。
