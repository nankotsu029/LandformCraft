package com.github.nankotsu029.landformcraft.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Narrow deterministic repairs for aggregate constraints JSON Schema cannot express. */
final class ProviderTerrainIntentNormalizer {
    private static final double AREA_SHARE_TOLERANCE = 1.0e-9;
    private static final double NORMALIZED_TOTAL = 1.0 - 1.0e-12;

    private ProviderTerrainIntentNormalizer() {
    }

    static String normalizeZoneAreaShares(ObjectMapper mapper, String input) throws IOException {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(input, "input");
        JsonNode document = mapper.readTree(input);
        if (!(document instanceof ObjectNode root)
                || !(root.get("zones") instanceof ArrayNode zones)
                || zones.isEmpty()) {
            return input;
        }

        List<ObjectNode> zoneObjects = new ArrayList<>(zones.size());
        List<Double> shares = new ArrayList<>(zones.size());
        double total = 0.0;
        for (JsonNode zone : zones) {
            if (!(zone instanceof ObjectNode zoneObject)) {
                return input;
            }
            JsonNode share = zoneObject.get("areaShare");
            if (share == null || !share.isNumber()) {
                return input;
            }
            double value = share.doubleValue();
            if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
                return input;
            }
            zoneObjects.add(zoneObject);
            shares.add(value);
            total += value;
        }
        if (total <= 1.0 + AREA_SHARE_TOLERANCE) {
            return input;
        }

        double scale = NORMALIZED_TOTAL / total;
        for (int index = 0; index < zoneObjects.size(); index++) {
            zoneObjects.get(index).put("areaShare", shares.get(index) * scale);
        }
        return mapper.writeValueAsString(root);
    }
}
