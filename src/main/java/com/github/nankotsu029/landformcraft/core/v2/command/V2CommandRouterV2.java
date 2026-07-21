package com.github.nankotsu029.landformcraft.core.v2.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shared, Bukkit-free routing for the official v2 command path (ADR 0035 D5, V2-12-03).
 *
 * <p>The CLI and the Paper adapter both call this router, so {@code lfc v2 <verb>} and
 * {@code /lfc v2 <verb>} accept exactly the same grammar, arity, and permission nodes. The
 * explicit {@code v2} form is retained as the stable version-qualified alias of the default
 * command surface. The evaluation-era {@code r2} root was removed in V2-12-06.</p>
 *
 * <p>Routing decides <em>what</em> was asked for. It never checks permissions, touches the
 * filesystem, or runs an application service — the surfaces own those steps.</p>
 */
public final class V2CommandRouterV2 {
    private V2CommandRouterV2() {
    }

    /**
     * Routes one invocation whose first token is {@link V2CommandVerbV2#ROOT}.
     */
    public static V2CommandRouteV2 route(String[] args, V2CommandVerbV2.Surface callerSurface) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(callerSurface, "callerSurface");
        if (args.length == 0) {
            return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_UNKNOWN_VERB,
                    "v2 requires a verb: " + String.join(", ", V2CommandVerbV2.verbs()));
        }
        if (!args[0].equalsIgnoreCase(V2CommandVerbV2.ROOT)) {
            return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_UNKNOWN_VERB,
                    "not a v2 command path: " + args[0]);
        }
        List<String> canonical = List.of(args);
        if (canonical.size() < 2) {
            return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_UNKNOWN_VERB,
                    "v2 requires a verb: " + String.join(", ", V2CommandVerbV2.verbs()));
        }
        String verb = canonical.get(1).toLowerCase(Locale.ROOT);
        List<V2CommandVerbV2> candidates = new ArrayList<>();
        for (V2CommandVerbV2 value : V2CommandVerbV2.values()) {
            if (value.verb().equals(verb)) candidates.add(value);
        }
        if (candidates.isEmpty()) {
            return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_UNKNOWN_VERB,
                    "unknown v2 verb '" + verb + "'; expected one of "
                            + String.join(", ", V2CommandVerbV2.verbs()));
        }
        // A verb may have both a direct form and operation forms — `export` takes its arguments
        // directly but also offers `export plan` / `export create`. Treat token 2 as an operation
        // only when it names one *and* the arity fits that form, so a file argument that happens to
        // be called "plan" still routes to the direct form.
        List<V2CommandVerbV2> direct = candidates.stream()
                .filter(candidate -> candidate.operation() == null)
                .toList();
        String operationToken = canonical.size() >= 3
                ? canonical.get(2).toLowerCase(Locale.ROOT) : null;
        boolean namesAnOperation = operationToken != null && candidates.stream()
                .anyMatch(candidate -> operationToken.equals(candidate.operation())
                        && canonical.size() >= candidate.minimumTokens()
                        && canonical.size() <= candidate.maximumTokens());
        V2CommandVerbV2 selected;
        if (!direct.isEmpty() && !namesAnOperation) {
            selected = direct.getFirst();
        } else {
            if (canonical.size() < 3) {
                return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_UNKNOWN_OPERATION,
                        "v2 " + verb + " requires one of "
                                + String.join(", ", V2CommandVerbV2.operationsOf(verb)));
            }
            String operation = canonical.get(2).toLowerCase(Locale.ROOT);
            List<V2CommandVerbV2> matches = candidates.stream()
                    .filter(candidate -> operation.equals(candidate.operation()))
                    .toList();
            // One operation token may have surface-specific forms with different arities — for
            // example `request prompt`, which captures a chat message on Paper but takes the text
            // inline on the CLI. Narrow by the caller's surface, then by arity, so each surface sees
            // its own usage text instead of the other one's.
            selected = disambiguate(matches, callerSurface, canonical.size());
            if (selected == null) {
                return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_UNKNOWN_OPERATION,
                        "unknown v2 " + verb + " operation '" + operation + "'; expected one of "
                                + String.join(", ", V2CommandVerbV2.operationsOf(verb)));
            }
        }
        if (canonical.size() < selected.minimumTokens() || canonical.size() > selected.maximumTokens()) {
            return V2CommandRouteV2.rejected(V2CommandErrorCodeV2.V2_USAGE,
                    "usage: " + selected.usage());
        }
        if (!selected.availableOn(callerSurface)) {
            return V2CommandRouteV2.rejected(selected.wrongSurfaceCode(),
                    selected.wrongSurfaceReason());
        }
        return V2CommandRouteV2.accepted(selected, canonical);
    }

    /**
     * Picks one verb from the forms sharing a {@code (verb, operation)} pair. Preference order is the
     * caller's surface, then a fitting arity; either filter is skipped when it would leave nothing,
     * so the caller still receives the wrong-surface or usage rejection rather than "unknown
     * operation".
     */
    private static V2CommandVerbV2 disambiguate(
            List<V2CommandVerbV2> matches,
            V2CommandVerbV2.Surface callerSurface,
            int tokenCount
    ) {
        if (matches.isEmpty()) return null;
        List<V2CommandVerbV2> narrowed = matches;
        if (narrowed.size() > 1) {
            List<V2CommandVerbV2> onSurface = narrowed.stream()
                    .filter(candidate -> candidate.availableOn(callerSurface))
                    .toList();
            if (!onSurface.isEmpty()) narrowed = onSurface;
        }
        if (narrowed.size() > 1) {
            List<V2CommandVerbV2> fitting = narrowed.stream()
                    .filter(candidate -> tokenCount >= candidate.minimumTokens()
                            && tokenCount <= candidate.maximumTokens())
                    .toList();
            if (!fitting.isEmpty()) narrowed = fitting;
        }
        return narrowed.getFirst();
    }

    /**
     * Tab-completion for the {@code v2} root and its verb/operation tokens. Only forms the caller is
     * permitted to run and that exist on the caller's surface are offered; argument-level
     * suggestions (worlds, placement IDs, release directories) stay with the surface adapter.
     */
    public static List<String> complete(
            String[] args,
            V2CommandVerbV2.Surface callerSurface,
            Predicate<V2CommandVerbV2> permitted
    ) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(callerSurface, "callerSurface");
        Objects.requireNonNull(permitted, "permitted");
        if (args.length == 0) return List.of();
        if (!args[0].equalsIgnoreCase(V2CommandVerbV2.ROOT)) return List.of();
        List<V2CommandVerbV2> available = new ArrayList<>();
        for (V2CommandVerbV2 value : V2CommandVerbV2.values()) {
            if (value.availableOn(callerSurface) && permitted.test(value)) available.add(value);
        }
        if (args.length == 2) {
            return available.stream()
                    .map(V2CommandVerbV2::verb)
                    .filter(prefixFilter(args, 1))
                    .distinct()
                    .sorted()
                    .toList();
        }
        if (args.length == 3) {
            String verb = args[1].toLowerCase(Locale.ROOT);
            return available.stream()
                    .filter(value -> value.verb().equals(verb) && value.operation() != null)
                    .map(V2CommandVerbV2::operation)
                    .filter(prefixFilter(args, 2))
                    .distinct()
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private static Predicate<String> prefixFilter(String[] args, int index) {
        String prefix = args.length > index ? args[index].toLowerCase(Locale.ROOT) : "";
        return value -> value.startsWith(prefix);
    }
}
