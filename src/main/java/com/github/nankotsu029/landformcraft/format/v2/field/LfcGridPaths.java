package com.github.nankotsu029.landformcraft.format.v2.field;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

final class LfcGridPaths {
    private LfcGridPaths() {
    }

    static Path resolveForWrite(Path root, String relativePath) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        if (Files.exists(absoluteRoot, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectoryWithoutLink(absoluteRoot);
        } else {
            Files.createDirectories(absoluteRoot);
            requireDirectoryWithoutLink(absoluteRoot);
        }
        Path target = absoluteRoot.resolve(relativePath).normalize();
        if (!target.startsWith(absoluteRoot)) {
            throw new IOException("field artifact path escapes its root");
        }
        Path parent = target.getParent();
        Path current = absoluteRoot;
        Path relativeParent = absoluteRoot.relativize(parent);
        for (Path component : relativeParent) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectoryWithoutLink(current);
            } else {
                Files.createDirectory(current);
                requireDirectoryWithoutLink(current);
            }
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("canonical field artifact already exists: " + relativePath);
        }
        return target;
    }

    static Path resolveForRead(Path root, String relativePath) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        requireDirectoryWithoutLink(absoluteRoot);
        Path realRoot = absoluteRoot.toRealPath();
        Path target = absoluteRoot.resolve(relativePath).normalize();
        if (!target.startsWith(absoluteRoot)) {
            throw new IOException("field artifact path escapes its root");
        }
        Path current = absoluteRoot;
        for (Path component : absoluteRoot.relativize(target)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("field artifact path contains a symbolic link");
            }
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("field artifact is not a regular file");
        }
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(realRoot)) {
            throw new IOException("field artifact resolves outside its root");
        }
        return realTarget;
    }

    private static void requireDirectoryWithoutLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("field artifact directory is invalid or symbolic: " + path.getFileName());
        }
    }
}
