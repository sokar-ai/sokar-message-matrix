# sokar-message-matrix
The Matrix transport: a project's room, where tasks talk

## Build

Java 25 - GraalVM for the native executable - and podman; `mvnw` brings the pinned Maven.

    ./mvnw verify                 # compile with NullAway, run the unit and integration tests
    ./mvnw -Pnative verify        # also the executable, the integration tests against it, the .deb and the .rpm

The integration tests start the homeserver of `homeserver/Dockerfile` in podman. `-Pnative` also needs
zlib's headers (`zlib1g-dev`) for the native image and `rpmbuild` for the `.rpm`.
