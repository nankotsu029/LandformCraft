package com.github.nankotsu029.landformcraft.validation.v2.conformance;

/**
 * The heterogeneous kinds of intent-conformance target a Blueprint carries (V2-18-07).
 *
 * <p>The V2-18 macro-foundation audit found that "conformance" had been collapsed onto a single shape
 * — a scalar metric compared to a range — which cannot express the two facts the audit cared about:
 * that a per-cell {@code LAND_WATER_MASK} is a <em>desired raster</em> the generated surface must match,
 * and that many hydrology/coastal checks are really <em>topological</em> (connected or not) rather than a
 * measured quantity. This enum names the distinct kinds so a residual can be represented faithfully for
 * each ({@link ConformanceResidualV2}) instead of forcing everything through one scalar comparison.</p>
 *
 * <ul>
 *   <li>{@link #DESIRED_RASTER} — a per-cell desired field (e.g. the land/water mask) the actual field
 *       must reproduce. Its conformance is a raster residual (mismatched cell count), and it is
 *       <em>optional</em>: a request need not supply a complete desired raster (V2-18-07 non-scope).</li>
 *   <li>{@link #AGGREGATE_METRIC} — a share/ratio measured over a region (e.g. an edge's land share).</li>
 *   <li>{@link #TOPOLOGY} — a connectivity/graph property that either holds or does not (e.g. a river
 *       reaching the sea, a delta's mouth connections, zero transition conflict cells).</li>
 *   <li>{@link #GEOMETRIC} — a physical dimension measured from the field (e.g. beach width, harbor
 *       depth, breakwater clear opening, waterfall drop).</li>
 * </ul>
 */
public enum ConformanceTargetKindV2 {
    DESIRED_RASTER,
    AGGREGATE_METRIC,
    TOPOLOGY,
    GEOMETRIC
}
