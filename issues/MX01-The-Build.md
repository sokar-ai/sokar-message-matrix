# MX01 — The build

**What must be true.** The repository builds, tests and packages the Matrix transport the way every
other Java repository of Sokar does, so that nothing written afterwards has to be retrofitted.

The requirement is `sokar-project` PJ02 ([index](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md)).

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
- **The changelog**, started with this issue.

## Done when

`mvnw verify` builds, tests and packages on the pinned JDK locally and in CI; each guard above has been
seen to fail on a deliberate breach and pass once it is restored.

## Open questions

None.
