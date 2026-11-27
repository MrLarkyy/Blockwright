# Client launch

`launcher` starts the real Minecraft client, downloaded at runtime into the user's cache, with the Fabric loader and
the Blockwright agent (`agent`, a Fabric mod). Nothing from Mojang is part of Blockwright: the client, its libraries
and the Fabric loader runtime are fetched by the launcher and verified against their hashes.

```kotlin
val cache = CacheLayout(CacheLayout.defaultRoot())
JdkHttpTransport(DownloadPolicy()).use { transport ->
    val version = VersionResolver(cache, transport).resolve(ResolveRequest("26.3"))
    val client = ClientLauncher(cache, Downloader(transport)).launch(
        ClientLaunchConfig(
            version = version,
            agentJar = Path.of("agent/build/libs/agent-0.1.0-SNAPSHOT.jar"),
            profile = LaunchProfile.LOGIC,
            serverHost = "127.0.0.1",
            serverPort = server.port,          // an offline-mode server, e.g. from server-runner
            username = "bw1",
            artifactDir = Path.of("build/blockwright-artifacts/bw1"),
        ),
    )
    client.awaitJoined(180.seconds)            // fails fast with the client's reason when the server refuses
    val result = client.stop()                 // graceful: exit 0, the server logs "bw1 left the game"
}
```

## Profiles

| profile | display | GPU | sound | render assets | use |
|---|---|---|---|---|---|
| `LOGIC` | none | no-op device | off | not loaded | correctness tests, bench `logic` |
| `REFERENCE` | real window | OpenGL | on | loaded | the parity baseline only |

Both profiles run the same agent, `options.txt`, JVM heap and quick-play join; only the cuts differ.

### What `logic` cuts

All cuts are in the agent, each a thin mixin that calls Kotlin, and are active only with `-Dblockwright.profile=logic`:

- **Window and display**: Minecraft 26.3 uses SDL3. Right before `SDL_Init` the agent selects SDL's `dummy` video and
  audio drivers, so a window exists without a display server, GL driver or audio device (Windows and Linux).
- **GPU**: Minecraft's backend list (`PreferredGraphicsApi.getBackendsToTry`) is replaced by the agent's
  `NoopGpuBackend`: a hidden dummy window and a `GpuDevice` that keeps sizes and formats but compiles no shader and
  draws nothing. The swapchain is never acquired, so nothing is presented.
- **Frame**: the frame loop keeps window updates, mouse input, picking and the frame limiter (60 per second), but
  `GameRenderer.extract` and `GameRenderer.render` do not run. A finished loading overlay is removed by the agent (in
  vanilla that happens during render-state extraction).
- **Resources**: reload listeners that only feed rendering or audio are not registered: everything in
  `net.minecraft.client.renderer`, `.sounds`, `.resources.model`, `.resources.palette`, `.gui.font`, `.particle` and the
  colour-map listeners. Languages, registries and other game data load as usual. Every lookup of `textures/`,
  `models/`, `blockstates/`, `items/`, `atlases/`, `sounds/` or `sounds.json` is counted (`assetReads`).
- **Sound**: the sound engine never loads its library.
- **Particles**: particles are not created (their sprites come from the atlases above).
- **Loading screen**: the level counts as loaded once the chunk under the player arrived (vanilla waits for that
  section to be compiled for rendering, which never happens).

Never cut: ticks, physics and movement prediction, input handling, networking and anything else the server sees. The
parity test below checks it.

### JVM flags and options

`LOGIC` and `REFERENCE`: `-Xms256m -Xmx1G -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -XX:-UsePerfData -Xss1m`;
`LOGIC` adds `-Djava.awt.headless=true`. The version's own JVM
arguments follow (natives, `-cp`), then Fabric's `KnotClient` main class, the version's game arguments with the
`is_quick_play_multiplayer` feature only (never demo mode or a custom resolution) and `--graphicsBackend opengl` (Vulkan
is never probed). Everything goes through an `@argfile` in the run directory: the classpath alone exceeds the Windows
command line limit.

`options.txt`: `renderDistance:2`, `simulationDistance:5`, `maxFps:60`, `enableVsync:false`,
`inactivityFpsLimit:minimized`, `pauseOnLostFocus:false`, `onboardAccessibility:false`, `skipMultiplayerWarning:true`,
`joinedFirstServer:true`, `tutorialStep:none`, `narrator:0`, `autoJump:false`, `realmsNotifications:false`,
`telemetryOptInExtra:false`, `soundCategory_master:0.0`.

Offline identity: the UUID is the one an offline server computes (`OfflinePlayer:<name>`), names are 3-16 letters,
digits or underscores. The access token is the non-secret `0`, and `minecraft.api.discovery.host` points at an
unreachable loopback URL, so the client never contacts Mojang services with it. The logic profile uses no external
asset objects: the run's assets directory is empty (Minecraft logs that the asset index is missing; the window icon
is not set).

## Fabric runtime

The launcher build pins the Fabric loader and the libraries of its `fabric-loader-<version>.json` (sponge-mixin, ASM)
from Gradle's resolved `fabricRuntime` configuration into `fabric-runtime.json`: coordinates, SHA-1, size and
repository (`maven.fabricmc.net` for `net.fabricmc`, Maven Central otherwise). At launch each jar is downloaded into the
cache's `libraries/` with the usual limits and kept only when SHA-1 and size match; `DownloadPolicy` allows exactly
those two hosts besides Mojang's. `ClientLauncher.prepare()` does that download ahead of time (the bench calls it
before anything is timed); a launch after it only verifies the cached jars. Versions live in `gradle/libs.versions.toml` (`minecraft`, `fabric-loader`,
`sponge-mixin`, `asm`).

