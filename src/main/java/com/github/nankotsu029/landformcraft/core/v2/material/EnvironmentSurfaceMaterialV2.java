package com.github.nankotsu029.landformcraft.core.v2.material;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V2-19-10 environment material of the exposed surface ({@code environment-surface-material-v1}).
 *
 * <p>This is the connection the 2026-07-23 audit (§2.4) found missing: the sealed semantic material
 * profile and the sealed Minecraft palette plan now decide a block. For every release-local cell the
 * resolved {@link MaterialProfilePlanV2.SemanticMaterialClass} is read, and</p>
 *
 * <ul>
 *   <li>a <em>snow</em> or <em>wet</em> variant — the two {@code CONDITIONAL_OVERRIDE} rules of the
 *       frozen V2-4-07 rule table — resolves through {@link MinecraftPalettePlanV2} at the
 *       {@code SURFACE} aspect and overrides the exposed natural surface;</li>
 *   <li>an <em>exposed</em> variant declares no override, so the surface role's base assignment
 *       stands. The base assignment is not re-derived from lithology here: the coastal roles carry
 *       shape-dependent meaning (beach sand, cape rock) that a per-cell lithology sample cannot
 *       express, and overwriting them would turn a beach into whatever the host rock happens to be.</li>
 * </ul>
 *
 * <p>Every emitted state passes {@link EnvironmentBlockStateCatalogV2} and the palette plan's own
 * closed mapping table, so no arbitrary block state can reach a tile. The per-cell override is held
 * as one byte per cell (index into at most six distinct states), which keeps the working set inside
 * the existing MEDIUM budget without a dense string grid.</p>
 */
public final class EnvironmentSurfaceMaterialV2 implements SurfaceMaterializationV2.NaturalSurfaceMaterialV2 {
    public static final String CONTRACT_VERSION = "environment-surface-material-v1";

    private final int width;
    private final int length;
    private final List<String> states;
    private final byte[] cells;
    private final long overriddenCells;

    private EnvironmentSurfaceMaterialV2(
            int width,
            int length,
            List<String> states,
            byte[] cells,
            long overriddenCells
    ) {
        this.width = width;
        this.length = length;
        this.states = List.copyOf(states);
        this.cells = cells;
        this.overriddenCells = overriddenCells;
    }

    /** Per-cell resolved semantic material class code, in the sealed material-profile catalog. */
    @FunctionalInterface
    public interface MaterialClassSourceV2 {
        int classCodeAt(int globalX, int globalZ);
    }

    /**
     * Resolves the whole release-local override field once, before any tile is written, so every
     * tile of the published Release sees the same frozen material decision.
     */
    public static EnvironmentSurfaceMaterialV2 resolve(
            MinecraftPalettePlanV2 palette,
            MaterialProfilePlanV2 materialProfile,
            int width,
            int length,
            MaterialClassSourceV2 classCodes,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(materialProfile, "materialProfile");
        Objects.requireNonNull(classCodes, "classCodes");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        palette.requireMaterialProfilePlan(materialProfile);
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("environment surface material bounds must be positive");
        }

        Map<Integer, Byte> indexByClassCode = new LinkedHashMap<>();
        List<String> states = new ArrayList<>();
        for (MaterialProfilePlanV2.SemanticMaterialClass kind :
                MaterialProfilePlanV2.SemanticMaterialClass.values()) {
            if (!kind.wetVariant() && !kind.snowVariant()) {
                continue;
            }
            String state = EnvironmentBlockStateCatalogV2.requireKnown(palette.catalog()
                    .require(kind, MaterialProfilePlanV2.SurfaceAspect.SURFACE)
                    .blockState());
            states.add(state);
            indexByClassCode.put(kind.compactCode(), (byte) states.size());
        }

        byte[] cells = new byte[Math.multiplyExact(width, length)];
        long overridden = 0;
        for (int z = 0; z < length; z++) {
            cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int code = classCodes.classCodeAt(x, z);
                MaterialProfilePlanV2.SemanticMaterialClass kind = classFor(code);
                Byte index = indexByClassCode.get(kind.compactCode());
                if (index != null) {
                    cells[z * width + x] = index;
                    overridden++;
                }
            }
        }
        return new EnvironmentSurfaceMaterialV2(width, length, states, cells, overridden);
    }

    @Override
    public String blockStateAt(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside the environment surface material");
        }
        int index = cells[globalZ * width + globalX];
        return index == 0 ? null : states.get(index - 1);
    }

    /** Cells whose exposed surface the environment material overrides. Evidence, not a gate. */
    public long overriddenCells() {
        return overriddenCells;
    }

    /** The override state of each overriding semantic class, in sealed catalog order. */
    public List<String> overrideStates() {
        return states;
    }

    /** Estimated resident bytes of the frozen override field. */
    public static long estimatedResidentBytes(int width, int length) {
        return Math.multiplyExact((long) width, (long) length);
    }

    private static MaterialProfilePlanV2.SemanticMaterialClass classFor(int classCode) {
        for (MaterialProfilePlanV2.SemanticMaterialClass kind :
                MaterialProfilePlanV2.SemanticMaterialClass.values()) {
            if (kind.compactCode() == classCode) {
                return kind;
            }
        }
        throw new IllegalArgumentException(
                "resolved semantic material class code is outside the sealed catalog: " + classCode);
    }
}
