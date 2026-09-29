# MX04 — Describe, check and read receipts

**What must be true.** The transport declares honestly what it is, can say whether it would work without
sending anything, and answers `confirms: read` from real read receipts or says that it cannot.

The requirement is `sokar-project` PJ02 ([index](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md)).

## What it covers

- **`describe`**: `scheme: matrix`, `poll: true`, `confirms: read`, `attests: [sender]`, its credentials,
  its size limit, and the homeserver in `hosts`. `hosts` is what the egress configuration permits the
  transport to reach, so the homeserver is named here rather than assumed.
- **`check`**: the token is valid and the room joinable, sending nothing.
- **`receipt`**: from Matrix read receipts - the first transport that can honestly answer
  `confirms: read`.

## Open questions

1. **Whose receipt counts as read.** The room is the project's, every participant's host receives every
   message, and a receipt is per account; which account's receipt confirms a message to one peer.
2. **Where the homeserver named in `hosts` comes from** - the transport's configuration or the contract.
