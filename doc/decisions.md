# Decisions

Settled reasoning, accepted risks included: what was decided, why, and what would change the answer.

## The homeserver is Tuwunel

**Decided:** the minimum arrangement - one machine, the homeserver in rootless podman, loopback only,
no federation - is built and tested against Tuwunel, pinned by digest in `homeserver/Dockerfile`. What
it is configured with and what it was measured to do are in [the homeserver](homeserver.md).

**What was weighed.** Three single-binary homeservers with an embedded database, each measured the same
way against the calls the transport depends on:

| | Tuwunel 1.9.3 | Conduit 0.10.14 | Continuwuity 26.9.1 |
|---|---|---|---|
| Image | 107 MB | 66 MB | 105 MB |
| First start | 0.6 - 2.8 s | 3.4 - 6.1 s | 0.6 s |
| Memory at rest | 33 - 56 MB | 8.5 MB | 34 MB |
| Newest client API | v1.19 | v1.12 | v1.18 |
| Configured registration token works from the first start | yes | yes | no |
| Every call the transport depends on | passed | passed | not reached |

**Continuwuity is out** because it cannot be started and forgotten: it ignores the configured
registration token until a first account exists, and that account needs a one-time token it prints
only in its log, or its interactive console.

**Tuwunel over Conduit.** Both pass every call, and Conduit costs a quarter of the memory. Tuwunel
implements the current client API and states that it is maintained by full-time staff, which counts
for more in a server that holds the whole conversation than tens of megabytes do.

**What would change the answer:** Tuwunel's maintenance stopping, or memory becoming the constraint on
the machines Sokar runs on. Conduit is then the measured alternative, configured the same way with the
prefix `CONDUIT_`, `CONDUIT_CONFIG` set empty and the container port `6167`.

## A message is a text event with its signature beside it

**Decided:** `send` puts the message into the room as the `body` of an `m.text` event, and its detached
signature, base64, beside it in `org.fuin.sokar.signature`.

**Why:** the room is the project's conversation, and a person reads it with any Matrix client; a client
shows a text event's body and ignores a field it does not know. The signature is bytes, and base64 keeps
it byte for byte whatever its form. Measured against Tuwunel: a body with non-ASCII text, a tab, a CRLF
and a trailing newline comes back from a sync identical, and so does the field beside it.

**What follows:** a JSON string carries text only, so a message whose bytes are not valid UTF-8 is
refused rather than carried with its bytes silently replaced. Sokar's messages are JSON, so this refuses
nothing Sokar sends.

**What would change the answer:** Sokar sending messages that are not text, or the size of one event
(64 KiB for the whole event) becoming too small for a message.

## Only Sokar messages are delivered, and the transport tells them by its own mark

**Decided:** `poll` delivers a room message only when it carries the signature field `send` writes,
and names on stderr every other message it skipped, by sender and room.

**Why:** a person's chat in the room is not a Sokar message - it has no id and no addressee - and
whether and how it reaches a task is Sokar's decision, not the transport's. The transport tells the two
apart by its own mark rather than by reading the message, which it never does. Skipping without a word
would make a person's words disappear with nobody having decided it, so each skip is said.

**What would change the answer:** Sokar deciding how a person's chat reaches a task; the transport then
delivers it in whatever form that decision gives.

## The position of a poll is saved after its files

**Decided:** `poll` keeps the homeserver's sync position under `$XDG_STATE_HOME/sokar/transports/matrix/`,
one file per homeserver and account, and saves it only once every file of that poll is written.

**Why:** Sokar passes a transport no directory for its state, and drops a message whose id it has
already delivered. So delivering a message twice after a crash costs nothing, while skipping one loses
it: the position is saved late, never early. A lock beside it keeps two polls of one account from
writing the same position.

**What would change the answer:** Sokar giving transports a state directory of their own.

## `max_bytes` is what every message fits, not what most do

**Decided:** `describe` declares `max_bytes: 10240`, and `send` refuses a larger message with 65.

**Why:** Matrix limits an event to 64 KiB in all, and a message travels inside it as a JSON string, where
escaping grows it by up to six times. Measured against Tuwunel with a 256-byte signature: plain ASCII
fits 64,397 bytes, three-byte UTF-8 64,401, quotes 32,197, control characters 10,727. A limit is only
something Sokar can rely on if it holds whatever the message contains, so it is the worst case, with room
for the envelope and a signature of up to 2 KiB. `send` keeps the same number rather than trying a larger
message, so what `describe` says and what `send` does never disagree.

**What would change the answer:** Sokar's messages being known never to contain characters that escape
to more than two bytes - Sokar's messages are JSON, whose strings already escape control characters -
which would allow 30 KiB; or messages carried as a file upload instead of an event body.

## A receipt is looked up by the event's own room, and read means read up to

**Decided:** `receipt` finds the event among the rooms the account is in, takes the room from the
event's own `room_id`, and answers `read` when `--by`'s read receipt is on that event or a later one.

**Why:** a Matrix read receipt marks the latest event an account has read, not each one, so a receipt
on a later event is the only sign that an earlier one was read. The room is taken from the event and not
from the path it was asked under because Tuwunel, measured, answers an event asked for under any room the
account is in - so trusting the path picked the admin room, where the addressee is not, whenever that
room happened to be listed first. Sokar names `--by`, because which account a message was for is inside
the message, which the transport does not read.

**What would change the answer:** Sokar handing the room to `receipt` as well; the lookup through the
account's rooms is then no longer needed.
