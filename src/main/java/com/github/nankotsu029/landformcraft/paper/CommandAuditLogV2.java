package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.LandformException;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central audit and error trail for the {@code /landformcraft} command surface.
 *
 * <p>Two responsibilities, both routed to the standard Paper server log:</p>
 * <ol>
 *   <li><b>Audit</b> — records who ran which command and how it resolved
 *       ({@link #invoked}/{@link #succeeded}/{@link #failed}), so operators can reconstruct
 *       the sequence of privileged actions from the server log alone.</li>
 *   <li><b>Error detail</b> — {@link #failed} writes the sanitized failure line <em>plus</em> the
 *       raw technical cause (with stack trace) under the same correlation ID the player is shown,
 *       so a player-reported correlation ID leads an admin straight to the underlying reason.</li>
 * </ol>
 *
 * <p>One-time confirmation tokens are bearer secrets and both tokens and identifiers are UUIDs, so
 * the command text is redacted by grammar in {@link #redactedCommand} before it ever reaches the
 * log — the token argument of a confirm/execute/create verb is replaced with {@code ***}.</p>
 */
final class CommandAuditLogV2 {

    private static final String REDACTED = "***";
    private static final String PREFIX = "[audit] ";

    private final Logger logger;

    CommandAuditLogV2(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Records that {@code actor} issued {@code command}, before permission and argument checks. */
    void invoked(String actor, String command) {
        logger.info(() -> PREFIX + actor + " ran: " + command);
    }

    /** Records that {@code command} issued by {@code actor} completed successfully. */
    void succeeded(String actor, String command) {
        logger.info(() -> PREFIX + actor + " ok: " + command);
    }

    /**
     * Records a failure of {@code command} issued by {@code actor}. The sanitized public failure
     * (stable code, correlation ID, operation/stage, safe message) is logged so it lines up with
     * what the player is shown; {@code rawCause} — the underlying technical exception — is attached
     * so the server log carries the detailed reason and stack trace the player never sees.
     */
    void failed(String actor, String command, LandformException failure, Throwable rawCause) {
        Objects.requireNonNull(failure, "failure");
        String summary = PREFIX + actor + " failed: " + command
                + " | " + failure.code().code() + ": " + failure.getMessage()
                + " | correlationId=" + failure.correlationId()
                + " operation=" + failure.operation()
                + " stage=" + failure.stage();
        // Attaching the raw cause prints its stack trace at WARNING, giving operators the technical
        // reason under the same correlation ID the player received. Fall back to the sanitized
        // failure itself when no distinct cause is available.
        logger.log(Level.WARNING, summary, rawCause != null ? rawCause : failure);
    }

    /**
     * Renders the command line for the audit log with any confirmation token redacted.
     *
     * <p>Token-bearing verbs ({@code place confirm}, {@code undo execute}, {@code recover execute},
     * {@code export create}, {@code retention execute}) always carry the single-use token as their
     * final argument, on both the default surface and the explicit {@code v2} root. The token is a
     * UUID and so is indistinguishable by shape from the (non-secret) placement/plan identifiers, so
     * it is located by grammar and replaced with {@value #REDACTED}.</p>
     */
    static String redactedCommand(String label, String[] args) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(args, "args");
        String[] display = args.clone();
        int index = tokenArgumentIndex(display);
        if (index >= 0) {
            display[index] = REDACTED;
        }
        return display.length == 0
                ? "/" + label
                : "/" + label + " " + String.join(" ", display);
    }

    /**
     * Index of the confirmation-token argument in {@code args}, or {@code -1} when the command is not
     * a token-bearing verb. Tolerates an optional leading explicit {@code v2} root token.
     */
    private static int tokenArgumentIndex(String[] args) {
        int base = args.length > 0 && args[0].equalsIgnoreCase("v2") ? 1 : 0;
        if (args.length < base + 3) {
            // Need at least verb, operation, and one trailing argument to carry a token.
            return -1;
        }
        String verb = args[base].toLowerCase(Locale.ROOT);
        String operation = args[base + 1].toLowerCase(Locale.ROOT);
        boolean tokenBearing =
                (verb.equals("place") && operation.equals("confirm"))
                        || (verb.equals("undo") && operation.equals("execute"))
                        || (verb.equals("recover") && operation.equals("execute"))
                        || (verb.equals("export") && operation.equals("create"))
                        || (verb.equals("retention") && operation.equals("execute"));
        return tokenBearing ? args.length - 1 : -1;
    }
}
