package com.github.nankotsu029.landformcraft.paper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Owner-only file delivery for confirmation commands issued to CONSOLE／RCON operators.
 *
 * <p>Messages sent to a non-player {@code CommandSender} are duplicated into the standard Paper
 * server log, so a confirmation token printed to CONSOLE persists in plaintext. This store keeps
 * the token out of the log: the full one-time command is written to
 * {@code <root>/<key>.command} with owner-only permissions (best effort on non-POSIX file
 * systems) and only the file path is echoed to the console. Files are one-time use and are
 * discarded on successful consumption; tokens themselves expire after their TTL regardless.</p>
 */
public final class ConsoleConfirmationFileStore {
    private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,120}");

    private final Path root;

    public ConsoleConfirmationFileStore(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /** Writes the confirmation command for {@code key} atomically and returns the file path. */
    public Path store(String key, String confirmationCommand) {
        String safeKey = requireSafeKey(key);
        Objects.requireNonNull(confirmationCommand, "confirmationCommand");
        if (confirmationCommand.isBlank()) {
            throw new IllegalArgumentException("confirmation command must not be blank");
        }
        try {
            Files.createDirectories(root);
            restrict(root, "rwx------");
            Path temp = Files.createTempFile(root, safeKey + "-", ".tmp");
            try {
                restrict(temp, "rw-------");
                Files.writeString(temp, confirmationCommand + System.lineSeparator(),
                        StandardCharsets.UTF_8);
                Path target = root.resolve(safeKey + ".command");
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return target;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("確認コマンドの保存に失敗しました", exception);
        }
    }

    /** Best-effort removal after the confirmation was consumed (tokens also expire by TTL). */
    public void discard(String key) {
        try {
            Files.deleteIfExists(root.resolve(requireSafeKey(key) + ".command"));
        } catch (IOException ignored) {
            // The token inside the file has already been consumed or expired; leaving the file
            // behind only wastes bytes and must not fail the completed operation.
        }
    }

    private static String requireSafeKey(String key) {
        Objects.requireNonNull(key, "key");
        String normalized = key.toLowerCase(Locale.ROOT);
        if (!SAFE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("unsafe confirmation file key: " + key);
        }
        return normalized;
    }

    private static void restrict(Path path, String posixPermissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posixPermissions));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX file system (e.g. Windows): fall back to default ACLs.
        }
    }
}
