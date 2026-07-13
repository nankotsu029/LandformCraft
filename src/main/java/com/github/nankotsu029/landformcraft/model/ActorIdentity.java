package com.github.nankotsu029.landformcraft.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** An explicit, portable actor identity. Secrets and display names are intentionally excluded. */
public record ActorIdentity(ActorKind kind, String id) {
    public ActorIdentity {
        Objects.requireNonNull(kind, "kind");
        id = ModelValidation.requireNonBlank(id, "id", 128);
        switch (kind) {
            case PLAYER -> {
                UUID parsed = UUID.fromString(id);
                id = parsed.toString();
            }
            case CONSOLE -> {
                if (!"CONSOLE".equals(id)) {
                    throw new IllegalArgumentException("console actor id must be CONSOLE");
                }
            }
            case SYSTEM -> {
                if (!id.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
                    throw new IllegalArgumentException("system actor id must be an uppercase slug");
                }
            }
        }
    }

    public static ActorIdentity player(UUID playerId) {
        return new ActorIdentity(ActorKind.PLAYER, Objects.requireNonNull(playerId, "playerId").toString());
    }

    public static ActorIdentity console() {
        return new ActorIdentity(ActorKind.CONSOLE, "CONSOLE");
    }

    public static ActorIdentity system(String id) {
        return new ActorIdentity(ActorKind.SYSTEM, Objects.requireNonNull(id, "id").toUpperCase(Locale.ROOT));
    }

    public String canonical() {
        return kind.name() + ":" + id;
    }
}
