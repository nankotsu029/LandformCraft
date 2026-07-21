package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.github.nankotsu029.landformcraft.format.Sha256;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Secure filesystem envelope that turns untrusted PNG/JPEG files into sanitized ARGB rasters for
 * deterministic extraction. Mirrors the retired ReferenceImageProcessor path/link/magic/byte/
 * pixel/frame/EXIF boundary without AI Provider normalization or Request Schema coupling.
 */
public final class SecureImageExtractionEnvelopeV2 {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final Pattern PORTABLE_PATH = Pattern.compile("[A-Za-z0-9._/-]{1,512}");
    private static final int READ_BUFFER_BYTES = 64 * 1024;

    private final SourceReadObserver observer;

    public SecureImageExtractionEnvelopeV2() {
        this((phase, source) -> { });
    }

    SecureImageExtractionEnvelopeV2(SourceReadObserver observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    /**
     * Loads and sanitizes one or more images under the request root. Aggregate byte/pixel budgets
     * and hardlink-alias checks apply across the whole list before any decode allocates ARGB.
     */
    public List<SanitizedArgbImageV2> load(
            Path requestPath,
            List<String> relativePaths,
            ImageExtractionInputLimitsV2 limits,
            BooleanSupplier cancellationRequested
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(relativePaths, "relativePaths");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (relativePaths.isEmpty()) {
            throw failure(ImageExtractionInputFailureCodeV2.INVALID_PATH_DESCRIPTOR,
                    "at least one extraction image path is required");
        }

        Path root = Objects.requireNonNull(
                requestPath.toAbsolutePath().normalize().getParent(), "request path must have a parent");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw failure(ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                    "extraction request root must be a non-symbolic directory");
        }

        List<PreflightSource> preflight = new ArrayList<>(relativePaths.size());
        Set<String> seenPaths = new HashSet<>();
        Set<Object> fileKeys = new HashSet<>();
        List<Path> pathsWithoutFileKeys = new ArrayList<>();
        long totalBytes = 0L;
        long maximumSourceBytes = 0L;
        for (String relativePath : relativePaths) {
            ensureNotCancelled(cancellationRequested);
            Objects.requireNonNull(relativePath, "relativePath");
            validateRelativePath(relativePath);
            if (!seenPaths.add(relativePath)) {
                throw failure(ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                        "duplicate extraction image path");
            }
            Path source = resolveSafe(root, relativePath);
            BasicFileAttributes before = attributes(source, relativePath);
            if (!before.isRegularFile() || Files.isSymbolicLink(source)) {
                throw failure(ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                        "extraction image must be a regular file");
            }
            checkFileIdentity(source, before.fileKey(), fileKeys, pathsWithoutFileKeys);
            if (before.size() < 1 || before.size() > limits.maximumSourceBytes()) {
                throw failure(ImageExtractionInputFailureCodeV2.FILE_TOO_LARGE,
                        "extraction image byte size is outside the limit");
            }
            totalBytes = addExact(totalBytes, before.size(),
                    ImageExtractionInputFailureCodeV2.TOTAL_BYTES_EXCEEDED,
                    "extraction image total source bytes overflow");
            if (totalBytes > limits.maximumTotalSourceBytes()) {
                throw failure(ImageExtractionInputFailureCodeV2.TOTAL_BYTES_EXCEEDED,
                        "extraction image total source bytes exceed the limit");
            }
            maximumSourceBytes = Math.max(maximumSourceBytes, before.size());
            preflight.add(new PreflightSource(relativePath, source, before));
        }

        long loadingPeak = addExact(totalBytes, maximumSourceBytes,
                ImageExtractionInputFailureCodeV2.DECODE_BUDGET_EXCEEDED,
                "extraction image loading working-set estimate overflow");
        loadingPeak = addExact(loadingPeak, READ_BUFFER_BYTES,
                ImageExtractionInputFailureCodeV2.DECODE_BUDGET_EXCEEDED,
                "extraction image loading working-set estimate overflow");
        if (loadingPeak > limits.maximumDecodeWorkingBytes()) {
            throw failure(ImageExtractionInputFailureCodeV2.DECODE_BUDGET_EXCEEDED,
                    "extraction image loading working set exceeds the limit");
        }

        List<LoadedSource> loaded = new ArrayList<>(preflight.size());
        for (PreflightSource prepared : preflight) {
            ensureNotCancelled(cancellationRequested);
            byte[] bytes = readLimited(prepared.source(), prepared.attributes(), limits, cancellationRequested);
            String mediaType = detectMediaType(bytes, prepared.relativePath());
            verifyExtension(prepared.relativePath(), mediaType);
            rejectAnimatedPng(bytes, mediaType);
            loaded.add(new LoadedSource(
                    prepared.relativePath(), mediaType, Sha256.bytes(bytes), bytes));
        }

        List<SanitizedArgbImageV2> sanitized = new ArrayList<>(loaded.size());
        long totalPixels = 0L;
        for (LoadedSource source : loaded) {
            ensureNotCancelled(cancellationRequested);
            SanitizedArgbImageV2 image = decode(source, limits, cancellationRequested);
            totalPixels = addExact(totalPixels, (long) image.width() * image.length(),
                    ImageExtractionInputFailureCodeV2.PIXELS_EXCEEDED,
                    "extraction image total pixels overflow");
            if (totalPixels > limits.maximumTotalPixels()) {
                throw failure(ImageExtractionInputFailureCodeV2.PIXELS_EXCEEDED,
                        "extraction image total decoded pixels exceed the limit");
            }
            sanitized.add(image);
        }
        return List.copyOf(sanitized);
    }

