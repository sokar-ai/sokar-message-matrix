# MX03 — Send and poll over a room

**What must be true.** The transport carries a message and its detached signature into a Matrix room and
back out of it, byte for byte, under Sokar's existing transport contract, with no change to that
contract.

The requirement is `sokar-project` PJ02 ([index](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md)).
The contract is `sokar` B14: a transport is a program Sokar drives over files.

## What it covers

- **`send <file> <sig> --to <rest>`**: `matrix:!room:server` arrives as `!room:server`, and the transport
  posts one event carrying the message and its signature. Its exit codes are the contract's.
- **`poll --into <inbound>`**: one sync, and each new event written atomically as the message and its
  `.sig`.
- **The account's token is given to the transport**, never created by it. Creating and deleting a task's
  account is `core`'s half of PJ02.
- **The transport decides nothing and never parses the message.** Every peer of a project resolves to
  the project's one room; the addressee is inside the signed bytes, and the receiving host delivers
  only what names one of its own tasks.
- **Fail closed and loud**: an unreachable homeserver, a room that is not the one given, a token that is
  refused - each stops with an exit code rather than delivering elsewhere.
- Proven against the homeserver of MX02, on one machine.

## Open questions

1. **The contract's text**: the verbs, their arguments and exit codes, the directories, and what
   `describe` must declare. Asked of `core` in the channel.
2. **How the token reaches the transport** - through its environment or standard input, and which of
   the two the contract's `credentials` provides.
3. **The event's shape.** A person reads the room with any client, so the message has to be an event a
   client shows, with the signature carried beside it; and it has to come back out byte for byte,
   whatever those bytes are.
4. **Where `poll` keeps its sync position** between runs, so that a message is written once and none is
   skipped.
