package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.ai.spi.PreparedReferenceImage;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.CardinalDirection;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ImageConsistencyCheck;
import com.github.nankotsu029.landformcraft.model.ImageConsistencyStatus;
import com.github.nankotsu029.landformcraft.model.ImageEvidenceEntry;
import com.github.nankotsu029.landformcraft.model.ImageInputEvidence;
import com.github.nankotsu029.landformcraft.model.ImageMapAxis;
import com.github.nankotsu029.landformcraft.model.ImageMapOrigin;
import com.github.nankotsu029.landformcraft.model.ImageSideExpectation;
import com.github.nankotsu029.landformcraft.model.ImageTransformation;
import com.github.nankotsu029.landformcraft.model.ImageValidationStatus;
import com.github.nankotsu029.landformcraft.model.ReferenceImage;
import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;
import com.github.nankotsu029.landformcraft.model.TopDownCoordinateMapping;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Loads untrusted image files on I/O threads and decodes/normalizes them on the bounded CPU pool. */
public final class ReferenceImageProcessor {
    public static final long MAX_SOURCE_BYTES = 8L * 1024L * 1024L;
    public static final long MAX_TOTAL_SOURCE_BYTES = 32L * 1024L * 1024L;
    public static final long MAX_PIXELS_PER_IMAGE = 4_000_000L;
    public static final long MAX_TOTAL_PIXELS = 16_000_000L;
    public static final int MAX_DIMENSION = 4096;
    public static final int MAX_ASPECT_RATIO = 32;
    public static final long MAX_NORMALIZED_BYTES = 16L * 1024L * 1024L;
    public static final long MAX_TOTAL_NORMALIZED_BYTES = 16L * 1024L * 1024L;
    private static final int MIN_WATER_BLUE = 24;
    private static final int MIN_WATER_BLUE_RED_DELTA = 12;
    private static final int MIN_WATER_BLUE_GREEN_DELTA = 6;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final String ENGLISH_CARDINAL_DIRECTIONS = "(?:north|east|south|west)";

    private final Clock clock;

    public ReferenceImageProcessor() {
        this(Clock.systemUTC());
    }

