# 0033: Release 2 verified canonical block sourceをcloseable viewで固定する

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-20

## Context

V2-6-06のapply／V2-6-07のfull verify／V2-6-09のUndo／V2-6-10のRecoveryは、preview／exportと同じfinal block streamを返す`PlacementCanonicalBlockSourceV2`を要求する。しかしproductionにはinterfaceとtest fixtureしかなく、Release format 2 directory／ZIPから再構築する実装がなかった。ZIP verifierのstagingはverify完了時に削除されるため、後続cursorが未検証entryを直接読み直す設計も許容できない。

## Decision

1. `ReleaseCoreVerifierV2.openVerified`はdirectoryまたはZIPを既存のexact-set／tamper／budget規則で検証し、`VerifiedReleaseViewV2`を返す。ZIP stagingはview closeまでだけ保持し、directory rootは所有・削除しない。
2. `VerifiedReleaseCanonicalBlockSourceV2`はverified viewからtile metadataを固定し、surface系prefixではsurface tile、`sparse-volume` prefixではvolume-composed final tileを選ぶ。FeatureKind別adapterは作らない。
3. cursorを開くたびに対象schematicのbyte length／artifact checksum／metadata semantic checksumを再検査し、`SpongeV3TileInspectorV2.decode`で1 tileだけをbounded decodeする。全Release blockを常駐させない。
4. release-local X-fastest→Z→Y streamをplacement targetのminimum cornerへ変換し、surface ordinal 0〜2、volume ordinal 3〜5をblock physics classから明示する。
5. source bindingはmanifest checksum、canonical capability prefix、overlay ordinal集合、sorted tile metadata fingerprintへ固定する。close後のreopenを拒否し、新しいsource instanceは元containerを再びstrict verifyする。

## Consequences

- directory／ZIPは同じbindingとblock streamを返し、ZIP stagingはsource lifecycleへboundedに所有される。
- source作成時のmissing／extra／duplicate／case collision／traversal／symlink／future version／semantic tamperは既存Release verifierで拒否され、作成後のtile byte driftもcursor開始前に拒否される。
- Paper lifecycle／command routing、WorldEdit／FAWE実機smoke、support昇格はV2-6-21以降へ残る。
- Release format、artifact type、Schema、v1 Release 1 allowlistは変更しない。
