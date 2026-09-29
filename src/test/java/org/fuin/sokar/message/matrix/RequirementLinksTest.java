package org.fuin.sokar.message.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A requirement is linked by its number and its index, never by its file: a finished requirement is
 * deleted, and so is an issue closed unbuilt, and a link to the file breaks on both while one to the
 * index does not. Nothing else notices a broken link until somebody follows it.
 */
class RequirementLinksTest {

    private static final Path ROOT = Path.of("");

    /** The one place allowed to link issue files: the index, naming its own issues beside it. */
    private static final Path INDEX = Path.of("issues", "README.md");

    /** A Markdown link target, or a bare URL. */
    private static final Pattern TARGET = Pattern.compile("\\]\\(([^)\\s]+)\\)|(https?://[^\\s)>\\]]+)");

    /** An issue's file name: letters, a number, a dash and a title, as every Sokar repository names them. */
    private static final Pattern ISSUE_FILE = Pattern.compile("(^|/)[A-Z]{1,3}[0-9]+-[A-Za-z0-9-]+\\.md([#?].*)?$");

    @Test
    @DisplayName("No Markdown file links a requirement's or issue's file")
    void noLinkToAnIssueFile() throws IOException {
        final List<String> offenders = new ArrayList<>();
        for (final Path file : markdownFiles()) {
            offenders.addAll(linksToIssueFiles(file));
        }

        assertThat(offenders).as("links to an issue's file rather than to its index").isEmpty();
    }

    static List<String> linksToIssueFiles(final Path file) {
        final String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Cannot read " + file, ex);
        }
        final boolean isIndex = file.normalize().equals(INDEX);
        final List<String> offenders = new ArrayList<>();
        final Matcher m = TARGET.matcher(text);
        while (m.find()) {
            final String target = m.group(1) != null ? m.group(1) : m.group(2);
            if (!ISSUE_FILE.matcher(target).find()) {
                continue;
            }
            // The index links each of its own issues by the bare file name beside it.
            if (isIndex && !target.contains("/")) {
                continue;
            }
            offenders.add(file + ": " + target);
        }
        return offenders;
    }

    private static List<Path> markdownFiles() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT.toAbsolutePath())) {
            final Path base = ROOT.toAbsolutePath();
            return files.map(base::relativize)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(RequirementLinksTest::isCheckedIn)
                    .sorted()
                    .toList();
        }
    }

    /** Dot directories and build output are not checked in, so what they hold is not the repository's. */
    private static boolean isCheckedIn(final Path relative) {
        for (final Path part : relative) {
            final String name = part.toString();
            if (name.startsWith(".") || name.equals("target")) {
                return false;
            }
        }
        return true;
    }

}
