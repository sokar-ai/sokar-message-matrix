package org.fuin.sokar.message.matrix;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The few client-API calls the transport makes, each turned into a JSON answer or a {@link Failure} whose
 * exit code says whether Sokar should try again.
 */
final class MatrixClient {

    static final ObjectMapper JSON = new ObjectMapper();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final Config config;

    private final HttpClient http;

    MatrixClient(final Config config) {
        this.config = config;
        // A redirect would carry the token to wherever the homeserver points, so none is followed.
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    static String segment(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    JsonNode put(final String path, final JsonNode body) throws Failure {
        return call(request(path).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes(body)))
                .header("Content-Type", "application/json"));
    }

    JsonNode get(final String path) throws Failure {
        return call(request(path).GET());
    }

    /** Like {@link #get}, but a 404 - the event or state is not there, or not visible - answers null. */
    @Nullable
    JsonNode find(final String path) throws Failure {
        try {
            return get(path);
        } catch (final NotFound ex) {
            return null;
        }
    }

    /** A 404, told apart so that {@link #find} can answer it with null. */
    private static final class NotFound extends Failure {

        private static final long serialVersionUID = 1L;

        NotFound(final String reason) {
            super(Exit.PROTOCOL, reason);
        }

    }

    private HttpRequest.Builder request(final String path) {
        return HttpRequest.newBuilder(URI.create(config.homeserver() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.accessToken());
    }

    private JsonNode call(final HttpRequest.Builder builder) throws Failure {
        final HttpRequest request = builder.build();
        final HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (final IOException ex) {
            throw new Failure(Exit.TEMPORARY, "homeserver " + config.homeserver() + " not reachable: " + ex, ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure(Exit.TEMPORARY, "interrupted while calling the homeserver", ex);
        }
        final int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return parse(response.body(), request);
        }
        final String reason = request.method() + " " + request.uri().getPath() + " answered " + status + " "
                + matrixError(response.body());
        if (status == 401 || status == 403) {
            throw new Failure(Exit.NOT_PERMITTED, reason);
        }
        if (status == 404) {
            throw new NotFound(reason);
        }
        if (status == 413) {
            throw new Failure(Exit.DATA, reason);
        }
        if (status == 429 || status >= 500) {
            throw new Failure(Exit.TEMPORARY, reason);
        }
        throw new Failure(Exit.PROTOCOL, reason);
    }

    private static JsonNode parse(final byte[] body, final HttpRequest request) throws Failure {
        try {
            final JsonNode node = JSON.readTree(body);
            if (node == null || !node.isObject()) {
                throw new Failure(Exit.PROTOCOL, request.uri().getPath() + " answered something that is not a JSON object");
            }
            return node;
        } catch (final IOException ex) {
            throw new Failure(Exit.PROTOCOL, request.uri().getPath() + " answered something that is not JSON", ex);
        }
    }

    /** The homeserver's own words for a refusal, so the person reading stderr sees why. */
    private static String matrixError(final byte[] body) {
        try {
            final JsonNode node = JSON.readTree(body);
            if (node != null && node.isObject()) {
                return node.path("errcode").asText("") + ": " + node.path("error").asText("");
            }
        } catch (final IOException ex) {
            // Not JSON: the status alone has to do.
        }
        return "(no Matrix error in the answer)";
    }

    private static byte[] bytes(final JsonNode body) {
        try {
            return JSON.writeValueAsBytes(body);
        } catch (final JacksonException ex) {
            throw new IllegalStateException("A JSON tree could not be written", ex);
        }
    }

}
