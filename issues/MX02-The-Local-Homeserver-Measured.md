# MX02 — The local homeserver, measured

**What must be true.** The minimum arrangement is known from measurement rather than assumed: one
machine, a homeserver in podman beside Sokar, reached on loopback, nothing federating and nothing
leaving the host.

The requirement is `sokar-project` PJ02 ([index](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md)):
messaging requires a running homeserver, so this arrangement is the minimum rather than a convenience,
and one a developer starts and forgets.

## What it covers

Measured in this repository's own account on the ubuntu test VM, in podman:

- **Which homeserver.** A single binary with an embedded database, so no database server is needed
  beside it. The candidates are compared by what they need, not by reputation.
- **What it needs**: image size, memory at rest, disk after a room with some history, and what it writes
  where.
- **How fast it starts**, from `podman run` to a client API that answers.
- **What it listens on**, and that it can be bound to loopback only with federation off.
- **What it offers for the calls the rest of PJ02 depends on**, without building them here: creating
  and deleting an account from the host with an administrative credential, a room, a sync, read
  receipts.

The result goes to `doc/`, as what a person running the transport needs to know. The homeserver stays a
service beside Sokar and never a task: a task container binds nothing, and a homeserver listens.

## Open questions

1. **Which homeserver**, among the single-binary ones with an embedded database.
2. **Where its image comes from and how it is pinned** - a registry image by digest, or one built here.
