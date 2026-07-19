package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Capability-named facade over the strict Release 2 directory/ZIP verifier. */
public final class ReleaseSparseVolumeVerifierV2 {
    private final ReleaseCoreVerifierV2 delegate;

    public ReleaseSparseVolumeVerifierV2() {
        this(ReleaseV2Limits.defaults());
    }

    public ReleaseSparseVolumeVerifierV2(ReleaseV2Limits limits) {
        delegate = new ReleaseCoreVerifierV2(Objects.requireNonNull(limits, "limits"));
    }

    public ReleaseCoreVerificationV2 verify(Path path) throws IOException {
        return delegate.verify(path);
    }

    public ReleaseCoreVerificationV2 verify(Path path, CancellationToken cancellationToken) throws IOException {
        return delegate.verify(path, cancellationToken);
    }
}
