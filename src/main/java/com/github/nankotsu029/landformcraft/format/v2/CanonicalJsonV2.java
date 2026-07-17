package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.format.Sha256;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Canonical JSON v1: sorted object keys, stable arrays, UTF-8, plain normalized decimal numbers, no whitespace. */
public final class CanonicalJsonV2 {
    public static final String VERSION = "lfc-canonical-json-v1";
    private static final ObjectMapper STRING_ENCODER = new ObjectMapper();

    private CanonicalJsonV2() {
    }

    public static byte[] bytes(JsonNode node) {
        StringBuilder result = new StringBuilder();
        append(node, result);
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String string(JsonNode node) {
        return new String(bytes(node), StandardCharsets.UTF_8);
    }

    public static String checksum(JsonNode node) {
        return Sha256.bytes(bytes(node));
    }

    private static void append(JsonNode node, StringBuilder output) {
        if (node.isObject()) {
            output.append('{');
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            for (int index = 0; index < fields.size(); index++) {
                if (index > 0) output.append(',');
                appendString(fields.get(index), output);
                output.append(':');
                append(node.get(fields.get(index)), output);
            }
            output.append('}');
        } else if (node.isArray()) {
            output.append('[');
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) output.append(',');
                append(node.get(index), output);
            }
            output.append(']');
        } else if (node.isTextual()) {
            appendString(node.textValue(), output);
        } else if (node.isIntegralNumber()) {
            output.append(node.bigIntegerValue());
        } else if (node.isNumber()) {
            BigDecimal decimal = node.decimalValue();
            if (decimal.signum() == 0) {
                output.append('0');
            } else {
                output.append(decimal.stripTrailingZeros().toPlainString());
            }
        } else if (node.isBoolean()) {
            output.append(node.booleanValue());
        } else if (node.isNull()) {
            output.append("null");
        } else {
            throw new IllegalArgumentException("unsupported canonical JSON node: " + node.getNodeType());
        }
    }

    private static void appendString(String value, StringBuilder output) {
        try {
            output.append(STRING_ENCODER.writeValueAsString(value));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode JSON string", exception);
        }
    }
}
