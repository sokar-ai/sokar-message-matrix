# The transport

`sokar-message-transport-matrix` is a program Sokar runs on the host, over files: it carries a message
the filter let through into a project's Matrix room, and brings the room's messages back into a task's
mailbox. It decides nothing and never reads a message; what may pass is the filter's, and what is
delivered to whom is Sokar's.

## Configuration

From the environment, never from an argument, which every process on the machine can read:

| Variable | What |
|---|---|
| `SOKAR_MATRIX_HOMESERVER` | The homeserver's client URL, `http` or `https`, e.g. `http://127.0.0.1:8008` |
| `SOKAR_MATRIX_ACCESS_TOKEN` | The token of the account the transport acts as - one account per task |
| `XDG_STATE_HOME`, else `HOME` | Where `poll` keeps its position: `$XDG_STATE_HOME/sokar/transports/matrix/`, else `~/.local/state/sokar/transports/matrix/` |

The transport follows no redirect, so the token never reaches a server other than the one configured.

## `describe`

Prints what the transport is, as one JSON object, reading nothing - no configuration, no network:

    {"scheme":"matrix","poll":true,"confirms":"read","max_bytes":10240,
     "credentials":[{"name":"SOKAR_MATRIX_ACCESS_TOKEN","as":"env"}],"hosts":[],"attests":[]}

It claims nothing it cannot prove: `attests` is empty until Sokar defines the evidence for `sender` - a
fact claimed without its evidence makes Sokar hold every message - and `hosts` is empty until the
package installs the homeserver it names.

## `check`

Whether the transport could work now, sending nothing: the configuration is complete, the homeserver
answers and it accepts the token. Exit 0 usable, 2 not, with the reason on stderr.

## `send <file> <sig> --to <room id>`

Puts one message and its detached signature into the room as one event:

- an `m.room.message` of type `m.text`, whose `body` is the message - so any Matrix client shows it;
- the signature beside it, base64, in the field `org.fuin.sokar.signature`, so it comes back byte for byte.

`--to` is a room id: `!` and an opaque rest. From room version 12 on a room id carries no server name
(`!XDYBXYJ9…55H4k`); an alias (`#…`) is refused, since the homeserver would choose the room.

A message larger than `max_bytes` (10,240 bytes) is refused with 65, whatever it contains, so the
limit `describe` declares is the limit `send` keeps.

Sending the same message and signature again is the same Matrix transaction, so a retry after a
temporary failure never makes a second event.

When it exits 0, `send` prints one line on stdout, which Sokar keeps beside the sent message and hands
back to `receipt` unread: `{"reference":"<event id>"}`. A retried send prints the same reference.

## `receipt <reference> --by <account>`

What became of a message this account sent, as seen by `--by` - the account it was for, which Sokar
names because the transport does not read the message. It prints one line on stdout and exits 0 for
every answer:

| Answer | When |
|---|---|
| `{"state":"read","at":"<ISO-8601>"}` | `--by`'s read receipt is on the event, or on a later one - a Matrix read receipt means "read up to here" - and `at` is when it was set |
| `{"state":"delivered"}` | The event is in the room and `--by` is in it, but has not read that far |
| `{"state":"unknown"}` | It cannot say: no room the account is in holds the event, or `--by` is not in that room. Why goes to stderr |

`pending` never happens here: once `send` exits 0, the event is in the room. `receipt` reads the room's
receipts with a sync of its own, so it never moves `poll`'s position.

## `poll --into <inbound>`

One sync of the account's joined rooms, and every new Sokar message written into `inbound` as
`<event id>.json`, with its signature beside it as `<event id>.json.sig`. The signature is written first
and each file is renamed into place whole, so nothing half-written is ever seen.

- **What is a Sokar message**: a room message carrying the signature field that `send` puts beside every
  message. Anything else - a person's chat in the room - is not delivered, and `poll` names on stderr how
  many it skipped and from whom.
- **A Sokar message whose signature field is empty or unreadable** is delivered without a `.sig`, and
  Sokar holds it as having arrived without a signature.
- **Nothing is skipped**: when the homeserver cuts a busy timeline short, the rest is paged back down to
  where the previous poll stopped.
- **The position is saved only after the files are written**: a crash delivers a message twice, which
  Sokar drops by its id, and never skips one.
- **One poll per account at a time**: a second one while the first runs exits 75.

## Exit codes

`check` answers only 0 or 2, as its contract says. For the other verbs, Sokar's contract gives meaning
to 0 (handed over) and 75 (temporary: Sokar keeps the message and retries);
every other code refuses the message back to its sender. Each refusal has its own code, so the log says why:

| Code | Meaning |
|---|---|
| 0 | Done |
| 64 | The command line is not the contract's: a missing argument, a `--to` that is not a room id, an `--into` that is not a directory |
| 65 | The message cannot be carried: not valid UTF-8, or larger than `max_bytes` |
| 75 | Temporary: the homeserver is unreachable, overloaded or rate-limiting, or another poll for the account is running |
| 76 | The homeserver answered something the transport does not understand |
| 77 | The homeserver refused: an unknown token, or a room the account is not in |
| 78 | The transport's own configuration is missing: the homeserver, the token, or a directory for its state |

The reason goes to stderr. It never contains the token.
