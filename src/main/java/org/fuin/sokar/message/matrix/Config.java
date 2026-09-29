package org.fuin.sokar.message.matrix;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Where the homeserver is and the account's token, from the environment - never from an argument, which
 * every process on the machine can read.
 */
record Config(URI homeserver, String accessToken) {

    static final String HOMESERVER = "SOKAR_MATRIX_HOMESERVER";

    static final String ACCESS_TOKEN = "SOKAR_MATRIX_ACCESS_TOKEN";

    static Config from(final Map<String, String> env) throws Failure {
        final String homeserver = env.get(HOMESERVER);
        final String token = env.get(ACCESS_TOKEN);
        if (homeserver == null || homeserver.isBlank()) {
            throw new Failure(Exit.CONFIG, HOMESERVER + " is not set");
        }
        if (token == null || token.isBlank()) {
            throw new Failure(Exit.CONFIG, ACCESS_TOKEN + " is not set");
        }
        final URI uri;
        try {
            uri = new URI(homeserver.endsWith("/") ? homeserver.substring(0, homeserver.length() - 1) : homeserver);
        } catch (final URISyntaxException ex) {
            throw new Failure(Exit.CONFIG, HOMESERVER + " is not a URL: " + ex.getMessage(), ex);
        }
        final String scheme = uri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
            throw new Failure(Exit.CONFIG, HOMESERVER + " must be an http or https URL with a host, not '" + homeserver + "'");
        }
        return new Config(uri, token);
    }

    @Override
    public String toString() {
        // A record prints every component by default, and this one holds a credential.
        return "Config[homeserver=" + homeserver + "]";
    }

}
