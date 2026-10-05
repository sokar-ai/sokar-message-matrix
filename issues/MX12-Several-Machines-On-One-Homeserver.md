# MX12 — Several Machines On One Homeserver

**Status:** later.

**What must be true.** A project whose tasks run on several machines has one conversation, in one room on
one central homeserver, where a task on one machine messages a task on another and a person sees all of
them. Each machine provisions only its own accounts there, and no machine holds a secret that lets it act
on another machine's accounts.

## Why

- **The transport reaches a named homeserver already:** `homeserver` in the settings, `https` with a
  public, an intranet or a self-signed certificate, registration with the homeserver's registration token
  (`SOKAR_MATRIX_REGISTRATION_TOKEN`), `--loopback-only` for an offline project.
- **But only one machine can provision there.** `setup` registers `@sokar` as the first account, which
  Tuwunel makes its administrator, and prints its token for Sokar to keep. A second machine running `setup`
  against the same homeserver is refused (78): `@sokar` exists, and that machine holds no token for it.
- **What the administrator is needed for:** `retire` deactivates a task's account, and `join --reset` or a
  task enrolled again sets a password - both only through the admin room (`!admin users …`), since Tuwunel
  offers no HTTP call for either. Registering an account, making the room and inviting need no
  administrator: the registration token and the room's power levels are enough.
- **The room is made by whoever runs `setup` first**; another machine's accounts join it only when a
  member with the power to invite invites them.
- **Sokar's side is built, and dormant** until `describe` lists `"takes": ["machine"]`: it passes
  `--machine`, records `machine`, `admitted`, `address` and `room` from `setup`, starts no task while
  `admitted` is false, and runs `setup` without a task through `SetUpMessages`.

## The shape

- **On a central homeserver, people are administered outside Sokar.** A person creates the project's room,
  invites each machine into it, and manages their own account. Sokar makes no room there and does not
  `join` a person: `join` answers 77, "people are managed at the homeserver itself".
- **The room is named in the project's settings**, `room`: an alias or a room id. `setup` refuses (78) a
  central homeserver without it, and `settings` checks it.
- **Each machine registers its own accounts** with the homeserver's registration token: `@sokar.<machine>`,
  its tasks' `@<container>.<machine>` and its relay `@sokar-relay-<project>.<machine>`.
- **A person admits a machine** by inviting `@sokar.<machine>` from their Matrix client and giving it the
  power to invite; there is no `admit` verb. Until then `setup` exits 0 with its `account` secrets,
  `"machine"` and `"admitted": false`, and no `conversation`, so the account it registered is kept; once
  invited it joins and prints `"admitted": true` and the room.
- **No administrator on a central homeserver.** `enroll` prints each task's password beside its token;
  `retire` and enrolling a task again act as the task's own account through the client API. A machine
  reaches only the accounts it made.
- **`--machine <name>`** on every lifecycle verb, announced by `describe` as `"takes": ["machine"]`: the
  host name, sanitized, kept in `setup`'s `account` and printed as `"machine"`; a name another machine
  holds on that homeserver is refused (78).
- **The account's own homeserver on loopback stays as it is**: `@sokar` its administrator, the room made by
  `setup`, `join` and `--reset` as today.
- **`doc/homeserver.md` says how to run the central homeserver**: where, TLS, the registration token, and
  the person's steps - make the room, invite each machine.

## Acceptance

- Across two machines, the Ubuntu VM and `silentmaxx-ms`, tasks on both in one project message each other,
  and a person in the room sees all of them.
- A task retired on one machine is gone, and the other machine's tasks and accounts are untouched.
- Seen to fail:
  - one machine acting on another machine's account (`retire`, or enrolling its task again) is refused;
  - `setup` without `room` on a central homeserver is refused with 78;
  - a `--machine` name another machine holds is refused with 78;
  - `join` on a central homeserver answers 77;
  - a machine not yet invited starts no task.
