package com.github.nankotsu029.landformcraft.format.v2.tile;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical, NBT-free block-state string boundary used by the V2 tile format. */
public final class CanonicalBlockStateV2 {
    private static final Pattern IDENTIFIER = Pattern.compile("minecraft:[a-z0-9_./-]+");
    private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Pattern PROPERTY_VALUE = Pattern.compile("[a-z0-9_-]+");

    private CanonicalBlockStateV2() {
    }

    public static String requireCanonical(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || value.indexOf('{') >= 0 || value.indexOf('}') >= 0) {
            throw new IllegalArgumentException("V2 tile block state is blank, oversized, or contains NBT");
        }
        int propertiesStart = value.indexOf('[');
        String identifier = propertiesStart < 0 ? value : value.substring(0, propertiesStart);
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("V2 tile block state has a non-canonical identifier: " + value);
        }
        if (propertiesStart >= 0) {
            if (!value.endsWith("]") || propertiesStart == value.length() - 2
                    || value.indexOf('[', propertiesStart + 1) >= 0) {
                throw new IllegalArgumentException("V2 tile block state has malformed properties: " + value);
            }
            Set<String> names = new HashSet<>();
            String previous = null;
            String body = value.substring(propertiesStart + 1, value.length() - 1);
            for (String property : body.split(",", -1)) {
                int equals = property.indexOf('=');
                if (equals < 1 || equals != property.lastIndexOf('=') || equals == property.length() - 1) {
                    throw new IllegalArgumentException("V2 tile block state has malformed properties: " + value);
                }
                String name = property.substring(0, equals);
                String propertyValue = property.substring(equals + 1);
                if (!PROPERTY_NAME.matcher(name).matches() || !PROPERTY_VALUE.matcher(propertyValue).matches()
                        || !names.add(name) || (previous != null && previous.compareTo(name) >= 0)) {
                    throw new IllegalArgumentException("V2 tile block-state properties are not canonical: " + value);
                }
                previous = name;
            }
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new IllegalArgumentException("V2 tile block state exceeds the UTF-8 byte budget");
        }
        return value;
    }
}
