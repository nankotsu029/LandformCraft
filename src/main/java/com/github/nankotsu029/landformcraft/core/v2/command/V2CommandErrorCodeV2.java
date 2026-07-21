package com.github.nankotsu029.landformcraft.core.v2.command;

/**
 * Stable, surface-independent failure codes for the official v2 command path (V2-12-03).
 * The names are part of the operator contract: CLI output, Paper messages, and audit records
 * all quote them verbatim, so they must not be renamed without an ADR.
 */
public enum V2CommandErrorCodeV2 {
    /** The first token after {@code v2} is not a known verb. */
    V2_UNKNOWN_VERB,
    /** The verb is known but the operation token is not. */
    V2_UNKNOWN_OPERATION,
    /** Argument count is outside the verb's declared arity. */
    V2_USAGE,
    /** The verb exists but is not available on this surface (for example Paper-only from the CLI). */
    V2_PAPER_ONLY,
    /** The verb exists but runs only from the CLI, for example offline v1 asset migration. */
    V2_CLI_ONLY,
    /** The caller lacks the required permission node. */
    V2_PERMISSION_DENIED,
    /** The caller is not an eligible Release 2 operator. */
    V2_OPERATOR_REQUIRED,
    /** The backing application service is not wired on this runtime. */
    V2_UNAVAILABLE
}
