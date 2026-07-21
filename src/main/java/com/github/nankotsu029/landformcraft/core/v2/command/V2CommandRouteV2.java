package com.github.nankotsu029.landformcraft.core.v2.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Outcome of routing one {@code v2} invocation (V2-12-03).
 *
 * <p>Every route — accepted or rejected — carries a correlation ID so a Paper message, a CLI line,
 * and an audit record can be tied together without exposing paths or tokens.</p>
 */
public record V2CommandRouteV2(
        String correlationId,
        Optional<V2CommandVerbV2> verb,
        List<String> arguments,
        Optional<V2CommandErrorCodeV2> errorCode,
        String message
) {
    public V2CommandRouteV2 {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(verb, "verb");
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(message, "message");
        arguments = List.copyOf(arguments);
        if (verb.isPresent() == errorCode.isPresent()) {
            throw new IllegalArgumentException("a route is either accepted with a verb or rejected with a code");
        }
    }

    public boolean accepted() {
        return verb.isPresent();
    }

    /** The routed verb; only call after {@link #accepted()}. */
    public V2CommandVerbV2 requireVerb() {
        return verb.orElseThrow(() -> new IllegalStateException("route was rejected: " + message));
    }

    static V2CommandRouteV2 accepted(V2CommandVerbV2 verb, List<String> arguments) {
        return new V2CommandRouteV2(newCorrelationId(), Optional.of(verb), arguments,
                Optional.empty(), verb.usage());
    }

    static V2CommandRouteV2 rejected(V2CommandErrorCodeV2 code, String message) {
        return new V2CommandRouteV2(newCorrelationId(), Optional.empty(), List.of(),
                Optional.of(code), message);
    }

    private static String newCorrelationId() {
        return "v2-" + UUID.randomUUID();
    }
}
