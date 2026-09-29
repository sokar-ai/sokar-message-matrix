package org.fuin.sokar.message.matrix;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code send <file> <sig> --to <rest>}: one message and its detached signature become one room event. The
 * message is the event's body, so any Matrix client shows it; the signature travels beside it untouched.
 */
final class Send {

    /** Namespaced, as the Matrix specification asks of a field it does not define. */
    static final String SIGNATURE_FIELD = "org.fuin.sokar.signature";

    /**
     * A room id - never an alias, which the homeserver would resolve to a room of its choosing. Its form
     * is otherwise opaque: from room version 12 on it carries no server name.
     */
    private static final Pattern ROOM_ID = Pattern.compile("![^\\s\\p{Cntrl}]+");

    /** The longest identifier the Matrix specification allows, in bytes. */
    private static final int MAX_ID_BYTES = 255;

    private Send() {
    }

    static void run(final List<String> args, final Config config, final PrintStream out) throws Failure {
        if (args.size() != 4 || !"--to".equals(args.get(2))) {
            throw new Failure(Exit.USAGE, "usage: send <file> <sig> --to <!room-id>");
        }
        final String room = args.get(3);
        if (!ROOM_ID.matcher(room).matches() || room.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES) {
            throw new Failure(Exit.USAGE, "--to must be a room id, starting with '!', not '" + room + "'");
        }
        final byte[] message = read(Path.of(args.get(0)));
        final byte[] signature = read(Path.of(args.get(1)));
        // What describe promises is what send does: a larger message might fit, depending on its content,
        // and a limit that depends on content is no limit Sokar can rely on.
        if (message.length > Describe.MAX_BYTES) {
            throw new Failure(Exit.DATA, "the message is " + message.length + " bytes, more than the "
                    + Describe.MAX_BYTES + " this transport carries");
        }
        final String body = utf8(message);

        final ObjectNode content = MatrixClient.JSON.createObjectNode();
        content.put("msgtype", "m.text");
        content.put("body", body);
        content.put(SIGNATURE_FIELD, Base64.getEncoder().encodeToString(signature));

        final String path = "/_matrix/client/v3/rooms/" + MatrixClient.segment(room) + "/send/m.room.message/"
                + transactionId(message, signature);
        final JsonNode answer = new MatrixClient(config).put(path, content);
        if (!answer.path("event_id").isTextual()) {
            throw new Failure(Exit.PROTOCOL, "the homeserver accepted the message but named no event");
        }
        // Sokar keeps this beside the sent message and hands the reference back to receipt, unread.
        try {
            out.println(MatrixClient.JSON.writeValueAsString(
                    MatrixClient.JSON.createObjectNode().put("reference", answer.path("event_id").asText())));
        } catch (final JacksonException ex) {
            throw new IllegalStateException("A JSON tree could not be written", ex);
        }
    }

    /**
     * The same message and signature always give the same id, so a send Sokar retries after a temporary
     * failure lands as the event the first attempt may already have made, not as a second one.
     */
    static String transactionId(final byte[] message, final byte[] signature) {
        final MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", ex);
        }
        sha256.update(ByteBuffer.allocate(Long.BYTES).putLong(message.length).array());
        sha256.update(message);
        sha256.update(signature);
        return "sokar-" + HexFormat.of().formatHex(sha256.digest());
    }

    /** A JSON string holds text only; bytes that are not UTF-8 are refused rather than silently replaced. */
    private static String utf8(final byte[] message) throws Failure {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(message))
                    .toString();
        } catch (final CharacterCodingException ex) {
            throw new Failure(Exit.DATA, "the message is not valid UTF-8, and a room event can carry only text", ex);
        }
    }

    private static byte[] read(final Path file) throws Failure {
        try {
            return Files.readAllBytes(file);
        } catch (final IOException ex) {
            throw new Failure(Exit.USAGE, "cannot read " + file + ": " + ex.getMessage(), ex);
        }
    }

}
