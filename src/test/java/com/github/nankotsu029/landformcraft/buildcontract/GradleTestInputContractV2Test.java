package com.github.nankotsu029.landformcraft.buildcontract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Build-input contract for the filesystem inventory tests (V2-19-02).
 *
 * <p>The 2026-07-23 cross-cutting audit measured the defect this pins: the same working tree yielded
 * a passing {@code SchemaContractTest} when Gradle reused the task result and a failing one when the
 * task actually ran, because the roots the test walks were not declared as task inputs. Declaring
 * them is what makes the two verdicts identical, with or without the build cache — an executed run
 * and a reused run then answer the same question about the same bytes.</p>
 *
 * <p>Both directions are checked here. The declaration in {@code build.gradle.kts} must equal
 * {@link FilesystemInventoryRootsV2#SCANNED_ROOTS}, and no test source may read a repository-root
 * entry that the declaration does not cover.</p>
 */
class GradleTestInputContractV2Test {
    private static final Path BUILD_SCRIPT = Path.of("build.gradle.kts");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Pattern ROOT_LIST =
            Pattern.compile("val filesystemInventoryRoots = listOf\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern PATH_OF_LITERAL = Pattern.compile("Path\\.of\\(\\s*\"([^\"]+)\"");

    /**
     * Repository-root entries a test may read without being an inventory input. {@code build} is
     * this build's own output directory: the one artifact whose content decides a verdict is the
     * shaded plugin jar, and that is declared separately as the {@code pluginJar} input. Everything
     * else tests touch under {@code build} is scratch space they create themselves.
     */
    private static final Set<String> ALLOWED_UNDECLARED_ROOTS = Set.of("build");

    @Test
    void buildScriptDeclaresExactlyTheScannedInventoryRoots() throws Exception {
        String script = Files.readString(BUILD_SCRIPT, StandardCharsets.UTF_8);
        Matcher list = ROOT_LIST.matcher(script);
        assertTrue(list.find(), "build.gradle.kts no longer declares val filesystemInventoryRoots");

        List<String> declared = new ArrayList<>();
        Matcher literal = STRING_LITERAL.matcher(list.group(1));
        while (literal.find()) {
            declared.add(literal.group(1));
        }
        assertEquals(FilesystemInventoryRootsV2.SCANNED_ROOTS, declared,
                "build.gradle.kts filesystemInventoryRoots and FilesystemInventoryRootsV2.SCANNED_ROOTS disagree");
    }

    @Test
    void theTestTaskConsumesTheDeclaredRootsAsRelativeInputs() throws Exception {
        String script = Files.readString(BUILD_SCRIPT, StandardCharsets.UTF_8);
        int testBlock = script.indexOf("\n    test {");
        assertTrue(testBlock >= 0, "build.gradle.kts no longer configures the test task");
        String tail = script.substring(testBlock);

        assertTrue(tail.contains("inputs.files(filesystemInventoryRoots)"),
                "the test task does not consume filesystemInventoryRoots as an input");
        assertTrue(tail.contains(".withPropertyName(\"filesystemInventoryRoots\")"),
                "the inventory input has no stable property name");
        assertTrue(tail.contains(".withPathSensitivity(PathSensitivity.RELATIVE)"),
                "the inventory input is not normalized relative to the project directory");
    }

    /**
     * Drift guard for new tests: any repository-root entry a test reads through a literal path must
     * be a declared inventory input, otherwise the same hidden-failure mode returns for that test.
     */
    @Test
    void noTestSourceReadsAnUndeclaredRepositoryRoot() throws Exception {
        Set<String> covered = new TreeSet<>(FilesystemInventoryRootsV2.SCANNED_ROOTS);
        Set<String> undeclared = new TreeSet<>();
        for (Path source : testSources()) {
            Matcher matcher = PATH_OF_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String root = Path.of(matcher.group(1)).getName(0).toString();
                if (root.equals(".") || covered.contains(root) || ALLOWED_UNDECLARED_ROOTS.contains(root)) {
                    continue;
                }
                // Names that do not exist at the repository root are resolved against a @TempDir or
                // another test-created directory, so they are not working-tree inputs.
                if (Files.exists(Path.of(root))) {
                    undeclared.add(root + " (" + source + ")");
                }
            }
        }
        assertEquals(Set.of(), undeclared,
                "test sources read repository roots that the test task does not declare as inputs");
    }

    private static List<Path> testSources() throws Exception {
        try (var walk = Files.walk(TEST_SOURCES)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }
}
