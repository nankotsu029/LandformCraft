package com.github.nankotsu029.landformcraft.core;

import java.util.List;

/** Secret-free read-only installation diagnosis shared by CLI and Paper. */
public record DoctorReport(
        String javaVersion,
        String runtime,
        String dataDirectory,
        boolean writable,
        boolean atomicMove,
        long usableBytes,
        boolean openAiKeyPresent,
        boolean anthropicKeyPresent,
        int runningJobs,
        int recoveryRequiredPlacements,
        List<String> warnings
) {
    public DoctorReport {
        warnings = List.copyOf(warnings);
    }
}
