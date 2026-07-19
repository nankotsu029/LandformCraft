# 0029: Provider／manual／image を v2 capability 明示選択と no-fallback で同一 canonical Intent へ接続する

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-11

## Context

v1 の `TerrainDesignProvider`／`TerrainDesignApplicationService` は TerrainIntent v1 だけを返す。V2-2〜5 の supported contracts と Release 2 はあるが、OpenAI／Anthropic／manual import／constraint bundle／reference-image soft draft を **明示 capability 選択** で同じ v2 design 経路へ接続する orchestration が欠けていた。migration-plan は未対応 model を v1 へ限定すると書く一方、V2-6-11 Acceptance は unsupported capability の fallback を禁止する。画像からの hard geometry 暗黙生成は非 Scope（ADR 0017）である。

## Decision

1. **versioned SPI**（`ai.spi.v2`）を導入する。`TerrainDesignProviderV2` は `TerrainIntentV2` だけを返し、intent contract version は常に明示する（auto-upgrade なし）。
2. **`ProviderCapabilityCatalogV2` + `DesignCapabilityNegotiatorV2`** が path／model／requested capability を照合する。未知 version・未知／未宣言 model・capability mismatch は hard reject し、v1 design path へ書き換えない。
3. **`TerrainDesignApplicationServiceV2`**（`isRelease2Path() == true`）が OPENAI／ANTHROPIC／IMPORT／FIXTURE／MANUAL_CONSTRAINT／REFERENCE_IMAGE_DRAFT を同一 publish 境界へ接続する。
4. **Design Package v2** は `terrain-intent-v2.json`／`audit-v2.json`／任意 `image-draft-evidence-v2.json`／exact `checksums.sha256` とし、atomic publish + strict read-back する。audit の intentChecksum は intent ファイル SHA-256 とする（v1 Design Package と同型）。
5. **reference image soft draft** は `ImageLandWaterExtractorV2` → `ImageDraftEvidenceV2`（UNCONFIRMED／CONFIRMED_SOFT／REJECTED）に限り、HARD `mapReferences` への暗黙昇格 API は `forbidHardPromotion` で拒否する。secure ファイル封筒の接続は V2-7-02 のまま。
6. **v1 default** は変更しない。CLI／Paper の既存 `design` は v1 のまま。v2 は明示 dispatch のみ。

## Consequences

- Provider 差は canonical Intent checksum 以降の generation に影響しない（同一 Intent → 同一 checksum）。
- 実 API credential は fixture に不要。contract test（local fake HTTP）で閉じ、live smoke は別 Task。
- V2-6-12 の cross-capability Release hardening へ進める。

## Alternatives

- v1 SPI を in-place で v2 化する案は v1 golden／default を壊すため不採用。
- unsupported model を静かに v1 へ落とす案は Acceptance（no-fallback）に反するため不採用。
- soft draft を即 HARD constraint 化する案は ADR 0017／非 Scope に反するため不採用。
