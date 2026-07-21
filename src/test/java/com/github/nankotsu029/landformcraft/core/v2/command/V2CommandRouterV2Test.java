package com.github.nankotsu029.landformcraft.core.v2.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-12-03 routing contract shared by the CLI and the Paper command adapter. */
class V2CommandRouterV2Test {
    private static final V2CommandVerbV2.Surface PAPER = V2CommandVerbV2.Surface.PAPER;
    private static final V2CommandVerbV2.Surface CLI = V2CommandVerbV2.Surface.CLI;

    @Test
    void routesTheCanonicalPlacementLifecycle() {
        V2CommandRouteV2 route = V2CommandRouterV2.route(
                new String[] {"v2", "place", "plan", "release", "world", "1", "2", "3"}, PAPER);

        assertTrue(route.accepted());
        assertEquals(V2CommandVerbV2.PLACE_PLAN, route.requireVerb());
        assertEquals("landformcraft.v2.plan", route.requireVerb().permission());
        assertTrue(route.correlationId().startsWith("v2-"));
    }

    @Test
    void removedR2AliasIsRejected() {
        V2CommandRouteV2 alias = V2CommandRouterV2.route(
                new String[] {"r2", "undo-execute", "id", "token"}, PAPER);

        assertFalse(alias.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_UNKNOWN_VERB, alias.errorCode().orElseThrow());
    }

    @Test
    void routesTheV2RequestAuthoringVerbs() {
        V2CommandRouteV2 create = V2CommandRouterV2.route(
                new String[] {"v2", "request", "create", "harbor-cove"}, CLI);
        assertTrue(create.accepted());
        assertEquals(V2CommandVerbV2.REQUEST_CREATE, create.requireVerb());
        assertEquals("landformcraft.v2.request.create", create.requireVerb().permission());

        V2CommandRouteV2 bounds = V2CommandRouterV2.route(
                new String[] {"v2", "request", "bounds", "harbor-cove", "64", "64", "0", "120", "54"}, CLI);
        assertTrue(bounds.accepted());
        assertEquals(V2CommandVerbV2.REQUEST_BOUNDS, bounds.requireVerb());
        assertEquals("landformcraft.v2.request.edit", bounds.requireVerb().permission());

        V2CommandRouteV2 list = V2CommandRouterV2.route(new String[] {"v2", "request", "list"}, CLI);
        assertTrue(list.accepted());
        assertEquals(V2CommandVerbV2.REQUEST_LIST, list.requireVerb());
    }

    @Test
    void promptResolvesToTheFormThatBelongsToTheCallerSurface() {
        // Paper captures the operator's next chat message, so the text is not an argument.
        V2CommandRouteV2 paper = V2CommandRouterV2.route(
                new String[] {"v2", "request", "prompt", "harbor-cove"}, PAPER);
        assertTrue(paper.accepted());
        assertEquals(V2CommandVerbV2.REQUEST_PROMPT, paper.requireVerb());

        // The CLI has no chat, so the same operation token takes the prompt inline.
        V2CommandRouteV2 cli = V2CommandRouterV2.route(
                new String[] {"v2", "request", "prompt", "harbor-cove", "a", "sheltered", "cove"}, CLI);
        assertTrue(cli.accepted());
        assertEquals(V2CommandVerbV2.REQUEST_PROMPT_INLINE, cli.requireVerb());
    }

    @Test
    void eachPromptFormRejectsTheOtherSurfacesArity() {
        V2CommandRouteV2 paperWithInlineText = V2CommandRouterV2.route(
                new String[] {"v2", "request", "prompt", "harbor-cove", "text"}, PAPER);
        assertFalse(paperWithInlineText.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_USAGE, paperWithInlineText.errorCode().orElseThrow());
        assertTrue(paperWithInlineText.message().contains("v2 request prompt <request-id>"),
                paperWithInlineText.message());

        V2CommandRouteV2 cliWithoutText = V2CommandRouterV2.route(
                new String[] {"v2", "request", "prompt", "harbor-cove"}, CLI);
        assertFalse(cliWithoutText.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_USAGE, cliWithoutText.errorCode().orElseThrow());
        assertTrue(cliWithoutText.message().contains("<prompt...>"), cliWithoutText.message());
    }

