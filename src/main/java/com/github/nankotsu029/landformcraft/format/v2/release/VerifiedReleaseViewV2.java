package com.github.nankotsu029.landformcraft.format.v2.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Closeable verified payload view; ZIP views own and clean only their private staging root. */
public final class VerifiedReleaseViewV2 implements AutoCloseable {
    private final Path root;
    private final ReleaseCoreVerificationV2 verification;
    private final boolean ownedRoot;
    private boolean closed;

    VerifiedReleaseViewV2(Path root, ReleaseCoreVerificationV2 verification, boolean ownedRoot) {
        this.root = Objects.requireNonNull(root, "root");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.ownedRoot = ownedRoot;
    }

    static VerifiedReleaseViewV2 owned(Path input, Path root, ReleaseCoreVerificationV2 staged) {
        return new VerifiedReleaseViewV2(root, new ReleaseCoreVerificationV2(
                input, staged.manifest(), staged.verifiedFiles(), staged.verifiedBytes()), true);
    }

    public Path root() {
        requireOpen();
        return root;
    }

    public ReleaseCoreVerificationV2 verification() {
        requireOpen();
        return verification;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("verified Release 2 view is closed");
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (ownedRoot) ReleaseCoreVerifierV2.deleteTree(root);
        }
    }
}