## Checks before launch

Before any download, file or process: the username, the agent jar (its `fabric.mod.json`, at most 64 KiB, must be the
Blockwright agent and depend on exactly the resolved version, e.g. `26.3`) and the JVM (read from its `release` file,
at least the version JSON's `javaVersion.majorVersion`). Versions with only legacy `minecraftArguments` are refused.

## Status lines

The agent prints `BLOCKWRIGHT_AGENT <token> state=<state> key=value ...` on the client's stdout. The token is a
random 128-bit value the launcher creates for each launch and passes as `-Dblockwright.statusToken`; the launcher
accepts only console lines that start with the prefix followed by that token. Server text that reaches the console
(chat, a kick reason, also one with an embedded newline that starts a console line of its own) cannot know the token,
so it cannot complete `joined`, `traced` or any other state. Values never contain whitespace (replaced by `_`), so a
server's disconnect reason cannot forge a field either.

| state | when | fields |
|---|---|---|
| `starting` | Fabric pre-launch | `profile` |
| `joined` | first tick in a level with a player and no screen | `profile player video gpu sound assetReads soundLoadsBlocked` |
| `failed` | disconnected screen before the join, or Minecraft cannot create its window or GPU backend | `reason` |
| `disconnected` | disconnected screen after the join | `reason` |
| `tracing` / `traced` | movement trace started / written | `script ticks` / `ticks file` |
| `alert` | Minecraft wanted to show a blocking error box | `reason` (the box's text) |
| `stopping` | a stop was requested | `reason` (`stdin-closed`, `parent-exited`) |

A logic client reports `video=dummy gpu=Blockwright_headless sound=off assetReads=0`.

In both profiles Minecraft's blocking error boxes are not shown: nobody can click them in a client the launcher started,
so a box would hang it until the join timeout (seen with no usable OpenGL under a display without GLX). The box's text
becomes an `alert` line, and a window or GPU backend that cannot be created a `failed` line: `awaitJoined` fails at
once with Minecraft's reason, as it does when the client process exits.

## Stopping and parent death

`ClientProcess.stop()` closes the client's stdin. The agent turns EOF into `Minecraft.stop()` on the client thread, so
the client disconnects and exits 0; after `stopTimeout` (30 s) the launcher kills the process tree. An interrupted
`stop()` skips the wait, still kills and cleans up and keeps the interrupt flag; `stop()` is idempotent. Without
`stop()`, a JVM shutdown hook kills the tree and removes the run directory.

When the owning JVM dies without hooks (Task Manager, `kill -9`, Gradle cancelling a bench on Windows), the client
stops itself: its stdin reaches EOF and a watchdog on `-Dblockwright.parentPid` fires (its own thread polls the parent
every 500 ms; never the JDK's process reaper thread, whose small stack overflows on Fabric class loading). Both go
through one `ShutdownCoordinator`; if the client does not exit within 10 s it halts with code 70.

Its `blockwright-client-*` run directory in the temp folder is left behind at first. Each run directory records the
owning JVM and the client process (pid and start time) in `blockwright-owners`; the next `ClientLauncher.launch` in any
JVM removes every such directory whose owner and client are both gone. A directory without that record or a symbolic
link is never touched, and a still-locked one is tried again at the following launch.

## Artefacts

`artifactDir/console.log` holds everything the client printed, `artifactDir/latest.log` Minecraft's own log. Each
console line is cut at 8192 characters (the rest is skipped, never buffered, and the line ends with
`[<n> more chars cut]`), and only the last 50 lines are kept in memory for error messages, so server text cannot grow
the launcher's heap. The run
directory (`options.txt`, the argfile, the game directory) is removed after the stop unless `keepRunDir` is set; on
Windows a directory still locked is kept and reported as `runDirKept`.

## Movement trace and parity

`ClientLaunchConfig.trace = TraceRequest(path)` makes the agent play its built-in `walk-jump-strafe` script (110 ticks
of walking, jumping, strafing and sprinting through the real key mappings) from the first tick after the join with the
player on the ground, and write one line per tick: position relative to the start, velocity, ground contact and the
input the player saw. `MovementParityIntegrationTest` runs `REFERENCE` and then `LOGIC` on one flat server where every
player spawns on the same block, and requires identical velocity, ground contact and input and positions within 1e-9.

## Integration tests

```
./gradlew :launcher:integrationTest -Pblockwright.acceptEula=true
```

They start a real Paper server (the EULA is accepted only by this flag, for that run's temporary server), download the
client and the Fabric runtime into the cache next to the default one (`blockwright-it`, or
`-Dblockwright.it.cacheDir=<dir>`) and build the agent jar first. `HeadlessClientIntegrationTest` (join without
display, 30 s in the world, graceful stop, a refused join failing fast), `ClientParentDeathIntegrationTest` (the client
exits within 20 s after its parent is killed) and `MovementParityIntegrationTest` (needs a display for the reference:
skipped without one, unless `-Pblockwright.requireParity=true` makes that a failure; Linux CI runs it under `xvfb-run`
with a 24-bit GLX screen and Mesa's llvmpipe software renderer). Without the EULA flag every one of them is reported as skipped. In CI the `client-integration` job runs them
on Windows and Linux only on a manual `workflow_dispatch` with `accept_eula`.