    @Test
    void selectionAuthoringIsPaperOnly() {
        V2CommandRouteV2 route = V2CommandRouterV2.route(
                new String[] {"v2", "request", "selection", "harbor-cove"}, CLI);

        assertFalse(route.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_PAPER_ONLY, route.errorCode().orElseThrow());
    }

    @Test
    void requestOperationsAreListedOnceEvenWhenSurfaceFormsShareAToken() {
        List<String> operations = V2CommandVerbV2.operationsOf("request");

        assertEquals(operations.stream().distinct().toList(), operations);
        assertTrue(operations.containsAll(
                List.of("validate", "info", "create", "bounds", "selection", "prompt", "list")),
                operations.toString());
    }

    @Test
    void completionOffersRequestOperationsPermittedOnTheCallerSurface() {
        List<String> paper = V2CommandRouterV2.complete(
                new String[] {"v2", "request", ""}, PAPER, verb -> true);
        assertTrue(paper.contains("selection"), paper.toString());
        assertTrue(paper.contains("create"), paper.toString());

        List<String> cli = V2CommandRouterV2.complete(
                new String[] {"v2", "request", ""}, CLI, verb -> true);
        assertFalse(cli.contains("selection"), cli.toString());
        assertTrue(cli.contains("prompt"), cli.toString());
    }

    @Test
    void exportKeepsItsDirectFormAlongsideThePlanAndCreateOperations() {
        V2CommandRouteV2 direct = V2CommandRouterV2.route(new String[] {
                "v2", "export", "request.json", "intent.json", "exports", "rel", "water", "54", "46"}, PAPER);
        assertTrue(direct.accepted());
        assertEquals(V2CommandVerbV2.EXPORT, direct.requireVerb());

        V2CommandRouteV2 plan = V2CommandRouterV2.route(new String[] {
                "v2", "export", "plan", "request.json", "intent.json", "exports", "rel",
                "water", "54", "46"}, PAPER);
        assertTrue(plan.accepted());
        assertEquals(V2CommandVerbV2.EXPORT_PLAN, plan.requireVerb());

        V2CommandRouteV2 create = V2CommandRouterV2.route(
                new String[] {"v2", "export", "create", "plan-id", "token"}, PAPER);
        assertTrue(create.accepted());
        assertEquals(V2CommandVerbV2.EXPORT_CREATE, create.requireVerb());
    }

    @Test
    void anArgumentNamedLikeAnOperationStillRoutesToTheDirectExportForm() {
        // A request file literally called "plan" must not be mistaken for `export plan`: the arity
        // of the direct form is what decides.
        V2CommandRouteV2 route = V2CommandRouterV2.route(new String[] {
                "v2", "export", "plan", "intent.json", "exports", "rel", "water", "54", "46"}, PAPER);

        assertTrue(route.accepted());
        assertEquals(V2CommandVerbV2.EXPORT, route.requireVerb());
    }

    @Test
    void theTwoStepExportIsPaperOnly() {
        for (String[] arguments : List.of(
                new String[] {"v2", "export", "plan", "r.json", "i.json", "e", "rel", "water", "54", "46"},
                new String[] {"v2", "export", "create", "plan-id", "token"})) {
            V2CommandRouteV2 route = V2CommandRouterV2.route(arguments, CLI);
            assertFalse(route.accepted());
            assertEquals(V2CommandErrorCodeV2.V2_PAPER_ONLY, route.errorCode().orElseThrow());
        }
    }

