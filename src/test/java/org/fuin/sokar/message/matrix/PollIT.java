package org.fuin.sokar.message.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code poll} against the real homeserver the transport is built for, on loopback in podman. Each test
 * gets its own homeserver, so what one delivers cannot leak into another.
 */
class PollIT {

    private static final byte[] SIGNATURE = {0, 1, 2, (byte) 0xfe, (byte) 0xff, '\n', '-', '-'};

    @TempDir
    private Path dir;

    private Tuwunel tuwunel;

    private Tuwunel.Account alice;

    private Tuwunel.Account bob;

    private String room;

    private Path inbound;

    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        tuwunel = Tuwunel.start();
        alice = tuwunel.register("alice");
        bob = tuwunel.register("bob");
        room = tuwunel.createRoom(alice, bob);
        inbound = Files.createDirectories(dir.resolve("inbound"));
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        tuwunel.close();
    }

    private Map<String, String> env(final Tuwunel.Account account) {
        return Map.of(Config.HOMESERVER, tuwunel.url(), Config.ACCESS_TOKEN, account.accessToken(),
                "XDG_STATE_HOME", dir.resolve("state").toString());
    }

    private void send(final String message) throws IOException {
        send(message, SIGNATURE);
    }

    private void send(final String message, final byte[] signature) throws IOException {
        final Path file = Files.writeString(dir.resolve("out"), message, StandardCharsets.UTF_8);
        final Path sig = Files.write(dir.resolve("out.sig"), signature);
        assertThat(Transport.run(new String[] {"send", file.toString(), sig.toString(), "--to", room}, env(alice),
                new PrintStream(OutputStream.nullOutputStream()), new PrintStream(errBytes, true, StandardCharsets.UTF_8))).as(this::stderr).isEqualTo(Exit.OK);
    }

    private int poll() {
        return Transport.run(new String[] {"poll", "--into", inbound.toString()}, env(bob),
                new PrintStream(OutputStream.nullOutputStream()), new PrintStream(errBytes, true, StandardCharsets.UTF_8));
    }

    /** What is in inbound: each message by its text, mapped to its signature's bytes, or null without one. */
    private Map<String, byte @Nullable []> delivered() throws IOException {
        final Map<String, byte @Nullable []> messages = new TreeMap<>();
        try (Stream<Path> files = Files.list(inbound)) {
            for (final Path file : files.toList()) {
                final String name = file.getFileName().toString();
                assertThat(name).as("every file in inbound is a message or its signature").matches("[A-Za-z0-9_-]+\\.json(\\.sig)?");
                if (name.endsWith(".json")) {
                    final Path sig = file.resolveSibling(name + ".sig");
                    messages.put(Files.readString(file, StandardCharsets.UTF_8), Files.exists(sig) ? Files.readAllBytes(sig) : null);
                }
            }
        }
        return messages;
    }

    private void empty() throws IOException {
        try (Stream<Path> files = Files.list(inbound)) {
            for (final Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Messages arrive in inbound byte for byte, each beside its signature")
    void deliversMessagesWithSignatures() throws IOException {
        send("Grüße ✓\n\tline two\r\n");
        send("second\n");

        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);

        assertThat(delivered()).containsOnlyKeys("Grüße ✓\n\tline two\r\n", "second\n")
                .allSatisfy((message, signature) -> assertThat(signature).isEqualTo(SIGNATURE));
    }

    @Test
    @DisplayName("A second poll delivers only what arrived since the first")
    void deliversOnlyWhatIsNew() throws IOException {
        send("first\n");
        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);
        empty();

        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);
        assertThat(delivered()).isEmpty();

        send("later\n");
        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);
        assertThat(delivered()).containsOnlyKeys("later\n");
    }

    @Test
    @DisplayName("A person's chat in the room is not a Sokar message: not delivered, and counted on stderr")
    void chatIsSkippedAndSaidSo() throws IOException, InterruptedException {
        tuwunel.say(alice, room, "typed in a client");
        send("{\"a\":\"sokar message\"}");

        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);

        assertThat(delivered()).containsOnlyKeys("{\"a\":\"sokar message\"}");
        assertThat(stderr()).contains("skipped 1 room message(s) that are not Sokar messages: from " + alice.userId());
    }

    @Test
    @DisplayName("A Sokar message that came without a signature arrives without a .sig, for Sokar to hold")
    void unsignedSokarMessageHasNoSig() throws IOException {
        send("unsigned\n", new byte[0]);

        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);

        assertThat(delivered()).containsOnlyKeys("unsigned\n")
                .allSatisfy((message, signature) -> assertThat(signature).isNull());
    }

    @Test
    @DisplayName("More messages than one sync returns are all delivered, none skipped")
    void nothingSkippedWhenTheSyncIsCutShort() throws IOException {
        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);
        empty();
        for (int i = 0; i < 150; i++) {
            send("message " + i + "\n");
        }

        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);

        assertThat(delivered()).hasSize(150);
    }

    @Test
    @DisplayName("Nothing half-written is left in inbound")
    void noPartialFiles() throws IOException {
        send("whole\n");

        assertThat(poll()).as(this::stderr).isEqualTo(Exit.OK);

        try (Stream<Path> files = Files.list(inbound)) {
            assertThat(files.map(f -> f.getFileName().toString())).noneMatch(name -> name.startsWith("."));
        }
    }

}
