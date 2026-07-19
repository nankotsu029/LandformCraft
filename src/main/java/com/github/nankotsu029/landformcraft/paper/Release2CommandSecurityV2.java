package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Pure sender／actor gate for the explicit operator-only Release 2 command tree. */
final class Release2CommandSecurityV2 {
    private Release2CommandSecurityV2() {
    }

    static boolean isEligibleOperator(CommandSender sender) {
        return sender != null && sender.isOp() && isSupportedIdentity(sender);
    }

    static void requireOperator(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (!isSupportedIdentity(sender)) {
            throw failure(
                    "Release 2 placement commands accept only a Player or CONSOLE/RCON identity.",
                    "sender-identity");
        }
        if (!sender.isOp()) {
            throw failure(
                    "Release 2 placement commands are restricted to server operators.",
                    "operator-gate");
        }
    }

    static PlacementPlanV2.PlacementActorV2 actor(CommandSender sender) {
        requireOperator(sender);
        if (sender instanceof Player player) {
            return PlacementPlanV2.PlacementActorV2.player(player.getUniqueId());
        }
        return PlacementPlanV2.PlacementActorV2.console();
    }

    private static boolean isSupportedIdentity(CommandSender sender) {
        return sender instanceof Player
                || sender instanceof ConsoleCommandSender
                || sender instanceof RemoteConsoleCommandSender;
    }

    private static LandformException failure(String message, String stage) {
        return new LandformException(
                LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                message,
                "release2-command",
                "",
                stage,
                "Run the command manually as an operator Player or from CONSOLE/RCON.");
    }
}