    @Test
    void routesTheJobAndCandidateVerbsOnBothSurfaces() {
        for (V2CommandVerbV2.Surface surface : List.of(PAPER, CLI)) {
            V2CommandRouteV2 status = V2CommandRouterV2.route(
                    new String[] {"v2", "job", "status", "job-id"}, surface);
            assertTrue(status.accepted(), surface.name());
            assertEquals(V2CommandVerbV2.JOB_STATUS, status.requireVerb());
            assertEquals("landformcraft.v2.job", status.requireVerb().permission());

            V2CommandRouteV2 cancel = V2CommandRouterV2.route(
                    new String[] {"v2", "job", "cancel", "job-id"}, surface);
            assertTrue(cancel.accepted(), surface.name());
            assertEquals(V2CommandVerbV2.JOB_CANCEL, cancel.requireVerb());

            V2CommandRouteV2 candidates = V2CommandRouterV2.route(
                    new String[] {"v2", "candidate", "list", "harbor-cove-64"}, surface);
            assertTrue(candidates.accepted(), surface.name());
            assertEquals(V2CommandVerbV2.CANDIDATE_LIST, candidates.requireVerb());
            assertEquals("landformcraft.v2.candidate", candidates.requireVerb().permission());
        }
    }

    @Test
    void unknownJobOperationsUseTheStableCode() {
        V2CommandRouteV2 route = V2CommandRouterV2.route(
                new String[] {"v2", "job", "restart", "job-id"}, PAPER);

        assertFalse(route.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_UNKNOWN_OPERATION, route.errorCode().orElseThrow());
    }

    @Test
    void routesTheRetentionVerbsAsPaperOnly() {
        for (String[] arguments : List.of(
                new String[] {"v2", "retention", "plan", "11111111-1111-1111-1111-111111111111"},
                new String[] {"v2", "retention", "execute",
                        "11111111-1111-1111-1111-111111111111", "plan-id", "token"},
                new String[] {"v2", "retention", "status", "11111111-1111-1111-1111-111111111111"})) {
            V2CommandRouteV2 paper = V2CommandRouterV2.route(arguments, PAPER);
            assertTrue(paper.accepted(), String.join(" ", arguments));
            assertEquals("landformcraft.v2.retention", paper.requireVerb().permission());

            V2CommandRouteV2 cli = V2CommandRouterV2.route(arguments, CLI);
            assertFalse(cli.accepted());
            assertEquals(V2CommandErrorCodeV2.V2_PAPER_ONLY, cli.errorCode().orElseThrow());
        }
    }

    @Test
    void routesTheReadOnlyPlacementStateVerbsAsCliOnly() {
        V2CommandRouteV2 journal = V2CommandRouterV2.route(
                new String[] {"v2", "journal-verify", "journal.json"}, CLI);
        assertTrue(journal.accepted());
        assertEquals(V2CommandVerbV2.JOURNAL_VERIFY, journal.requireVerb());

        V2CommandRouteV2 inspect = V2CommandRouterV2.route(
                new String[] {"v2", "recovery", "inspect", "journal", "journal.json"}, CLI);
        assertTrue(inspect.accepted());
        assertEquals(V2CommandVerbV2.RECOVERY_INSPECT, inspect.requireVerb());

        // Both are CLI-only: they read an operator-workstation artifact and never touch a world.
        assertEquals(V2CommandErrorCodeV2.V2_CLI_ONLY, V2CommandRouterV2.route(
                new String[] {"v2", "journal-verify", "journal.json"}, PAPER).errorCode().orElseThrow());
        assertEquals(V2CommandErrorCodeV2.V2_CLI_ONLY, V2CommandRouterV2.route(
                new String[] {"v2", "recovery", "inspect", "journal", "journal.json"}, PAPER)
                .errorCode().orElseThrow());
    }

    @Test
    void theRecoverAndRecoveryVerbsAreDistinctTokens() {
        // `recover` is the Paper placement-recovery lifecycle; `recovery` is the CLI read-only view.
        assertEquals(V2CommandVerbV2.RECOVER_DIAGNOSE, V2CommandRouterV2.route(
                new String[] {"v2", "recover", "diagnose", "id"}, PAPER).requireVerb());
        assertEquals(V2CommandVerbV2.RECOVERY_INSPECT, V2CommandRouterV2.route(
                new String[] {"v2", "recovery", "inspect", "journal", "j.json"}, CLI).requireVerb());
    }
    void worldBoundVerbsAreRejectedOnTheCliWithAStableCode() {
        V2CommandRouteV2 route = V2CommandRouterV2.route(
                new String[] {"v2", "status", "placement-id"}, CLI);

        assertFalse(route.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_PAPER_ONLY, route.errorCode().orElseThrow());
    }