    /** Single-file convenience wrapper around {@link #load}. */
    public SanitizedArgbImageV2 loadOne(
            Path requestPath,
            String relativePath,
            ImageExtractionInputLimitsV2 limits,
            BooleanSupplier cancellationRequested
    ) {
        return load(requestPath, List.of(relativePath), limits, cancellationRequested).getFirst();
    }

    /**
     * Strict path from a real PNG/JPEG file through the envelope into the land-water draft core.
     * Provenance is {@code sourceChecksum → draft.sourceChecksum → draft.semanticChecksum}.
     */
    public ExtractedMaskDraftV2 loadAndExtractLandWater(
            Path requestPath,
            String relativePath,
            ImageExtractionInputLimitsV2 inputLimits,
            ImageMaskExtractionLimitsV2 extractLimits,
            BooleanSupplier cancellationRequested
    ) {
        SanitizedArgbImageV2 image = loadOne(requestPath, relativePath, inputLimits, cancellationRequested);
        return ImageLandWaterExtractorV2.extract(
                image.width(),
                image.length(),
                image.argbPixels(),
                image.sourceChecksum(),
                extractLimits,
                cancellationRequested
        );
    }

    /**
     * Strict path from a real PNG/JPEG file through the envelope into the height-guide draft core.
     * Provenance is {@code sourceChecksum → draft.sourceChecksum → draft.semanticChecksum}.
     */
    public ExtractedHeightGuideDraftV2 loadAndExtractHeightGuide(
            Path requestPath,
            String relativePath,
            ImageExtractionInputLimitsV2 inputLimits,
            ImageMaskExtractionLimitsV2 extractLimits,
            BooleanSupplier cancellationRequested
    ) {
        SanitizedArgbImageV2 image = loadOne(requestPath, relativePath, inputLimits, cancellationRequested);
        return ImageHeightGuideExtractorV2.extract(
                image.width(),
                image.length(),
                image.argbPixels(),
                image.sourceChecksum(),
                extractLimits,
                cancellationRequested
        );
    }

    /**
     * Strict path from a real PNG/JPEG file through the envelope into the zone-label draft core.
     * Provenance is {@code sourceChecksum → draft.sourceChecksum → draft.semanticChecksum}.
     */
    public ExtractedZoneLabelDraftV2 loadAndExtractZoneLabel(
            Path requestPath,
            String relativePath,
            ImageExtractionInputLimitsV2 inputLimits,
            ImageMaskExtractionLimitsV2 extractLimits,
            BooleanSupplier cancellationRequested
    ) {
        SanitizedArgbImageV2 image = loadOne(requestPath, relativePath, inputLimits, cancellationRequested);
        return ImageZoneLabelExtractorV2.extract(
                image.width(),
                image.length(),
                image.argbPixels(),
                image.sourceChecksum(),
                extractLimits,
                cancellationRequested
        );
    }

