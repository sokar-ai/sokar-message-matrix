# The local homeserver

One homeserver per account that runs Sokar, beside it on the same machine: Tuwunel in rootless podman,
reachable on `127.0.0.1` only, not federating. The package `sokar-matrix-homeserver` installs it as a
systemd user unit, disabled. A project's first task start enables and starts it; by hand:

    systemctl --user enable --now sokar-matrix-homeserver

Without the package, that first start stops with "the unit sokar-matrix-homeserver is not installed for
this account". With lingering on, the homeserver comes back after a reboot without anybody logging in.

## Settings and data

| What | Where | Default |
|---|---|---|
| Port, on `127.0.0.1` only | `SOKAR_MATRIX_PORT` in `~/.config/sokar/matrix/homeserver.conf` | the first free one from `8008` |
| Server name, part of every account id; fixed once the database exists | `SOKAR_MATRIX_SERVER_NAME` in the same file | `localhost` |
| Registration token, made on first start, readable by the account alone | `~/.config/sokar/matrix/registration-token` | - |
| Database | the podman volume `sokar-matrix-homeserver` | - |
| Container | `matrix-homeserver`, acted on only by the id in `$XDG_RUNTIME_DIR/sokar-matrix-homeserver.cid` | - |

The unit and `clear` stop and remove the container by the id podman gave the unit, never by its name. A
container somebody else named `matrix-homeserver` is neither replaced nor removed: the unit's start fails
and the journal says the name is in use.

Two accounts on one machine each get their own homeserver, on their own port.

The first account registered is the server's administrator. It is Sokar's own, `@sokar`, which makes
the project's room and an account per task, and deactivates a task's account when the task is removed.
The image is pinned by digest in `homeserver/Dockerfile`; it is about 107 MB and is pulled at the first
start.

## Resetting it

`sokar clear` takes away everything here: the transport's `clear` stops and disables the unit and removes
its container, its database, `homeserver.conf`, the registration token and the poll positions, and Sokar
removes its own entries beside it. The package stays.

By hand, starting from an empty database takes three things; while any old one is left, the transport
stops and says the homeserver "was reset":

1. `systemctl --user stop sokar-matrix-homeserver` and `podman volume rm sokar-matrix-homeserver`;
2. what Sokar keeps of the transport: `sokar vault remove transport/matrix/account` and
   `sokar vault remove transport/matrix/project/<project>` for each project;
3. the transport's state: `rm -r ~/.local/state/sokar/transports/matrix`.

`registration-token` and `homeserver.conf` stay. The next task start registers `@sokar` again.

## On Fedora

The `.rpm`s install the same way. With SELinux enforcing, podman relabels the registration token for the
container, and the account still reads it.
