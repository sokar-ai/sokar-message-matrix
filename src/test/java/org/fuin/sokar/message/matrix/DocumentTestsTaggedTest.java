package org.fuin.sokar.message.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A change to documents or issues runs only the tests tagged {@code documents}; a test that reads a document
 * without the tag is left out of that run, and the run stays green while the document it checks is broken.
 */
@Tag("documents")
class DocumentTestsTaggedTest {

    private static final Path TESTS = Path.of("src", "test", "java");

    /** How a test reaches the documents: the documentation chapter, its navigation, the issues, or any Markdown file. */
    private static final Pattern READS_DOCUMENTS =
            Pattern.compile("Path\\.of\\(\"(doc|issues)\"|mkdocs\\.yml|\\.md\"");

    private static final String TAG = "@Tag(\"documents\")";

    @Test
    @DisplayName("Every test that reads a document is tagged documents")
    void everyTestThatReadsADocumentIsTagged() throws IOException {
        final List<Path> found = testsReadingDocuments();

        // A guard that finds nothing passes forever: assert what it found, then what it checks.
        assertThat(found).extracting(path -> path.getFileName().toString())
                .as("tests found reading documents")
                .contains("RequirementLinksTest.java", "IssueCitationTest.java");
        assertThat(found).filteredOn(path -> !read(path).contains(TAG))
                .as("tests that read a document without %s", TAG)
                .isEmpty();
    }

    private static List<Path> testsReadingDocuments() throws IOException {
        try (Stream<Path> sources = Files.walk(TESTS)) {
            return sources.filter(path -> path.getFileName().toString().endsWith("Test.java"))
                    .filter(path -> READS_DOCUMENTS.matcher(read(path)).find())
                    .sorted()
                    .toList();
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Cannot read " + path, ex);
        }
    }

}
