package com.github.nankotsu029.landformcraft.core.v2.material;

import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V2-19-10 profile-driven block vocabulary of the 2.5D surface column
 * ({@code surface-material-profile-v1}).
 *
 * <p>The 2026-07-23 cross-cutting audit (§2.4) recorded that the canonical surface resolver picked
 * its blocks from eleven block-state literals inlined in the resolver body, so no layer declared
 * what the surface column may contain and the sealed environment material／palette plans could not
 * reach a block. This profile is that declaration: a closed role catalog, exactly one block state
 * per role, and every state checked against the {@link EnvironmentBlockStateCatalogV2} allowlist. A
 * caller cannot introduce an arbitrary block state, an NBT payload, or a state outside the closed
 * export catalog, and nothing here executes an external script or expression.</p>
 *
 * <p>{@link #builtIn()} is the frozen V2-2 coastal table and reproduces the pre-V2-19-10 resolver
 * byte for byte, so the {@code surface-2_5d} and {@code hydrology-plan} routes keep their terrain
 * field／tile／block semantic checksums. The environment route contributes the material profile's
 * two conditional overrides on top of it — see {@link SurfaceMaterializationV2}.</p>
 */
public final class SurfaceMaterialProfileV2 {
    public static final String CONTRACT_VERSION = "surface-material-profile-v1";
    public static final String BUILT_IN_PROFILE_ID = "landformcraft.builtin-coastal-surface-v2-2";

    /**
     * Which tier of the surface column a role belongs to. Only {@link LayerV2#NATURAL_SURFACE} is
     * open to the environment material override: bedrock／air／water are structural, a breakwater
     * crest or core is a built structure whose material its own Feature owns, and the subsurface
     * layers are the base substrate assignment of the coastal role.
     */
    public enum LayerV2 { STRUCTURAL, NATURAL_SURFACE, NATURAL_SUBSURFACE, BUILT_STRUCTURE }

    /** The closed role catalog. Adding a role is a contract change, not a caller-supplied value. */
    public enum RoleV2 {
        BEDROCK_FLOOR(LayerV2.STRUCTURAL),
        OPEN_AIR(LayerV2.STRUCTURAL),
        OPEN_WATER(LayerV2.STRUCTURAL),
        VEGETATED_SURFACE(LayerV2.NATURAL_SURFACE),
        SUBSOIL(LayerV2.NATURAL_SUBSURFACE),
        DEEP_SUBSTRATE(LayerV2.NATURAL_SUBSURFACE),
        BEACH_SURFACE(LayerV2.NATURAL_SURFACE),
        BEACH_SUBSURFACE(LayerV2.NATURAL_SUBSURFACE),
        ROCK_SURFACE(LayerV2.NATURAL_SURFACE),
        SEABED_SURFACE(LayerV2.NATURAL_SURFACE),
        SEABED_SUBSURFACE(LayerV2.NATURAL_SUBSURFACE),
        STRUCTURE_CREST(LayerV2.BUILT_STRUCTURE),
        STRUCTURE_CORE(LayerV2.BUILT_STRUCTURE);

        private final LayerV2 layer;

        RoleV2(LayerV2 layer) {
            this.layer = layer;
        }

        public LayerV2 layer() {
            return layer;
        }
    }

    private static final SurfaceMaterialProfileV2 BUILT_IN = builtInProfile();

    private final String profileId;
    private final Map<RoleV2, String> states;
    private final String canonicalChecksum;

    private SurfaceMaterialProfileV2(String profileId, Map<RoleV2, String> states) {
        this.profileId = profileId;
        this.states = new EnumMap<>(states);
        this.canonicalChecksum = checksum(profileId, this.states);
    }

    /**
     * Builds a profile from a complete role table. Every role must be mapped exactly once and every
     * block state must already be in the closed environment export allowlist.
     */
    public static SurfaceMaterialProfileV2 of(String profileId, Map<RoleV2, String> states) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(states, "states");
        if (profileId.isBlank() || profileId.length() > 128) {
            throw new IllegalArgumentException("surface material profileId must be non-blank and bounded");
        }
        Map<RoleV2, String> checked = new EnumMap<>(RoleV2.class);
        for (RoleV2 role : RoleV2.values()) {
            String state = states.get(role);
            if (state == null) {
                throw new IllegalArgumentException("surface material profile is missing role " + role);
            }
            checked.put(role, EnvironmentBlockStateCatalogV2.requireKnown(state));
        }
        return new SurfaceMaterialProfileV2(profileId, checked);
    }

    /** The frozen V2-2 coastal table: identical output to the pre-V2-19-10 inlined resolver. */
    public static SurfaceMaterialProfileV2 builtIn() {
        return BUILT_IN;
    }

    public String profileId() {
        return profileId;
    }

    /** SHA-256 over the ordered role table. Not published; it identifies the table in evidence. */
    public String canonicalChecksum() {
        return canonicalChecksum;
    }

    public String blockState(RoleV2 role) {
        String state = states.get(Objects.requireNonNull(role, "role"));
        if (state == null) {
            throw new IllegalStateException("surface material profile is missing role " + role);
        }
        return state;
    }

    /** The distinct block states this profile may emit, in role order. */
    public List<String> declaredBlockStates() {
        Set<String> ordered = new LinkedHashSet<>();
        for (RoleV2 role : RoleV2.values()) {
            ordered.add(states.get(role));
        }
        return List.copyOf(ordered);
    }

    private static SurfaceMaterialProfileV2 builtInProfile() {
        Map<RoleV2, String> table = new EnumMap<>(RoleV2.class);
        table.put(RoleV2.BEDROCK_FLOOR, "minecraft:bedrock");
        table.put(RoleV2.OPEN_AIR, "minecraft:air");
        table.put(RoleV2.OPEN_WATER, "minecraft:water");
        table.put(RoleV2.VEGETATED_SURFACE, "minecraft:grass_block");
        table.put(RoleV2.SUBSOIL, "minecraft:dirt");
        table.put(RoleV2.DEEP_SUBSTRATE, "minecraft:stone");
        table.put(RoleV2.BEACH_SURFACE, "minecraft:sand");
        table.put(RoleV2.BEACH_SUBSURFACE, "minecraft:sandstone");
        table.put(RoleV2.ROCK_SURFACE, "minecraft:cobblestone");
        table.put(RoleV2.SEABED_SURFACE, "minecraft:gravel");
        table.put(RoleV2.SEABED_SUBSURFACE, "minecraft:stone");
        table.put(RoleV2.STRUCTURE_CREST, "minecraft:stone_bricks");
        table.put(RoleV2.STRUCTURE_CORE, "minecraft:cobblestone");
        return of(BUILT_IN_PROFILE_ID, table);
    }

    private static String checksum(String profileId, Map<RoleV2, String> states) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((CONTRACT_VERSION + '\0' + profileId + '\0').getBytes(StandardCharsets.UTF_8));
            for (RoleV2 role : RoleV2.values()) {
                digest.update((role.name() + '=' + states.get(role) + '\n')
                        .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
