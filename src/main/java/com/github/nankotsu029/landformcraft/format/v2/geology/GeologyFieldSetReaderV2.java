package com.github.nankotsu029.landformcraft.format.v2.geology;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldWindow;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.ProvinceLithologyResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict, bounded cross-field reader for one V2-4-01 geology field set. */
public final class GeologyFieldSetReaderV2 implements AutoCloseable {
    private final GeologyPlanV2 plan;
    private final EnumMap<GeologyPlanV2.FieldSemantic, LfcGridReaderV1> readers;

    private GeologyFieldSetReaderV2(
            GeologyPlanV2 plan,
            EnumMap<GeologyPlanV2.FieldSemantic, LfcGridReaderV1> readers
    ) {
        this.plan = plan;
        this.readers = readers;
    }

    public static GeologyFieldSetReaderV2 open(
            Path root,
            GeologyPlanV2 plan,
            List<FieldArtifactDescriptorV2> descriptors,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(token, "token");
        if (descriptors.size() != GeologyPlanV2.MAX_FIELDS) {
            throw new IOException("geology field set must contain exactly four descriptors");
        }
        requireExactFiles(root, descriptors);
        Map<String, GeologyPlanV2.FieldBinding> bindings = new HashMap<>();
        for (GeologyPlanV2.FieldBinding binding : plan.fields()) {
            bindings.put(binding.fieldId(), binding);
        }
        EnumMap<GeologyPlanV2.FieldSemantic, LfcGridReaderV1> opened =
                new EnumMap<>(GeologyPlanV2.FieldSemantic.class);
        try {
            for (FieldArtifactDescriptorV2 descriptor : descriptors) {
                GeologyPlanV2.FieldBinding binding = bindings.get(descriptor.definition().fieldId());
                if (binding == null) {
                    throw new IOException("unknown geology field descriptor: " + descriptor.definition().fieldId());
                }
                GeologyPlanV2.FieldSemantic semantic;
                try {
                    semantic = GeologyPlanV2.FieldSemantic.valueOf(
                            descriptor.definition().semantic().name().substring("GEOLOGY_".length()));
                } catch (IllegalArgumentException | StringIndexOutOfBoundsException exception) {
                    throw new IOException("non-geology semantic in geology field set", exception);
                }
                requireDescriptor(plan, binding, semantic, descriptor);
                LfcGridReaderV1 reader = LfcGridReaderV1.open(
                        root,
                        descriptor,
                        new LfcGridReaderV1.ReadLimits(
                                plan.budget().maximumSingleArtifactBytes(),
                                plan.budget().maximumWorkingBytes()),
                        token);
                if (opened.putIfAbsent(semantic, reader) != null) {
                    reader.close();
                    throw new IOException("duplicate geology field semantic: " + semantic);
                }
            }
            if (!opened.keySet().equals(Set.of(GeologyPlanV2.FieldSemantic.values()))) {
                throw new IOException("geology field set is incomplete");
            }
            return new GeologyFieldSetReaderV2(plan, opened);
        } catch (IOException | RuntimeException exception) {
            IOException closeFailure = closeAll(opened.values());
            if (closeFailure != null) exception.addSuppressed(closeFailure);
            throw exception;
        }
    }

    public Window readWindow(
            int originX,
            int originZ,
            int width,
            int length,
            CancellationToken token
    ) throws IOException {
        if (width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()) {
            throw new IOException("geology window dimensions exceed the declared bound");
        }
        long workingBytes = 0L;
        for (GeologyPlanV2.FieldSemantic semantic : GeologyPlanV2.FieldSemantic.values()) {
            FieldArtifactDescriptorV2.FieldValueType valueType =
                    readers.get(semantic).descriptor().definition().valueType();
            workingBytes = Math.addExact(workingBytes,
                    LfcGridReaderV1.estimateWindowWorkingBytes(width, length, valueType));
        }
        if (workingBytes > plan.budget().maximumWorkingBytes()) {
            throw new IOException("geology window exceeds declared working-memory budget");
        }
        Window result = new Window(
                readers.get(GeologyPlanV2.FieldSemantic.PROVINCE_ID)
                        .readWindow(originX, originZ, width, length, token),
                readers.get(GeologyPlanV2.FieldSemantic.FORMATION_ID)
                        .readWindow(originX, originZ, width, length, token),
                readers.get(GeologyPlanV2.FieldSemantic.HARDNESS)
                        .readWindow(originX, originZ, width, length, token),
                readers.get(GeologyPlanV2.FieldSemantic.PERMEABILITY)
                        .readWindow(originX, originZ, width, length, token));
        validateWindow(result, plan);
        return result;
    }

