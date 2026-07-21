# V2-7 Image fidelity Phase gate audit

> Status: PASS — `V2-7-07` completed (2026-07-21). Offline extract→draft→explicit promotion→multi-source reconciliation gate. Extract path is recorded as a **SUPPORTED candidate** only; production lifecycle remains `EXPERIMENTAL`. CLI／Paper／Request未接続. Release capability追加なし.

## Decision

`V2-7-01` through `V2-7-06` plus `V2-7-07` multi-source reconciliation satisfy the V2-7 parent gate.

- Deterministic land-water / height-guide / zone-label extraction cores are frozen under versioned algorithms.
- Secure image envelope, draft artifacts, confidence preview, and explicit promotion re-enter V2-1 strict decoders.
- Multi-source priority (`image-constraint-priority-v1`) resolves prompt vs image and hard vs draft without last-write-wins; HARD/HARD and same-rank SOFT peers fail closed.
- Source-to-result diff metrics and diagnostic preview layers are published with strict indexes.

This audit does **not** promote extract to production `SUPPORTED`, does **not** add a Release 2 capability, and does **not** wire CLI／Paper／Request.

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-7-01 land-water extract | `ImageLandWaterExtractorV2Test` | PASS |
| V2-7-02 secure envelope | `SecureImageExtractionEnvelopeV2Test` | PASS |
| V2-7-03 draft/preview | `ExtractedMaskDraftArtifactPublisherV2Test` / `ExtractedMaskDraftPreviewRendererV2Test` | PASS |
| V2-7-04 promotion | `ExtractedMaskPromotionServiceV2Test` | PASS |
| V2-7-05 height guide | `ImageHeightGuideExtractorV2Test` / `ExtractedHeightGuidePromotionServiceV2Test` | PASS |
| V2-7-06 zone label | `ImageZoneLabelExtractorV2Test` / `ExtractedZoneLabelPromotionServiceV2Test` | PASS |
| V2-7-07 multi-source + gate | `MultiSourceReconciliationServiceV2Test` / `ImageFidelityPhaseGateV2Test` / this audit | PASS |

## Determinism / resource / security

- Integer-only extract and reconcile; repeat／thread／locale／timezone checksum identity covered by suite tests.
- Trusted ceilings remain on envelope／extract／promotion／reconcile pixel and working budgets; cancel cleans staging before atomic commit.
- Artifact directories reject extra／missing／checksum／symlink tampering via strict codecs.
- No secret／raw path／EXIF embedding in sealed indexes.

## Compatibility

- v1 Schema／generator／Release 1／placement／Undo untouched.
- V2-1 constraint map contracts unchanged; promotion still re-enters existing decoders.
- No Release capability registry change.

## SUPPORTED candidate record

Extract path (secure envelope → draft → explicit promotion → multi-source reconcile) is a **SUPPORTED candidate** for a future wiring／capability Task. Until that Task:

- lifecycle documentation and runtime posture stay `EXPERIMENTAL`
- CLI／Paper／Request remain unwired
- Release capability追加は非Scope（必要なら新Task）

## Verification commands

```text
./gradlew test --tests '*MultiSourceReconciliationServiceV2Test' --tests '*ImageFidelityPhaseGateV2Test'
./gradlew test --tests '*ImageLandWater*' --tests '*SecureImageExtraction*' --tests '*ExtractedMask*' --tests '*ImageHeightGuide*' --tests '*ImageZoneLabel*' --tests '*ExtractedHeightGuide*' --tests '*ExtractedZoneLabel*'
./gradlew test
./gradlew build
git diff --check
```

## Remaining boundaries

- Multi-source rules for free-curve vector layers or AI vision drafts beyond `PROMPT_SOFT`／`IMAGE_DRAFT` are out of scope.
- Paper UI and Request Schema wiring are future Tasks.
- Track C（V2-8）and other tracks are independent.
