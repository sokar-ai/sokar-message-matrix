package org.fuin.sokar.message.matrix;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code poll --into <inbound>}: one sync, and every new Sokar message in the account's rooms written into
 * {@code inbound} as {@code <name>.json} beside its {@code <name>.json.sig}, each whole or not at all.
 * Where the last poll stopped is the transport's own state, saved only once the files are written: a
 * crash delivers a message twice, which Sokar drops by its id, and never skips one.
 */
final class Poll {

    /** Only messages, and nothing Sokar has no use for: no presence, no account data, no typing. */
    private static final String FILTER = """
            {"room":{"timeline":{"types":["m.room.message"],"limit":100},"state":{"types":[]},\
            "ephemeral":{"types":[]},"account_data":{"types":[]}},\
            "presence":{"types":[]},"account_data":{"types":[]}}""";

    /** An event id without its sigil, as a file name: base64url in current room versions. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,255}");

    private static final int PAGE = 100;

    private Poll() {
    }

    static void run(final List<String> args, final Config config, final Map<String, String> env, final PrintStream err)
            throws Failure {
        if (args.size() != 2 || !"--into".equals(args.get(0))) {
            throw new Failure(Exit.USAGE, "usage: poll --into <inbound>");
        }
        final Path inbound = Path.of(args.get(1));
        if (!Files.isDirectory(inbound)) {
            throw new Failure(Exit.USAGE, "--into " + inbound + " is not a directory");
        }
        final MatrixClient client = new MatrixClient(config);
        final String userId = client.get("/_matrix/client/v3/account/whoami").path("user_id").asText("");
        if (userId.isEmpty()) {
            throw new Failure(Exit.PROTOCOL, "the homeserver did not say whose token this is");
        }
        final Path stateDir = stateDirectory(env);
        final String key = key(config, userId);
        try {
            Files.createDirectories(stateDir);
        } catch (final IOException ex) {
            throw new Failure(Exit.CONFIG, "cannot create the state directory " + stateDir + ": " + ex.getMessage(), ex);
        }
        try (FileChannel lockFile = FileChannel.open(stateDir.resolve(key + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = tryLock(lockFile)) {
            final List<String> skipped = poll(client, inbound, stateDir, stateDir.resolve(key + ".since"));
            if (!skipped.isEmpty()) {
                err.println("poll: skipped " + skipped.size() + " room message(s) that are not Sokar messages: "
                        + String.join(", ", skipped));
            }
        } catch (final IOException ex) {
            throw new Failure(Exit.CONFIG, "cannot use the state in " + stateDir + ": " + ex.getMessage(), ex);
        }
    }

    /** Answers the room messages that were not Sokar messages and so were not delivered, by sender and room. */
    private static List<String> poll(final MatrixClient client, final Path inbound, final Path stateDir, final Path state)
            throws Failure {
        final String since = readSince(state);
        final StringBuilder path = new StringBuilder("/_matrix/client/v3/sync?timeout=0&filter=")
                .append(MatrixClient.segment(FILTER));
        if (since != null) {
            path.append("&since=").append(MatrixClient.segment(since));
        }
        final JsonNode sync = client.get(path.toString());
        final String next = sync.path("next_batch").asText("");
        if (next.isEmpty()) {
            throw new Failure(Exit.PROTOCOL, "the sync answered no next_batch");
        }
        final List<String> skipped = new ArrayList<>();
        // Paging back from a cut-short timeline can return events the timeline holds too: each is handled once.
        final Set<String> seen = new HashSet<>();
        for (final Map.Entry<String, JsonNode> room : sync.path("rooms").path("join").properties()) {
            for (final JsonNode event : timeline(client, room.getKey(), room.getValue().path("timeline"), since)) {
                if (!seen.add(event.path("event_id").asText(""))) {
                    continue;
                }
                // Other event types are room business - joins, names - and never a message to deliver.
                if ("m.room.message".equals(event.path("type").asText()) && !deliver(event, inbound)) {
                    skipped.add("from " + event.path("sender").asText("?") + " in " + room.getKey());
                }
            }
        }
        writeAtomically(state, next.getBytes(StandardCharsets.UTF_8), stateDir);
        return skipped;
    }