    public void verifyAll(int maximumWindowSize, CancellationToken token) throws IOException {
        if (maximumWindowSize < 1 || maximumWindowSize > 256) {
            throw new IllegalArgumentException("maximumWindowSize outside 1..256");
        }
        for (int z = 0; z < plan.length(); z += maximumWindowSize) {
            int length = Math.min(maximumWindowSize, plan.length() - z);
            for (int x = 0; x < plan.width(); x += maximumWindowSize) {
                token.throwIfCancellationRequested();
                int width = Math.min(maximumWindowSize, plan.width() - x);
                readWindow(x, z, width, length, token);
            }
        }
    }

    /** Strictly binds this province sidecar to a V2-4-02 catalog without adding a new field artifact. */
    public void verifyLithologyAssignments(
            LithologyPlanV2 lithologyPlan,
            int maximumWindowSize,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        ProvinceLithologyResolverV2 resolver;
        try {
            resolver = new ProvinceLithologyResolverV2(plan, lithologyPlan);
        } catch (IllegalArgumentException exception) {
            throw new IOException("lithology plan does not bind this geology field set", exception);
        }
        if (maximumWindowSize < 1 || maximumWindowSize > plan.budget().maximumWindowSize()) {
            throw new IllegalArgumentException("lithology verification window exceeds declared geology bound");
        }
        for (int z = 0; z < plan.length(); z += maximumWindowSize) {
            int length = Math.min(maximumWindowSize, plan.length() - z);
            for (int x = 0; x < plan.width(); x += maximumWindowSize) {
                token.throwIfCancellationRequested();
                FieldWindow province = readWindow(x, z, Math.min(maximumWindowSize, plan.width() - x), length, token)
                        .province();
                for (int raw : province.toRawArray()) {
                    try {
                        resolver.lithologyCodeForProvinceRaw(raw);
                    } catch (IllegalArgumentException exception) {
                        throw new IOException("unknown province value in lithology-bound field", exception);
                    }
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = closeAll(readers.values());
        if (failure != null) throw failure;
    }

    private static void requireDescriptor(
            GeologyPlanV2 plan,
            GeologyPlanV2.FieldBinding binding,
            GeologyPlanV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2 descriptor
    ) throws IOException {
        FieldArtifactDescriptorV2.Definition definition = descriptor.definition();
        long expectedScale = semantic == GeologyPlanV2.FieldSemantic.PROVINCE_ID
                || semantic == GeologyPlanV2.FieldSemantic.FORMATION_ID ? 1_000_000L : 1_000L;
        boolean matches = binding.semantic() == semantic
                && definition.fieldId().equals(binding.fieldId())
                && definition.semantic().name().equals("GEOLOGY_" + semantic.name())
                && definition.valueType() == FieldArtifactDescriptorV2.FieldValueType.U16
                && definition.width() == plan.width()
                && definition.length() == plan.length()
                && definition.coordinateSpace() == FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ
                && definition.sampling() == FieldArtifactDescriptorV2.Sampling.NEAREST
                && definition.scaleMillionths() == expectedScale
                && definition.offsetMillionths() == 0L
                && definition.hasNoData()
                && definition.noDataRaw() == GeologyPlanV2.NO_DATA_RAW
                && descriptor.provenance().sourceKind() == FieldArtifactDescriptorV2.SourceKind.DERIVED
                && descriptor.provenance().sourceId().equals("derived-source:geology-foundation")
                && descriptor.provenance().sourceChecksum().equals(plan.canonicalChecksum())
                && descriptor.provenance().decoderId().equals("geology-field-rasterizer")
                && descriptor.provenance().decoderVersion().equals("1")
                && descriptor.provenance().transformId().equals(
                        GeologyFoundationModuleV2.GENERATOR_VERSION);
        if (!matches) {
            throw new IOException("geology field descriptor contract mismatch: " + binding.fieldId());
        }
    }

    private static void validateWindow(Window window, GeologyPlanV2 plan) throws IOException {
        Map<Integer, GeologyPlanV2.ProvinceDescriptor> provinces = new HashMap<>();
        for (GeologyPlanV2.ProvinceDescriptor province : plan.provinces()) {
            provinces.put(province.provinceCode(), province);
        }
        for (int z = 0; z < window.province().length(); z++) {
            for (int x = 0; x < window.province().width(); x++) {
                int provinceCode = window.province().rawValueAt(x, z);
                int formationCode = window.formation().rawValueAt(x, z);
                int hardness = window.hardness().rawValueAt(x, z);
                int permeability = window.permeability().rawValueAt(x, z);
                boolean missing = provinceCode == GeologyPlanV2.NO_DATA_RAW;
                if (missing) {
                    if (formationCode != GeologyPlanV2.NO_DATA_RAW
                            || hardness != GeologyPlanV2.NO_DATA_RAW
                            || permeability != GeologyPlanV2.NO_DATA_RAW) {
                        throw new IOException("partial no-data geology cell");
                    }
                    continue;
                }
                GeologyPlanV2.ProvinceDescriptor province = provinces.get(provinceCode);
                if (province == null) {
                    throw new IOException("unknown geology province code: " + provinceCode);
                }
                if (formationCode != province.formationCode()
                        || hardness != province.hardnessRaw()
                        || permeability != province.permeabilityRaw()) {
                    throw new IOException("geology formation/scalar fields disagree with province contract");
                }
            }
        }
    }

    private static void requireExactFiles(
            Path root,
            List<FieldArtifactDescriptorV2> descriptors
    ) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("geology field root is not a non-symbolic directory");
        }
        Set<Path> expected = new HashSet<>();
        for (FieldArtifactDescriptorV2 descriptor : descriptors) {
            expected.add(normalized.resolve(descriptor.relativePath()).normalize());
        }
        Set<Path> actual = new HashSet<>();
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("geology field set contains a symbolic link");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    actual.add(path.toAbsolutePath().normalize());
                }
            }
        }
        if (!actual.equals(expected)) {
            throw new IOException("geology field set contains missing or extra files");
        }
    }

    private static IOException closeAll(Iterable<LfcGridReaderV1> values) {
        IOException failure = null;
        for (LfcGridReaderV1 reader : values) {
            try {
                reader.close();
            } catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        return failure;
    }

    public record Window(
            FieldWindow province,
            FieldWindow formation,
            FieldWindow hardness,
            FieldWindow permeability
    ) {
        public Window {
            Objects.requireNonNull(province, "province");
            Objects.requireNonNull(formation, "formation");
            Objects.requireNonNull(hardness, "hardness");
            Objects.requireNonNull(permeability, "permeability");
            if (province.originX() != formation.originX() || province.originX() != hardness.originX()
                    || province.originX() != permeability.originX()
                    || province.originZ() != formation.originZ() || province.originZ() != hardness.originZ()
                    || province.originZ() != permeability.originZ()
                    || province.width() != formation.width() || province.width() != hardness.width()
                    || province.width() != permeability.width()
                    || province.length() != formation.length() || province.length() != hardness.length()
                    || province.length() != permeability.length()) {
                throw new IllegalArgumentException("geology windows do not share bounds");
            }
        }
    }
}
