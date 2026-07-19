package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-02 ordered CSG contract for VolumePlan. Operations are applied in explicit
 * ordinal order only. Implicit last-write-wins, spatial index, feature generators, and material
 * paint are out of scope.
 */
public record VolumeCsgPlanV2(
        int planVersion,
        String csgContractVersion,
        PrimitivePlanBinding primitivePlanBinding,
        Kernel kernel,
        List<Operator> operators,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CSG_CONTRACT_VERSION = "volume-csg-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;
    public static final int MAXIMUM_OPERATORS = 64;
    public static final int MAXIMUM_DEPENDENCY_DEPTH = 8;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public VolumeCsgPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volume-csg planVersion must be 1");
        }
        csgContractVersion = nonBlank(csgContractVersion, "csgContractVersion", 64);
        if (!CSG_CONTRACT_VERSION.equals(csgContractVersion)) {
            throw new IllegalArgumentException("unknown volume-csg contract version");
        }
        Objects.requireNonNull(primitivePlanBinding, "primitivePlanBinding");
        Objects.requireNonNull(kernel, "kernel");
        operators = List.copyOf(Objects.requireNonNull(operators, "operators"));
        if (operators.isEmpty() || operators.size() > MAXIMUM_OPERATORS) {
            throw new IllegalArgumentException("volume-csg operator count out of range");
        }
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateOperators(operators, kernel);
        validateBudget(budget, operators);
    }

    public VolumeCsgPlanV2 withCanonicalChecksum(String checksum) {
        return new VolumeCsgPlanV2(
                planVersion, csgContractVersion, primitivePlanBinding, kernel, operators, budget, checksum);
    }

    /** Fails closed unless this plan binds the sealed SDF primitive plan checksum. */
    public void requirePrimitivePlan(VolumeSdfPrimitivePlanV2 primitivePlan) {
        Objects.requireNonNull(primitivePlan, "primitivePlan");
        if (!primitivePlanBinding.sourceVolumeSdfPrimitivePlanChecksum()
                .equals(primitivePlan.canonicalChecksum())) {
            throw new IllegalArgumentException("volume-csg primitive-plan binding mismatch");
        }
        Set<String> primitiveIds = new HashSet<>();
        for (VolumeSdfPrimitiveV2 primitive : primitivePlan.primitives()) {
            primitiveIds.add(primitive.primitiveId());
        }
        for (Operator operator : operators) {
            if (!primitiveIds.contains(operator.primitiveId())) {
                throw new IllegalArgumentException("volume-csg operator references unknown primitive");
            }
            if (operator.mask() == MaskMode.INTERSECTION_WITH_PRIMITIVE
                    && !primitiveIds.contains(operator.maskPrimitiveId())) {
                throw new IllegalArgumentException("volume-csg mask references unknown primitive");
            }
        }
    }

    public enum OperationKind { ADD_SOLID, CARVE_SOLID, ADD_FLUID }

    public enum MaskMode { NONE, INTERSECTION_WITH_PRIMITIVE }

    public record PrimitivePlanBinding(
            int bindingVersion,
            String sourceVolumeSdfPrimitivePlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "volume-csg-primitive-binding-v1";

        public PrimitivePlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown volume-csg primitive binding");
            }
            sourceVolumeSdfPrimitivePlanChecksum = checksum(
                    sourceVolumeSdfPrimitivePlanChecksum, "sourceVolumeSdfPrimitivePlanChecksum");
        }
    }

    public record Kernel(
            String kernelVersion,
            int maximumOperators,
            int maximumDependencyDepth,
            long maximumCpuWorkUnits
    ) {
        public static final String VERSION = "volume-csg-ordered-v1";
        public static final long MAXIMUM_CPU_WORK_UNITS = 1_048_576L;

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown volume-csg kernel version");
            }
            if (maximumOperators < 1 || maximumOperators > MAXIMUM_OPERATORS
                    || maximumDependencyDepth < 1 || maximumDependencyDepth > MAXIMUM_DEPENDENCY_DEPTH
                    || maximumCpuWorkUnits < 1 || maximumCpuWorkUnits > MAXIMUM_CPU_WORK_UNITS) {
                throw new IllegalArgumentException("volume-csg kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(VERSION, MAXIMUM_OPERATORS, MAXIMUM_DEPENDENCY_DEPTH, MAXIMUM_CPU_WORK_UNITS);
        }
    }

    public record Operator(
            String operatorId,
            int ordinal,
            OperationKind kind,
            String primitiveId,
            MaskMode mask,
            String maskPrimitiveId,
            List<String> dependsOnOperatorIds,
            String fluidBodyId
    ) {
        public Operator {
            operatorId = qualified(operatorId, "operatorId");
            Objects.requireNonNull(kind, "kind");
            primitiveId = qualified(primitiveId, "primitiveId");
            Objects.requireNonNull(mask, "mask");
            dependsOnOperatorIds = List.copyOf(Objects.requireNonNull(dependsOnOperatorIds, "dependsOnOperatorIds"));
            if (ordinal < 0) {
                throw new IllegalArgumentException("operator ordinal must be non-negative");
            }
            if (mask == MaskMode.NONE) {
                if (maskPrimitiveId != null && !maskPrimitiveId.isEmpty()) {
                    throw new IllegalArgumentException("NONE mask forbids maskPrimitiveId");
                }
                maskPrimitiveId = "";
            } else {
                maskPrimitiveId = qualified(maskPrimitiveId, "maskPrimitiveId");
            }
            Set<String> deps = new HashSet<>();
            for (String dependency : dependsOnOperatorIds) {
                String id = qualified(dependency, "dependsOnOperatorId");
                if (!deps.add(id)) {
                    throw new IllegalArgumentException("duplicate operator dependency");
                }
                if (id.equals(operatorId)) {
                    throw new IllegalArgumentException("operator cannot depend on itself");
                }
            }
            dependsOnOperatorIds = List.copyOf(deps).stream().sorted().toList();
            if (kind == OperationKind.ADD_FLUID) {
                fluidBodyId = qualified(fluidBodyId, "fluidBodyId");
            } else {
                if (fluidBodyId != null && !fluidBodyId.isEmpty()) {
                    throw new IllegalArgumentException(kind + " forbids fluidBodyId");
                }
                fluidBodyId = "";
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumOperators,
            int maximumDependencyDepth,
            long estimatedCpuWorkUnits,
            long maximumCpuWorkUnits,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes
    ) {
        public static final String VERSION = "volume-csg-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown volume-csg budget version");
            }
            if (maximumOperators < 1 || maximumOperators > MAXIMUM_OPERATORS
                    || maximumDependencyDepth < 1 || maximumDependencyDepth > MAXIMUM_DEPENDENCY_DEPTH
                    || estimatedCpuWorkUnits < 1
                    || maximumCpuWorkUnits < 1 || maximumCpuWorkUnits > Kernel.MAXIMUM_CPU_WORK_UNITS
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1) {
                throw new IllegalArgumentException("volume-csg budget out of range");
            }
        }
    }

    private static void validateOperators(List<Operator> operators, Kernel kernel) {
        if (operators.size() > kernel.maximumOperators()) {
            throw new IllegalArgumentException("volume-csg operator count exceeds kernel budget");
        }
        Set<String> ids = new HashSet<>();
        Map<String, Operator> byId = new HashMap<>();
        for (int index = 0; index < operators.size(); index++) {
            Operator operator = operators.get(index);
            if (operator.ordinal() != index) {
                throw new IllegalArgumentException(
                        "ambiguous non-commutative order: operators must be listed in ordinal sequence");
            }
            if (!ids.add(operator.operatorId())) {
                throw new IllegalArgumentException("duplicate volume-csg operator id");
            }
            byId.put(operator.operatorId(), operator);
        }
        for (Operator operator : operators) {
            for (String dependencyId : operator.dependsOnOperatorIds()) {
                Operator dependency = byId.get(dependencyId);
                if (dependency == null) {
                    throw new IllegalArgumentException("unknown volume-csg dependency operator");
                }
                if (dependency.ordinal() >= operator.ordinal()) {
                    throw new IllegalArgumentException(
                            "volume-csg dependency must precede the dependent operator");
                }
            }
            int depth = dependencyDepth(operator, byId, new HashSet<>());
            if (depth > kernel.maximumDependencyDepth()) {
                throw new IllegalArgumentException("volume-csg dependency depth exceeds budget");
            }
        }
        detectCycles(byId);
    }

    private static int dependencyDepth(
            Operator operator,
            Map<String, Operator> byId,
            Set<String> visiting
    ) {
        if (!visiting.add(operator.operatorId())) {
            throw new IllegalArgumentException("volume-csg dependency cycle detected");
        }
        int depth = 1;
        for (String dependencyId : operator.dependsOnOperatorIds()) {
            depth = Math.max(depth, 1 + dependencyDepth(byId.get(dependencyId), byId, visiting));
        }
        visiting.remove(operator.operatorId());
        return depth;
    }

    private static void detectCycles(Map<String, Operator> byId) {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (String id : byId.keySet()) {
            if (!visited.contains(id)) {
                ArrayDeque<String> path = new ArrayDeque<>();
                dfsCycle(id, byId, visited, stack, path);
            }
        }
    }

    private static void dfsCycle(
            String id,
            Map<String, Operator> byId,
            Set<String> visited,
            Set<String> stack,
            ArrayDeque<String> path
    ) {
        if (stack.contains(id)) {
            throw new IllegalArgumentException("volume-csg dependency cycle detected");
        }
        if (!visited.add(id)) {
            return;
        }
        stack.add(id);
        path.addLast(id);
        for (String dependencyId : byId.get(id).dependsOnOperatorIds()) {
            dfsCycle(dependencyId, byId, visited, stack, path);
        }
        stack.remove(id);
        path.removeLast();
    }

    private static void validateBudget(ResourceBudget budget, List<Operator> operators) {
        if (operators.size() > budget.maximumOperators()) {
            throw new IllegalArgumentException("volume-csg operator count exceeds budget");
        }
        long estimated = Math.multiplyExact(operators.size(), 1_024L);
        if (estimated > budget.maximumCpuWorkUnits()
                || budget.estimatedCpuWorkUnits() > budget.maximumCpuWorkUnits()) {
            throw new IllegalArgumentException("volume-csg CPU budget exceeded");
        }
        if (budget.estimatedCanonicalBytes() > budget.maximumCanonicalBytes()) {
            throw new IllegalArgumentException("volume-csg canonical budget exceeded");
        }
    }

    static String qualified(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a qualified id");
        }
        return value;
    }

    static String checksum(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a sha-256 hex digest");
        }
        return value;
    }

    static String nonBlank(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " length out of range");
        }
        return value;
    }
}
