# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- A Maven build through `mvnw`, which refuses a Maven whose SHA-256 is not the pinned one.
- Null-checking in the main compile: every package is `@NullMarked`, NullAway fails the build, and a
  test fails on a package that is not marked.
- A test that fails on a link to a requirement's or an issue's file instead of to its index.
- `sokar-message-transport-matrix` as a native executable (`-Pnative`).
- `send <file> <sig> --to <!room-id>` puts the message into the room as one `m.text` event, its
  detached signature beside it in `org.fuin.sokar.signature`. The homeserver and the account's token
  come from `SOKAR_MATRIX_HOMESERVER` and `SOKAR_MATRIX_ACCESS_TOKEN`. It exits 0 when the homeserver
  took the event and 75 when it could not take it now; a retry is the same Matrix transaction, so it
  never makes a second event. A message that is not UTF-8, an address that is not a room id, a refused
  token or a room the account is not in are refused with their own exit code. Proven against the pinned
  Tuwunel, started in podman by the integration tests.
- `poll --into <inbound>` writes every new Sokar message in the account's rooms as `<event id>.json`
  beside its `<event id>.json.sig`, each file whole or not at all. It keeps its position under
  `$XDG_STATE_HOME/sokar/transports/matrix/` and saves it only after the files are written, pages back
  when the homeserver cuts a timeline short, and names on stderr the room messages it skipped because
  they are not Sokar messages. Proven against the pinned Tuwunel. What the transport does, its
  configuration and its exit codes are in `doc/transport.md`.
- `describe` prints the transport's description without reading any configuration, and claims nothing
  it cannot prove yet: `confirms: "read"`, `attests: []`, `max_bytes: 10240`, the credential as
  `{"name":"SOKAR_MATRIX_ACCESS_TOKEN","as":"env"}`. `send` refuses a larger message with 65.
- `send` prints `{"reference":"<event id>"}` when it hands a message over, for Sokar to keep.
- `receipt <reference> --by <account>` answers `read` (with when), `delivered` or `unknown` from the
  room's read receipts, a receipt on a later event counting as reading an earlier one. `check` answers 0 when the homeserver accepts the token and 2 otherwise,
  sending nothing.
- The local homeserver the transport is built and tested against: Tuwunel in rootless podman, pinned
  by digest, loopback only - what it is configured with and what it was measured to do
  (`doc/homeserver.md`), and why it was chosen (`doc/decisions.md`).
- A `.deb` and an `.rpm` (`-Pnative`) that install the executable into `/usr/libexec/sokar/transports/`,
  depending on `sokar`, glibc 2.34 and zlib, with its CycloneDX bill and license under
  `/usr/share/doc/sokar-message-transport-matrix/`.
- Under `-Pnative` the integration tests run the executable as its own process, as Sokar does.
- A GitHub build: the pinned GraalVM from `sokar-machines jdk --github`, `sokar-release check-actions`,
  the native build with its integration tests, and the packages kept as the run's artifact.
- Dependabot moves the pinned GitHub actions and the homeserver's image digest weekly, in one group,
  after a three-day cooldown.
