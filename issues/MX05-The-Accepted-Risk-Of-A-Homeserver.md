# MX05 — The accepted risk of a homeserver

**What must be true.** `doc/decisions.md` states, as an accepted risk, what a homeserver holding the
conversation exposes, why it stays, and what would change the answer.

The requirement is `sokar-project` PJ02 ([index](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md)),
where the operator settled it: no end-to-end encryption, on a homeserver the operator runs.

## What it covers

- **The exposure**: every message that passed the filter is stored by the homeserver in the clear. On one
  machine it reaches the homeserver over loopback and nothing leaves the host; on a homeserver somebody
  else runs, the filter's guarantee that only prose passes is the only one left.
- **Why it stays**: authenticity never rested on Matrix - Sokar signs every message and verifies it
  against its key directory. End-to-end encryption would add confidentiality against whoever runs the
  server, and the operator runs it. For an unattended sender it would mean device keys, cross-signing
  and key backup for every task ever started, and an unverified client shows an empty room rather than
  an error.
- **What would change it**: a homeserver somewhere the operator does not control. The decision is then
  taken again rather than inherited.
- **Room membership is not authorization**: what arrives is delivered only if its signature verifies
  against a key listed for that peer, and is held otherwise, whatever the room says.

## Open questions

None.
