package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.WorldAccessPolicy;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Release2CommandSecurityV2Test {

    @Test
    void operatorPlayerConsoleAndRconMapToStableActors() {
        UUID playerId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Player player = sender(Player.class, true, playerId);
        ConsoleCommandSender console = sender(ConsoleCommandSender.class, true, null);
        RemoteConsoleCommandSender rcon = sender(RemoteConsoleCommandSender.class, true, null);

        assertTrue(Release2CommandSecurityV2.isEligibleOperator(player));
        assertEquals(PlacementPlanV2.PlacementActorV2.player(playerId),
                Release2CommandSecurityV2.actor(player));
        assertEquals(PlacementPlanV2.PlacementActorV2.console(),
                Release2CommandSecurityV2.actor(console));
        assertEquals(PlacementPlanV2.PlacementActorV2.console(),
                Release2CommandSecurityV2.actor(rcon));
    }

    @Test
    void nonOperatorAndCommandBlockAreRejectedBeforeActorBinding() {
        Player nonOperator = sender(Player.class, false, UUID.randomUUID());
        BlockCommandSender commandBlock = sender(BlockCommandSender.class, true, null);

        LandformException nonOperatorFailure = assertThrows(
                LandformException.class,
                () -> Release2CommandSecurityV2.actor(nonOperator));
        assertEquals(LandformErrorCode.CONFIRM_ACTOR_MISMATCH, nonOperatorFailure.code());
        assertEquals("operator-gate", nonOperatorFailure.stage());
        assertFalse(Release2CommandSecurityV2.isEligibleOperator(nonOperator));

        LandformException commandBlockFailure = assertThrows(
                LandformException.class,
                () -> Release2CommandSecurityV2.actor(commandBlock));
        assertEquals(LandformErrorCode.CONFIRM_ACTOR_MISMATCH, commandBlockFailure.code());
        assertEquals("sender-identity", commandBlockFailure.stage());
        assertFalse(Release2CommandSecurityV2.isEligibleOperator(commandBlock));
    }

    @Test
    void worldPolicyRunsBeforeLookupAndMissingWorldIsAStableDomainFailure() {
        WorldAccessPolicy policy = new WorldAccessPolicy(List.of("allowed"), List.of("denied"));
        AtomicBoolean deniedLookup = new AtomicBoolean();

        LandformException denied = assertThrows(LandformException.class, () ->
                PaperRelease2PlacementServiceV2.requireAllowedWorld(
                        policy, "denied", ignored -> {
                            deniedLookup.set(true);
                            return world("denied");
                        }));
        assertEquals(LandformErrorCode.CONFIG_INVALID, denied.code());
        assertEquals("world-policy", denied.stage());
        assertFalse(deniedLookup.get());

        LandformException missing = assertThrows(LandformException.class, () ->
                PaperRelease2PlacementServiceV2.requireAllowedWorld(
                        policy, "allowed", ignored -> null));
        assertEquals(LandformErrorCode.NOT_FOUND, missing.code());
        assertEquals("world-lookup", missing.stage());

        World expected = world("allowed");
        assertSame(expected, PaperRelease2PlacementServiceV2.requireAllowedWorld(
                policy, "allowed", ignored -> expected));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CommandSender> T sender(Class<T> type, boolean operator, UUID playerId) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isOp" -> operator;
                    case "getUniqueId" -> playerId;
                    case "toString" -> type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return (char) 0;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        throw new AssertionError("unsupported primitive: " + type);
    }
}
