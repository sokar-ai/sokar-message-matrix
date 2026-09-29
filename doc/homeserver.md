# The local homeserver

The Matrix transport needs a homeserver, and the arrangement that must work before any other is the
smallest one: one machine, the homeserver in podman beside Sokar, reached on loopback, nothing
federating and nothing leaving the host. This page is what that arrangement is built and tested
against, and what was measured to choose it.

## Tuwunel, in rootless podman

The homeserver is **Tuwunel**, a single Rust binary with its database (RocksDB) embedded, so nothing
runs beside it. The image is taken from its registry and pinned by digest, never by a moving tag, in
one place: the `FROM` line of `homeserver/Dockerfile`. The measurements below are of Tuwunel 1.9.3.

It is configured entirely by environment variables, handed to podman as an `--env-file` readable only
by its owner, because the registration token is among them:

| Variable | Value | Why |
|---|---|---|
| `TUWUNEL_SERVER_NAME` | the installation's server name | Part of every user and room id; it cannot change once the database exists |
| `TUWUNEL_ADDRESS`, `TUWUNEL_PORT` | `0.0.0.0`, `8008` inside the container | Published by podman as `-p 127.0.0.1:<port>:8008`, so the host binds loopback only |
| `TUWUNEL_DATABASE_PATH` | `/data`, a volume | The room's history outlives every container |
| `TUWUNEL_ALLOW_FEDERATION` | `false` | Nothing federates in this arrangement |
| `TUWUNEL_ALLOW_REGISTRATION`, `TUWUNEL_REGISTRATION_TOKEN` | `true`, a secret | Only a holder of the token creates accounts |

**The first account registered becomes the server's administrator** and is joined to its admin room
(`#admins:<server name>`). So the account that provisions the others registers first, before any task
has one.

## What it offers the transport, measured

Each of these was exercised against the pinned image through the client API, in one run, with two
accounts in one room:

- **Loopback only**: the host listens on `127.0.0.1` alone; the machine's own address gets no answer.
  The federation API answers `M_FORBIDDEN: Federation is disabled.`
- **Registration** succeeds with the token and is refused without it (`401`, the token stage still
  required); an account refused there cannot log in.
- **A message comes back byte for byte**: an `m.room.message` of type `m.text` whose `body` held
  non-ASCII text, a tab, a CRLF and a trailing newline was returned by `/sync` identical to what was
  sent, and a custom field beside the body, carrying 64 random bytes in base64, was returned
  unchanged. A client shows the body; the custom field travels with it.
- **Sending twice is one event**: a repeated `PUT` with the same transaction id answers the same event
  id, and a sync from the previous `next_batch` delivers nothing new.
- **Read receipts**: a receipt one account sends for an event reaches the other account's sync as
  `m.receipt`, naming the reader and the time.
- **Deactivation**: an account deactivates itself with its own password; its token is refused from then
  on (`M_UNKNOWN_TOKEN`), and the messages it sent stay readable in the room.

## What it costs, measured

On a 2-vCPU VM with 3.3 GiB of memory, Ubuntu 26.04, rootless podman 5.7, over five runs:

| | |
|---|---|
| Image | 107 MB |
| Start with a new database, until the client API answers | 0.6 s to 2.8 s |
| Restart with an existing database | 0.6 s to 1.1 s |
| Memory at rest | 33 MB to 56 MB |
| Database after the run above | 61 MB |

Why Tuwunel, and what would change that, is in [the decisions](decisions.md).