    /**
     * The room's new events, oldest first. A sync may cut a busy timeline short; what it left out is paged
     * backwards down to where the last poll stopped, so nothing is skipped.
     */
    private static List<JsonNode> timeline(final MatrixClient client, final String room, final JsonNode timeline,
            @Nullable final String since) throws Failure {
        final List<JsonNode> events = new ArrayList<>();
        if (timeline.path("limited").asBoolean(false) && timeline.path("prev_batch").isTextual()) {
            final List<JsonNode> older = new ArrayList<>();
            String from = timeline.path("prev_batch").asText();
            while (true) {
                final StringBuilder path = new StringBuilder("/_matrix/client/v3/rooms/").append(MatrixClient.segment(room))
                        .append("/messages?dir=b&limit=").append(PAGE)
                        .append("&filter=").append(MatrixClient.segment("{\"types\":[\"m.room.message\"]}"))
                        .append("&from=").append(MatrixClient.segment(from));
                if (since != null) {
                    path.append("&to=").append(MatrixClient.segment(since));
                }
                final JsonNode page = client.get(path.toString());
                page.path("chunk").forEach(older::add);
                final String end = page.path("end").asText("");
                if (end.isEmpty() || end.equals(from) || page.path("chunk").isEmpty()) {
                    break;
                }
                from = end;
            }
            events.addAll(older.reversed());
        }
        timeline.path("events").forEach(events::add);
        return events;
    }

    /**
     * Writes one room message if it is a Sokar message, and answers whether it was one. What makes it one is the
     * signature field this transport puts beside every message it sends - never the message's own content,
     * which the transport does not read. A person's chat in the room has no such field; whether and how it
     * reaches a task is Sokar's to decide, not the transport's.
     */
    private static boolean deliver(final JsonNode event, final Path inbound) throws Failure {
        final JsonNode content = event.path("content");
        // A redacted message keeps its event and loses its content, so it is no Sokar message either.
        if (!content.has(Send.SIGNATURE_FIELD) || !content.path("body").isTextual()) {
            return false;
        }
        final String name = fileName(event.path("event_id").asText("")) + ".json";
        final byte[] signature = signature(content);
        // The signature first, so the message never appears without the one it came with. A message that
        // came with none gets no .sig at all, and Sokar holds it as having arrived without a signature.
        if (signature.length > 0) {
            writeAtomically(inbound.resolve(name + ".sig"), signature, inbound);
        }
        writeAtomically(inbound.resolve(name), content.path("body").asText().getBytes(StandardCharsets.UTF_8), inbound);
        return true;
    }

    /** What travelled beside the body, byte for byte - or nothing, when it is empty or not base64. */
    private static byte[] signature(final JsonNode content) {
        final JsonNode field = content.path(Send.SIGNATURE_FIELD);
        if (!field.isTextual()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(field.asText());
        } catch (final IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    private static String fileName(final String eventId) throws Failure {
        final String name = eventId.startsWith("$") ? eventId.substring(1) : eventId;
        if (!SAFE_NAME.matcher(name).matches()) {
            // Older room versions name events 'local:server'; hashing keeps the name unique and safe.
            if (eventId.isEmpty()) {
                throw new Failure(Exit.PROTOCOL, "the sync returned a message without an event id");
            }
            return "h" + sha256(eventId);
        }
        return name;
    }

    private static void writeAtomically(final Path target, final byte[] bytes, final Path dir) throws Failure {
        final Path temporary = dir.resolve("." + target.getFileName() + ".part");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException ex) {
                Files.deleteIfExists(temporary);
                throw new Failure(Exit.CONFIG, dir + " cannot rename atomically, so a half file could be read", ex);
            }
        } catch (final IOException ex) {
            throw new Failure(Exit.TEMPORARY, "cannot write " + target + ": " + ex.getMessage(), ex);
        }
    }

    private static @Nullable String readSince(final Path state) throws Failure {
        if (!Files.exists(state)) {
            return null;
        }
        try {
            final String since = Files.readString(state, StandardCharsets.UTF_8).strip();
            return since.isEmpty() ? null : since;
        } catch (final IOException ex) {
            throw new Failure(Exit.CONFIG, "cannot read the sync position " + state + ": " + ex.getMessage(), ex);
        }
    }

    private static FileLock tryLock(final FileChannel channel) throws IOException, Failure {
        final FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (final OverlappingFileLockException ex) {
            throw new Failure(Exit.TEMPORARY, "another poll for this account is running", ex);
        }
        if (lock == null) {
            throw new Failure(Exit.TEMPORARY, "another poll for this account is running");
        }
        return lock;
    }

    static Path stateDirectory(final Map<String, String> env) throws Failure {
        final String xdg = env.get("XDG_STATE_HOME");
        if (xdg != null && Path.of(xdg).isAbsolute()) {
            return Path.of(xdg, "sokar", "transports", "matrix");
        }
        final String home = env.get("HOME");
        if (home == null || !Path.of(home).isAbsolute()) {
            throw new Failure(Exit.CONFIG, "neither XDG_STATE_HOME nor HOME names an absolute directory for the sync position");
        }
        return Path.of(home, ".local", "state", "sokar", "transports", "matrix");
    }

    /** One position per account and homeserver, named so that no id has to be a safe file name. */
    private static String key(final Config config, final String userId) {
        return sha256(config.homeserver() + "\n" + userId);
    }

    private static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", ex);
        }
    }

}
