# Benchmark harness

The harness lives in the `orchestrator` module (`dev.blockwright.orchestrator.bench`). It starts N clients of a
profile, measures them and compares the numbers with the versioned budgets in `perf/budgets.json`.

## Running

```
./gradlew :orchestrator:bench                         # local, report only
./gradlew :orchestrator:bench -Pbench.strict=true     # CI mode
./gradlew :orchestrator:bench -Pbench.profile=stub    # one profile
./gradlew :orchestrator:bench -Pbench.budgets=/path/to/budgets.json   # other budgets file
./gradlew :orchestrator:bench -Pbench.profile=logic -Pblockwright.acceptEula=true   # real headless clients
```

The `logic` profile starts real clients (see [client-launch.md](client-launch.md)) against a local Paper server, which
runs only after you accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that run with
`-Pblockwright.acceptEula=true`. Without it, or without the agent jar (the task builds `:agent:jar` itself), the
profile has no target and is reported `NOT_RUN`. The client is downloaded into the user cache (`CacheLayout`),
`-Dblockwright.it.cacheDir=<dir>` moves it.

Output: `orchestrator/build/reports/bench/bench-report.json` (machine readable) and `bench-summary.md`. Both are
overwritten per run and written even when the gate fails. The CI job `bench` runs strict on Linux and uploads the
folder as the `bench-report` artefact.

Exit codes: `0` pass, `1` a budget is exceeded (or a required profile has no target, or a metric failed), `2`
configuration or usage error (unknown metric key, non-positive budget, bad `schemaVersion`, a warm budget without a
warm sample). A breach prints `FAIL profile=P metric=M value=V budget=B unit=U`.
A metric that could not run but is budgeted under `--strict` prints `value=NOT_RUN`. Gradle wraps the exit code: any
non-zero harness exit shows as Gradle exit 1; run `dev.blockwright.orchestrator.bench.BenchMain` directly for the exact
0/1/2 codes.

## Metrics

| Key | Meaning |
|---|---|
| `coldStartMs` | Process start to `READY` of the first client of the run (fresh JVM; the OS page cache is not dropped). For a real client `READY` is the agent's `joined`; the target's own setup (`prepare()`: resolving the client, starting the server) is not timed. |
| `warmStartMs` | Median start of the later launches (clients 2..N plus `warmRuns` relaunches). |
| `pssMiB` | Proportional set size of a client and its descendants, worst client, measured with all clients up. |
| `cpuIdlePercent` / `cpuActivePercent` | CPU time of a client and descendants over the idle / active window, percent of one core, worst client. Active = `startLoad()` is on. |
| `screenshotP95Ms` | 95th percentile (nearest rank) of the `screenshot()` round trip. |

PSS is read from `/proc/<pid>/smaps_rollup` (fallback: the sum of `/proc/<pid>/smaps`) and exists on Linux only. On
Windows and macOS it is reported `NOT_RUN` with a reason; there is no RSS substitute. A `NOT_RUN` metric is not
gated, except with `--strict`, where a budgeted `NOT_RUN` metric of a profile that ran fails (a non-required profile without a target stays `NOT_RUN`) (CI runs strict on Linux, so a broken probe
cannot pass silently).

Two metrics are `NOT_RUN` by configuration rather than failure: `screenshotP95Ms` when the profile sets
`"screenshots": 0`, and `cpuActivePercent` when the client cannot generate load (`BenchClient.supportsLoad` false).
The `logic` client has neither a screenshot nor a load command until the control channel exists (BW-14), so its
budgets cover `coldStartMs`, `warmStartMs`, `pssMiB` and `cpuIdlePercent` only.

## Budgets file

```json
{ "schemaVersion": 1,
  "profiles": { "stub": { "required": true, "clients": 2, "warmRuns": 2, "idleSeconds": 2, "activeSeconds": 2,
                          "screenshots": 10, "startTimeoutSeconds": 60, "budgets": { "pssMiB": 256 } } } }
```

Budgets are absolute ceilings per profile. A required profile without a bench target fails the gate; a non-required
one is reported `NOT_RUN`, also under `--strict`. The `stub` profile gates the harness itself (a stand-in child JVM), not the product; its
ceilings are generous. `logic` is not required (it needs the EULA flag) and has generous first ceilings (cold start
90 s, warm start 60 s, PSS 1024 MiB, idle CPU 100 %) to be tightened from the first CI numbers; `snapshot` and `live`
are listed without budgets until a real target exists. `screenshots` may be `0` (the metric is skipped), never
negative.

## Adding a profile

1. Implement `BenchTarget` (and `BenchClient`) for the real client; `ProcessBenchTarget` covers anything that speaks
   the line protocol `READY`, `SHOT` -> `SHOT ok`, `LOAD on|off`, `QUIT`. Setup that must not be timed goes into
   `prepare()`, whatever the target owns (a server) is released in `close()`, which `BenchMain` calls after the
   profile, also on failure. A real client must stop itself when the bench JVM dies without hooks (Gradle cancelling
   on Windows): `ChildProcessReaper` runs only on a normal JVM exit. `ClientBenchTarget` (via `ClientLauncher`) gets
   that from the agent's stdin and parent-pid watchdogs and also tracks each client in the reaper.
2. Register it in `BenchTargets.forProfile`.
3. Add its budgets to `perf/budgets.json`.

## Report schema

`BenchReport` (`schemaVersion` 1): `startedAt` (informational), `gitSha` (`GITHUB_SHA` or null), `os`, `arch`, `jdk`,
`cpus`, `strict`, `passed`, and per profile its configuration plus one entry per metric with `status`
(`PASS|FAIL|NOT_RUN`), `samples`, `aggregate`, `budget` and `reason`.
