package org.fuin.sokar.message.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class DescribeTest {

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    private int describe(final String... args) {
        final String[] all = new String[args.length + 1];
        all[0] = "describe";
        System.arraycopy(args, 0, all, 1, args.length);
        // No environment at all: describe reads nothing.
        return Main.run(all, Map.of(), new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("It prints one JSON object with every field the contract names, reading no configuration")
    void describesItself() throws IOException {
        assertThat(describe()).as(errBytes.toString(StandardCharsets.UTF_8)).isEqualTo(Exit.OK);

        final JsonNode description = MatrixClient.JSON.readTree(outBytes.toByteArray());
        assertThat(description.path("scheme").asText()).isEqualTo("matrix");
        assertThat(description.path("poll").asBoolean(false)).isTrue();
        assertThat(description.path("max_bytes").asInt()).isEqualTo(Describe.MAX_BYTES);
        assertThat(description.path("credentials")).singleElement().satisfies(credential -> {
            assertThat(credential.path("name").asText()).isEqualTo(Config.ACCESS_TOKEN);
            assertThat(credential.path("as").asText()).isEqualTo("env");
        });
        assertThat(description.path("hosts").isArray()).isTrue();
    }

    @Test
    @DisplayName("It claims no fact it cannot prove yet, and confirms reading, which receipt answers")
    void claimsNothingUnproven() throws IOException {
        describe();

        final JsonNode description = MatrixClient.JSON.readTree(outBytes.toByteArray());
        assertThat(description.path("attests").isArray()).isTrue();
        assertThat(description.path("attests")).isEmpty();
        assertThat(description.path("confirms").asText()).isEqualTo("read");
    }

    @Test
    @DisplayName("Arguments it does not take are 64")
    void noArguments() {
        assertThat(describe("--verbose")).isEqualTo(Exit.USAGE);
    }

}