    private SanitizedArgbImageV2 decode(
            LoadedSource source,
            ImageExtractionInputLimitsV2 limits,
            BooleanSupplier cancellationRequested
    ) {
        byte[] bytes = source.bytes();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw failure(ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                        "extraction image has no decoder");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.contains("png") || format.contains("jpeg") || format.contains("jpg"))) {
                    throw failure(ImageExtractionInputFailureCodeV2.UNSUPPORTED_FORMAT,
                            "extraction decoder format is not PNG/JPEG");
                }
                int images = reader.getNumImages(true);
                if (images != 1) {
                    throw failure(ImageExtractionInputFailureCodeV2.MULTI_FRAME,
                            "multi-frame extraction images are not allowed");
                }
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                requireSourceDimensions(sourceWidth, sourceHeight, limits);
                ensureNotCancelled(cancellationRequested);

                long sourcePixels = (long) sourceWidth * sourceHeight;
                long decodeWorking = addExact(
                        sourcePixels * ImageExtractionInputLimitsV2.ARGB_WORKING_BYTES_PER_PIXEL,
                        ImageExtractionInputLimitsV2.DECODE_OVERHEAD_BYTES,
                        ImageExtractionInputFailureCodeV2.WORKING_BUDGET_EXCEEDED,
                        "extraction decode working-set estimate overflow");
                if (decodeWorking > limits.maximumDecodeWorkingBytes()) {
                    throw failure(ImageExtractionInputFailureCodeV2.WORKING_BUDGET_EXCEEDED,
                            "extraction decode working set exceeds the limit");
                }

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw failure(ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                            "extraction decoder returned no image");
                }
                int orientation = "image/jpeg".equals(source.mediaType()) ? exifOrientation(bytes) : 1;
                int[] argb = orientToArgb(decoded, orientation, cancellationRequested);
                int width = orientation >= 5 ? sourceHeight : sourceWidth;
                int length = orientation >= 5 ? sourceWidth : sourceHeight;
                if (argb.length != Math.multiplyExact(width, length)) {
                    throw failure(ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                            "oriented ARGB buffer does not match dimensions");
                }
                requireSourceDimensions(width, length, limits);
                return new SanitizedArgbImageV2(
                        source.relativePath(),
                        source.mediaType(),
                        width,
                        length,
                        argb,
                        source.checksum(),
                        orientation,
                        hasMetadata(bytes, source.mediaType())
                );
            } finally {
                reader.dispose();
            }
        } catch (ImageExtractionInputExceptionV2 exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ImageExtractionInputExceptionV2(
                    ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                    "extraction image decode failed",
                    exception);
        }
    }

    private static void requireSourceDimensions(int width, int height, ImageExtractionInputLimitsV2 limits) {
        if (width < 1 || height < 1
                || width > limits.maximumDimension()
                || height > limits.maximumDimension()) {
            throw failure(ImageExtractionInputFailureCodeV2.DIMENSIONS_EXCEEDED,
                    "extraction image dimensions exceed the limit");
        }
        long pixels = (long) width * height;
        if (pixels > limits.maximumPixelsPerImage()) {
            throw failure(ImageExtractionInputFailureCodeV2.PIXELS_EXCEEDED,
                    "extraction image pixels exceed the limit");
        }
        int shorter = Math.min(width, height);
        int longer = Math.max(width, height);
        if ((long) longer > (long) shorter * limits.maximumAspectRatio()) {
            throw failure(ImageExtractionInputFailureCodeV2.ASPECT_RATIO_EXCEEDED,
                    "extraction image aspect ratio exceeds the limit");
        }
    }

    private static int[] orientToArgb(
            BufferedImage source,
            int orientation,
            BooleanSupplier cancellationRequested
    ) {
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = orientation >= 5 ? height : width;
        int targetHeight = orientation >= 5 ? width : height;
        int[] target = new int[Math.multiplyExact(targetWidth, targetHeight)];
        for (int y = 0; y < height; y++) {
            if ((y & 31) == 0) {
                ensureNotCancelled(cancellationRequested);
            }
            for (int x = 0; x < width; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 1 -> {
                        targetX = x;
                        targetY = y;
                    }
                    case 2 -> {
                        targetX = width - 1 - x;
                        targetY = y;
                    }
                    case 3 -> {
                        targetX = width - 1 - x;
                        targetY = height - 1 - y;
                    }
                    case 4 -> {
                        targetX = x;
                        targetY = height - 1 - y;
                    }
                    case 5 -> {
                        targetX = y;
                        targetY = x;
                    }
                    case 6 -> {
                        targetX = height - 1 - y;
                        targetY = x;
                    }
                    case 7 -> {
                        targetX = height - 1 - y;
                        targetY = width - 1 - x;
                    }
                    case 8 -> {
                        targetX = y;
                        targetY = width - 1 - x;
                    }
                    default -> throw failure(ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                            "invalid EXIF orientation");
                }
                target[targetY * targetWidth + targetX] = source.getRGB(x, y);
            }
        }
        return target;
    }

    private byte[] readLimited(
            Path source,
            BasicFileAttributes before,
            ImageExtractionInputLimitsV2 limits,
            BooleanSupplier cancellationRequested
    ) {
        try (SeekableByteChannel channel = Files.newByteChannel(
                source, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(before.size()))) {
            observer.onPhase(ReadPhase.AFTER_OPEN_BEFORE_READ, source);
            ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_BYTES);
            long readTotal = 0L;
            while (true) {
                ensureNotCancelled(cancellationRequested);
                int read = channel.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                readTotal = addExact(readTotal, read,
                        ImageExtractionInputFailureCodeV2.FILE_TOO_LARGE,
                        "extraction image byte count overflow");
                if (readTotal > limits.maximumSourceBytes()) {
                    throw failure(ImageExtractionInputFailureCodeV2.FILE_TOO_LARGE,
                            "extraction image exceeds the byte limit");
                }
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
            observer.onPhase(ReadPhase.AFTER_READ_BEFORE_RESTAT, source);
            ensureNotCancelled(cancellationRequested);
            BasicFileAttributes after = attributes(source, "source");
            if (readTotal != before.size()
                    || !after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                throw failure(ImageExtractionInputFailureCodeV2.SOURCE_CHANGED,
                        "extraction image changed while being read");
            }
            return output.toByteArray();
        } catch (ImageExtractionInputExceptionV2 exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ImageExtractionInputExceptionV2(
                    ImageExtractionInputFailureCodeV2.MISSING_FILE,
                    "failed to read extraction image",
                    exception);
        }
    }

    private static Path resolveSafe(Path root, String relative) {
        Path candidate;
        try {
            candidate = root.resolve(relative).normalize();
        } catch (RuntimeException exception) {
            throw new ImageExtractionInputExceptionV2(
                    ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                    "extraction image path is invalid",
                    exception);
        }
        if (!candidate.startsWith(root)) {
            throw failure(ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                    "extraction image path escapes its request root");
        }
        Path current = root;
        for (Path segment : Path.of(relative)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw failure(ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                        "symbolic links are not allowed for extraction images");
            }
        }
        return candidate;
    }

    private static void validateRelativePath(String value) {
        if (!PORTABLE_PATH.matcher(value).matches() || value.indexOf('\0') >= 0
                || value.startsWith("/") || value.startsWith("\\") || value.indexOf('\\') >= 0
                || value.length() >= 2 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':') {
            throw failure(ImageExtractionInputFailureCodeV2.INVALID_PATH_DESCRIPTOR,
                    "extraction image path must be portable and relative");
        }
        try {
            if (Path.of(value).isAbsolute()) {
                throw failure(ImageExtractionInputFailureCodeV2.INVALID_PATH_DESCRIPTOR,
                        "extraction image path must be portable and relative");
            }
        } catch (InvalidPathException exception) {
            throw new ImageExtractionInputExceptionV2(
                    ImageExtractionInputFailureCodeV2.INVALID_PATH_DESCRIPTOR,
                    "extraction image path is invalid",
                    exception);
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw failure(ImageExtractionInputFailureCodeV2.INVALID_PATH_DESCRIPTOR,
                        "extraction image path contains an unsafe segment");
            }
        }
    }

    private static BasicFileAttributes attributes(Path source, String label) {
        try {
            return Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new ImageExtractionInputExceptionV2(
                    ImageExtractionInputFailureCodeV2.MISSING_FILE,
                    "extraction image is missing: " + label,
                    exception);
        }
    }

    private static void checkFileIdentity(
            Path source,
            Object fileKey,
            Set<Object> fileKeys,
            List<Path> pathsWithoutFileKeys
    ) {
        if (fileKey != null) {
            if (!fileKeys.add(fileKey)) {
                throw failure(ImageExtractionInputFailureCodeV2.HARD_LINK_ALIAS,
                        "multiple extraction image paths reference the same file");
            }
            return;
        }
        for (Path previous : pathsWithoutFileKeys) {
            try {
                if (Files.isSameFile(previous, source)) {
                    throw failure(ImageExtractionInputFailureCodeV2.HARD_LINK_ALIAS,
                            "multiple extraction image paths reference the same file");
                }
            } catch (IOException exception) {
                throw new ImageExtractionInputExceptionV2(
                        ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                        "extraction image file identity could not be verified",
                        exception);
            }
        }
        pathsWithoutFileKeys.add(source);
    }

    private static String detectMediaType(byte[] bytes, String relative) {
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        throw failure(ImageExtractionInputFailureCodeV2.INVALID_MAGIC,
                "extraction image magic is not PNG or JPEG: " + relative);
    }

    private static void verifyExtension(String relative, String mediaType) {
        String lower = relative.toLowerCase(Locale.ROOT);
        boolean valid = "image/png".equals(mediaType) && lower.endsWith(".png")
                || "image/jpeg".equals(mediaType) && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
        if (!valid) {
            throw failure(ImageExtractionInputFailureCodeV2.UNSUPPORTED_FORMAT,
                    "extraction image extension and content disagree: " + relative);
        }
    }

    /** APNG animation control is multi-frame even when ImageIO reports a single PNG frame. */
    private static void rejectAnimatedPng(byte[] bytes, String mediaType) {
        if (!"image/png".equals(mediaType)) {
            return;
        }
        int position = PNG_SIGNATURE.length;
        while (position + 12 <= bytes.length) {
            int length = readInt(bytes, position, false);
            if (length < 0 || position + 12L + length > bytes.length) {
                throw failure(ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                        "extraction PNG chunk structure is corrupt");
            }
            if (bytes[position + 4] == 'a'
                    && bytes[position + 5] == 'c'
                    && bytes[position + 6] == 'T'
                    && bytes[position + 7] == 'L') {
                throw failure(ImageExtractionInputFailureCodeV2.MULTI_FRAME,
                        "animated PNG extraction images are not allowed");
            }
            if (bytes[position + 4] == 'I'
                    && bytes[position + 5] == 'E'
                    && bytes[position + 6] == 'N'
                    && bytes[position + 7] == 'D') {
                return;
            }
            position += 12 + length;
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

    private static long addExact(
            long first,
            long second,
            ImageExtractionInputFailureCodeV2 code,
            String message
    ) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new ImageExtractionInputExceptionV2(code, message, exception);
        }
    }

    private static void ensureNotCancelled(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("extraction image loading was cancelled");
        }
    }

    private static ImageExtractionInputExceptionV2 failure(
            ImageExtractionInputFailureCodeV2 code,
            String message
    ) {
        return new ImageExtractionInputExceptionV2(code, message);
    }

    enum ReadPhase {
        AFTER_OPEN_BEFORE_READ,
        AFTER_READ_BEFORE_RESTAT
    }

    @FunctionalInterface
    interface SourceReadObserver {
        void onPhase(ReadPhase phase, Path source) throws IOException;
    }

    private record PreflightSource(String relativePath, Path source, BasicFileAttributes attributes) {
    }

    private record LoadedSource(String relativePath, String mediaType, String checksum, byte[] bytes) {
    }
}
