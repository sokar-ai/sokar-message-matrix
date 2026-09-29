# MX01 — The build

**What must be true.** The repository builds, tests and packages the Matrix transport the way every
other Java repository of Sokar does, so that nothing written afterwards has to be retrofitted.

Sokar's side of the transport is `sokar` B86.

## What it covers

- **Maven through `mvnw`**, with `distributionSha256Sum` beside the version in
  `.mvn/wrapper/maven-wrapper.properties`, so the wrapper refuses a Maven it was not pinned to.
- **The pinned JDK**: CI compiles with the GraalVM pins `sokar-machines jdk --github` installs and checks
  against its digest - never `setup-graalvm` or `setup-java` by a moving version.
- **A native executable**, `sokar-message-transport-matrix`, so the installed transport needs no Java at
  run time, as the other transports ship.
- **Packages**: a `.deb` and an `.rpm` that install the executable as
  `/usr/libexec/sokar/transports/sokar-message-transport-matrix`, where Sokar finds a transport by scanning.
- **Every action pinned to a commit** with the release beside it (`@<sha> # vX.Y.Z`), moved by Dependabot
  (`github-actions`, weekly, grouped, `cooldown: default-days: 3`, reviewed, never auto-merged), and
  refused otherwise by `sokar-release check-actions` in the build. No third-party action is given a
  signing key.
- **Null-checking from the first commit**: every package holding main code is `@NullMarked`, NullAway
  runs in the main compile as an error scoped by `OnlyNullMarked`, and a test fails on an unmarked
  package - an unmarked package is otherwise skipped in silence.
- **A test that refuses a link to a requirement's file** in this repository's Markdown, since a
  requirement is linked by its number and its index.
- **The integration tests run against the native executable too**, started as a separate process the
  way Sokar starts it, not only through `Main.run` in the test JVM. A native image leaves out what it
  cannot see being reached - reflection, service loading, resources - so a green JVM run does not
  prove the executable Sokar runs.
- **The changelog**, started with this issue.

## Done when

`mvnw verify` builds, tests and packages on the pinned JDK locally and in CI; each guard above has been
seen to fail on a deliberate breach and pass once it is restored.

## Where it stands

Built, and each guard seen to fail on a deliberate breach: `mvnw` with its checksum, NullAway in the main
compile, the test for an unmarked package, the test for a link to an issue's file, the integration tests
against the native executable (all fail when pointed at `/bin/false`), and `check-actions` (exit 1 on an
action pinned by tag). The packages were inspected: the executable at 0755 in
`/usr/libexec/sokar/transports/`, `Depends: sokar, libc6 (>= 2.34), zlib1g` as the executable links them,
and the license and the bill under `/usr/share/doc/`. `.github/workflows/build.yml` runs the whole of it.

**What is left: the first CI run**, which only a push starts. Until it is green, the workflow's own
steps - `JAVA_HOME_25_X64`, `jdk --github`, podman on the runner - are read from the other repositories,
not measured here.

## Settled

- **The pinned GraalVM in CI**: the one place is `sokar-machines jdk --github`, which holds the pin,
  the download, the digest check and the export. This repository carries only the two commands that
  call it, inline in `build.yml`: a `ci-tools` profile resolves `org.fuin.sokar:sokar-machines` with
  `dependency:build-classpath`, and the runner's own Java 25 (`JAVA_HOME_25_X64`) runs its `Main jdk
  --github`. That Java compiles nothing that is published. The `actions/cache` step is pinned by
  commit like every other.
- **Linking**: glibc, dynamically, with no `--libc` argument - a transport is a program Sokar runs on
  the host, like `sokar` itself. Static musl is for what podman runs outside Sokar's process. The build
  host needs `zlib1g-dev`.
- **Packages**: `jdeb` for the `.deb`, `rpm-maven-plugin` for the `.rpm`, with a CycloneDX bill in the
  package's documentation directory. They are built in the native profile of the one module, beside the
  executable they carry: with one executable, `dist-deb` and `dist-rpm` modules would be a second place
  for what that profile already has.

## Open questions

None.
