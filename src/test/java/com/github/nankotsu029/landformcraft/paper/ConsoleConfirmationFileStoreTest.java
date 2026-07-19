package com.github.nankotsu029.landformcraft.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleConfirmationFileStoreTest {

    @Test
    void storesOwnerOnlyFileAndReplacesOnRePlan(@TempDir Path temp) throws Exception {
        ConsoleConfirmationFileStore store = new ConsoleConfirmationFileStore(temp.resolve("confirmations"));
        Path first = store.store("r2-confirm-11111111-2222-4333-8444-555555555555",
                "/landformcraft r2 confirm 1111 tokenA");
        assertTrue(Files.isRegularFile(first));
        assertEquals("/landformcraft r2 confirm 1111 tokenA",
                Files.readString(first, StandardCharsets.UTF_8).strip());
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(first);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                permissions);

        Path second = store.store("r2-confirm-11111111-2222-4333-8444-555555555555",
                "/landformcraft r2 confirm 1111 tokenB");
        assertEquals(first, second);
        assertEquals("/landformcraft r2 confirm 1111 tokenB",
                Files.readString(second, StandardCharsets.UTF_8).strip());
        try (var stream = Files.list(temp.resolve("confirmations"))) {
            assertEquals(1, stream.count());
        }
    }

    @Test
    void discardRemovesConsumedConfirmation(@TempDir Path temp) throws Exception {
        ConsoleConfirmationFileStore store = new ConsoleConfirmationFileStore(temp.resolve("confirmations"));
        Path file = store.store("apply-execute-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", "cmd token");
        store.discard("apply-execute-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        assertFalse(Files.exists(file));
        store.discard("apply-execute-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    }

    @Test
    void rejectsUnsafeKeysAndBlankCommands(@TempDir Path temp) {
        ConsoleConfirmationFileStore store = new ConsoleConfirmationFileStore(temp);
        assertThrows(IllegalArgumentException.class, () -> store.store("../escape", "cmd"));
        assertThrows(IllegalArgumentException.class, () -> store.store("bad/slash", "cmd"));
        assertThrows(IllegalArgumentException.class, () -> store.store("", "cmd"));
        assertThrows(IllegalArgumentException.class, () -> store.store("ok-key", " "));
        assertFalse(Files.exists(temp.resolve("..").resolve("escape.command")));
    }
}
