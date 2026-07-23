package com.github.nankotsu029.landformcraft.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the audit command line never leaks a one-time confirmation token. */
final class CommandAuditLogV2Test {

    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "22222222-2222-2222-2222-222222222222";

    @Test
    void redactsConfirmToken() {
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"place", "confirm", ID, TOKEN});
        assertEquals("/lfc place confirm " + ID + " ***", line);
    }

    @Test
    void redactsConfirmTokenOnExplicitV2Root() {
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"v2", "place", "confirm", ID, TOKEN});
        assertEquals("/lfc v2 place confirm " + ID + " ***", line);
    }

    @Test
    void redactsUndoExecuteToken() {
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"undo", "execute", ID, TOKEN});
        assertEquals("/lfc undo execute " + ID + " ***", line);
    }

    @Test
    void redactsRecoverExecuteToken() {
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"recover", "execute", "rollback", ID, TOKEN});
        assertEquals("/lfc recover execute rollback " + ID + " ***", line);
    }

    @Test
    void redactsExportCreateToken() {
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"export", "create", ID, TOKEN});
        assertEquals("/lfc export create " + ID + " ***", line);
    }

    @Test
    void redactsRetentionExecuteToken() {
        String planId = UUID.randomUUID().toString();
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"retention", "execute", ID, planId, TOKEN});
        assertEquals("/lfc retention execute " + ID + " " + planId + " ***", line);
    }

    @Test
    void keepsNonTokenCommandsIntact() {
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"place", "execute", ID});
        assertEquals("/lfc place execute " + ID, line);
        assertFalse(line.contains("***"), "no token to redact");
    }

    @Test
    void keepsReadOnlyCommandsIntact() {
        assertEquals("/lfc status " + ID,
                CommandAuditLogV2.redactedCommand("lfc", new String[] {"status", ID}));
        assertEquals("/lfc version",
                CommandAuditLogV2.redactedCommand("lfc", new String[] {"version"}));
        assertEquals("/lfc", CommandAuditLogV2.redactedCommand("lfc", new String[0]));
    }

    @Test
    void doesNotRedactWhenTokenArgumentAbsent() {
        // Missing the trailing token: only the operation is present, so there is nothing to redact.
        String line = CommandAuditLogV2.redactedCommand(
                "lfc", new String[] {"place", "confirm"});
        assertEquals("/lfc place confirm", line);
        assertFalse(line.contains("***"));
    }

    @Test
    void neverEchoesTheTokenValueForAnyTokenBearingVerb() {
        String[][] shapes = {
                {"place", "confirm", ID, TOKEN},
                {"undo", "execute", ID, TOKEN},
                {"recover", "execute", "accept", ID, TOKEN},
                {"export", "create", ID, TOKEN},
                {"retention", "execute", ID, ID, TOKEN},
        };
        for (String[] shape : shapes) {
            String line = CommandAuditLogV2.redactedCommand("lfc", shape);
            assertFalse(line.contains(TOKEN), "token leaked for " + shape[0] + " " + shape[1] + ": " + line);
            assertTrue(line.endsWith("***"), "token position redacted for " + shape[0] + " " + shape[1]);
        }
    }
}
