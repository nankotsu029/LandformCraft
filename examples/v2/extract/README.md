# Extracted mask / height-guide / zone-label / multi-source artifacts (V2-7-03〜V2-7-07)

Sealed indexes for offline extraction drafts, explicit promotion provenance, and multi-source reconciliation.

- `extracted-mask-draft-v2.json` — land-water draft index (`classes.u8` / `confidence.u8` not checked in)
- `extracted-mask-draft-preview-index-v2.json` — fixed three-layer preview index
- `extracted-mask-promotion-v2.json` — land-water promotion provenance (`land-water.png` not checked in)
- `extracted-height-guide-draft-v2.json` — height-guide draft index (`samples.u8` / `confidence.u8` not checked in)
- `extracted-height-guide-promotion-v2.json` — height-guide promotion provenance (`height-guide.png` not checked in)
- `extracted-zone-label-draft-v2.json` — zone-label draft index (`labels.u8` / `confidence.u8` not checked in)
- `extracted-zone-label-promotion-v2.json` — zone-label promotion provenance (`zone-labels.png` not checked in)
- `multi-source-reconciliation-v2.json` — multi-source reconcile index (`result.u8` / `conflict.u8` / `source-diff.u8` not checked in)
- `multi-source-reconciliation-preview-index-v2.json` — result／conflict／source-diff preview index (PNGs not checked in)

Height samples require an explicit V2-1 `HeightValueMeaning` at promotion. Zone samples are fixed-palette categorical proposals. Multi-source priority is `image-constraint-priority-v1`. All remain `EXPERIMENTAL` (SUPPORTED candidate after V2-7 gate). CLI／Paper／Request wiring is still out of scope.
