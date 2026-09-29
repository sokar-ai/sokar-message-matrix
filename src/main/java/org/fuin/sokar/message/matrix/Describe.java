package org.fuin.sokar.message.matrix;

import java.io.PrintStream;
import java.util.List;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code describe}: what the transport is, as one JSON object. It reads nothing - no configuration, no
 * network - and claims nothing it does not do: a fact claimed in {@code attests} whose evidence is missing
 * makes Sokar hold the message.
 */
final class Describe {

    /**
     * The largest message carried whatever its content. An event is 64 KiB in all, and JSON escaping grows a
     * message by up to six times (a control character becomes {@code \u0001}); measured against Tuwunel, the
     * worst content fits 10,727 bytes and plain ASCII 64,397. So this is a promise for every message, with
     * room left for the envelope and a signature of up to 2 KiB.
     */
    static final int MAX_BYTES = 10_240;

    private Describe() {
    }

    static void run(final List<String> args, final PrintStream out) throws Failure {
        if (!args.isEmpty()) {
            throw new Failure(Exit.USAGE, "usage: describe");
        }
        final ObjectNode description = MatrixClient.JSON.createObjectNode()
                .put("scheme", "matrix")
                .put("poll", true)
                // receipt answers from the room's read receipts.
                .put("confirms", "read")
                .put("max_bytes", MAX_BYTES);
        // One environment variable per credential, named per credential.
        description.putArray("credentials").addObject().put("name", Config.ACCESS_TOKEN).put("as", "env");
        // The homeserver the package installs, once it does; until then no host is claimed.
        description.putArray("hosts");
        // "sender" once Sokar defines the evidence that proves it; claimed without it, every message is held.
        description.putArray("attests");
        try {
            out.println(MatrixClient.JSON.writeValueAsString(description));
        } catch (final JacksonException ex) {
            throw new IllegalStateException("A JSON tree could not be written", ex);
        }
    }

}
