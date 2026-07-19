package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

/** Strict pre-mutation verification port. Production uses the filesystem-backed implementation. */
@FunctionalInterface
public interface PlacementApplyPrerequisiteVerifierV2 {
    void verify(PlacementApplyRequestV2 request);
}
