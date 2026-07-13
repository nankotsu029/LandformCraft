package com.github.nankotsu029.landformcraft.preview;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.GridPosition;
import com.github.nankotsu029.landformcraft.model.StructurePlan;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;
import com.github.nankotsu029.landformcraft.model.TerrainFeature;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.ValidationIssue;
import com.github.nankotsu029.landformcraft.model.ValidationResult;
import com.github.nankotsu029.landformcraft.model.ValidationSeverity;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/** Writes bounded, one-image-at-a-time PNG previews to avoid retaining every render in memory. */
public final class TerrainPreviewRenderer {
    public PreviewArtifacts render(
            TerrainPlan plan,
            ValidationResult validation,
            Path outputDirectory,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Path directory = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        List<Path> files = new ArrayList<>();

        files.add(write(directory, "overview.png", plan, cancellationToken,
                (x, z) -> overviewColor(plan, x, z)));
        files.add(write(directory, "height.png", plan, cancellationToken,
                (x, z) -> heightColor(plan, x, z)));
        files.add(write(directory, "water.png", plan, cancellationToken,
                (x, z) -> waterColor(plan, x, z)));
        files.add(write(directory, "slope.png", plan, cancellationToken,
                (x, z) -> slopeColor(plan, x, z)));
        files.add(write(directory, "materials.png", plan, cancellationToken,
                (x, z) -> materialColor(plan.surfaceMaterials().get(x, z)).getRGB()));
        files.add(write(directory, "features.png", plan, cancellationToken,
                (x, z) -> featureColor(plan.featureMask().get(x, z))));
        files.add(writeStructures(directory, plan, cancellationToken));
        files.add(writeValidation(directory, plan, validation, cancellationToken));
        return new PreviewArtifacts(files);
    }

    private static Path write(
            Path directory,
            String fileName,
            TerrainPlan plan,
            CancellationToken token,
            IntBinaryOperator color
    ) throws IOException {
        int width = plan.blueprint().bounds().width();
        int length = plan.blueprint().bounds().length();
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, color.applyAsInt(x, z));
            }
        }
        return writeImage(directory, fileName, image);
    }

    private static Path writeStructures(Path directory, TerrainPlan plan, CancellationToken token)
            throws IOException {
        int width = plan.blueprint().bounds().width();
        int length = plan.blueprint().bounds().length();
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0, 0, 0, 255));
            graphics.fillRect(0, 0, width, length);
            for (StructurePlan structure : plan.structures()) {
                token.throwIfCancellationRequested();
                graphics.setColor(structure.preferredZoneFallback()
                        ? new Color(255, 72, 196, 255)
                        : new Color(255, 170, 32, 255));
                graphics.fillOval(structure.anchorX() - 3, structure.anchorZ() - 3, 7, 7);
            }
        } finally {
            graphics.dispose();
        }
        return writeImage(directory, "structures.png", image);
    }

    private static Path writeValidation(
            Path directory,
            TerrainPlan plan,
            ValidationResult validation,
            CancellationToken token
    ) throws IOException {
        int width = plan.blueprint().bounds().width();
        int length = plan.blueprint().bounds().length();
        int background = validation.isValid() ? new Color(38, 110, 62).getRGB() : new Color(45, 45, 45).getRGB();
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, background);
            }
        }
        for (ValidationIssue issue : validation.issues()) {
            GridPosition position = issue.position();
            if (position.equals(GridPosition.GLOBAL)) {
                continue;
            }
            int rgb = issue.severity() == ValidationSeverity.ERROR
                    ? new Color(230, 45, 45).getRGB()
                    : new Color(255, 196, 32).getRGB();
            if (position.x() < width && position.z() < length) {
                image.setRGB(position.x(), position.z(), rgb);
            }
        }
        return writeImage(directory, "validation.png", image);
    }

    private static int overviewColor(TerrainPlan plan, int x, int z) {
        int waterDepth = plan.waterDepthMap().get(x, z);
        if (waterDepth > 0) {
            int blue = Math.max(70, 220 - waterDepth * 6);
            return new Color(20, 90, blue).getRGB();
        }
        Color base = materialColor(plan.surfaceMaterials().get(x, z));
        int slope = slope(plan, x, z);
        double shade = Math.max(0.55, 1.0 - slope * 0.035);
        return new Color(
                (int) (base.getRed() * shade),
                (int) (base.getGreen() * shade),
                (int) (base.getBlue() * shade)
        ).getRGB();
    }

    private static int heightColor(TerrainPlan plan, int x, int z) {
        int minimum = plan.blueprint().bounds().minY();
        int span = plan.blueprint().bounds().verticalSpan() - 1;
        int gray = (plan.heightMap().get(x, z) - minimum) * 255 / span;
        return new Color(gray, gray, gray).getRGB();
    }

    private static int waterColor(TerrainPlan plan, int x, int z) {
        int depth = plan.waterDepthMap().get(x, z);
        if (depth == 0) {
            return Color.BLACK.getRGB();
        }
        return new Color(0, Math.max(35, 150 - depth * 3), Math.max(80, 255 - depth * 4)).getRGB();
    }

    private static int slopeColor(TerrainPlan plan, int x, int z) {
        int value = Math.min(255, slope(plan, x, z) * 24);
        return new Color(value, value, value).getRGB();
    }

    private static int slope(TerrainPlan plan, int x, int z) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        int center = plan.heightMap().get(x, z);
        int west = plan.heightMap().get(Math.max(0, x - 1), z);
        int east = plan.heightMap().get(Math.min(width - 1, x + 1), z);
        int north = plan.heightMap().get(x, Math.max(0, z - 1));
        int south = plan.heightMap().get(x, Math.min(length - 1, z + 1));
        return Math.max(Math.max(Math.abs(center - west), Math.abs(center - east)),
                Math.max(Math.abs(center - north), Math.abs(center - south)));
    }

    private static Color materialColor(SurfaceMaterial material) {
        return switch (material) {
            case GRASS -> new Color(77, 133, 65);
            case SAND -> new Color(218, 205, 145);
            case STONE -> new Color(116, 116, 116);
            case GRAVEL -> new Color(98, 102, 108);
            case MUD -> new Color(92, 70, 49);
            case SNOW -> new Color(235, 241, 244);
        };
    }

    private static int featureColor(int mask) {
        if (TerrainFeature.RIVER.isPresent(mask)) {
            return new Color(20, 135, 245).getRGB();
        }
        if (TerrainFeature.LAKE.isPresent(mask)) {
            return new Color(32, 90, 210).getRGB();
        }
        if (TerrainFeature.CLIFF.isPresent(mask)) {
            return new Color(180, 80, 60).getRGB();
        }
        if (TerrainFeature.VEGETATION.isPresent(mask)) {
            return new Color(40, 175, 70).getRGB();
        }
        if (TerrainFeature.COAST.isPresent(mask)) {
            return new Color(235, 210, 80).getRGB();
        }
        return Color.BLACK.getRGB();
    }

    private static Path writeImage(Path directory, String fileName, BufferedImage image) throws IOException {
        Path target = directory.resolve(fileName);
        Path temporary = Files.createTempFile(directory, fileName, ".tmp");
        try {
            if (!ImageIO.write(image, "PNG", temporary.toFile())) {
                throw new IOException("PNG writer is unavailable");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
