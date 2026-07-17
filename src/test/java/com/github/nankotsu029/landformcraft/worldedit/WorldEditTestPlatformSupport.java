package com.github.nankotsu029.landformcraft.worldedit;

import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.Preference;
import com.sk89q.worldedit.util.io.ResourceLoader;
import com.sk89q.worldedit.util.io.file.ArchiveUnpacker;
import com.sk89q.worldedit.util.translation.TranslationManager;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BiomeRegistry;
import com.sk89q.worldedit.world.registry.BlockCategoryRegistry;
import com.sk89q.worldedit.world.registry.BlockRegistry;
import com.sk89q.worldedit.world.registry.BundledBlockRegistry;
import com.sk89q.worldedit.world.registry.BundledItemRegistry;
import com.sk89q.worldedit.world.registry.EntityRegistry;
import com.sk89q.worldedit.world.registry.ItemCategoryRegistry;
import com.sk89q.worldedit.world.registry.ItemRegistry;
import com.sk89q.worldedit.world.registry.NullBiomeRegistry;
import com.sk89q.worldedit.world.registry.NullBlockCategoryRegistry;
import com.sk89q.worldedit.world.registry.NullEntityRegistry;
import com.sk89q.worldedit.world.registry.NullItemCategoryRegistry;
import com.sk89q.worldedit.world.registry.Registries;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** One idempotent WorldEdit 7.3.19 test platform shared by v1 and v2 offline read-back tests. */
public final class WorldEditTestPlatformSupport {
    private static boolean registered;

    private WorldEditTestPlatformSupport() {
    }

    public static synchronized void ensureRegistered() throws IOException {
        if (registered) return;
        var registeredBlockTypes = new java.util.HashSet<String>();
        for (String state : MinecraftBlockPalette.states().keySet()) {
            String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
            if (registeredBlockTypes.add(id)) BlockType.REGISTRY.register(id, new BlockType(id));
        }
        var manager = WorldEdit.getInstance().getPlatformManager();
        Registries registries = bundledRegistries();
        Path translationRoot = Path.of("build", "tmp", "worldedit-translation-test");
        Files.createDirectories(translationRoot);
        ResourceLoader resourceLoader = name -> translationRoot.resolve(name).normalize();
        var translationManager = new TranslationManager(new ArchiveUnpacker(translationRoot), resourceLoader);
        LocalConfiguration configuration = new LocalConfiguration() {
            @Override
            public void load() {
                // Test platform has no external configuration.
            }
        };
        Platform platform = (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(), new Class<?>[]{Platform.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getDataVersion" -> 4671;
                    case "getCapabilities" -> Map.of(
                            Capability.WORLD_EDITING, Preference.NORMAL,
                            Capability.GAME_HOOKS, Preference.NORMAL,
                            Capability.CONFIGURATION, Preference.NORMAL);
                    case "getRegistries" -> registries;
                    case "getResourceLoader" -> resourceLoader;
                    case "getTranslationManager" -> translationManager;
                    case "getConfiguration" -> configuration;
                    case "getVersion", "getPlatformName", "getPlatformVersion", "id" -> "landformcraft-test";
                    case "getWorlds" -> List.of();
                    case "getSupportedSideEffects" -> java.util.Set.of();
                    case "isValidMobType" -> false;
                    case "schedule" -> -1;
                    case "getTickCount" -> 0L;
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "LandformCraft WorldEdit test platform";
                    default -> null;
                });
        manager.register(platform);
        manager.handlePlatformsRegistered(new PlatformsRegisteredEvent());
        manager.handleNewPlatformReady(new PlatformReadyEvent(platform));
        registered = true;
    }

    private static Registries bundledRegistries() {
        return new Registries() {
            @Override public BlockRegistry getBlockRegistry() { return new BundledBlockRegistry(); }
            @Override public ItemRegistry getItemRegistry() { return new BundledItemRegistry(); }
            @Override public EntityRegistry getEntityRegistry() { return new NullEntityRegistry(); }
            @Override public BiomeRegistry getBiomeRegistry() { return new NullBiomeRegistry(); }
            @Override public BlockCategoryRegistry getBlockCategoryRegistry() {
                return new NullBlockCategoryRegistry();
            }
            @Override public ItemCategoryRegistry getItemCategoryRegistry() {
                return new NullItemCategoryRegistry();
            }
        };
    }
}
