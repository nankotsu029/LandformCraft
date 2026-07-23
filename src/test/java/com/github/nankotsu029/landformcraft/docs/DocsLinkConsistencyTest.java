package com.github.nankotsu029.landformcraft.docs;

import com.github.nankotsu029.landformcraft.buildcontract.FilesystemInventoryRootsV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Anchor checker for the Markdown corpus. Every relative link must resolve to a file that exists,
 * and every {@code #fragment} into a Markdown file must resolve to a heading slug or an explicit
 * {@code <a id="…">} anchor. Renaming a heading without fixing its inbound links fails here.
 */
class DocsLinkConsistencyTest {
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)\\s]+)\\)");
    private static final Pattern EXPLICIT_ANCHOR = Pattern.compile("<a id=\"([^\"]+)\"");

    @Test
    void everyRelativeMarkdownLinkAndAnchorResolves() throws Exception {
        List<String> broken = new ArrayList<>();
        for (Path document : markdownFiles()) {
            String text = Files.readString(document, StandardCharsets.UTF_8);
            Matcher matcher = LINK.matcher(text);
            while (matcher.find()) {
                String target = matcher.group(1);
                if (target.startsWith("http://") || target.startsWith("https://")
                        || target.startsWith("mailto:")) {
                    continue;
                }
                int hash = target.indexOf('#');
                String file = hash < 0 ? target : target.substring(0, hash);
                String fragment = hash < 0 ? "" : target.substring(hash + 1);
                Path resolved = file.isEmpty()
                        ? document
                        : document.getParent().resolve(file).normalize();
                if (!Files.isRegularFile(resolved)) {
                    broken.add(document + " -> " + target + " (missing file)");
                    continue;
                }
                if (fragment.isEmpty() || !resolved.toString().endsWith(".md")) {
                    continue;
                }
                if (!anchors(resolved).contains(fragment)) {
                    broken.add(document + " -> " + target + " (missing anchor)");
                }
            }
        }
        assertEquals(List.of(), broken, "unresolvable Markdown links");
    }

    private static Set<String> anchors(Path document) throws Exception {
        String text = Files.readString(document, StandardCharsets.UTF_8);
        Set<String> anchors = new HashSet<>();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("#")) {
                anchors.add(slug(line.replaceFirst("^#+", "")));
            }
        }
        Matcher explicit = EXPLICIT_ANCHOR.matcher(text);
        while (explicit.find()) {
            anchors.add(explicit.group(1));
        }
        return anchors;
    }

    /** GitHub heading slug: lower-cased, punctuation dropped, spaces folded to hyphens. */
    private static String slug(String heading) {
        StringBuilder slug = new StringBuilder();
        for (char character : heading.strip().toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(character) || character == '-') {
                slug.append(character);
            } else if (character == ' ') {
                slug.append('-');
            }
        }
        return slug.toString();
    }

    private static List<Path> markdownFiles() throws Exception {
        List<Path> documents = new ArrayList<>();
        try (var walk = Files.walk(FilesystemInventoryRootsV2.DOCS)) {
            documents.addAll(walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .sorted()
                    .toList());
        }
        for (String root : FilesystemInventoryRootsV2.ROOT_MARKDOWN_DOCUMENTS) {
            // Keep a parent component so relative link resolution works for repository-root documents.
            Path path = Path.of(".", root);
            if (Files.isRegularFile(path)) {
                documents.add(path);
            }
        }
        return documents;
    }
}
