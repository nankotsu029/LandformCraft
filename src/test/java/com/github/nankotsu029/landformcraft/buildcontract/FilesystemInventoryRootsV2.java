package com.github.nankotsu029.landformcraft.buildcontract;

import java.nio.file.Path;
import java.util.List;

/**
 * Single source of truth for the working-tree roots that the filesystem inventory tests read at run
 * time (V2-19-02).
 *
 * <p>An inventory test derives its verdict from whatever happens to be on disk under these roots,
 * so the Gradle {@code test} task must declare exactly the same roots as task inputs. When a root is
 * scanned but undeclared, an edit under it (an untracked file included) leaves the task
 * {@code UP-TO-DATE} or served from the build cache, and a stale success hides the failure the same
 * working tree would produce on a fresh run. {@link GradleTestInputContractV2Test} pins both
 * directions: the declaration in {@code build.gradle.kts} equals {@link #SCANNED_ROOTS}, and no test
 * source reads a repository-root entry that {@link #SCANNED_ROOTS} does not cover.</p>
 *
 * <p>This type carries no verdict logic. The inventory tests keep their own rules unchanged; they
 * only name their roots from here so the two lists cannot drift apart.</p>
 */
public final class FilesystemInventoryRootsV2 {
    /** Java, resource and test sources; read as text by the reference-corpus and boundary checks. */
    public static final Path SRC = Path.of("src");

    /** Markdown corpus: link/anchor resolution and the registry drift checks. */
    public static final Path DOCS = Path.of("docs");

    /** JSON Schema inventory compared against the bundled and packaged schema sets. */
    public static final Path SCHEMAS = Path.of("schemas");

    /** Example document inventory checked for orphans. */
    public static final Path EXAMPLES = Path.of("examples");

    /** Repository-root Markdown documents walked together with {@link #DOCS}. */
    public static final List<String> ROOT_MARKDOWN_DOCUMENTS =
            List.of("README.md", "AGENTS.md", "CHANGELOG.md");

    /**
     * Text corpus searched for example references. {@code README.md} is part of it because the
     * repository-root README names fixtures that no other document names.
     */
    public static final List<Path> REFERENCE_CORPUS = List.of(SRC, DOCS, Path.of("README.md"));

    /**
     * Every root above, sorted, as declared in {@code build.gradle.kts}'s
     * {@code filesystemInventoryRoots}.
     */
    public static final List<String> SCANNED_ROOTS = List.of(
            "AGENTS.md",
            "CHANGELOG.md",
            "README.md",
            "build.gradle.kts",
            "docs",
            "examples",
            "schemas",
            "src");

    private FilesystemInventoryRootsV2() {
    }
}
