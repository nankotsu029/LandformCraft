package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.model.SelectionBounds;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Synchronous WorldEdit selection boundary; invoke it only through the Paper scheduler. */
public final class WorldEditSelectionAccess {
    public SelectionBounds selection(Player player) {
        Plugin plugin = compatiblePlugin();
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("WorldEdit or FAWE is not enabled");
        }
        try {
            var actor = BukkitAdapter.adapt(player);
            Region region = WorldEdit.getInstance().getSessionManager().get(actor)
                    .getSelection(BukkitAdapter.adapt(player.getWorld()));
            return new SelectionBounds(
                    player.getWorld().getName(),
                    region.getMinimumPoint().x(), region.getMinimumPoint().y(), region.getMinimumPoint().z(),
                    region.getMaximumPoint().x(), region.getMaximumPoint().y(), region.getMaximumPoint().z()
            );
        } catch (IncompleteRegionException exception) {
            throw new IncompleteSelectionException(exception);
        }
    }

    private static Plugin compatiblePlugin() {
        Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
        return worldEdit != null ? worldEdit : Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
    }
}
