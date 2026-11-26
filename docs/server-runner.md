# Server runner

`server-runner` starts throwaway [Paper](https://papermc.io) test servers for the plugins under test. It is a Kotlin
library (`dev.blockwright.serverrunner`); the `blockwright` CLI will expose it later.

```kotlin
val server = PaperServerRunner().start(
    ServerRunConfig(
        version = "26.3",
        build = 262,                       // null: the newest STABLE build
        plugins = listOf(Path.of("build/libs/my-plugin.jar")),
        cacheRoot = userCache,             // e.g. the Blockwright cache directory
        artifactDir = Path.of("build/blockwright-artifacts/run-1"),
        acceptEula = userAcceptedTheEula,  // the user's explicit choice, never a default
    ),
)
server.onLine { println(it) }
server.console("say hello")
val result = server.stop()                 // logs stay in artifactDir
```

## The Minecraft EULA

A Paper server runs only after its operator accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA). Blockwright
never accepts it for you: `ServerRunConfig.acceptEula` is `false` by default, and without `true` the runner throws
`EulaNotAcceptedException` before it requests anything from the network, writes a file or starts a process. With
`true` it writes `eula.txt` into that run's throwaway directory only; no system property, no shared directory.

## What a start does

1. Resolves the build from the PaperMC Fill v3 API (`fill.papermc.io`): the pinned build, or the newest `STABLE` one.
2. Downloads the jar from `fill-data.papermc.io` into `<cacheRoot>/paper/<version>/<build>/` and keeps it only when its
   size and sha256 match the Fill metadata. A pinned build that is already cached starts without any request.
3. Runs Paperclip once per cached build in patch-only mode into `<build>/bundler/`. Paperclip downloads the Mojang
   server jar from Mojang at that point, into the user's cache; Blockwright never ships it.
4. Creates a temporary run directory with `server.properties`, the plugins in `plugins/` and `eula.txt`.
5. Starts `java -jar <paper.jar> --nogui` and waits for Paper's `Done (...)! For help, type "help"` line.

Server-provided URLs are untrusted: only `https` on the two Fill hosts is followed (redirects included, at most 3),
metadata is capped at 4 MiB and 30 s, the jar at its declared size (hard maximum 256 MiB) and 10 min.

## Test server defaults

`online-mode=false`, `server-ip=127.0.0.1`, `server-port=<free loopback port>`, `enable-rcon=false`,
`enable-query=false`, `enforce-secure-profile=false`. Other keys can be set with `propertyOverrides`; `online-mode`,
`server-ip`, `server-port` and `enable-rcon` belong to the runner and are rejected (use `port` to pin the port).

## Stopping and artefacts

`stop()` sends `stop` to the console, waits `stopTimeout` (30 s), then kills the server and every descendant process.
A JVM shutdown hook does the same if the owner never calls `stop()`. Afterwards `artifactDir` holds `console.log`
(everything the server printed) and `latest.log` (Paper's own log); the run directory is removed unless `keepRunDir`
is set (on Windows a directory still locked after the kill is kept and reported as `runDirKept`).

A start that fails (early exit, e.g. a port taken in the meantime, or no ready line within `readyTimeout`) throws
`ServerStartException` with the exit code and the last console lines and leaves no process behind.

Limits: Java is checked against the build's minimum only for the runner's own runtime; a custom `javaExecutable` is
not probed. Velocity networks are not supported yet.

## Integration test

The unit tests use a fake server and fixture JSON and never touch Paper or the network. The real end-to-end test is
tagged `integration`, is excluded from `./gradlew build` and runs only when you accept the EULA for that run:

```
./gradlew :server-runner:integrationTest -Pblockwright.acceptEula=true
```

Without the property it is reported as skipped. It needs network access to `fill.papermc.io`,
`fill-data.papermc.io` and Mojang; `-Dblockwright.it.cacheDir=<dir>` moves its cache (default
`server-runner/build/blockwright-cache`). In CI the `server-integration` job runs it only on a manual
`workflow_dispatch` with the `accept_eula` box ticked.