    public ReferenceImageProcessor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** File-only stage. Call from the admitted I/O executor. */
    public LoadedImageInputs load(Path requestPath, GenerationRequest request) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(request, "request");
        Path root = Objects.requireNonNull(
                requestPath.toAbsolutePath().normalize().getParent(), "request path must have a parent"
        );
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw failure(ImageInputFailureCode.UNSAFE_PATH, "request root must be a non-symbolic directory");
        }
        List<LoadedReferenceImage> loaded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<Object> seenFileKeys = new HashSet<>();
        long totalBytes = 0L;
        for (int index = 0; index < request.images().size(); index++) {
            ReferenceImage image = request.images().get(index);
            if (!seen.add(image.file())) {
                throw failure(ImageInputFailureCode.UNSAFE_PATH, "duplicate image path: " + image.file());
            }
            Path source = resolveSafe(root, image.file());
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(
                        source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
                );
            } catch (IOException exception) {
                throw failure(ImageInputFailureCode.MISSING_FILE, "image is missing: " + image.file(), exception);
            }
            if (!attributes.isRegularFile() || Files.isSymbolicLink(source)) {
                throw failure(ImageInputFailureCode.UNSAFE_PATH, "image must be a regular file: " + image.file());
            }
            if (attributes.fileKey() != null && !seenFileKeys.add(attributes.fileKey())) {
                throw failure(ImageInputFailureCode.UNSAFE_PATH,
                        "multiple image paths reference the same file: " + image.file());
            }
            if (attributes.size() < 1 || attributes.size() > MAX_SOURCE_BYTES) {
                throw failure(ImageInputFailureCode.FILE_TOO_LARGE, "image size is outside the limit: " + image.file());
            }
            byte[] bytes = readLimited(source, image.file(), attributes);
            totalBytes = Math.addExact(totalBytes, bytes.length);
            if (totalBytes > MAX_TOTAL_SOURCE_BYTES) {
                throw failure(ImageInputFailureCode.TOTAL_BYTES_EXCEEDED, "total image bytes exceed the limit");
            }
            String mediaType = detectMediaType(bytes, image.file());
            verifyExtension(image.file(), mediaType);
            loaded.add(new LoadedReferenceImage(index, image.file(), image.role(), mediaType, bytes));
        }
        return new LoadedImageInputs(loaded);
    }

    /** Decode/analysis stage. Call from the bounded generation executor. */
    public PreparedImageInputs process(
            GenerationRequest request,
            LoadedImageInputs loaded,
            BooleanSupplier cancellationRequested
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (loaded.images().size() != request.images().size()) {
            throw new IllegalArgumentException("loaded image count must match request");
        }
        List<PreparedReferenceImage> prepared = new ArrayList<>();
        List<ImageEvidenceEntry> evidence = new ArrayList<>();
        List<ImageConsistencyCheck> checks = new ArrayList<>();
        List<Map<CardinalDirection, Double>> topDownObservations = new ArrayList<>();
        boolean hasTopDown = loaded.images().stream()
                .anyMatch(image -> image.role() == ReferenceImageRole.TOP_DOWN_SKETCH);
        Map<CardinalDirection, ImageSideExpectation> expectations = hasTopDown
                ? promptExpectations(request.prompt()) : Map.of();
        long totalPixels = 0L;
        long totalNormalizedBytes = 0L;
        for (LoadedReferenceImage source : loaded.images()) {
            ensureNotCancelled(cancellationRequested);
            DecodedImage decoded = decode(source, cancellationRequested);
            totalPixels = Math.addExact(totalPixels, (long) decoded.width() * decoded.height());
            if (totalPixels > MAX_TOTAL_PIXELS) {
                throw failure(ImageInputFailureCode.PIXELS_EXCEEDED, "total decoded pixels exceed the limit");
            }
            byte[] normalized = encodePng(decoded.image());
            totalNormalizedBytes = Math.addExact(totalNormalizedBytes, normalized.length);
            if (normalized.length > MAX_NORMALIZED_BYTES || totalNormalizedBytes > MAX_TOTAL_NORMALIZED_BYTES) {
                throw failure(
                        ImageInputFailureCode.NORMALIZED_TOO_LARGE,
                        "normalized image bytes exceed the limit: " + source.sourceFile()
                );
            }
            String normalizedChecksum = Sha256.bytes(normalized);
            List<ImageTransformation> transformations = new ArrayList<>();
            transformations.add(ImageTransformation.DECODED);
            transformations.add(ImageTransformation.COLOR_SPACE_NORMALIZED);
            if (decoded.orientation() != 1) {
                transformations.add(ImageTransformation.ORIENTATION_NORMALIZED);
            }
            transformations.add(ImageTransformation.METADATA_STRIPPED);
            transformations.add(ImageTransformation.PNG_REENCODED);
            List<TopDownCoordinateMapping> mappings = List.of();
            Map<CardinalDirection, Double> ratios = Map.of();
            if (source.role() == ReferenceImageRole.TOP_DOWN_SKETCH) {
                transformations.add(ImageTransformation.TOP_DOWN_COORDINATES_NORMALIZED);
                mappings = List.of(new TopDownCoordinateMapping(
                        ImageMapOrigin.TOP_LEFT,
                        ImageMapAxis.POSITIVE_X_EAST,
                        ImageMapAxis.POSITIVE_Z_SOUTH,
                        request.bounds().width(),
                        request.bounds().length()
                ));
                ratios = edgeWaterRatios(decoded.image(), cancellationRequested);
                checkPromptConsistency(source.sourceFile(), ratios, expectations, checks);
                checkImageConsistency(source.sourceFile(), ratios, topDownObservations);
                topDownObservations.add(ratios);
            }
            prepared.add(new PreparedReferenceImage(
                    source.index(), source.sourceFile(), source.role(), "image/png",
                    decoded.width(), decoded.height(), normalizedChecksum, normalized
            ));
            evidence.add(new ImageEvidenceEntry(
                    source.index(), String.format(Locale.ROOT, "image-%02d", source.index()),
                    source.sourceFile(), source.role(), false, ImageValidationStatus.VERIFIED,
                    source.mediaType(), source.content().length,
                    decoded.sourceWidth(), decoded.sourceHeight(), Sha256.bytes(source.content()),
                    "image/png", normalized.length, decoded.width(), decoded.height(), normalizedChecksum,
                    decoded.metadataDetected(), decoded.orientation(), transformations, mappings, ratios
            ));
        }
        return new PreparedImageInputs(
                prepared,
                new ImageInputEvidence(
                        1, request.requestId(), ImageInputEvidence.NORMALIZATION_VERSION,
                        "unresolved", "unresolved", "unresolved",
                        evidence, checks, clock.instant()
                )
        );
    }

    private static Path resolveSafe(Path root, String relative) {
        Path candidate;
        try {
            candidate = root.resolve(relative).normalize();
        } catch (RuntimeException exception) {
            throw failure(ImageInputFailureCode.UNSAFE_PATH, "invalid image path: " + relative, exception);
        }
        if (!candidate.startsWith(root)) {
            throw failure(ImageInputFailureCode.UNSAFE_PATH, "image path escapes request root: " + relative);
        }
        Path current = root;
        for (Path segment : Path.of(relative)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw failure(ImageInputFailureCode.UNSAFE_PATH, "symbolic links are not allowed: " + relative);
            }
        }
        return candidate;
    }

    private static byte[] readLimited(Path source, String relative, BasicFileAttributes before) {
        try (SeekableByteChannel channel = Files.newByteChannel(
                source, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        ); InputStream input = Channels.newInputStream(channel)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(MAX_SOURCE_BYTES + 1L));
            if (bytes.length > MAX_SOURCE_BYTES) {
                throw failure(ImageInputFailureCode.FILE_TOO_LARGE, "image exceeds the byte limit: " + relative);
            }
            BasicFileAttributes after = Files.readAttributes(
                    source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
            );
            if (!after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                throw failure(ImageInputFailureCode.UNSAFE_PATH,
                        "image changed while it was being read: " + relative);
            }
            return bytes;
        } catch (ImageInputException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(ImageInputFailureCode.MISSING_FILE, "failed to read image: " + relative, exception);
        }
    }

    private static String detectMediaType(byte[] bytes, String relative) {
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        throw failure(ImageInputFailureCode.INVALID_MAGIC, "image magic is not PNG or JPEG: " + relative);
    }

    private static void verifyExtension(String relative, String mediaType) {
        String lower = relative.toLowerCase(Locale.ROOT);
        boolean valid = "image/png".equals(mediaType) && lower.endsWith(".png")
                || "image/jpeg".equals(mediaType) && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
        if (!valid) {
            throw failure(ImageInputFailureCode.UNSUPPORTED_FORMAT, "image extension and content disagree: " + relative);
        }
    }

    private static DecodedImage decode(LoadedReferenceImage source, BooleanSupplier cancellationRequested) {
        byte[] bytes = source.content();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw failure(ImageInputFailureCode.CORRUPT_IMAGE, "image has no decoder: " + source.sourceFile());
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.contains("png") || format.contains("jpeg") || format.contains("jpg"))) {
                    throw failure(ImageInputFailureCode.UNSUPPORTED_FORMAT, "decoder format is not PNG/JPEG");
                }
                int images = reader.getNumImages(true);
                if (images != 1) {
                    throw failure(ImageInputFailureCode.MULTI_FRAME, "multi-frame images are not allowed");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireDimensions(width, height, source.sourceFile());
                ensureNotCancelled(cancellationRequested);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw failure(ImageInputFailureCode.CORRUPT_IMAGE, "decoder returned no image");
                }
                int orientation = "image/jpeg".equals(source.mediaType()) ? exifOrientation(bytes) : 1;
                BufferedImage oriented = orient(decoded, orientation, cancellationRequested);
                return new DecodedImage(
                        oriented, width, height, oriented.getWidth(), oriented.getHeight(),
                        hasMetadata(bytes, source.mediaType()), orientation
                );
            } finally {
                reader.dispose();
            }
        } catch (ImageInputException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure(ImageInputFailureCode.CORRUPT_IMAGE, "image decode failed: " + source.sourceFile(), exception);
        }
    }

    private static void requireDimensions(int width, int height, String sourceFile) {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw failure(ImageInputFailureCode.DIMENSIONS_EXCEEDED, "image dimensions exceed the limit: " + sourceFile);
        }
        if ((long) width * height > MAX_PIXELS_PER_IMAGE) {
            throw failure(ImageInputFailureCode.PIXELS_EXCEEDED, "image pixels exceed the limit: " + sourceFile);
        }
        int shorter = Math.min(width, height);
        int longer = Math.max(width, height);
        if ((long) longer > (long) shorter * MAX_ASPECT_RATIO) {
            throw failure(ImageInputFailureCode.DIMENSIONS_EXCEEDED,
                    "image aspect ratio exceeds the limit: " + sourceFile);
        }
    }

    private static BufferedImage orient(
            BufferedImage source,
            int orientation,
            BooleanSupplier cancellationRequested
    ) {
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = orientation >= 5 ? height : width;
        int targetHeight = orientation >= 5 ? width : height;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            if ((y & 31) == 0) {
                ensureNotCancelled(cancellationRequested);
            }
            for (int x = 0; x < width; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 1 -> { targetX = x; targetY = y; }
                    case 2 -> { targetX = width - 1 - x; targetY = y; }
                    case 3 -> { targetX = width - 1 - x; targetY = height - 1 - y; }
                    case 4 -> { targetX = x; targetY = height - 1 - y; }
                    case 5 -> { targetX = y; targetY = x; }
                    case 6 -> { targetX = height - 1 - y; targetY = x; }
                    case 7 -> { targetX = height - 1 - y; targetY = width - 1 - x; }
                    case 8 -> { targetX = y; targetY = width - 1 - x; }
                    default -> throw failure(ImageInputFailureCode.CORRUPT_IMAGE, "invalid EXIF orientation");
                }
                target.setRGB(targetX, targetY, source.getRGB(x, y));
            }
        }
        return target;
    }

    private static byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw failure(ImageInputFailureCode.CORRUPT_IMAGE, "PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw failure(ImageInputFailureCode.CORRUPT_IMAGE, "failed to normalize image", exception);
        }
    }

    private static Map<CardinalDirection, Double> edgeWaterRatios(
            BufferedImage image,
            BooleanSupplier cancellationRequested
    ) {
        int width = image.getWidth();
        int height = image.getHeight();
        int bandX = Math.max(1, Math.min(width, width / 20));
        int bandY = Math.max(1, Math.min(height, height / 20));
        EnumMap<CardinalDirection, Double> result = new EnumMap<>(CardinalDirection.class);
        result.put(CardinalDirection.NORTH, waterRatio(image, 0, 0, width, bandY, cancellationRequested));
        result.put(CardinalDirection.SOUTH,
                waterRatio(image, 0, height - bandY, width, bandY, cancellationRequested));
        result.put(CardinalDirection.WEST, waterRatio(image, 0, 0, bandX, height, cancellationRequested));
        result.put(CardinalDirection.EAST,
                waterRatio(image, width - bandX, 0, bandX, height, cancellationRequested));
        return Collections.unmodifiableMap(result);
    }

    private static double waterRatio(
            BufferedImage image,
            int startX,
            int startY,
            int width,
            int height,
            BooleanSupplier cancellationRequested
    ) {
        long water = 0L;
        long total = 0L;
        for (int y = startY; y < startY + height; y++) {
            if ((y & 31) == 0) {
                ensureNotCancelled(cancellationRequested);
            }
            for (int x = startX; x < startX + width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = argb >>> 16 & 0xff;
                int green = argb >>> 8 & 0xff;
                int blue = argb & 0xff;
                if (alpha >= 128
                        && blue >= MIN_WATER_BLUE
                        && blue >= red + MIN_WATER_BLUE_RED_DELTA
                        && blue >= green + MIN_WATER_BLUE_GREEN_DELTA) {
                    water++;
                }
                total++;
            }
        }
        return total == 0L ? 0.0 : water / (double) total;
    }

    private static Map<CardinalDirection, ImageSideExpectation> promptExpectations(String prompt) {
        EnumMap<CardinalDirection, ImageSideExpectation> result = new EnumMap<>(CardinalDirection.class);
        for (CardinalDirection direction : CardinalDirection.values()) {
            boolean sea = mentions(prompt, direction, true);
            boolean land = mentions(prompt, direction, false);
            if (sea && land) {
                throw failure(
                        ImageInputFailureCode.PROMPT_IMAGE_CONFLICT,
                        "prompt declares both sea and land on " + direction
                );
            }
            if (sea || land) {
                result.put(direction, sea ? ImageSideExpectation.SEA : ImageSideExpectation.LAND);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean mentions(String prompt, CardinalDirection direction, boolean sea) {
        String japaneseDirection = japaneseDirection(direction);
        String japaneseConcept = sea ? "海" : "陸";
        Pattern japanese = Pattern.compile(
                japaneseDirection
                        + "(?:側|部|端|方)?"
                        + "(?:を中心に|付近には|付近に|には|に|を|は|が|の)"
                        + "[^北東南西。、，,\\n]{0,16}"
                        + Pattern.quote(japaneseConcept)
        );
        if (japanese.matcher(prompt).find()) {
            return true;
        }
        String englishDirection = direction.name().toLowerCase(Locale.ROOT);
        String englishConcept = sea ? "(?:sea|ocean|water)" : "land";
        Pattern pattern = Pattern.compile(
                "(?i)\\b" + Pattern.quote(englishDirection) + "\\b"
                        + "(?:(?!\\b" + ENGLISH_CARDINAL_DIRECTIONS + "\\b)[^\\n]){0,24}"
                        + "\\b" + englishConcept + "\\b"
        );
        return pattern.matcher(prompt).find();
    }

    private static String japaneseDirection(CardinalDirection direction) {
        return switch (direction) {
            case NORTH -> "北(?:東|西)?";
            case EAST -> "(?:北|南)?東";
            case SOUTH -> "南(?:東|西)?";
            case WEST -> "(?:北|南)?西";
        };
    }

    private static void checkPromptConsistency(
            String sourceFile,
            Map<CardinalDirection, Double> ratios,
            Map<CardinalDirection, ImageSideExpectation> expectations,
            List<ImageConsistencyCheck> checks
    ) {
        expectations.forEach((side, expected) -> {
            double ratio = ratios.get(side);
            boolean conflict = expected == ImageSideExpectation.SEA && ratio <= 0.15
                    || expected == ImageSideExpectation.LAND && ratio >= 0.85;
            if (conflict) {
                throw failure(
                        ImageInputFailureCode.PROMPT_IMAGE_CONFLICT,
                        "top-down image contradicts prompt on " + side + ": " + sourceFile
                );
            }
            boolean consistent = expected == ImageSideExpectation.SEA && ratio >= 0.65
                    || expected == ImageSideExpectation.LAND && ratio <= 0.35;
            checks.add(new ImageConsistencyCheck(
                    sourceFile, side, expected, ratio,
                    consistent ? ImageConsistencyStatus.CONSISTENT : ImageConsistencyStatus.INCONCLUSIVE
            ));
        });
    }

    private static void checkImageConsistency(
            String sourceFile,
            Map<CardinalDirection, Double> current,
            List<Map<CardinalDirection, Double>> previous
    ) {
        for (Map<CardinalDirection, Double> observation : previous) {
            for (CardinalDirection direction : CardinalDirection.values()) {
                double first = observation.get(direction);
                double second = current.get(direction);
                if (first >= 0.85 && second <= 0.15 || first <= 0.15 && second >= 0.85) {
                    throw failure(
                            ImageInputFailureCode.IMAGE_IMAGE_CONFLICT,
                            "top-down images strongly disagree on " + direction + ": " + sourceFile
                    );
                }
            }
        }
    }

    private static boolean hasMetadata(byte[] bytes, String mediaType) {
        if ("image/jpeg".equals(mediaType)) {
            return jpegHasMetadata(bytes);
        }
        int position = PNG_SIGNATURE.length;
        while (position + 12 <= bytes.length) {
            int length = readInt(bytes, position, false);
            if (length < 0 || position + 12L + length > bytes.length) {
                return false;
            }
            int type = position + 4;
            if ((bytes[type] & 0x20) != 0) {
                return true;
            }
            position += 12 + length;
        }
        return false;
    }

    private static boolean jpegHasMetadata(byte[] bytes) {
        int position = 2;
        while (position + 1 < bytes.length) {
            if ((bytes[position++] & 0xff) != 0xff) {
                continue;
            }
            while (position < bytes.length && (bytes[position] & 0xff) == 0xff) {
                position++;
            }
            if (position >= bytes.length) {
                return false;
            }
            int marker = bytes[position++] & 0xff;
            if (marker == 0xda || marker == 0xd9) {
                return false;
            }
            if (marker >= 0xd0 && marker <= 0xd7 || marker == 0x01) {
                continue;
            }
            if (position + 2 > bytes.length) {
                return false;
            }
            int length = readUnsignedShort(bytes, position, false);
            if (length < 2 || position + length > bytes.length) {
                return false;
            }
            if (marker >= 0xe1 && marker <= 0xef || marker == 0xfe) {
                return true;
            }
            position += length;
        }
        return false;
    }

    private static int exifOrientation(byte[] bytes) {
        int position = 2;
        while (position + 4 <= bytes.length) {
            if ((bytes[position++] & 0xff) != 0xff) {
                continue;
            }
            int marker = bytes[position++] & 0xff;
            if (marker == 0xda || marker == 0xd9) {
                return 1;
            }
            if (marker >= 0xd0 && marker <= 0xd7 || marker == 0x01) {
                continue;
            }
            if (position + 2 > bytes.length) {
                return 1;
            }
            int length = readUnsignedShort(bytes, position, false);
            if (length < 2 || position + length > bytes.length) {
                return 1;
            }
            int payload = position + 2;
            if (marker == 0xe1 && length >= 16 && matchesAscii(bytes, payload, "Exif\0\0")) {
                return parseTiffOrientation(bytes, payload + 6, position + length);
            }
            position += length;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] bytes, int tiff, int limit) {
        if (tiff + 8 > limit) {
            return 1;
        }
        boolean littleEndian;
        if (bytes[tiff] == 'I' && bytes[tiff + 1] == 'I') {
            littleEndian = true;
        } else if (bytes[tiff] == 'M' && bytes[tiff + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        int offset = readInt(bytes, tiff + 4, littleEndian);
        long ifdLong = (long) tiff + Integer.toUnsignedLong(offset);
        if (ifdLong < tiff || ifdLong + 2 > limit) {
            return 1;
        }
        int ifd = (int) ifdLong;
        int count = readUnsignedShort(bytes, ifd, littleEndian);
        for (int index = 0; index < count; index++) {
            int entry = ifd + 2 + index * 12;
            if (entry + 12 > limit) {
                return 1;
            }
            int tag = readUnsignedShort(bytes, entry, littleEndian);
            int type = readUnsignedShort(bytes, entry + 2, littleEndian);
            int values = readInt(bytes, entry + 4, littleEndian);
            if (tag == 0x0112 && type == 3 && values == 1) {
                int orientation = readUnsignedShort(bytes, entry + 8, littleEndian);
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
        }
        return 1;
    }

    private static int readUnsignedShort(byte[] bytes, int offset, boolean littleEndian) {
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        return littleEndian ? first | second << 8 : first << 8 | second;
    }

    private static int readInt(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return bytes[offset] & 0xff | (bytes[offset + 1] & 0xff) << 8
                    | (bytes[offset + 2] & 0xff) << 16 | (bytes[offset + 3] & 0xff) << 24;
        }
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8 | bytes[offset + 3] & 0xff;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAscii(byte[] bytes, int offset, String expected) {
        if (offset + expected.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if ((char) bytes[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static ImageInputException failure(ImageInputFailureCode code, String message) {
        return new ImageInputException(code, message);
    }

    private static ImageInputException failure(
            ImageInputFailureCode code,
            String message,
            Throwable cause
    ) {
        return new ImageInputException(code, message, cause);
    }

    private static void ensureNotCancelled(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("image processing was cancelled");
        }
    }

    private record DecodedImage(
            BufferedImage image,
            int sourceWidth,
            int sourceHeight,
            int width,
            int height,
            boolean metadataDetected,
            int orientation
    ) {
    }
}
