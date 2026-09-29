# MX06 — The package starts the homeserver

**What must be true.** Installing the transport's package is enough to have the homeserver it talks to:
Tuwunel in rootless podman, pinned by digest, on loopback only, started for the account that runs Sokar
and kept running beside it. `sokar`'s setup does not start it.

The operator decided this repository owns it: the homeserver is a service beside Sokar, never a task,
and its image pin lives beside the transport that needs it.

## What it covers

- **A systemd user unit or a podman quadlet** in the `.deb` and the `.rpm`, which runs the image of
  `doc/homeserver.md` with its configuration: loopback only, federation off, the database on a volume
  that outlives the container.
- **The image pin in one place**, moved like every other pin: reviewed, never by a moving tag.
- **The registration token**, created on the machine, readable only by the account that runs Sokar, and
  handed to podman through a file rather than an argument.
- **`describe` names the homeserver in `hosts`**, read from the configuration the package installs -
  the one file `describe` may read, since it is not set by the caller - so that the egress Sokar
  permits is what the package started.
- **What `sokar` needs to know of it**, stated in `doc/`: that it is on loopback, on which port, and
  that the provisioning account must be the first to register. The provisioning itself is `sokar`
  B86.
- Proven by installing the package on the ubuntu test VM and seeing the homeserver answer on loopback
  after a reboot.

## Open questions

1. **Quadlet or user unit.** A quadlet is podman's own way to describe a container as a unit; a plain
   user unit calling `podman run` works on older podman as well.
2. **Whether Dependabot moves the pin where it now lives**: the `FROM` line of `homeserver/Dockerfile`,
   `tag@digest`, watched by the `docker` ecosystem. The tests already start the homeserver from that
   line; what the package ships has to take it from there too, not from a copy.
3. **Which port**, and whether it is fixed or chosen at installation.
4. **Who registers the provisioning account first** - the package after starting the homeserver, or
   `sokar` on its first use - so that no task's account can ever be the first.
