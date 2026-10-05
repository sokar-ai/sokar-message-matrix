package org.fuin.sokar.message.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Code, tests and everything that ships never cite an issue number, of this repository or another: an issue
 * is deleted once it is finished, and its number then points at nothing. They state the constraint itself.
 */
class IssueCitationTest {

    private static final Path ROOT = Path.of("");

    /**
     * What is read: the code, its tests, the packaging, the workflows, and the documentation and files a build
     * or a user reads.
     */
    private static final List<Path> CHECKED = List.of(Path.of("src"), Path.of("homeserver"), Path.of(".github"),
            Path.of("doc"), Path.of("README.md"), Path.of("CHANGELOG.md"), Path.of("pom.xml"), Path.of("settings.xml"),
            Path.of("mkdocs.yml"));

    /** An issue number as every Sokar repository writes one: a prefix of capitals, then two or three digits. */
    private static final Pattern NUMBER = Pattern.compile("\\b[A-Z]{1,2}[0-9]{2,3}\\b");

    @Test
    @DisplayName("Nothing that is code, test or shipped cites an issue number")
    void noIssueNumberCited() throws IOException {
        final List<String> offenders = new ArrayList<>();
        for (final Path file : checkedFiles()) {
            offenders.addAll(citations(file, Files.readString(file, StandardCharsets.UTF_8)));
        }

        assertThat(offenders).as("issue numbers cited where the constraint itself belongs").isEmpty();
    }

    @Test
    @DisplayName("Numbers with two and three digits are found, and a longer word or a version is not")
    void findsWhatItMust() {
        final String two = "MX" + "12";
        final String three = "B" + "114";
        final String text = "see " + two + " and " + three + ", not X509TrustManager, SHA256, v1.19 or TLS1";

        assertThat(citations(Path.of("t"), text)).as("both numbers, and nothing else")
                .containsExactly("t:1: " + two, "t:1: " + three);
    }

    static List<String> citations(final Path file, final String text) {
        final List<String> found = new ArrayList<>();
        final String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final Matcher m = NUMBER.matcher(lines[i]);
            while (m.find()) {
                found.add(file + ":" + (i + 1) + ": " + m.group());
            }
        }
        return found;
    }

    private static List<Path> checkedFiles() throws IOException {
        final List<Path> found = new ArrayList<>();
        for (final Path start : CHECKED) {
            final Path path = ROOT.resolve(start);
            if (Files.isRegularFile(path)) {
                found.add(path);
            } else if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                        found.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                throw new UncheckedIOException(new IOException(start + " is gone; the check would read nothing there"));
            }
        }
        assertThat(found).as("the files the check reads").isNotEmpty();
        return found.stream().sorted().toList();
    }

}
