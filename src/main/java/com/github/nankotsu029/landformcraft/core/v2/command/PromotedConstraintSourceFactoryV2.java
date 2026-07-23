package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuidePromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuidePromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskPromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelPromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a sealed promotion record into the request's constraint-map source declaration (V2-19-04).
 *
 * <p>Authoring previously had one hard-coded land/water form and no way at all to declare a
 * {@code HEIGHT_GUIDE} or {@code ZONE_LABEL_MAP} source, even though the request contract carries
 * all three. The missing part is the encoding: a height map needs its value meaning, scale, offset,
 * valid sample range and no-data sentinel, and a zone map needs its label legend — none of which can
 * be guessed and none of which fit a command line.</p>
 *
 * <p>The {@code promote} verb already sealed exactly those values into its record, so this factory
 * reads them from there instead of inventing them. Dimensions and the digest also come from the
 * record, which means a declared source always describes the map that was actually produced. The
 * caller supplies only the source slug and the request-relative path where the map file will live.
 * Nothing here writes a file or touches the intent.</p>
 */
public final class PromotedConstraintSourceFactoryV2 {
    private final ExtractedMaskPromotionRecordCodecV2 maskCodec = new ExtractedMaskPromotionRecordCodecV2();
    private final ExtractedHeightGuidePromotionRecordCodecV2 heightCodec =
            new ExtractedHeightGuidePromotionRecordCodecV2();
    private final ExtractedZoneLabelPromotionRecordCodecV2 zoneCodec =
            new ExtractedZoneLabelPromotionRecordCodecV2();

    /**
     * Reads the promotion record of {@code role} from {@code promotionDirectory} and returns the
     * matching source declaration.
     *
     * @param sourceId    canonical {@code constraint-source:<slug>} id chosen by the operator
     * @param file        request-relative path of the map file inside the request directory
     */
    public GenerationRequestV2.ConstraintMapSource fromPromotion(
            Path promotionDirectory,
            TerrainIntentV2.ConstraintMapRole role,
            String sourceId,
            String file
    ) throws IOException {
        Objects.requireNonNull(promotionDirectory, "promotionDirectory");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(file, "file");
        return switch (role) {
            case LAND_WATER_MASK -> landWater(maskCodec.read(
                    promotionDirectory.resolve(ExtractedMaskPromotionRecordCodecV2.INDEX_FILE_NAME)),
                    sourceId, file);
            case HEIGHT_GUIDE -> heightGuide(heightCodec.read(
                    promotionDirectory.resolve(ExtractedHeightGuidePromotionRecordCodecV2.INDEX_FILE_NAME)),
                    sourceId, file);
            case ZONE_LABEL_MAP -> zoneLabel(zoneCodec.read(
                    promotionDirectory.resolve(ExtractedZoneLabelPromotionRecordCodecV2.INDEX_FILE_NAME)),
                    sourceId, file);
        };
    }

    private static GenerationRequestV2.ConstraintMapSource landWater(
            ExtractedMaskPromotionRecordV2 record,
            String sourceId,
            String file
    ) {
        // The promoted land/water PNG is U8 grayscale with 0=water and 1=land; unknown cells are
        // either resolved by the promotion's explicit handling or written as its no-data sentinel.
        GenerationRequestV2.NoData noData = record.noDataSample() == null
                ? new GenerationRequestV2.NoDataForbidden()
                : new GenerationRequestV2.NoDataSentinel(record.noDataSample());
        return source(
                sourceId,
                file,
                record.mapSha256(),
                record.width(),
                record.length(),
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                new GenerationRequestV2.CategoricalEncoding(
                        1,
                        GenerationRequestV2.SampleType.U8,
                        GenerationRequestV2.RasterChannel.GRAY,
                        List.of(
                                new GenerationRequestV2.LabelMapping(0, "water"),
                                new GenerationRequestV2.LabelMapping(1, "land")),
                        noData));
    }

    private static GenerationRequestV2.ConstraintMapSource heightGuide(
            ExtractedHeightGuidePromotionRecordV2 record,
            String sourceId,
            String file
    ) {
        return source(
                sourceId,
                file,
                record.mapSha256(),
                record.width(),
                record.length(),
                GenerationRequestV2.DecoderKind.HEIGHT_RASTER,
                new GenerationRequestV2.HeightEncoding(
                        1,
                        GenerationRequestV2.SampleType.U8,
                        GenerationRequestV2.RasterChannel.GRAY,
                        GenerationRequestV2.HeightValueMeaning.valueOf(record.valueMeaning()),
                        record.valueScaleMillionths(),
                        record.valueOffsetMillionths(),
                        new GenerationRequestV2.IntRange(
                                record.validSampleMinimum(), record.validSampleMaximum()),
                        new GenerationRequestV2.NoDataSentinel(record.noDataSample())));
    }

    private static GenerationRequestV2.ConstraintMapSource zoneLabel(
            ExtractedZoneLabelPromotionRecordV2 record,
            String sourceId,
            String file
    ) {
        List<GenerationRequestV2.LabelMapping> labels = new ArrayList<>();
        record.proposedLabels().forEach(proposal ->
                labels.add(new GenerationRequestV2.LabelMapping(proposal.sample(), proposal.label())));
        return source(
                sourceId,
                file,
                record.mapSha256(),
                record.width(),
                record.length(),
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                new GenerationRequestV2.CategoricalEncoding(
                        1,
                        GenerationRequestV2.SampleType.U8,
                        GenerationRequestV2.RasterChannel.GRAY,
                        labels,
                        new GenerationRequestV2.NoDataSentinel(record.noDataSample())));
    }

    /** Canonical coordinate mapping: north-west origin, east/south axes, pixel centres, full crop. */
    private static GenerationRequestV2.ConstraintMapSource source(
            String sourceId,
            String file,
            String expectedSha256,
            int width,
            int length,
            GenerationRequestV2.DecoderKind decoderKind,
            GenerationRequestV2.ConstraintMapEncoding encoding
    ) {
        return new GenerationRequestV2.ConstraintMapSource(
                sourceId,
                file,
                expectedSha256,
                width,
                length,
                decoderKind,
                new GenerationRequestV2.CoordinateMapping(
                        GenerationRequestV2.CoordinateOrigin.NORTH_WEST,
                        GenerationRequestV2.XAxis.EAST,
                        GenerationRequestV2.ZAxis.SOUTH,
                        GenerationRequestV2.PixelReference.PIXEL_CENTER,
                        GenerationRequestV2.AspectMismatchPolicy.REJECT,
                        GenerationRequestV2.QuarterTurn.DEGREES_0,
                        false,
                        false,
                        new GenerationRequestV2.PixelCrop(0, 0, width, length)),
                encoding);
    }
}