    @Test
    void arityAndUnknownTokensGetStableCodes() {
        assertEquals(V2CommandErrorCodeV2.V2_USAGE, V2CommandRouterV2.route(
                new String[] {"v2", "place", "execute"}, PAPER).errorCode().orElseThrow());
        assertEquals(V2CommandErrorCodeV2.V2_UNKNOWN_VERB, V2CommandRouterV2.route(
                new String[] {"v2", "teleport"}, PAPER).errorCode().orElseThrow());
        assertEquals(V2CommandErrorCodeV2.V2_UNKNOWN_OPERATION, V2CommandRouterV2.route(
                new String[] {"v2", "place", "detonate", "a", "b", "c", "d", "e"}, PAPER)
                .errorCode().orElseThrow());
        assertEquals(V2CommandErrorCodeV2.V2_UNKNOWN_VERB, V2CommandRouterV2.route(
                new String[] {"r2", "detonate"}, PAPER).errorCode().orElseThrow());
        assertEquals(V2CommandErrorCodeV2.V2_UNKNOWN_VERB, V2CommandRouterV2.route(
                new String[] {"apply", "plan"}, PAPER).errorCode().orElseThrow());
    }

    @Test
    void rejectedRoutesStillCarryACorrelationId() {
        V2CommandRouteV2 route = V2CommandRouterV2.route(new String[] {"v2", "teleport"}, PAPER);
        assertTrue(route.correlationId().startsWith("v2-"));
        assertNotEquals(route.correlationId(),
                V2CommandRouterV2.route(new String[] {"v2", "teleport"}, PAPER).correlationId());
    }

    @Test
    void migrationVerbsAreCliOnlyAndRejectedOnPaperWithAStableCode() {
        var accepted = V2CommandRouterV2.route(
                new String[] {"v2", "migrate", "inspect", "intent", "terrain-intent.json"}, CLI);
        assertTrue(accepted.accepted());
        assertEquals(V2CommandVerbV2.MIGRATE_INSPECT, accepted.requireVerb());
        assertEquals("landformcraft.v2.migrate", accepted.requireVerb().permission());

        var rejected = V2CommandRouterV2.route(
                new String[] {"v2", "migrate", "inspect", "intent", "terrain-intent.json"}, PAPER);
        assertFalse(rejected.accepted());
        assertEquals(V2CommandErrorCodeV2.V2_CLI_ONLY, rejected.errorCode().orElseThrow());
    }

    @Test
    void completionRespectsPermissionsAndSurface() {
        List<String> everything = V2CommandRouterV2.complete(
                new String[] {"v2", ""}, PAPER, verb -> true);
        assertTrue(everything.containsAll(List.of("design", "export", "place", "status", "undo")));

        List<String> cliOnly = V2CommandRouterV2.complete(
                new String[] {"v2", ""}, CLI, verb -> true);
        assertFalse(cliOnly.contains("place"));
        assertFalse(cliOnly.contains("status"));
        assertTrue(cliOnly.contains("export"));
        assertTrue(cliOnly.contains("migrate"));
        assertFalse(everything.contains("migrate"), "migrate never appears on the Paper surface");

        List<String> exportOnly = V2CommandRouterV2.complete(
                new String[] {"v2", ""}, PAPER, verb -> verb == V2CommandVerbV2.EXPORT);
        assertEquals(List.of("export"), exportOnly);

        assertEquals(List.of("confirm", "execute", "plan"), V2CommandRouterV2.complete(
                new String[] {"v2", "place", ""}, PAPER, verb -> true));
        assertEquals(List.of("plan"), V2CommandRouterV2.complete(
                new String[] {"v2", "place", "pl"}, PAPER, verb -> true));
        assertTrue(V2CommandRouterV2.complete(
                new String[] {"r2", ""}, PAPER, verb -> true).isEmpty());
        assertTrue(V2CommandRouterV2.complete(
                new String[] {"apply", ""}, PAPER, verb -> true).isEmpty());
    }

}
