package com.github.nankotsu029.landformcraft.model.v2.minecraft;

import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-4-08 Minecraft palette adapter contract. It binds a sealed semantic material profile
 * to a closed, Minecraft 1.21.11 block-state mapping table. No Paper world mutation, biome rewrite,
 * or v1 palette ID is represented here.
 */
public record MinecraftPalettePlanV2(
        int planVersion,
        String paletteContractVersion,
        MaterialProfileBinding materialProfileBinding,
        Compatibility compatibility,
        Catalog catalog,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PALETTE_CONTRACT_VERSION = "minecraft-palette-contract-v1";
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final int DATA_VERSION = 4671;
    public static final String RESOLVER_VERSION = "minecraft-palette-resolver-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;
    public static final int MAPPING_COUNT =
            MaterialProfilePlanV2.SemanticMaterialClass.values().length
                    * MaterialProfilePlanV2.SurfaceAspect.values().length;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern IDENTIFIER = Pattern.compile("minecraft:[a-z0-9_./-]+");
    private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Pattern PROPERTY_VALUE = Pattern.compile("[a-z0-9_-]+");

    public MinecraftPalettePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("minecraft-palette planVersion must be 1");
        }
        paletteContractVersion = nonBlank(paletteContractVersion, "paletteContractVersion", 64);
        if (!PALETTE_CONTRACT_VERSION.equals(paletteContractVersion)) {
            throw new IllegalArgumentException("unknown minecraft-palette contract version");
        }
        Objects.requireNonNull(materialProfileBinding, "materialProfileBinding");
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateBudget(budget, catalog);
    }

    public MinecraftPalettePlanV2 withCanonicalChecksum(String checksum) {
        return new MinecraftPalettePlanV2(
                planVersion, paletteContractVersion, materialProfileBinding, compatibility,
                catalog, budget, checksum);
    }

    /** Fails closed unless this plan exactly binds the frozen semantic material-profile checksum. */
    public void requireMaterialProfilePlan(MaterialProfilePlanV2 materialProfilePlan) {
        Objects.requireNonNull(materialProfilePlan, "materialProfilePlan");
        if (!materialProfileBinding.sourceMaterialProfilePlanChecksum()
                .equals(materialProfilePlan.canonicalChecksum())
                || !materialProfileBinding.sourceCatalogId().equals(materialProfilePlan.catalog().catalogId())
                || materialProfileBinding.sourceCatalogVersion() != materialProfilePlan.catalog().catalogVersion()) {
            throw new IllegalArgumentException("minecraft-palette material-profile binding mismatch");
        }
    }

    public record MaterialProfileBinding(
            int bindingVersion,
            String sourceMaterialProfilePlanChecksum,
            String sourceCatalogId,
            int sourceCatalogVersion,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "minecraft-palette-material-binding-v1";

        public MaterialProfileBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown minecraft-palette material binding");
            }
            sourceMaterialProfilePlanChecksum = checksum(
                    sourceMaterialProfilePlanChecksum, "sourceMaterialProfilePlanChecksum");
            sourceCatalogId = qualified(sourceCatalogId, "sourceCatalogId");
            if (!MaterialProfilePlanV2.Catalog.ID.equals(sourceCatalogId)
                    || sourceCatalogVersion != MaterialProfilePlanV2.Catalog.VERSION) {
                throw new IllegalArgumentException("minecraft-palette material catalog binding is unsupported");
            }
        }
    }

    public record Compatibility(
            String minecraftVersion,
            int dataVersion,
            String resolverVersion,
            String compatibilityContractVersion
    ) {
        public static final String CONTRACT_VERSION = "minecraft-palette-compatibility-v1";

        public Compatibility {
            minecraftVersion = nonBlank(minecraftVersion, "minecraftVersion", 32);
            resolverVersion = nonBlank(resolverVersion, "resolverVersion", 64);
            compatibilityContractVersion = nonBlank(
                    compatibilityContractVersion, "compatibilityContractVersion", 64);
            if (!MINECRAFT_VERSION.equals(minecraftVersion)
                    || dataVersion != DATA_VERSION
                    || !RESOLVER_VERSION.equals(resolverVersion)
                    || !CONTRACT_VERSION.equals(compatibilityContractVersion)) {
                throw new IllegalArgumentException("unknown or unsupported minecraft-palette compatibility");
            }
        }

        public static Compatibility standard() {
            return new Compatibility(
                    MINECRAFT_VERSION, DATA_VERSION, RESOLVER_VERSION, CONTRACT_VERSION);
        }
    }

    public record Catalog(
            int catalogVersion,
            String catalogId,
            String catalogContractVersion,
            List<Mapping> mappings,
            CatalogBudget budget
    ) {
        public static final int VERSION = 1;
        public static final String ID = "landformcraft.builtin-minecraft-palette";
        public static final String CONTRACT_VERSION = "builtin-minecraft-palette-catalog-v1";

        public Catalog {
            if (catalogVersion != VERSION) {
                throw new IllegalArgumentException("minecraft-palette catalogVersion must be 1");
            }
            if (!ID.equals(catalogId)) {
                throw new IllegalArgumentException("unknown minecraft-palette catalog ID");
            }
            catalogContractVersion = nonBlank(catalogContractVersion, "catalogContractVersion", 64);
            if (!CONTRACT_VERSION.equals(catalogContractVersion)) {
                throw new IllegalArgumentException("unknown minecraft-palette catalog contract version");
            }
            mappings = immutable(mappings, "mappings", MAPPING_COUNT).stream()
                    .sorted(Comparator
                            .comparingInt(Mapping::classCode)
                            .thenComparing(mapping -> mapping.aspect().name()))
                    .toList();
            Objects.requireNonNull(budget, "budget");
            validateCatalog(mappings, budget);
        }

        public Mapping require(
                MaterialProfilePlanV2.SemanticMaterialClass kind,
                MaterialProfilePlanV2.SurfaceAspect aspect
        ) {
            return mappings.stream()
                    .filter(mapping -> mapping.kind() == kind && mapping.aspect() == aspect)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown minecraft-palette mapping: " + kind + "/" + aspect));
        }

        public Mapping requireByCode(int classCode, MaterialProfilePlanV2.SurfaceAspect aspect) {
            return mappings.stream()
                    .filter(mapping -> mapping.classCode() == classCode && mapping.aspect() == aspect)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown minecraft-palette compact code: " + classCode + "/" + aspect));
        }

        private static void validateCatalog(List<Mapping> mappings, CatalogBudget budget) {
            if (mappings.size() != MAPPING_COUNT || mappings.size() > budget.maximumMappings()) {
                throw new IllegalArgumentException("minecraft-palette catalog mapping count is invalid");
            }
            Set<String> keys = new HashSet<>();
            EnumSet<MaterialProfilePlanV2.SemanticMaterialClass> kinds =
                    EnumSet.noneOf(MaterialProfilePlanV2.SemanticMaterialClass.class);
            for (Mapping mapping : mappings) {
                String key = mapping.kind().name() + "/" + mapping.aspect().name();
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("duplicate minecraft-palette mapping");
                }
                kinds.add(mapping.kind());
            }
            if (!kinds.equals(EnumSet.allOf(MaterialProfilePlanV2.SemanticMaterialClass.class))) {
                throw new IllegalArgumentException("minecraft-palette catalog is missing a semantic class");
            }
            for (MaterialProfilePlanV2.SemanticMaterialClass kind :
                    MaterialProfilePlanV2.SemanticMaterialClass.values()) {
                for (MaterialProfilePlanV2.SurfaceAspect aspect :
                        MaterialProfilePlanV2.SurfaceAspect.values()) {
                    boolean present = mappings.stream()
                            .anyMatch(mapping -> mapping.kind() == kind && mapping.aspect() == aspect);
                    if (!present) {
                        throw new IllegalArgumentException(
                                "minecraft-palette catalog is missing mapping " + kind + "/" + aspect);
                    }
                }
            }
        }
    }

    public record Mapping(
            MaterialProfilePlanV2.SemanticMaterialClass kind,
            String classId,
            int classCode,
            MaterialProfilePlanV2.SurfaceAspect aspect,
            String blockState
    ) {
        public Mapping {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(aspect, "aspect");
            classId = qualified(classId, "classId");
            if (!classId.equals(kind.classId()) || classCode != kind.compactCode()) {
                throw new IllegalArgumentException("minecraft-palette mapping is not the fixed built-in definition");
            }
            blockState = requireCanonicalBlockState(blockState);
        }
    }

    public record CatalogBudget(
            String budgetVersion,
            int maximumMappings,
            long maximumCanonicalBytes,
            int maximumDistinctBlockStates
    ) {
        public static final String VERSION = "minecraft-palette-catalog-budget-v1";

        public CatalogBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumMappings != MAPPING_COUNT
                    || maximumCanonicalBytes < 2_048L || maximumCanonicalBytes > 32L * 1024L
                    || maximumDistinctBlockStates < 1 || maximumDistinctBlockStates > 256) {
                throw new IllegalArgumentException("minecraft-palette catalog budget is invalid");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int mappingCount,
            int maximumPaletteSize,
            long maximumPaletteRetainedBytes,
            long maximumCanonicalBytes,
            long estimatedRetainedBytes
    ) {
        public static final String VERSION = "minecraft-palette-budget-v1";
        public static final int MAXIMUM_PALETTE_SIZE = 16_384;
        public static final long MAXIMUM_PALETTE_RETAINED_BYTES = 16L * 1024L * 1024L;

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || mappingCount != MAPPING_COUNT
                    || maximumPaletteSize < 128 || maximumPaletteSize > MAXIMUM_PALETTE_SIZE
                    || maximumPaletteRetainedBytes < 1 || maximumPaletteRetainedBytes > MAXIMUM_PALETTE_RETAINED_BYTES
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 1L * 1024L * 1024L) {
                throw new IllegalArgumentException("minecraft-palette resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateBudget(ResourceBudget budget, Catalog catalog) {
        long distinct = catalog.mappings().stream().map(Mapping::blockState).distinct().count();
        if (distinct > catalog.budget().maximumDistinctBlockStates()) {
            throw new IllegalArgumentException("minecraft-palette exceeds its distinct block-state budget");
        }
        if (budget.mappingCount() != catalog.mappings().size()) {
            throw new IllegalArgumentException("minecraft-palette resource budget mapping count mismatch");
        }
    }

    static String requireCanonicalBlockState(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || value.indexOf('{') >= 0 || value.indexOf('}') >= 0) {
            throw new IllegalArgumentException("minecraft-palette block state is blank, oversized, or contains NBT");
        }
        int propertiesStart = value.indexOf('[');
        String identifier = propertiesStart < 0 ? value : value.substring(0, propertiesStart);
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("minecraft-palette block state has a non-canonical identifier");
        }
        if (propertiesStart >= 0) {
            if (!value.endsWith("]") || propertiesStart == value.length() - 2
                    || value.indexOf('[', propertiesStart + 1) >= 0) {
                throw new IllegalArgumentException("minecraft-palette block state has malformed properties");
            }
            Set<String> names = new HashSet<>();
            String previous = null;
            String body = value.substring(propertiesStart + 1, value.length() - 1);
            for (String property : body.split(",", -1)) {
                int equals = property.indexOf('=');
                if (equals < 1 || equals != property.lastIndexOf('=') || equals == property.length() - 1) {
                    throw new IllegalArgumentException("minecraft-palette block state has malformed properties");
                }
                String name = property.substring(0, equals);
                String propertyValue = property.substring(equals + 1);
                if (!PROPERTY_NAME.matcher(name).matches() || !PROPERTY_VALUE.matcher(propertyValue).matches()
                        || !names.add(name) || (previous != null && previous.compareTo(name) >= 0)) {
                    throw new IllegalArgumentException("minecraft-palette block-state properties are not canonical");
                }
                previous = name;
            }
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new IllegalArgumentException("minecraft-palette block state exceeds the UTF-8 byte budget");
        }
        return value;
    }

    private static <T> List<T> immutable(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid or exceeds " + maximum);
        }
        return List.copyOf(values);
    }

    private static String qualified(String value, String name) {
        value = nonBlank(value, name, 128);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase qualified ID");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be non-blank and bounded");
        }
        return value;
    }
}
